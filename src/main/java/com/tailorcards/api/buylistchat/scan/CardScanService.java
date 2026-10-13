package com.tailorcards.api.buylistchat.scan;

import com.tailorcards.api.buylistchat.BuylistChatProperties;
import com.tailorcards.api.buylistchat.BuylistDraftService;
import com.tailorcards.api.buylistchat.BuylistLlmBudget;
import com.tailorcards.api.buylistchat.SanitizedPhotoFile;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.DraftView;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ScanAddRequest;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ScanCard;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ScanResponse;
import com.tailorcards.api.buylistchat.entity.BuylistDraft;
import com.tailorcards.api.buylistchat.entity.BuylistDraftLine;
import com.tailorcards.api.buylistchat.intake.ItemInput;
import com.tailorcards.api.buylistchat.intake.ItemNormalizer;
import com.tailorcards.api.buylistchat.pricing.DealCalculator;
import com.tailorcards.api.buylistchat.pricing.LineFacts;
import com.tailorcards.api.buylistchat.pricing.LineStatus;
import com.tailorcards.api.buylistchat.resolution.CardLookupService;
import com.tailorcards.api.listing.ListingImageSanitizer;
import com.tailorcards.api.listing.ListingImageSanitizer.SanitizedImage;
import com.tailorcards.api.service.BuylistStorageService;
import com.tailorcards.api.service.ProductImageService;
import com.tailorcards.api.trade.llm.AnthropicClient;
import com.tailorcards.api.trade.llm.AnthropicResponse;
import com.tailorcards.api.trade.llm.LlmRateLimiter;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.TcgdexPriceProvider.SetInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Card scanner for the buylist chat: reads the customer's front (and optional back) photo with a vision
 * model, identifies the card in TCGdex and estimates its condition on the store's scale (NM, LP+, LP, MP,
 * HP, DMG). The customer confirms before anything is added; a person checks the card again in hand.
 *
 * <p>Scans are held for two hours so the customer can confirm; adding one creates the line with the
 * front photo attached and notes the scan in the chat transcript so the assistant knows about it.
 */
@Slf4j
@Service
public class CardScanService {

    static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final int SCANS_PER_WINDOW = 12;
    private static final Duration SCAN_WINDOW = Duration.ofMinutes(10);
    private static final Duration SCAN_TTL = Duration.ofHours(2);
    private static final int MAX_PENDING = 2000;
    private static final int MAX_CANDIDATES = 5;
    private static final Pattern PROMO_NUMBER = Pattern.compile("^(SVP|MEP|SWSH|SM|XY|BW)\\s*-?\\s*(\\d{1,4})$", Pattern.CASE_INSENSITIVE);

    private static final Map<String, String> CONDITION_LABELS = Map.of(
            "NM", "Near mint", "LP+", "LP+ (one white dot)", "LP", "Lightly played", "MP", "Moderately played",
            "HP", "Heavily played", "DMG", "Damaged", "UNKNOWN", "Not sure from the photos");

    /** A finished scan waiting for the customer to confirm it. */
    record PendingScan(String email, String draftId, CardScanReading reading, ScanCard card, List<ScanCard> candidates,
                       String frontUrl, String backUrl, Instant expiresAt) {}

    private final AnthropicClient client;
    private final ListingImageSanitizer sanitizer;
    private final BuylistStorageService storage;
    private final CardLookupService lookup;
    private final BuylistLlmBudget budget;
    private final LlmRateLimiter rateLimiter;
    private final BuylistDraftService drafts;
    private final DealCalculator deals;
    private final Clock clock;
    private final Map<String, PendingScan> pending = new ConcurrentHashMap<>();

    @Autowired
    public CardScanService(AnthropicClient anthropicClient, BuylistChatProperties properties, ListingImageSanitizer sanitizer,
                           BuylistStorageService storage, CardLookupService lookup, BuylistLlmBudget budget,
                           LlmRateLimiter rateLimiter, BuylistDraftService drafts, DealCalculator deals) {
        this(anthropicClient.withSettings(properties.getModel(), 1024, 45), sanitizer, storage, lookup, budget,
                rateLimiter, drafts, deals, Clock.systemUTC());
    }

    CardScanService(AnthropicClient client, ListingImageSanitizer sanitizer, BuylistStorageService storage,
                    CardLookupService lookup, BuylistLlmBudget budget, LlmRateLimiter rateLimiter,
                    BuylistDraftService drafts, DealCalculator deals, Clock clock) {
        this.client = client;
        this.sanitizer = sanitizer;
        this.storage = storage;
        this.lookup = lookup;
        this.budget = budget;
        this.rateLimiter = rateLimiter;
        this.drafts = drafts;
        this.deals = deals;
        this.clock = clock;
    }

    public boolean isAvailable() {
        return client.isConfigured();
    }

    // ---------------------------------------------------------------- scan

    public ScanResponse scan(String email, long sessionId, String draftId, MultipartFile front, MultipartFile back)
            throws IOException {
        drafts.ownedOpenDraft(draftId, email); // 404 / 409 before any work
        SanitizedImage frontImage = read(front, "front");
        SanitizedImage backImage = back == null || back.isEmpty() ? null : read(back, "back");
        rateLimiter.checkRateLimit("buylist-scan:" + sessionId, SCANS_PER_WINDOW, SCAN_WINDOW,
                "That's a lot of scans in a short time. Wait a few minutes, or add cards by typing them.");
        if (!client.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Photo scanning is unavailable right now. Add the card by typing it instead.");
        }
        if (!budget.hasBudget()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Photo scanning is paused for today. Add the card by typing it instead.");
        }

        CardScanReading reading = read(frontImage, backImage);
        String frontUrl = storage.storeFile(new SanitizedPhotoFile(frontImage));
        String backUrl = backImage == null ? null : storage.storeFile(new SanitizedPhotoFile(backImage));

        ScanCard card = null;
        List<ScanCard> candidates = List.of();
        if (reading.readable() && !reading.sealed()) {
            Match match = match(reading);
            card = match.card();
            candidates = match.candidates();
        }
        evictExpired();
        String scanId = UUID.randomUUID().toString();
        pending.put(scanId, new PendingScan(email, draftId, reading, card, candidates, frontUrl, backUrl,
                clock.instant().plus(SCAN_TTL)));
        return response(scanId, reading, card, candidates, frontUrl, backUrl);
    }

    private SanitizedImage read(MultipartFile file, String side) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Take or upload a photo of the " + side + " of the card.");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException("The " + side + " photo is too large (5 MB max).");
        }
        return sanitizer.sanitize(file.getBytes());
    }

    /** Asks the vision model, retrying once with the validation problems if the reply is invalid. */
    CardScanReading read(SanitizedImage front, SanitizedImage back) {
        List<Map<String, Object>> content = new ArrayList<>();
        addPhoto(content, "front", front);
        if (back != null) {
            addPhoto(content, "back", back);
        } else {
            content.add(Map.of("type", "text", "text", "(No back photo was provided.)"));
        }
        content.add(Map.of("type", "text", "text", "Identify the card and estimate its condition. Reply with the JSON object only."));
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", content));

        BigDecimal cost = BigDecimal.ZERO;
        int calls = 0;
        try {
            for (int attempt = 1; attempt <= 2; attempt++) {
                Optional<AnthropicResponse> response = client.sendContentMessage(CardScanPrompts.SYSTEM_PROMPT, messages);
                if (response.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "We couldn't read the photo right now. Try again in a moment, or type the card instead.");
                }
                calls++;
                AnthropicResponse r = response.get();
                cost = cost.add(r.estimatedCostUsd() == null ? BigDecimal.ZERO : r.estimatedCostUsd());
                try {
                    CardScanReading reading = CardScanReading.parse(r.text());
                    log.info("Card scan: model='{}', readable={}, condition={}, confidence={}, latency={}ms, costUsd=${}",
                            client.getModel(), reading.readable(), reading.condition(), reading.confidence(), r.latencyMs(), cost);
                    return reading;
                } catch (CardScanReading.InvalidReading e) {
                    log.warn("Card scan attempt {} failed validation: {}", attempt, e.problems);
                    messages.add(Map.of("role", "assistant", "content", r.text() == null ? "" : r.text()));
                    messages.add(Map.of("role", "user", "content", "Your reply was rejected: " + String.join(" ", e.problems)
                            + " Reply again with only the corrected JSON object."));
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "We couldn't read that photo. Try another one, or type the card instead.");
        } finally {
            if (calls > 0) {
                budget.record(cost, calls);
            }
        }
    }

    private static void addPhoto(List<Map<String, Object>> content, String side, SanitizedImage image) {
        content.add(Map.of("type", "text", "text", "<photo side=\"" + side + "\">"));
        content.add(Map.of("type", "image", "source", Map.of("type", "base64", "media_type", image.type().mediaType(),
                "data", Base64.getEncoder().encodeToString(image.bytes()))));
        content.add(Map.of("type", "text", "text", "</photo>"));
    }

    // ---------------------------------------------------------------- card match

    record Match(ScanCard card, List<ScanCard> candidates) {}

    /**
     * Finds the card in TCGdex: set + number first (promo numbers like "SVP 053" go to their promo set),
     * then a name search narrowed by number and the set size printed after the slash.
     */
    Match match(CardScanReading reading) {
        String name = reading.cardName();
        String number = reading.cardNumber();
        LinkedHashSet<String> setIds = new LinkedHashSet<>();
        String head = number == null ? null : number.split("/")[0].trim();
        Matcher promo = head == null ? null : PROMO_NUMBER.matcher(head);
        if (promo != null && promo.matches()) {
            String prefix = promo.group(1).toUpperCase(Locale.ROOT);
            setIds.addAll(lookup.setIdsFor(switch (prefix) {
                case "SVP" -> "SVP Black Star Promos";
                case "MEP" -> "MEP Black Star Promos";
                case "SWSH" -> "SWSH Black Star Promos";
                case "SM" -> "SM Black Star Promos";
                case "XY" -> "XY Black Star Promos";
                default -> "BW Black Star Promos";
            }));
            if ("SVP".equals(prefix) || "MEP".equals(prefix)) {
                head = promo.group(2); // svp-053, mep-032
            } else {
                head = prefix + promo.group(2); // smp-SM60, xyp-XY121
            }
        }
        if (reading.setName() != null) {
            setIds.addAll(lookup.setIdsFor(reading.setName()));
        }
        if (head != null && !setIds.isEmpty()) {
            Optional<CardMarketPrice> card = lookup.findBySetAndNumber(List.copyOf(setIds), head);
            if (card.isPresent() && (name == null || ProductImageService.samePokemon(name, card.get().name()))) {
                return new Match(toScanCard(card.get()), List.of());
            }
        }
        if (name == null) {
            return new Match(null, List.of());
        }
        List<CardMarketPrice> found = lookup.searchFlexible(name);
        String wanted = numberHead(head);
        List<CardMarketPrice> byNumber = wanted == null ? found
                : found.stream().filter(c -> wanted.equals(numberHead(c.cardNumber()))).toList();
        if (byNumber.size() > 1 && !setIds.isEmpty()) {
            List<CardMarketPrice> inSet = byNumber.stream().filter(c -> CardLookupService.inSets(c.cardId(), List.copyOf(setIds))).toList();
            if (!inSet.isEmpty()) byNumber = inSet;
        }
        Integer total = setTotal(number);
        if (byNumber.size() > 1 && total != null) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (SetInfo s : lookup.sets()) counts.put(s.id(), s.officialCount());
            List<CardMarketPrice> bySize = byNumber.stream()
                    .filter(c -> total.equals(counts.get(c.cardId().substring(0, c.cardId().lastIndexOf('-'))))).toList();
            if (!bySize.isEmpty()) byNumber = bySize;
        }
        if (byNumber.size() == 1) {
            // Search results are summaries: fetch the full card for its image and price
            CardMarketPrice full = lookup.card(byNumber.getFirst().cardId()).orElse(byNumber.getFirst());
            return new Match(toScanCard(full), List.of());
        }
        List<CardMarketPrice> pool = byNumber.isEmpty() ? found : byNumber;
        return new Match(null, pool.stream().limit(MAX_CANDIDATES).map(this::toScanCard).toList());
    }

    private ScanCard toScanCard(CardMarketPrice c) {
        return new ScanCard(c.cardId(), c.name(), c.setName(), c.cardNumber(),
                c.largeImageUrl() != null ? c.largeImageUrl() : c.imageUrl(), c.marketPriceUsd());
    }

    static String numberHead(String number) {
        if (number == null || number.isBlank()) return null;
        String head = number.split("/")[0].replaceAll("[\\s#]+", "").toUpperCase(Locale.ROOT);
        String stripped = head.replaceFirst("^0+(?=.)", "");
        return stripped.isEmpty() ? null : stripped;
    }

    static Integer setTotal(String number) {
        if (number == null || !number.contains("/")) return null;
        String tail = number.substring(number.indexOf('/') + 1).replaceAll("\\D", "");
        return tail.isEmpty() || tail.length() > 4 ? null : Integer.valueOf(tail);
    }

    // ---------------------------------------------------------------- response

    private ScanResponse response(String scanId, CardScanReading r, ScanCard card, List<ScanCard> candidates,
                                  String frontUrl, String backUrl) {
        BigDecimal offer = card == null || card.marketUsd() == null ? null : unitOffer(card.marketUsd(), r.condition(), r.grading());
        String productName = r.sealed() ? r.cardName() : null;
        return new ScanResponse(scanId, r.readable(), r.retakeTips(), r.photoIssues(), r.sealed(), productName, card,
                candidates, r.condition(), CONDITION_LABELS.getOrDefault(r.condition(), r.condition()), r.conditionNotes(),
                r.whiteDots(), r.needsBackPhoto(), r.grading(), r.confidence(), offer, frontUrl, backUrl);
    }

    /** Our cash offer per card at the scanned condition, from the live buy rules. */
    private BigDecimal unitOffer(BigDecimal marketUsd, String condition, String grading) {
        if (grading != null) {
            return null; // graded prices come from the graded market lookup once the card is on the list
        }
        LineFacts facts = new LineFacts(0, false, "RESOLVED", "Pokemon", condition, 1, marketUsd, BigDecimal.ONE,
                true, "scan", null, "RAW");
        return deals.unitCashOffer(facts, LineStatus.ELIGIBLE, deals.activeRules());
    }

    // ---------------------------------------------------------------- add to list

    /**
     * Adds a confirmed scan to the draft: the matched card (or one of the candidates the customer picked),
     * at the condition they confirmed, with the front photo attached; notes the scan in the chat.
     */
    public DraftView add(String email, String draftId, String scanId, ScanAddRequest request) {
        PendingScan scan = pending.get(scanId);
        if (scan == null || !scan.email().equals(email) || !scan.draftId().equals(draftId)
                || scan.expiresAt().isBefore(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That scan has expired. Scan the card again.");
        }
        CardScanReading r = scan.reading();
        if (!r.readable()) {
            throw new IllegalArgumentException("We couldn't read that photo. Retake it, then add the card.");
        }
        BuylistDraft draft = drafts.ownedOpenDraft(draftId, email);
        int quantity = Math.max(1, Math.min(100, request.quantity() == null ? 1 : request.quantity()));
        String condition = request.condition() == null ? r.condition() : ItemNormalizer.normalizeCondition(request.condition());

        ItemInput item;
        String label;
        if (r.sealed()) {
            String name = Optional.ofNullable(r.cardName()).orElse("Sealed product");
            item = ItemInput.sealed(name, r.setName(), quantity, "photo scan");
            label = name;
        } else {
            ScanCard card = chosen(scan, request.cardId());
            if (card == null) {
                // Not matched: add what the photo says and let resolution try
                item = new ItemInput("CARD", Optional.ofNullable(r.cardName()).orElse("Unknown card"), r.setName(),
                        r.cardNumber(), null, condition, quantity, "photo scan", null, r.grading());
                label = Optional.ofNullable(r.cardName()).orElse("the card");
            } else {
                item = new ItemInput("CARD", card.name(), card.setName(), card.cardNumber(), null, condition, quantity,
                        "photo scan", card.cardId(), r.grading());
                label = card.name() + " (" + card.setName() + " " + card.cardNumber() + ")";
            }
        }
        BuylistDraftLine line = drafts.addItems(draft, List.of(item)).getFirst();
        drafts.attachPhoto(draft, line.getId(), scan.frontUrl());
        pending.remove(scanId);

        drafts.appendMessage(draft, "CUSTOMER", "[Scanned a card photo]");
        drafts.appendMessage(draft, "ASSISTANT", transcriptNote(label, quantity, condition, r, scan.backUrl()));
        return drafts.view(drafts.ownedDraft(draftId, email));
    }

    private static ScanCard chosen(PendingScan scan, String cardId) {
        if (cardId == null || cardId.isBlank()) {
            return scan.card() != null ? scan.card() : null;
        }
        if (scan.card() != null && scan.card().cardId().equalsIgnoreCase(cardId)) {
            return scan.card();
        }
        return scan.candidates().stream().filter(c -> c.cardId().equalsIgnoreCase(cardId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pick one of the suggested cards."));
    }

    private static String transcriptNote(String label, int quantity, String condition, CardScanReading r, String backUrl) {
        StringBuilder note = new StringBuilder("Added ").append(quantity > 1 ? quantity + "x " : "").append(label)
                .append(" from your photo");
        if (r.grading() != null) {
            note.append(", graded ").append(r.grading());
        } else if (!r.sealed()) {
            note.append(", condition ").append(CONDITION_LABELS.getOrDefault(condition, condition).toLowerCase(Locale.ROOT));
        }
        note.append(".");
        List<String> seen = r.conditionNotes().values().stream().limit(2).toList();
        if (!seen.isEmpty()) {
            note.append(" What I could see: ").append(String.join("; ", seen)).append(".");
        }
        if (r.needsBackPhoto() && backUrl == null && r.grading() == null && !r.sealed()) {
            note.append(" A photo of the back would make the condition estimate more accurate.");
        }
        note.append(" This is an estimate from the photo; we confirm the condition when we inspect the card.");
        return note.toString();
    }

    private void evictExpired() {
        Instant now = clock.instant();
        pending.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        if (pending.size() > MAX_PENDING) {
            pending.entrySet().stream().sorted(java.util.Comparator.comparing(e -> e.getValue().expiresAt()))
                    .limit(pending.size() - MAX_PENDING).map(Map.Entry::getKey).toList().forEach(pending::remove);
        }
    }
}
