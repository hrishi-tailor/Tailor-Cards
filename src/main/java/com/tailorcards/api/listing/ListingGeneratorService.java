package com.tailorcards.api.listing;

import com.tailorcards.api.dto.ProductRequest;
import com.tailorcards.api.listing.ListingImageSanitizer.SanitizedImage;
import com.tailorcards.api.listing.dto.CardCandidate;
import com.tailorcards.api.listing.dto.ListingDraftResponse;
import com.tailorcards.api.listing.dto.MarketReferenceResponse;
import com.tailorcards.api.listing.dto.MatchStatus;
import com.tailorcards.api.trade.llm.AnthropicClient;
import com.tailorcards.api.trade.llm.AnthropicResponse;
import com.tailorcards.api.trade.llm.LlmRateLimiter;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.PriceProvider;
import com.tailorcards.api.trade.service.CardPriceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns one or two card photos into a draft listing for an admin to review. The model only
 * reads the photos and writes text; any market reference comes from CardPriceService. Photos
 * are held in memory for the request and never stored.
 */
@Slf4j
@Service
public class ListingGeneratorService {

    static final int MAX_IMAGES = 2;
    static final int MAX_CANDIDATES = 8;
    private static final Pattern POKEMONTCG_ID = Pattern.compile(ProductRequest.POKEMONTCG_ID_PATTERN);

    private final AnthropicClient client;
    private final ListingImageSanitizer sanitizer;
    private final ListingDraftParser parser = new ListingDraftParser();
    private final LlmRateLimiter rateLimiter;
    private final ListingDailyCap dailyCap;
    private final PriceProvider priceProvider;
    private final CardPriceService cardPriceService;
    private final int rateLimitPerHour;
    private final long maxImageBytes;
    private final int maxNoteLength;

    @Autowired
    public ListingGeneratorService(
            AnthropicClient anthropicClient,
            ListingImageSanitizer sanitizer,
            LlmRateLimiter rateLimiter,
            ListingDailyCap dailyCap,
            PriceProvider priceProvider,
            CardPriceService cardPriceService,
            @Value("${app.listing-generator.model:${app.anthropic.model:claude-haiku-4-5-20251001}}") String model,
            @Value("${app.listing-generator.max-tokens:1024}") int maxTokens,
            @Value("${app.listing-generator.timeout-seconds:30}") int timeoutSeconds,
            @Value("${app.listing-generator.rate-limit-per-hour:20}") int rateLimitPerHour,
            @Value("${app.listing-generator.max-image-bytes:5242880}") long maxImageBytes,
            @Value("${app.listing-generator.max-note-length:500}") int maxNoteLength
    ) {
        this(anthropicClient.withSettings(model, maxTokens, timeoutSeconds), sanitizer, rateLimiter, dailyCap,
                priceProvider, cardPriceService, rateLimitPerHour, maxImageBytes, maxNoteLength);
    }

    ListingGeneratorService(
            AnthropicClient listingClient,
            ListingImageSanitizer sanitizer,
            LlmRateLimiter rateLimiter,
            ListingDailyCap dailyCap,
            PriceProvider priceProvider,
            CardPriceService cardPriceService,
            int rateLimitPerHour,
            long maxImageBytes,
            int maxNoteLength
    ) {
        this.client = listingClient;
        this.sanitizer = sanitizer;
        this.rateLimiter = rateLimiter;
        this.dailyCap = dailyCap;
        this.priceProvider = priceProvider;
        this.cardPriceService = cardPriceService;
        this.rateLimitPerHour = rateLimitPerHour;
        this.maxImageBytes = maxImageBytes;
        this.maxNoteLength = maxNoteLength;
    }

    public ListingDraftResponse generateDraft(String adminUsername, List<MultipartFile> files, String note) {
        // 1. Validate input before spending any quota
        List<SanitizedImage> images = readImages(files);
        String cleanNote = note == null ? "" : note.trim();
        if (cleanNote.length() > maxNoteLength) {
            throw new IllegalArgumentException("Note must be at most " + maxNoteLength + " characters.");
        }

        // 2. Per-admin rate limit and store-wide daily cap
        rateLimiter.checkRateLimit("listing:" + adminUsername, rateLimitPerHour, Duration.ofHours(1),
                "Listing draft limit reached (" + rateLimitPerHour + " per hour). Try again later or fill in the listing manually.");
        dailyCap.acquire();

        if (!client.isConfigured()) {
            throw unavailable("The listing generator is not configured. Fill in the listing manually.");
        }

        // 3. Ask the model, retrying once if the reply fails validation
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", buildUserContent(images, cleanNote)));

        long latencyMs = 0;
        int inputTokens = 0;
        int outputTokens = 0;
        BigDecimal costUsd = BigDecimal.ZERO;
        ListingDraft draft = null;
        List<String> lastProblems = List.of();

        for (int attempt = 1; attempt <= 2 && draft == null; attempt++) {
            AnthropicResponse response = client.sendContentMessage(ListingPrompts.SYSTEM_PROMPT, messages)
                    .orElseThrow(() -> unavailable(
                            "The AI service is unavailable right now. Fill in the listing manually or try again later."));
            latencyMs += response.latencyMs();
            inputTokens += response.inputTokens();
            outputTokens += response.outputTokens();
            costUsd = costUsd.add(response.estimatedCostUsd() != null ? response.estimatedCostUsd() : BigDecimal.ZERO);
            try {
                draft = parser.parse(response.text());
            } catch (ListingDraftValidationException e) {
                lastProblems = e.problems();
                log.warn("Listing draft attempt {} failed validation: {} problem(s)", attempt, lastProblems.size());
                messages.add(Map.of("role", "assistant", "content", response.text() == null ? "" : response.text()));
                messages.add(Map.of("role", "user", "content",
                        "Your previous reply was rejected: " + String.join(" ", lastProblems)
                                + " Reply again with only the corrected JSON object."));
            }
        }

        log.info("Listing draft request: model='{}', valid={}, totalLatency={}ms, inputTokens={}, outputTokens={}, costUsd=${}",
                client.getModel(), draft != null, latencyMs, inputTokens, outputTokens, costUsd);

        if (draft == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI draft failed validation twice. Fill in the listing manually.");
        }

        return withMarketData(draft);
    }

    public MarketReferenceResponse marketReference(String cardId, ListingCondition condition,
                                                   String gradingCompany, String grade, boolean sealed) {
        if (cardId == null || !POKEMONTCG_ID.matcher(cardId).matches()) {
            throw new IllegalArgumentException("Invalid card id.");
        }
        CardMarketPrice card = null;
        try {
            card = priceProvider.fetchPrice(cardId).orElse(null);
        } catch (Exception e) {
            log.warn("Card lookup failed for cardId={}: {}", cardId, e.getClass().getSimpleName());
        }
        if (card == null) {
            card = CardMarketPrice.builder().cardId(cardId).build();
        }
        return reference(card, condition, gradingCompany, grade, sealed);
    }

    private List<SanitizedImage> readImages(List<MultipartFile> files) {
        List<MultipartFile> present = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (present.isEmpty() || present.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("Upload one or two photos of the card.");
        }
        List<SanitizedImage> images = new ArrayList<>();
        for (MultipartFile file : present) {
            if (file.getSize() > maxImageBytes) {
                throw new IllegalArgumentException("Each photo must be at most " + (maxImageBytes / (1024 * 1024)) + " MB.");
            }
            try {
                images.add(sanitizer.sanitize(file.getBytes()));
            } catch (IOException e) {
                throw new IllegalArgumentException("A photo could not be read.");
            }
        }
        return images;
    }

    private List<Map<String, Object>> buildUserContent(List<SanitizedImage> images, String note) {
        List<Map<String, Object>> content = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            SanitizedImage image = images.get(i);
            content.add(Map.of("type", "text", "text", "<photo index=\"" + (i + 1) + "\">"));
            content.add(Map.of("type", "image", "source", Map.of(
                    "type", "base64",
                    "media_type", image.type().mediaType(),
                    "data", Base64.getEncoder().encodeToString(image.bytes()))));
            content.add(Map.of("type", "text", "text", "</photo>"));
        }
        String safeNote = note.isEmpty() ? "(none)" : ListingPrompts.neutralizeTags(note);
        content.add(Map.of("type", "text", "text", "<admin_note>\n" + safeNote + "\n</admin_note>"));
        content.add(Map.of("type", "text", "text",
                "Write the draft for the item in the photos. Reply with the JSON object only."));
        return content;
    }

    private ListingDraftResponse withMarketData(ListingDraft draft) {
        if (draft.isSealed()) {
            return new ListingDraftResponse(draft, MatchStatus.NONE, null, List.of());
        }
        List<CardMarketPrice> results;
        try {
            results = priceProvider.searchCards(draft.cardName(), 20);
        } catch (Exception e) {
            log.warn("Card search failed during listing draft: {}", e.getClass().getSimpleName());
            return new ListingDraftResponse(draft, MatchStatus.NONE, null, List.of());
        }
        if (results == null || results.isEmpty()) {
            return new ListingDraftResponse(draft, MatchStatus.NONE, null, List.of());
        }

        List<CardMarketPrice> narrowed = results;
        String number = normalizeNumber(draft.cardNumber());
        if (number != null) {
            narrowed = narrowed.stream().filter(c -> number.equals(normalizeNumber(c.cardNumber()))).toList();
        }
        if (narrowed.size() > 1 && draft.setName() != null) {
            String set = draft.setName().toLowerCase(Locale.ROOT);
            List<CardMarketPrice> bySet = narrowed.stream()
                    .filter(c -> c.setName() != null && c.setName().toLowerCase(Locale.ROOT).equals(set))
                    .toList();
            if (!bySet.isEmpty()) {
                narrowed = bySet;
            }
        }

        if (narrowed.size() == 1) {
            MarketReferenceResponse reference = reference(narrowed.getFirst(), draft.condition(),
                    draft.gradingCompany(), draft.grade(), false);
            return new ListingDraftResponse(draft, MatchStatus.MATCHED, reference, List.of());
        }
        List<CardMarketPrice> pool = narrowed.isEmpty() ? results : narrowed;
        List<CardCandidate> candidates = pool.stream()
                .limit(MAX_CANDIDATES)
                .map(c -> new CardCandidate(c.cardId(), c.name(), c.setName(), c.cardNumber(), c.imageUrl()))
                .toList();
        return new ListingDraftResponse(draft, MatchStatus.AMBIGUOUS, null, candidates);
    }

    private MarketReferenceResponse reference(CardMarketPrice card, ListingCondition condition,
                                              String gradingCompany, String grade, boolean sealed) {
        String grading = gradingCompany != null && grade != null ? gradingCompany + " " + grade : null;
        String conditionCode = condition != null && condition != ListingCondition.UNKNOWN ? condition.name() : null;
        BigDecimal priceCad = null;
        try {
            Optional<BigDecimal> resolved = cardPriceService.resolvePriceCad(card.cardId(), conditionCode, grading, sealed);
            priceCad = resolved.orElse(null);
        } catch (Exception e) {
            log.warn("Market reference lookup failed for cardId={}: {}", card.cardId(), e.getClass().getSimpleName());
        }
        String stockImage = card.largeImageUrl() != null ? card.largeImageUrl() : card.imageUrl();
        return new MarketReferenceResponse(card.cardId(), card.name(), card.setName(), card.cardNumber(),
                priceCad, stockImage);
    }

    static String normalizeNumber(String number) {
        if (number == null || number.isBlank()) {
            return null;
        }
        String head = number.split("/")[0].replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        String stripped = head.replaceFirst("^0+(?=.)", "");
        return stripped.isEmpty() ? null : stripped;
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
