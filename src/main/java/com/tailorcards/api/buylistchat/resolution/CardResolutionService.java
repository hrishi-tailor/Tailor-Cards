package com.tailorcards.api.buylistchat.resolution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.buylistchat.BuylistChatProperties;
import com.tailorcards.api.buylistchat.entity.BuylistDraftLine;
import com.tailorcards.api.buylistchat.intake.ItemNormalizer;
import com.tailorcards.api.buylistchat.repository.BuylistDraftLineRepository;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.TcgdexPriceProvider.ProviderUnavailableException;
import com.tailorcards.api.trade.service.ExchangeRateService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Background job that resolves PENDING draft lines against TCGdex: search by name (or fetch by
 * id), narrow by number and set, take the variant's USD market price. Bounded concurrency; one job
 * per draft at a time. Lines that cannot be resolved get NOT_FOUND / AMBIGUOUS / ERROR, never fail.
 */
@Slf4j
@Service
public class CardResolutionService {

    public static final String PENDING = "PENDING";
    public static final String RESOLVED = "RESOLVED";
    public static final String AMBIGUOUS = "AMBIGUOUS";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String ERROR = "ERROR";

    private final BuylistDraftLineRepository lineRepository;
    private final CardLookupService lookup;
    private final BuylistChatProperties.Resolution config;
    private final ExecutorService jobPool;
    private final ExecutorService linePool;
    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final Set<String> rerun = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TransactionTemplate saveInNewTransaction;
    private static final java.util.regex.Pattern TRAILING_NUMBER = java.util.regex.Pattern.compile(
            "(?<=\\D)\\s+#?((?:TG|GG|SV|SWSH|SM|XY|BW)?\\d{1,3}[a-z]?)\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE);

    private final GradedPriceService gradedPrices;
    private final SealedPriceService sealedPrices;
    private final ExchangeRateService exchangeRates;
    /** Live progress for pages that are watching; null in tests that don't need it. */
    private ResolutionProgress progress;

    @Autowired(required = false)
    void setProgress(ResolutionProgress progress) {
        this.progress = progress;
    }

    @Autowired
    public CardResolutionService(BuylistDraftLineRepository lineRepository, CardLookupService lookup,
                                 BuylistChatProperties properties, PlatformTransactionManager transactionManager,
                                 GradedPriceService gradedPrices, SealedPriceService sealedPrices,
                                 ExchangeRateService exchangeRates) {
        this.lineRepository = lineRepository;
        this.gradedPrices = gradedPrices;
        this.sealedPrices = sealedPrices;
        this.exchangeRates = exchangeRates;
        // Each result is written in its own transaction: inline runs happen in an afterCommit callback,
        // where plain repository writes would join the already-committed transaction and be lost.
        this.saveInNewTransaction = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        if (this.saveInNewTransaction != null) {
            this.saveInNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
        this.lookup = lookup;
        this.config = properties.getResolution();
        this.jobPool = Executors.newFixedThreadPool(2, Thread.ofPlatform().name("buylist-resolve-job-", 0).daemon().factory());
        this.linePool = Executors.newFixedThreadPool(Math.max(1, config.getConcurrency()),
                Thread.ofPlatform().name("buylist-resolve-line-", 0).daemon().factory());
    }

    public CardResolutionService(BuylistDraftLineRepository lineRepository, CardLookupService lookup,
                                 BuylistChatProperties properties, PlatformTransactionManager transactionManager,
                                 GradedPriceService gradedPrices) {
        this(lineRepository, lookup, properties, transactionManager, gradedPrices, null, null);
    }

    @PreDestroy
    void shutdown() {
        jobPool.shutdownNow();
        linePool.shutdownNow();
    }

    /** Starts (or re-triggers) resolution of the draft's PENDING lines. Returns immediately when async. */
    public void schedule(String draftId) {
        if (!config.isAsync()) {
            runJob(draftId);
            return;
        }
        if (!running.add(draftId)) {
            rerun.add(draftId); // the running job will pick up the new lines
            return;
        }
        jobPool.submit(() -> {
            try {
                do {
                    rerun.remove(draftId);
                    runJob(draftId);
                } while (rerun.contains(draftId));
            } catch (Exception e) {
                log.warn("Resolution job failed for a draft: {}", e.getClass().getSimpleName());
            } finally {
                running.remove(draftId);
            }
        });
    }

    private void runJob(String draftId) {
        List<BuylistDraftLine> pending = lineRepository.findByDraftIdAndResolveState(draftId, PENDING);
        if (pending.isEmpty()) {
            return;
        }
        long start = System.nanoTime();
        CardLookupService.Stats before = lookup.stats();
        java.util.concurrent.atomic.AtomicLong lineNanosTotal = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicLong lineNanosMax = new java.util.concurrent.atomic.AtomicLong();
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (BuylistDraftLine line : pending) {
            Runnable task = () -> {
                long lineStart = System.nanoTime();
                try {
                    resolveAndSave(line);
                } catch (RuntimeException e) {
                    // One bad line must not leave the rest of the job, or itself, stuck in "Checking"
                    log.warn("Resolving a line failed: {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
                    markError(line);
                }
                long took = System.nanoTime() - lineStart;
                lineNanosTotal.addAndGet(took);
                lineNanosMax.accumulateAndGet(took, Math::max);
            };
            if (config.isAsync()) {
                tasks.add(CompletableFuture.runAsync(task, linePool));
            } else {
                task.run();
            }
        }
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        // One line per job, greppable: "Buylist timing: resolution"
        CardLookupService.Stats used = lookup.stats().minus(before);
        log.info("Buylist timing: resolution lines={} totalMs={} avgLineMs={} maxLineMs={} tcgdexRequests={} cacheHits={} throttleWaitMs={} tcgdexMs={}",
                pending.size(), (System.nanoTime() - start) / 1_000_000, lineNanosTotal.get() / 1_000_000 / pending.size(),
                lineNanosMax.get() / 1_000_000, used.requests(), used.cacheHits(), used.throttleWaitMs(), used.requestMs());
    }

    /** Marks a line that failed unexpectedly as ERROR (needs review) so it doesn't stay pending forever. */
    private void markError(BuylistDraftLine snapshot) {
        try {
            BuylistDraftLine failed = copyOf(snapshot);
            failed.setResolveState(ERROR);
            failed.setIdConfidence(BigDecimal.ZERO);
            if (saveInNewTransaction != null) {
                saveInNewTransaction.executeWithoutResult(status -> saveResult(snapshot, failed));
            } else {
                saveResult(snapshot, failed);
            }
        } catch (RuntimeException e) {
            log.warn("Could not mark a failed line: {}", e.getClass().getSimpleName());
        }
        if (progress != null) {
            progress.lineResolved(snapshot.getDraftId());
        }
    }

    private void resolveAndSave(BuylistDraftLine snapshot) {
        try {
            saveResolved(snapshot);
        } finally {
            if (progress != null) {
                progress.lineResolved(snapshot.getDraftId()); // after the save has committed
            }
        }
    }

    private void saveResolved(BuylistDraftLine snapshot) {
        BuylistDraftLine result = resolve(copyOf(snapshot));
        if (saveInNewTransaction != null) {
            saveInNewTransaction.executeWithoutResult(status -> saveResult(snapshot, result));
        } else {
            saveResult(snapshot, result);
        }
    }

    private void saveResult(BuylistDraftLine snapshot, BuylistDraftLine result) {
        // Re-read: skip if the customer deleted or edited the line meanwhile
        Optional<BuylistDraftLine> current = lineRepository.findById(snapshot.getId());
        if (current.isEmpty() || !PENDING.equals(current.get().getResolveState()) || !sameRequest(current.get(), snapshot)) {
            return;
        }
        BuylistDraftLine target = current.get();
        target.setCardId(result.getCardId());
        target.setMatchedName(result.getMatchedName());
        target.setMatchedSet(result.getMatchedSet());
        target.setMatchedNumber(result.getMatchedNumber());
        target.setRarity(result.getRarity());
        target.setCategory(result.getCategory());
        target.setImageUrl(result.getImageUrl());
        target.setUnitMarketUsd(result.getUnitMarketUsd());
        target.setEurTrend(result.getEurTrend());
        target.setPriceUpdatedAt(result.getPriceUpdatedAt());
        target.setPriceSource(result.getPriceSource());
        target.setIdConfidence(result.getIdConfidence());
        target.setCandidatesJson(result.getCandidatesJson());
        target.setResolveState(result.getResolveState());
        target.setPriceBasis(result.getPriceBasis());
        target.setPriceSampleSize(result.getPriceSampleSize());
        if ("SEALED".equals(result.getKind())) {
            // A line typed as a card that turned out to be sealed product: store it as sealed
            target.setKind("SEALED");
            target.setItemCondition("SEALED");
            target.setGrading(null);
            target.setVariant(null);
        }
        if ((target.getCardNumber() == null || target.getCardNumber().isBlank()) && result.getCardNumber() != null) {
            target.setCardNumber(result.getCardNumber()); // number found at the end of the name
        }
        target.setResolvedAt(java.time.Instant.now());
        lineRepository.save(target);
    }

    /** Resolves one line in memory (no persistence): sealed product, card match, or graded slab price. */
    BuylistDraftLine resolve(BuylistDraftLine line) {
        if ("SEALED".equals(line.getKind())) {
            return resolveSealed(line);
        }
        BuylistDraftLine result = resolveCard(line);
        if (NOT_FOUND.equals(result.getResolveState()) && ItemNormalizer.isSealed(line.getName())) {
            line.setKind("SEALED");
            return resolveSealed(line);
        }
        result.setPriceSampleSize(null);
        if (result.getUnitMarketUsd() != null || result.getCardId() != null) {
            result.setPriceBasis("RAW");
        }
        if (gradedPrices != null && result.getGrading() != null && result.getCardId() != null
                && !CardResolutionService.NOT_FOUND.equals(result.getResolveState())) {
            BigDecimal rawUsd = result.getUnitMarketUsd();
            gradedPrices.gradedPrice(result.getCardId(), result.getMatchedName(), result.getMatchedSet(),
                    result.getMatchedNumber() != null ? result.getMatchedNumber() : result.getCardNumber(),
                    result.getGrading())
                    .filter(graded -> plausible(graded.usd(), rawUsd, result.getGrading()))
                    .ifPresent(graded -> {
                        result.setUnitMarketUsd(graded.usd());
                        result.setPriceSource(graded.source());
                        result.setPriceUpdatedAt(null);
                        result.setPriceSampleSize(graded.saleCount());
                        result.setPriceBasis("GRADED");
                    });
        }
        return result;
    }

    BuylistDraftLine resolveSealed(BuylistDraftLine line) {
        if (sealedPrices == null || !sealedPrices.isConfigured()) {
            line.setResolveState(NOT_FOUND);
            line.setIdConfidence(BigDecimal.ZERO);
            return line;
        }
        String query = line.getName();
        if (line.getSetName() != null && !line.getSetName().isBlank()
                && !query.toLowerCase(Locale.ROOT).contains(line.getSetName().toLowerCase(Locale.ROOT))) {
            query = line.getSetName() + " " + query;
        }
        List<SealedPriceService.SealedProduct> found = sealedPrices.search(query, config.getMaxCandidates());
        if (found.isEmpty() && line.getSetName() != null && !line.getSetName().isBlank()) {
            found = sealedPrices.search(line.getName(), config.getMaxCandidates());
        }
        if (found.isEmpty()) {
            line.setResolveState(NOT_FOUND);
            line.setIdConfidence(BigDecimal.ZERO);
            return line;
        }
        SealedPriceService.SealedProduct top = found.getFirst();
        boolean matched = top.matchScore() >= 0.75;
        BigDecimal confidence = BigDecimal.valueOf(Math.min(1.0, top.matchScore())).setScale(3, RoundingMode.HALF_UP);
        if (matched) {
            return applySealed(line, top, confidence, RESOLVED, null);
        }
        return applySealed(line, top, new BigDecimal("0.350"), AMBIGUOUS, sealedCandidatesJson(found));
    }

    private BuylistDraftLine applySealed(BuylistDraftLine line, SealedPriceService.SealedProduct product,
                                        BigDecimal confidence, String state, String candidates) {
        line.setCardId("sealed-" + product.id());
        line.setMatchedName(product.name());
        line.setMatchedSet(product.setName());
        line.setCategory("SEALED");
        line.setImageUrl(truncate(product.imageUrl(), 500));
        line.setEurTrend(product.eur30dAverage());
        if (product.eur30dAverage() != null) {
            BigDecimal usd = exchangeRates != null
                    ? exchangeRates.convertEurToUsd(product.eur30dAverage())
                    : product.eur30dAverage().multiply(new BigDecimal("1.5000")).divide(new BigDecimal("1.3800"), 2, RoundingMode.HALF_UP);
            line.setUnitMarketUsd(usd);
        } else {
            line.setUnitMarketUsd(null);
        }
        line.setPriceSource("CARDMARKET");
        line.setPriceBasis("SEALED");
        line.setPriceUpdatedAt(null);
        line.setIdConfidence(confidence);
        line.setResolveState(state);
        line.setCandidatesJson(candidates);
        return line;
    }

    private String sealedCandidatesJson(List<SealedPriceService.SealedProduct> products) {
        try {
            return objectMapper.writeValueAsString(products.stream().limit(config.getMaxCandidates())
                    .map(p -> Map.of(
                            "cardId", "sealed-" + p.id(),
                            "name", Objects.toString(p.name(), ""),
                            "setName", Objects.toString(p.setName(), ""),
                            "cardNumber", ""))
                    .toList());
        } catch (Exception e) {
            return null;
        }
    }

    /** Which candidates are left after the set filters, and how sure we are of the first one. */
    private record Decision(List<CardMarketPrice> cards, String state, BigDecimal confidence) {}

    /**
     * Narrows candidates (non-empty, each with its set name and size) the way a person would: the printed set size
     * ("4/102": Base Set has 102 cards, Base Set 2 has 130), then the set name. One left is RESOLVED; several are
     * AMBIGUOUS with the first applied.
     */
    private Decision decide(BuylistDraftLine line, List<CardMarketPrice> cards, Integer setTotal, String number, int narrowedSize) {
        List<CardMarketPrice> full = cards;
        if (setTotal != null && full.size() > 1) {
            List<CardMarketPrice> byTotal = full.stream().filter(c -> setTotal.equals(c.setOfficialCount())).toList();
            if (!byTotal.isEmpty()) {
                full = byTotal;
            }
        }
        if (line.getSetName() != null && !line.getSetName().isBlank() && full.size() > 1) {
            String set = line.getSetName().trim().toLowerCase(Locale.ROOT);
            // Exact set name first ("Base Set 2" must not also match "Base Set"), then partial
            List<CardMarketPrice> bySet = full.stream()
                    .filter(c -> c.setName() != null && c.setName().trim().equalsIgnoreCase(set)).toList();
            if (bySet.isEmpty()) {
                bySet = full.stream().filter(c -> c.setName() != null
                        && (c.setName().toLowerCase(Locale.ROOT).contains(set) || set.contains(c.setName().toLowerCase(Locale.ROOT))))
                        .toList();
            }
            if (!bySet.isEmpty()) {
                full = bySet;
            }
        }
        boolean pinnedBySetTotal = setTotal != null && full.size() == 1 && setTotal.equals(full.getFirst().setOfficialCount());
        if (full.size() == 1 && (pinnedBySetTotal || narrowedSize <= config.getMaxCandidates())) {
            BigDecimal confidence = pinnedBySetTotal ? new BigDecimal("0.980")
                    : number != null ? new BigDecimal("0.950") : new BigDecimal("0.600");
            return new Decision(full, RESOLVED, confidence);
        }
        return new Decision(full, AMBIGUOUS, new BigDecimal("0.350"));
    }

    /**
     * Search results with the set name and printed size filled in from the cached set list (the same values a full
     * card fetch returns), or null when the set list is unavailable or doesn't know a set.
     */
    private List<CardMarketPrice> withSetInfo(List<CardMarketPrice> briefs) {
        Map<String, com.tailorcards.api.trade.provider.TcgdexPriceProvider.SetInfo> sets = new java.util.HashMap<>();
        for (com.tailorcards.api.trade.provider.TcgdexPriceProvider.SetInfo info : lookup.sets()) {
            sets.put(info.id(), info);
        }
        if (sets.isEmpty()) {
            return null;
        }
        List<CardMarketPrice> out = new ArrayList<>();
        for (CardMarketPrice brief : briefs) {
            int dash = brief.cardId() == null ? -1 : brief.cardId().lastIndexOf('-');
            com.tailorcards.api.trade.provider.TcgdexPriceProvider.SetInfo info = dash > 0 ? sets.get(brief.cardId().substring(0, dash)) : null;
            if (info == null) {
                return null;
            }
            out.add(brief.toBuilder().setName(info.name()).setOfficialCount(info.officialCount()).build());
        }
        return out;
    }

    /**
     * A 9 or 10 selling below an ungraded copy means the sales data is mixed up (wrong print, reprint):
     * leave the slab to be priced by hand rather than offer on it.
     */
    static boolean plausible(BigDecimal gradedUsd, BigDecimal rawUsd, String grading) {
        if (rawUsd == null || rawUsd.signum() <= 0) {
            return true;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.5)?)").matcher(grading);
        boolean highGrade = m.find() && Double.parseDouble(m.group(1)) >= 9;
        if (highGrade && gradedUsd.compareTo(rawUsd) < 0) {
            log.info("Graded price {} for {} is below the ungraded {}; pricing by hand", gradedUsd, grading, rawUsd);
            return false;
        }
        return true;
    }

    private BuylistDraftLine resolveCard(BuylistDraftLine line) {
        if ("BULK".equals(line.getKind())) {
            line.setResolveState(RESOLVED);
            line.setIdConfidence(BigDecimal.ONE);
            return line;
        }
        try {
            if (line.getCardId() != null && !line.getCardId().isBlank()) {
                Optional<CardMarketPrice> byId = lookup.card(line.getCardId());
                if (byId.isPresent()) {
                    return apply(line, byId.get(), new BigDecimal("1.000"), RESOLVED, null);
                }
            }
            // A number left at the end of the name ("Charizard V 154") is the card number
            String lineName = line.getName();
            if (line.getCardNumber() == null || line.getCardNumber().isBlank()) {
                java.util.regex.Matcher trailing = TRAILING_NUMBER.matcher(lineName);
                if (trailing.find()) {
                    line.setCardNumber(trailing.group(1));
                    lineName = lineName.substring(0, trailing.start()).trim();
                }
            }
            // Set names typed into the card name ("Phantasmal Flames Charizard") become a set filter
            String searchName = lookup.stripSetNames(lineName);
            if (searchName.isBlank()) {
                searchName = lineName;
            }
            List<String> setIds = new ArrayList<>(lookup.setIdsFor(line.getSetName()));
            lookup.setIdsMentionedIn(line.getName()).stream().filter(id -> !setIds.contains(id)).forEach(setIds::add);
            // Set + number identifies one print: look it up directly (swsh9-154)
            Optional<CardMarketPrice> direct = lookup.findBySetAndNumber(setIds, line.getCardNumber());
            if (direct.isPresent()) {
                return apply(line, direct.get(), new BigDecimal("0.980"), RESOLVED, null);
            }
            List<CardMarketPrice> found = lookup.searchFlexible(searchName);
            if (found.isEmpty()) {
                line.setResolveState(NOT_FOUND);
                line.setIdConfidence(BigDecimal.ZERO);
                return line;
            }
            String number = normalizeNumber(line.getCardNumber());
            List<CardMarketPrice> narrowed = found;
            if (number != null) {
                List<CardMarketPrice> byNumber = found.stream()
                        .filter(c -> number.equals(normalizeNumber(c.cardNumber()))).toList();
                if (byNumber.isEmpty()) {
                    line.setResolveState(NOT_FOUND);
                    line.setIdConfidence(BigDecimal.ZERO);
                    line.setCandidatesJson(candidatesJson(found));
                    return line;
                }
                narrowed = byNumber;
            }
            if (!setIds.isEmpty()) {
                List<CardMarketPrice> inSet = narrowed.stream().filter(c -> CardLookupService.inSets(c.cardId(), setIds)).toList();
                if (!inSet.isEmpty()) {
                    narrowed = inSet;
                }
            }
            // Search results are brief. Narrow the top candidates by set size and set name using the cached set
            // list, then fetch the full card (prices) only for the one we apply. Fetching every candidate just to
            // read its set was most of the TCGdex traffic for vintage and vague lines.
            Integer setTotal = setTotal(line.getCardNumber());
            int fetchLimit = setTotal != null ? config.getMaxCandidatesWithSetTotal() : config.getMaxCandidates();
            List<CardMarketPrice> top = narrowed.stream().limit(fetchLimit).toList();
            List<CardMarketPrice> withSets = withSetInfo(top);
            if (withSets != null) {
                Decision d = decide(line, withSets, setTotal, number, narrowed.size());
                Optional<CardMarketPrice> chosen = lookup.card(d.cards().getFirst().cardId());
                if (chosen.isPresent()) {
                    return apply(line, chosen.get(), d.confidence(), d.state(),
                            AMBIGUOUS.equals(d.state()) ? candidatesJson(d.cards()) : null);
                }
                // The chosen card has no full record: fall back to fetching every candidate, as before
            }
            List<CardMarketPrice> full = new ArrayList<>();
            for (CardMarketPrice brief : top) {
                lookup.card(brief.cardId()).ifPresent(full::add);
            }
            if (full.isEmpty()) {
                line.setResolveState(NOT_FOUND);
                line.setIdConfidence(BigDecimal.ZERO);
                return line;
            }
            Decision d = decide(line, full, setTotal, number, narrowed.size());
            return apply(line, d.cards().getFirst(), d.confidence(), d.state(),
                    AMBIGUOUS.equals(d.state()) ? candidatesJson(d.cards()) : null);
        } catch (ProviderUnavailableException e) {
            log.warn("TCGdex unavailable while resolving a line: {}", e.getCause() != null
                    ? e.getCause().getClass().getSimpleName() + ": " + e.getMessage() : e.getMessage());
            line.setResolveState(ERROR);
            line.setIdConfidence(BigDecimal.ZERO);
            return line;
        }
    }

    private BuylistDraftLine apply(BuylistDraftLine line, CardMarketPrice card, BigDecimal confidence,
                                   String state, String candidates) {
        line.setCardId(card.cardId());
        line.setMatchedName(card.name());
        line.setMatchedSet(card.setName());
        line.setMatchedNumber(card.cardNumber());
        line.setRarity(truncate(card.rarity(), 80));
        line.setCategory(truncate(card.category(), 40));
        line.setImageUrl(truncate(card.imageUrl(), 500));
        BigDecimal usd = variantPrice(card, line.getVariant());
        if (usd == null && CardPricing.cardmarketUsd(card, exchangeRates) != null) {
            // No TCGplayer price: Cardmarket 30-day average, converted from EUR
            line.setUnitMarketUsd(CardPricing.cardmarketUsd(card, exchangeRates));
            line.setEurTrend(CardPricing.cardmarketEur(card));
            line.setPriceSource(CardPricing.CARDMARKET);
        } else {
            line.setUnitMarketUsd(usd);
            line.setEurTrend(card.eurTrend());
            line.setPriceSource(card.source());
        }
        line.setPriceUpdatedAt(card.pricesUpdatedAt());
        line.setIdConfidence(confidence);
        line.setResolveState(state);
        line.setCandidatesJson(candidates);
        return line;
    }

    /** USD market price for the requested variant, else the headline price; null when unpriced. */
    static BigDecimal variantPrice(CardMarketPrice card, String variant) {
        Map<String, BigDecimal> variants = card.variantPricesUsd();
        if (variant != null && variants != null) {
            String normalized = ItemNormalizer.normalizeVariant(variant);
            String wanted = normalized != null ? normalized : variant.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
            for (Map.Entry<String, BigDecimal> e : variants.entrySet()) {
                if (e.getKey().equalsIgnoreCase(wanted)) {
                    return e.getValue();
                }
            }
        }
        return card.marketPriceUsd();
    }

    private String candidatesJson(List<CardMarketPrice> cards) {
        try {
            return objectMapper.writeValueAsString(cards.stream().limit(config.getMaxCandidates())
                    .map(c -> Map.of("cardId", c.cardId(), "name", Objects.toString(c.name(), ""),
                            "setName", Objects.toString(c.setName(), ""), "cardNumber", Objects.toString(c.cardNumber(), "")))
                    .toList());
        } catch (Exception e) {
            return null;
        }
    }

    /** The set total after the slash ("4/102" gives 102); null when absent or not numeric. */
    static Integer setTotal(String number) {
        if (number == null || !number.contains("/")) {
            return null;
        }
        String tail = number.substring(number.indexOf('/') + 1).replaceAll("\\s", "");
        return tail.matches("\\d{1,4}") ? Integer.valueOf(tail) : null;
    }

    static String normalizeNumber(String number) {
        if (number == null || number.isBlank()) {
            return null;
        }
        String head = number.split("/")[0].replaceAll("[\\s#]+", "").toUpperCase(Locale.ROOT);
        String stripped = head.replaceFirst("^0+(?=.)", "");
        return stripped.isEmpty() ? null : stripped;
    }

    private static boolean sameRequest(BuylistDraftLine a, BuylistDraftLine b) {
        return Objects.equals(a.getName(), b.getName()) && Objects.equals(a.getSetName(), b.getSetName())
                && Objects.equals(a.getCardNumber(), b.getCardNumber()) && Objects.equals(a.getVariant(), b.getVariant())
                && Objects.equals(a.getCardId(), b.getCardId()) && Objects.equals(a.getGrading(), b.getGrading());
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static BuylistDraftLine copyOf(BuylistDraftLine l) {
        return BuylistDraftLine.builder().id(l.getId()).draftId(l.getDraftId()).lineNo(l.getLineNo()).kind(l.getKind())
                .inputText(l.getInputText()).name(l.getName()).setName(l.getSetName()).cardNumber(l.getCardNumber())
                .variant(l.getVariant()).itemCondition(l.getItemCondition()).grading(l.getGrading())
                .quantity(l.getQuantity()).cardId(l.getCardId())
                .resolveState(l.getResolveState()).photoUrl(l.getPhotoUrl()).createdAt(l.getCreatedAt()).build();
    }
}
