package com.tailorcards.api.buylistchat;

import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ChatMessageRequest;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ChatTurnResponse;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ConfirmRequest;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ConfirmResponse;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.DraftView;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.GuestSessionRequest;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.LineUpdateRequest;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.OtpRequest;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.OtpVerifyRequest;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.PasteRequest;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.SessionResponse;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.StatusResponse;
import com.tailorcards.api.buylistchat.entity.BuylistChatSession;
import com.tailorcards.api.buylistchat.entity.BuylistDraft;
import com.tailorcards.api.buylistchat.identity.BuylistIdentityService;
import com.tailorcards.api.buylistchat.identity.ClientIp;
import com.tailorcards.api.buylistchat.identity.TurnstileVerifier;
import com.tailorcards.api.buylistchat.intake.CsvItemParser;
import com.tailorcards.api.buylistchat.intake.ItemInput;
import com.tailorcards.api.buylistchat.intake.ItemNormalizer;
import com.tailorcards.api.listing.ListingImageSanitizer;
import com.tailorcards.api.service.BuylistStorageService;
import com.tailorcards.api.trade.llm.LlmRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Customer-facing buylist chat. Public (no Basic Auth); every call except status and the code
 * endpoints needs the session token from email verification in the X-Buylist-Session header.
 */
@RestController
@RequestMapping("/api/buylist-chat")
@Tag(name = "Buylist Chat", description = "AI-assisted buylist intake: verify email, build a list, review, confirm")
public class BuylistChatController {

    static final String SESSION_HEADER = "X-Buylist-Session";
    private static final long MAX_CSV_BYTES = 2L * 1024 * 1024;
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final int GUEST_SESSIONS_PER_HOUR = 10;

    private final BuylistChatProperties properties;
    private final BuylistIdentityService identity;
    private final BuylistDraftService drafts;
    private final BuylistChatService chat;
    private final BuylistChatSubmissionService submissions;
    private final ItemNormalizer normalizer;
    private final CsvItemParser csvParser;
    private final ListingImageSanitizer sanitizer;
    private final BuylistStorageService storage;
    private final TurnstileVerifier turnstile;
    private final LlmRateLimiter rateLimiter;
    private final com.tailorcards.api.buylistchat.pricing.StoreCardService storeCardService;

    public BuylistChatController(BuylistChatProperties properties, BuylistIdentityService identity,
                                 BuylistDraftService drafts, BuylistChatService chat,
                                 BuylistChatSubmissionService submissions, ItemNormalizer normalizer,
                                 CsvItemParser csvParser, ListingImageSanitizer sanitizer, BuylistStorageService storage,
                                 TurnstileVerifier turnstile, LlmRateLimiter rateLimiter,
                                 com.tailorcards.api.buylistchat.pricing.StoreCardService storeCardService) {
        this.storeCardService = storeCardService;
        this.properties = properties;
        this.identity = identity;
        this.drafts = drafts;
        this.chat = chat;
        this.submissions = submissions;
        this.normalizer = normalizer;
        this.csvParser = csvParser;
        this.sanitizer = sanitizer;
        this.storage = storage;
        this.turnstile = turnstile;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/status")
    @Operation(summary = "Whether the chat is enabled, plus public widget settings")
    public StatusResponse status() {
        String siteKey = properties.getTurnstile().getSecret().isBlank() ? null : properties.getTurnstile().getSiteKey();
        return new StatusResponse(properties.isEnabled(), properties.isRequireEmailVerification(), siteKey,
                properties.getLimits().getMaxLines(), properties.getLimits().getMaxMessageLength(), 2000);
    }

    @PostMapping("/session/guest")
    @Operation(summary = "Start without an email code (only while email verification is turned off)")
    public SessionResponse guestSession(@RequestBody(required = false) GuestSessionRequest request, HttpServletRequest http) {
        requireEnabled();
        if (properties.isRequireEmailVerification()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please verify your email to continue.");
        }
        String ip = ClientIp.of(http);
        if (!turnstile.verify(request == null ? null : request.turnstileToken(), ip)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please complete the verification check and try again.");
        }
        rateLimiter.checkRateLimit("buylist-guest:" + ip, GUEST_SESSIONS_PER_HOUR, java.time.Duration.ofHours(1),
                "Too many new chats from this network. Please try again later.");
        BuylistIdentityService.IssuedSession session = identity.createGuestSession(ip);
        return new SessionResponse(session.token(), null, session.expiresAt());
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Email a 6-digit sign-in code")
    public ResponseEntity<Void> requestCode(@RequestBody OtpRequest request, HttpServletRequest http) {
        requireEnabled();
        identity.requestCode(request.email(), request.turnstileToken(), ClientIp.of(http));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Exchange the code for a session token")
    public SessionResponse verifyCode(@RequestBody OtpVerifyRequest request, HttpServletRequest http) {
        requireEnabled();
        BuylistIdentityService.IssuedSession session = identity.verifyCode(request.email(), request.code(), ClientIp.of(http));
        return new SessionResponse(session.token(), session.email(), session.expiresAt());
    }

    @PostMapping("/drafts")
    @Operation(summary = "Open (or resume) the caller's draft")
    public DraftView openDraft(@RequestHeader(value = SESSION_HEADER, required = false) String token) {
        BuylistChatSession session = session(token);
        return drafts.view(drafts.openDraft(session.getEmail()));
    }

    @GetMapping("/drafts/{draftId}")
    @Operation(summary = "Draft with line statuses, progress and summary (poll while cards are being checked)")
    public DraftView getDraft(@PathVariable String draftId,
                              @RequestHeader(value = SESSION_HEADER, required = false) String token) {
        BuylistChatSession session = session(token);
        return drafts.view(drafts.ownedDraft(draftId, session.getEmail()));
    }

    @PostMapping("/drafts/{draftId}/messages")
    @Operation(summary = "Send a chat message")
    public ChatTurnResponse sendMessage(@PathVariable String draftId, @RequestBody ChatMessageRequest request,
                                        @RequestHeader(value = SESSION_HEADER, required = false) String token) {
        BuylistChatSession session = session(token);
        return chat.handleTurn(session.getEmail(), session.getId(), draftId, request.message());
    }

    @PostMapping("/drafts/{draftId}/paste")
    @Operation(summary = "Add a pasted list (one item per line)")
    public DraftView paste(@PathVariable String draftId, @RequestBody PasteRequest request,
                           @RequestHeader(value = SESSION_HEADER, required = false) String token) {
        BuylistChatSession session = session(token);
        BuylistDraft draft = drafts.ownedOpenDraft(draftId, session.getEmail());
        String text = request.text() == null ? "" : request.text();
        if (text.length() > properties.getLimits().getMaxPasteChars()) {
            throw new IllegalArgumentException("That list is too long to paste. Upload a CSV instead.");
        }
        List<String> lines = Arrays.stream(text.split("\\R")).filter(l -> !l.isBlank()).toList();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Paste at least one card.");
        }
        if (lines.size() > properties.getLimits().getMaxLines()) {
            throw new IllegalArgumentException("A list can have at most " + properties.getLimits().getMaxLines() + " items.");
        }
        drafts.addItems(draft, normalizer.normalize(lines));
        return drafts.view(drafts.ownedDraft(draftId, session.getEmail()));
    }

    @PostMapping(value = "/drafts/{draftId}/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add items from a CSV file (name, set, number, quantity, condition, variant)")
    public DraftView uploadCsv(@PathVariable String draftId, @RequestPart("file") MultipartFile file,
                               @RequestHeader(value = SESSION_HEADER, required = false) String token) throws IOException {
        BuylistChatSession session = session(token);
        BuylistDraft draft = drafts.ownedOpenDraft(draftId, session.getEmail());
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose a CSV file.");
        }
        if (file.getSize() > MAX_CSV_BYTES) {
            throw new IllegalArgumentException("CSV files can be at most 2 MB.");
        }
        CsvItemParser.Parsed parsed = csvParser.parse(file.getBytes(), properties.getLimits().getMaxLines(), normalizer);
        List<ItemInput> items = new ArrayList<>(parsed.structured());
        if (!parsed.freeText().isEmpty()) {
            items.addAll(normalizer.normalize(parsed.freeText()));
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("No cards found in that file.");
        }
        drafts.addItems(draft, items);
        return drafts.view(drafts.ownedDraft(draftId, session.getEmail()));
    }

    @PatchMapping("/drafts/{draftId}/lines/{lineId}")
    @Operation(summary = "Change a line's quantity, condition or variant")
    public DraftView updateLine(@PathVariable String draftId, @PathVariable long lineId,
                                @RequestBody LineUpdateRequest request,
                                @RequestHeader(value = SESSION_HEADER, required = false) String token) {
        BuylistChatSession session = session(token);
        drafts.updateLine(drafts.ownedOpenDraft(draftId, session.getEmail()), lineId, request);
        return drafts.view(drafts.ownedDraft(draftId, session.getEmail()));
    }

    @DeleteMapping("/drafts/{draftId}/lines/{lineId}")
    @Operation(summary = "Remove a line")
    public DraftView removeLine(@PathVariable String draftId, @PathVariable long lineId,
                                @RequestHeader(value = SESSION_HEADER, required = false) String token) {
        BuylistChatSession session = session(token);
        drafts.removeLine(drafts.ownedOpenDraft(draftId, session.getEmail()), lineId);
        return drafts.view(drafts.ownedDraft(draftId, session.getEmail()));
    }

    @PostMapping(value = "/drafts/{draftId}/lines/{lineId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Attach a photo to a line (JPEG/PNG/WebP, 5 MB; location data is stripped)")
    public DraftView uploadPhoto(@PathVariable String draftId, @PathVariable long lineId,
                                 @RequestPart("file") MultipartFile file,
                                 @RequestHeader(value = SESSION_HEADER, required = false) String token) throws IOException {
        BuylistChatSession session = session(token);
        BuylistDraft draft = drafts.ownedOpenDraft(draftId, session.getEmail());
        if (file == null || file.isEmpty() || file.getSize() > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException("Photos must be JPEG, PNG or WebP and at most 5 MB.");
        }
        drafts.ownedLine(draft, lineId); // 404 before storing anything
        String url = storage.storeFile(new SanitizedPhotoFile(sanitizer.sanitize(file.getBytes())));
        drafts.attachPhoto(draft, lineId, url);
        return drafts.view(drafts.ownedDraft(draftId, session.getEmail()));
    }

    @GetMapping("/store-cards")
    @Operation(summary = "Cards in our shop available for trade (CAD price and USD at the Bank of Canada rate)")
    public List<com.tailorcards.api.buylistchat.dto.BuylistChatDtos.StoreCardView> storeCards(
            @org.springframework.web.bind.annotation.RequestParam(value = "q", required = false) String query) {
        requireEnabled();
        return storeCardService.search(query, 40).stream()
                .map(c -> new com.tailorcards.api.buylistchat.dto.BuylistChatDtos.StoreCardView(c.productId(), c.name(),
                        c.setName(), c.cardNumber(), c.condition(), c.grading(), c.imageUrl(), c.priceCad(), c.priceUsd(),
                        c.stock(), true))
                .toList();
    }

    @PatchMapping("/drafts/{draftId}/deal")
    @Operation(summary = "Choose sell, trade or partial, and the cash wanted on top for partial deals")
    public DraftView setDeal(@PathVariable String draftId,
                             @RequestBody com.tailorcards.api.buylistchat.dto.BuylistChatDtos.DealRequest request,
                             @RequestHeader(value = SESSION_HEADER, required = false) String token) {
        BuylistChatSession session = session(token);
        drafts.setDeal(drafts.ownedOpenDraft(draftId, session.getEmail()), request.dealType(), request.requestedCashUsd());
        return drafts.view(drafts.ownedDraft(draftId, session.getEmail()));
    }

    @PostMapping("/drafts/{draftId}/trade-items")
    @Operation(summary = "Add a shop card to the trade")
    public DraftView addTradeItem(@PathVariable String draftId,
                                  @RequestBody com.tailorcards.api.buylistchat.dto.BuylistChatDtos.TradeItemRequest request,
                                  @RequestHeader(value = SESSION_HEADER, required = false) String token) {
        BuylistChatSession session = session(token);
        drafts.addTradeItem(drafts.ownedOpenDraft(draftId, session.getEmail()), request.productId());
        return drafts.view(drafts.ownedDraft(draftId, session.getEmail()));
    }

    @DeleteMapping("/drafts/{draftId}/trade-items/{productId}")
    @Operation(summary = "Remove a shop card from the trade")
    public DraftView removeTradeItem(@PathVariable String draftId, @PathVariable Long productId,
                                     @RequestHeader(value = SESSION_HEADER, required = false) String token) {
        BuylistChatSession session = session(token);
        drafts.removeTradeItem(drafts.ownedOpenDraft(draftId, session.getEmail()), productId);
        return drafts.view(drafts.ownedDraft(draftId, session.getEmail()));
    }

    @PostMapping("/drafts/{draftId}/confirm")
    @Operation(summary = "Confirm button: re-validates the content hash and daily limits, then submits")
    public ConfirmResponse confirm(@PathVariable String draftId, @RequestBody ConfirmRequest request,
                                   @RequestHeader(value = SESSION_HEADER, required = false) String token,
                                   HttpServletRequest http) {
        BuylistChatSession session = session(token);
        return submissions.confirm(session.getEmail(), draftId, request, ClientIp.of(http));
    }

    private BuylistChatSession session(String token) {
        requireEnabled();
        BuylistChatSession session = identity.authenticate(token);
        if (properties.isRequireEmailVerification() && BuylistIdentityService.isGuest(session.getEmail())) {
            // Verification was turned back on: anonymous sessions stop working
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please verify your email to continue.");
        }
        return session;
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The buylist chat is turned off right now. Please use the standard sell form.");
        }
    }
}
