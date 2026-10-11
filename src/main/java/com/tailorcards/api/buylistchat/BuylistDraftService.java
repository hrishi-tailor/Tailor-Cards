package com.tailorcards.api.buylistchat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.CandidateView;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.DealView;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.StoreCardView;
import com.tailorcards.api.buylistchat.entity.BuylistDraftTradeItem;
import com.tailorcards.api.buylistchat.pricing.DealCalculator;
import com.tailorcards.api.buylistchat.pricing.StoreCardService;
import com.tailorcards.api.buylistchat.repository.BuylistDraftTradeItemRepository;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ChatMessageView;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.DraftView;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.LineUpdateRequest;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.LineView;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.Progress;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.Summary;
import com.tailorcards.api.buylistchat.entity.BuylistChatMessage;
import com.tailorcards.api.buylistchat.entity.BuylistDraft;
import com.tailorcards.api.buylistchat.entity.BuylistDraftLine;
import com.tailorcards.api.buylistchat.identity.BuylistIdentityService;
import com.tailorcards.api.buylistchat.intake.ItemInput;
import com.tailorcards.api.buylistchat.intake.ItemNormalizer;
import com.tailorcards.api.buylistchat.pricing.LikelihoodCalculator;
import com.tailorcards.api.buylistchat.pricing.LineFacts;
import com.tailorcards.api.buylistchat.pricing.LineStatus;
import com.tailorcards.api.buylistchat.pricing.ScrapFilter;
import com.tailorcards.api.buylistchat.repository.BuylistChatMessageRepository;
import com.tailorcards.api.buylistchat.repository.BuylistDraftLineRepository;
import com.tailorcards.api.buylistchat.repository.BuylistDraftRepository;
import com.tailorcards.api.buylistchat.resolution.CardResolutionService;
import com.tailorcards.api.repository.BuylistSubmissionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-side drafts: line items, statuses (ScrapFilter), totals, likelihood and the content hash
 * the customer confirms. Every method takes the caller's verified email and only touches that
 * email's draft.
 */
@Service
public class BuylistDraftService {

    public static final String OPEN = "OPEN";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String CURRENCY = "USD";
    public static final String QUOTE_NOTICE = "Market reference only, not an offer. Valid 48 hours; re-priced when your cards arrive.";
    public static final String DAILY_LIMIT_NOTICE = "You have 1 submission per day. Submit?";

    private final BuylistDraftRepository draftRepository;
    private final BuylistDraftLineRepository lineRepository;
    private final BuylistChatMessageRepository messageRepository;
    private final BuylistSubmissionRepository submissionRepository;
    private final CardResolutionService resolutionService;
    private final ScrapFilter scrapFilter;
    private final LikelihoodCalculator likelihoodCalculator;
    private final ItemNormalizer normalizer;
    private final BuylistChatProperties properties;
    private final BuylistDraftTradeItemRepository tradeItemRepository;
    private final StoreCardService storeCards;
    private final DealCalculator dealCalculator;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.systemUTC();

    public BuylistDraftService(BuylistDraftRepository draftRepository, BuylistDraftLineRepository lineRepository,
                               BuylistChatMessageRepository messageRepository,
                               BuylistSubmissionRepository submissionRepository,
                               CardResolutionService resolutionService, ScrapFilter scrapFilter,
                               LikelihoodCalculator likelihoodCalculator, ItemNormalizer normalizer,
                               BuylistChatProperties properties, BuylistDraftTradeItemRepository tradeItemRepository,
                               StoreCardService storeCards, DealCalculator dealCalculator) {
        this.tradeItemRepository = tradeItemRepository;
        this.storeCards = storeCards;
        this.dealCalculator = dealCalculator;
        this.draftRepository = draftRepository;
        this.lineRepository = lineRepository;
        this.messageRepository = messageRepository;
        this.submissionRepository = submissionRepository;
        this.resolutionService = resolutionService;
        this.scrapFilter = scrapFilter;
        this.likelihoodCalculator = likelihoodCalculator;
        this.normalizer = normalizer;
        this.properties = properties;
    }

    /** Returns the email's open draft, creating one if needed. */
    @Transactional
    public BuylistDraft openDraft(String email) {
        return draftRepository.findTopByEmailAndStatusOrderByCreatedAtDesc(email, OPEN).orElseGet(() -> {
            Instant now = clock.instant();
            return draftRepository.save(BuylistDraft.builder().id(UUID.randomUUID().toString()).email(email)
                    .status(OPEN).createdAt(now).updatedAt(now).build());
        });
    }

    /** The draft, if it belongs to the email; 404 otherwise (never reveals other drafts). */
    @Transactional(readOnly = true)
    public BuylistDraft ownedDraft(String draftId, String email) {
        return draftRepository.findById(draftId == null ? "" : draftId)
                .filter(d -> d.getEmail().equals(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Draft not found."));
    }

    public BuylistDraft ownedOpenDraft(String draftId, String email) {
        BuylistDraft draft = ownedDraft(draftId, email);
        if (!OPEN.equals(draft.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This list was already submitted.");
        }
        return draft;
    }

    @Transactional
    public List<BuylistDraftLine> addItems(BuylistDraft draft, List<ItemInput> items) {
        long existing = lineRepository.countByDraftId(draft.getId());
        int max = properties.getLimits().getMaxLines();
        if (existing + items.size() > max) {
            throw new IllegalArgumentException("A list can have at most " + max + " items (you have " + existing
                    + ", adding " + items.size() + ").");
        }
        int nextNo = lineRepository.findByDraftIdOrderByLineNoAsc(draft.getId()).stream()
                .mapToInt(BuylistDraftLine::getLineNo).max().orElse(0) + 1;
        Instant now = clock.instant();
        List<BuylistDraftLine> saved = new ArrayList<>();
        for (ItemInput item : items) {
            boolean bulk = "BULK".equals(item.kind());
            saved.add(lineRepository.save(BuylistDraftLine.builder()
                    .draftId(draft.getId()).lineNo(nextNo++).kind(bulk ? "BULK" : "CARD")
                    .inputText(truncate(item.inputText(), 500)).name(truncate(item.name(), 200))
                    .setName(truncate(item.setName(), 150)).cardNumber(truncate(item.cardNumber(), 40))
                    .variant(truncate(item.variant(), 40)).itemCondition(ItemNormalizer.normalizeCondition(item.condition()))
                    .grading(truncate(ItemNormalizer.normalizeGrading(item.grading()), 30))
                    .quantity(normalizer.clampQuantity(item.quantity(), bulk)).cardId(truncate(item.cardId(), 60))
                    .resolveState(CardResolutionService.PENDING).createdAt(now).build()));
        }
        touch(draft);
        afterCommit(() -> resolutionService.schedule(draft.getId()));
        return saved;
    }

    @Transactional
    public void removeLine(BuylistDraft draft, long lineId) {
        lineRepository.delete(ownedLine(draft, lineId));
        touch(draft);
    }

    @Transactional
    public BuylistDraftLine updateLine(BuylistDraft draft, long lineId, LineUpdateRequest request) {
        BuylistDraftLine line = ownedLine(draft, lineId);
        if (request.quantity() != null) {
            line.setQuantity(normalizer.clampQuantity(request.quantity(), "BULK".equals(line.getKind())));
        }
        if (request.condition() != null) {
            line.setItemCondition(ItemNormalizer.normalizeCondition(request.condition()));
        }
        if (request.requestedUnitUsd() != null) {
            BigDecimal ask = request.requestedUnitUsd();
            if (ask.signum() < 0 || ask.compareTo(new BigDecimal("1000000")) > 0) {
                throw new IllegalArgumentException("Enter a price between $0 and $1,000,000.");
            }
            line.setRequestedUnitUsd(ask.signum() == 0 ? null : ask.setScale(2, java.math.RoundingMode.HALF_UP));
        }
        boolean reprice = false;
        if (request.grading() != null && "CARD".equals(line.getKind())) {
            String grading = "RAW".equalsIgnoreCase(request.grading().trim()) ? null
                    : truncate(ItemNormalizer.normalizeGrading(request.grading()), 30);
            if (grading == null && !request.grading().isBlank() && !"RAW".equalsIgnoreCase(request.grading().trim())) {
                throw new IllegalArgumentException("Enter a grade such as PSA 10, BGS 9.5 or CGC 10.");
            }
            if (!Objects.equals(grading, line.getGrading())) {
                line.setGrading(grading);
                reprice = true;
            }
        }
        if (request.variant() != null) {
            String variant = ItemNormalizer.normalizeVariant(request.variant());
            if (!Objects.equals(variant, line.getVariant())) {
                line.setVariant(variant);
                reprice = true;
            }
        }
        if (reprice && "CARD".equals(line.getKind())) {
            line.setResolveState(CardResolutionService.PENDING);
            afterCommit(() -> resolutionService.schedule(draft.getId()));
        }
        touch(draft);
        return lineRepository.save(line);
    }

    @Transactional
    public void attachPhoto(BuylistDraft draft, long lineId, String photoUrl) {
        BuylistDraftLine line = ownedLine(draft, lineId);
        line.setPhotoUrl(truncate(photoUrl, 500));
        lineRepository.save(line);
        touch(draft);
    }

    static final int MAX_TRADE_ITEMS = 50;

    @Transactional
    public void setDeal(BuylistDraft draft, String dealType, BigDecimal requestedCashUsd) {
        if (dealType != null) {
            try {
                draft.setDealType(DealCalculator.DealType.valueOf(dealType.trim().toUpperCase(java.util.Locale.ROOT)).name());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Choose sell, trade or partial.");
            }
        }
        if (requestedCashUsd != null) {
            if (requestedCashUsd.signum() < 0 || requestedCashUsd.compareTo(new BigDecimal("10000000")) > 0) {
                throw new IllegalArgumentException("Enter a cash amount between $0 and $10,000,000.");
            }
            draft.setRequestedCashUsd(requestedCashUsd.signum() == 0 ? null : requestedCashUsd.setScale(2, java.math.RoundingMode.HALF_UP));
        }
        touch(draft);
    }

    @Transactional
    public void addTradeItem(BuylistDraft draft, Long productId) {
        storeCards.available(productId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "That shop card isn't available."));
        if (tradeItemRepository.findByDraftIdAndProductId(draft.getId(), productId).isPresent()) {
            return;
        }
        if (tradeItemRepository.countByDraftId(draft.getId()) >= MAX_TRADE_ITEMS) {
            throw new IllegalArgumentException("You can pick up to " + MAX_TRADE_ITEMS + " shop cards.");
        }
        tradeItemRepository.save(BuylistDraftTradeItem.builder().draftId(draft.getId()).productId(productId)
                .createdAt(clock.instant()).build());
        if (draft.getDealType() == null || "SELL".equals(draft.getDealType())) {
            draft.setDealType("TRADE"); // picking shop cards means trading
        }
        touch(draft);
    }

    @Transactional
    public void removeTradeItem(BuylistDraft draft, Long productId) {
        tradeItemRepository.findByDraftIdAndProductId(draft.getId(), productId).ifPresent(tradeItemRepository::delete);
        touch(draft);
    }

    BuylistDraftLine ownedLine(BuylistDraft draft, long lineId) {
        return lineRepository.findById(lineId)
                .filter(l -> l.getDraftId().equals(draft.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found in your list."));
    }

    @Transactional
    public void appendMessage(BuylistDraft draft, String role, String content) {
        messageRepository.save(BuylistChatMessage.builder().draftId(draft.getId()).senderRole(role)
                .content(content).createdAt(clock.instant()).build());
    }

    @Transactional(readOnly = true)
    public List<BuylistChatMessage> messages(BuylistDraft draft) {
        return messageRepository.findByDraftIdOrderByCreatedAtAscIdAsc(draft.getId());
    }

    @Transactional(readOnly = true)
    public DraftView view(BuylistDraft draft) {
        List<BuylistDraftLine> lines = lineRepository.findByDraftIdOrderByLineNoAsc(draft.getId());
        Evaluation evaluation = evaluate(draft, lines);
        List<ChatMessageView> messages = messages(draft).stream()
                .map(m -> new ChatMessageView(m.getSenderRole(), m.getContent(), m.getCreatedAt())).toList();
        int pending = (int) lines.stream().filter(l -> CardResolutionService.PENDING.equals(l.getResolveState())).count();
        return new DraftView(draft.getId(), draft.getStatus(), properties.getLimits().getMaxLines(),
                new Progress(lines.size(), pending), evaluation.lineViews(), evaluation.summary(), messages);
    }

    /** Statuses, totals, likelihood and hash for the given lines. */
    public record Evaluation(List<LineView> lineViews, Map<Long, LineStatus> statuses, Summary summary,
                             LikelihoodCalculator.Result likelihood) {}

    public Evaluation evaluate(BuylistDraft draft, List<BuylistDraftLine> lines) {
        List<LineView> views = new ArrayList<>();
        Map<Long, LineStatus> statuses = new LinkedHashMap<>();
        List<LineFacts> facts = new ArrayList<>();
        Map<LineStatus, Integer> counts = new EnumMap<>(LineStatus.class);
        BigDecimal total = null;
        BigDecimal eligible = null;
        StringBuilder canonical = new StringBuilder();
        List<com.tailorcards.api.entity.BuyRule> rules = dealCalculator.activeRules();

        for (BuylistDraftLine line : lines) {
            LineFacts fact = facts(line);
            ScrapFilter.Verdict verdict = scrapFilter.evaluate(fact);
            facts.add(fact);
            statuses.put(line.getId(), verdict.status());
            counts.merge(verdict.status(), 1, Integer::sum);
            BigDecimal lineValue = fact.lineMarketUsd();
            if (lineValue != null && !fact.ungradedPriceForSlab()) { // a slab's ungraded price is not a fair total
                total = total == null ? lineValue : total.add(lineValue);
                if (verdict.status() == LineStatus.ELIGIBLE) {
                    eligible = eligible == null ? lineValue : eligible.add(lineValue);
                }
            }
            views.add(new LineView(line.getId(), line.getLineNo(), line.getKind(), line.getName(), line.getSetName(),
                    line.getCardNumber(), line.getVariant(), line.getItemCondition(), line.getGrading(), line.getQuantity(),
                    line.getCardId(), line.getMatchedName(), line.getMatchedSet(), line.getMatchedNumber(),
                    line.getRarity(), line.getImageUrl(), CURRENCY, line.getUnitMarketUsd(), lineValue,
                    line.getEurTrend(), line.getPriceUpdatedAt(), line.getPriceSource(), line.getPriceBasis(),
                    line.getPriceSampleSize(), line.getRequestedUnitUsd(),
                    dealCalculator.unitCashOffer(fact, verdict.status(), rules), verdict.status().name(),
                    verdict.reason(), scrapFilter.photoRequired(fact), line.getPhotoUrl(), candidates(line)));
            canonical.append(line.getId()).append('|').append(line.getKind()).append('|').append(line.getCardId())
                    .append('|').append(line.getName()).append('|').append(line.getSetName()).append('|')
                    .append(line.getCardNumber()).append('|').append(line.getVariant()).append('|')
                    .append(line.getItemCondition()).append('|').append(line.getGrading()).append('|')
                    .append(line.getQuantity()).append('|')
                    .append(line.getUnitMarketUsd()).append('|').append(verdict.status()).append('|')
                    .append(line.getPhotoUrl()).append('|').append(line.getPriceBasis()).append('|')
                    .append(line.getRequestedUnitUsd()).append('|').append(line.getPriceSampleSize()).append('\n');
        }

        DealComputation dealComputation = dealView(draft, facts, statuses, lines);
        DealView deal = dealComputation.view();
        canonical.append("deal|").append(deal.dealType()).append('|').append(draft.getRequestedCashUsd()).append('|');
        deal.storeCards().forEach(c -> canonical.append(c.productId()).append(':').append(c.priceCad()).append(':')
                .append(c.available()).append(','));
        canonical.append('\n');

        DealCalculator.Result dealResult = dealComputation.result();
        LikelihoodCalculator.Result likelihood = likelihoodCalculator.calculate(facts, statuses,
                dealResult.meterFactor(), dealResult.meterBonus(), dealResult.askRatio() == null ? null : dealResult.message());
        Map<String, Integer> countsByName = new LinkedHashMap<>();
        counts.forEach((status, n) -> countsByName.put(status.name(), n));
        boolean ready = !lines.isEmpty() && !counts.containsKey(LineStatus.PENDING);
        boolean canSubmitToday = BuylistIdentityService.isGuest(draft.getEmail())
                || !submissionRepository.existsByCustomerEmailAndLocalDate(draft.getEmail(), today());
        String hash = BuylistIdentityService.sha256(draft.getId() + "\n" + canonical + total + "|" + eligible
                + "|" + likelihood.percent());
        Summary summary = new Summary(CURRENCY, total, eligible, countsByName, likelihood.percent(),
                LikelihoodCalculator.LABEL, LikelihoodCalculator.DISCLAIMER, likelihood.reasons(), hash, ready, canSubmitToday,
                canSubmitToday ? DAILY_LIMIT_NOTICE : "You've already submitted a list today. You can submit again tomorrow.",
                QUOTE_NOTICE, deal);
        return new Evaluation(views, statuses, summary, likelihood);
    }

    private record DealComputation(DealView view, DealCalculator.Result result) {}

    private DealComputation dealView(BuylistDraft draft, List<LineFacts> facts, Map<Long, LineStatus> statuses,
                              List<BuylistDraftLine> lines) {
        BigDecimal rate = storeCards.usdCadRate();
        List<StoreCardView> picked = new ArrayList<>();
        BigDecimal storeUsd = BigDecimal.ZERO;
        BigDecimal storeCad = BigDecimal.ZERO;
        for (BuylistDraftTradeItem item : tradeItemRepository.findByDraftIdOrderByCreatedAtAsc(draft.getId())) {
            java.util.Optional<StoreCardService.StoreCard> card = storeCards.available(item.getProductId());
            if (card.isPresent()) {
                StoreCardService.StoreCard c = card.get();
                picked.add(new StoreCardView(c.productId(), c.name(), c.setName(), c.cardNumber(), c.condition(),
                        c.grading(), c.imageUrl(), c.priceCad(), c.priceUsd(), c.stock(), true));
                storeUsd = storeUsd.add(c.priceUsd());
                storeCad = storeCad.add(c.priceCad());
            } else {
                picked.add(new StoreCardView(item.getProductId(), "No longer available", null, null, null, null, null,
                        null, null, 0, false));
            }
        }
        Map<Long, BigDecimal> requested = new java.util.HashMap<>();
        lines.forEach(l -> { if (l.getRequestedUnitUsd() != null) requested.put(l.getId(), l.getRequestedUnitUsd()); });
        DealCalculator.DealType type = draft.getDealType() == null ? DealCalculator.DealType.SELL
                : DealCalculator.DealType.valueOf(draft.getDealType());
        long availablePicks = picked.stream().filter(StoreCardView::available).count();
        DealCalculator.Result r = dealCalculator.calculate(new DealCalculator.Input(type, facts, statuses, requested,
                storeUsd, (int) availablePicks, draft.getRequestedCashUsd()));
        return new DealComputation(new DealView(r.type().name(), CURRENCY, r.ratesText(), r.offerableMarketUsd(), r.cashOfferUsd(),
                r.tradeCreditUsd(), picked, r.storeTotalUsd(), storeCad.setScale(2, java.math.RoundingMode.HALF_UP), rate,
                r.requestedCashUsd(), r.askTotalUsd(), r.askRatio(), r.withinRules(), r.message(), r.needsStoreCards(),
                r.overByUsd()), r);
    }

    static LineFacts facts(BuylistDraftLine line) {
        return new LineFacts(line.getId(), "BULK".equals(line.getKind()), line.getResolveState(), line.getCategory(),
                line.getItemCondition(), line.getQuantity(), line.getUnitMarketUsd(), line.getIdConfidence(),
                line.getPhotoUrl() != null, line.getCardId(), line.getGrading(), line.getPriceBasis(),
                line.getPriceSampleSize());
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(properties.zone()));
    }

    private List<CandidateView> candidates(BuylistDraftLine line) {
        if (line.getCandidatesJson() == null) {
            return List.of();
        }
        try {
            List<Map<String, String>> raw = objectMapper.readValue(line.getCandidatesJson(), new TypeReference<>() {});
            return raw.stream().map(c -> new CandidateView(c.get("cardId"), c.get("name"), c.get("setName"),
                    c.get("cardNumber"))).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private void touch(BuylistDraft draft) {
        draft.setUpdatedAt(clock.instant());
        draftRepository.save(draft);
    }

    /** Runs after the surrounding transaction commits (so background jobs see the new rows). */
    private static void afterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
