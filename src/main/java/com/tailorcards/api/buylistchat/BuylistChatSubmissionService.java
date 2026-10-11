package com.tailorcards.api.buylistchat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ConfirmRequest;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.ConfirmResponse;
import com.tailorcards.api.buylistchat.dto.BuylistChatDtos.LineView;
import com.tailorcards.api.buylistchat.email.EmailSender;
import com.tailorcards.api.buylistchat.entity.BuylistDraft;
import com.tailorcards.api.buylistchat.entity.BuylistDraftLine;
import com.tailorcards.api.buylistchat.entity.BuylistSubmissionLine;
import com.tailorcards.api.buylistchat.identity.BuylistIdentityService;
import com.tailorcards.api.buylistchat.pricing.LineStatus;
import com.tailorcards.api.buylistchat.repository.BuylistDraftLineRepository;
import com.tailorcards.api.buylistchat.repository.BuylistDraftRepository;
import com.tailorcards.api.buylistchat.repository.BuylistSubmissionLineRepository;
import com.tailorcards.api.entity.BuylistSubmission;
import com.tailorcards.api.repository.BuylistSubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Confirm-button flow: re-validates ownership, readiness, the content hash and the daily limits,
 * then creates the submission in the existing buylist queue. Only this endpoint (driven by the
 * customer's button) creates submissions; the model has no way to call it.
 */
@Slf4j
@Service
public class BuylistChatSubmissionService {

    public static final String SOURCE_CHAT = "CHAT";
    private static final String ALREADY_TODAY = "You've already submitted a list today (one per day). You can submit again tomorrow.";

    private final BuylistDraftService draftService;
    private final BuylistDraftRepository draftRepository;
    private final BuylistDraftLineRepository lineRepository;
    private final BuylistSubmissionRepository submissionRepository;
    private final BuylistSubmissionLineRepository submissionLineRepository;
    private final EmailSender emailSender;
    private final BuylistChatProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService notifier = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("buylist-notify").daemon().factory());
    private final Clock clock = Clock.systemUTC();

    public BuylistChatSubmissionService(BuylistDraftService draftService, BuylistDraftRepository draftRepository,
                                        BuylistDraftLineRepository lineRepository,
                                        BuylistSubmissionRepository submissionRepository,
                                        BuylistSubmissionLineRepository submissionLineRepository,
                                        EmailSender emailSender, BuylistChatProperties properties) {
        this.draftService = draftService;
        this.draftRepository = draftRepository;
        this.lineRepository = lineRepository;
        this.submissionRepository = submissionRepository;
        this.submissionLineRepository = submissionLineRepository;
        this.emailSender = emailSender;
        this.properties = properties;
    }

    @Transactional
    public ConfirmResponse confirm(String sessionIdentity, String draftId, ConfirmRequest request, String ip) {
        BuylistDraft draft = draftService.ownedOpenDraft(draftId, sessionIdentity);
        // Anonymous sessions (verification off) give an unverified contact email at confirm;
        // the one-per-day limit applies to that email.
        boolean guest = BuylistIdentityService.isGuest(sessionIdentity);
        if (guest && (request == null || request.email() == null || request.email().isBlank())) {
            throw new IllegalArgumentException("Enter your email so we can contact you about this list.");
        }
        String email = guest ? BuylistIdentityService.normalizeEmail(request.email()) : sessionIdentity;
        List<BuylistDraftLine> lines = lineRepository.findByDraftIdOrderByLineNoAsc(draft.getId());
        BuylistDraftService.Evaluation evaluation = draftService.evaluate(draft, lines);

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Add at least one card before submitting.");
        }
        if (!evaluation.summary().ready()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "We're still checking your cards. Try again in a moment.");
        }
        if (request == null || request.contentHash() == null || !request.contentHash().equals(evaluation.summary().contentHash())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Your list changed since you reviewed it. Check the updated summary and confirm again.");
        }

        BuylistChatDtos.DealView deal = evaluation.summary().deal();
        if (deal.storeCards().stream().anyMatch(c -> !c.available())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A shop card you picked is no longer available. Remove it and confirm again.");
        }
        if (deal.needsStoreCards()) {
            throw new IllegalArgumentException("Pick at least one card from our shop for a trade, or switch to sell.");
        }

        LocalDate today = draftService.today();
        if (submissionRepository.existsByCustomerEmailAndLocalDate(email, today)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, ALREADY_TODAY);
        }
        long fromIp = ip == null ? 0 : submissionRepository.countBySubmitterIpAndLocalDate(ip, today);
        if (fromIp >= properties.getLimits().getSubmissionsPerIpPerDay()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many submissions from this network today. Please try again tomorrow.");
        }

        Instant now = clock.instant();
        Instant quoteExpires = now.plus(Duration.ofHours(properties.getQuoteValidHours()));
        int eligibleCount = evaluation.summary().statusCounts().getOrDefault(LineStatus.ELIGIBLE.name(), 0);
        BuylistSubmission submission = BuylistSubmission.builder()
                .trackingToken(UUID.randomUUID().toString())
                .customerEmail(email)
                .customerName(clean(request.customerName(), 150))
                .cardName("Chat buylist (" + deal.dealType().toLowerCase(java.util.Locale.ROOT) + "): " + lines.size()
                        + " line(s), " + eligibleCount + " eligible")
                .additionalComments(clean(request.notes(), 2000))
                .status("PENDING")
                .createdAt(now)
                .source(SOURCE_CHAT)
                .currency(BuylistDraftService.CURRENCY)
                .totalMarketUsd(scale(evaluation.summary().totalMarketUsd()))
                .eligibleMarketUsd(scale(evaluation.summary().eligibleMarketUsd()))
                .likelihoodPct(evaluation.likelihood().percent())
                .likelihoodReasons(json(evaluation.likelihood().reasons()))
                .redFlags(json(withDealFlag(withVerificationFlag(redFlags(evaluation, lines, fromIp), guest), deal)))
                .localDate(today)
                .contentHash(evaluation.summary().contentHash())
                .submitterIp(ip)
                .draftId(draft.getId())
                .chatTranscript(json(draftService.messages(draft).stream()
                        .map(m -> Map.of("role", m.getSenderRole(), "content", m.getContent(), "at", m.getCreatedAt().toString()))
                        .toList()))
                .quoteExpiresAt(quoteExpires)
                .dealType(deal.dealType())
                .requestedCashUsd("TRADE".equals(deal.dealType()) ? null
                        : "PARTIAL".equals(deal.dealType()) ? deal.requestedCashUsd() : deal.askTotalUsd())
                .storeCardsJson(deal.storeCards().isEmpty() ? null : json(deal.storeCards().stream()
                        .map(c -> Map.of("productId", c.productId(), "name", c.name(), "priceCad", c.priceCad(),
                                "priceUsd", c.priceUsd()))
                        .toList()))
                .storeTotalUsd(deal.storeCards().isEmpty() ? null : deal.storeTotalUsd())
                .cashOfferUsd(deal.cashOfferUsd())
                .tradeCreditUsd(deal.tradeCreditUsd())
                .askRatio(deal.askRatio())
                .usdCadRate(deal.usdCadRate())
                .build();
        try {
            submission = submissionRepository.saveAndFlush(submission);
        } catch (DataIntegrityViolationException e) {
            // Unique (customer_email, local_date): a concurrent confirm won the race
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, ALREADY_TODAY);
        }

        Map<Long, BuylistDraftLine> byId = new java.util.HashMap<>();
        lines.forEach(l -> byId.put(l.getId(), l));
        for (LineView view : evaluation.lineViews()) {
            BuylistDraftLine line = byId.get(view.id());
            submissionLineRepository.save(BuylistSubmissionLine.builder()
                    .submissionId(submission.getId()).lineNo(view.lineNo()).kind(view.kind()).name(view.name())
                    .setName(view.setName()).cardNumber(view.cardNumber()).variant(view.variant())
                    .itemCondition(view.condition()).grading(view.grading()).quantity(view.quantity()).cardId(view.cardId())
                    .matchedName(view.matchedName()).matchedSet(view.matchedSet()).rarity(view.rarity())
                    .imageUrl(view.imageUrl()).currency(view.currency()).unitMarketUsd(view.unitMarketUsd())
                    .lineMarketUsd(scale(view.lineMarketUsd())).eurTrend(view.eurTrend())
                    .priceUpdatedAt(view.priceUpdatedAt()).idConfidence(line.getIdConfidence())
                    .priceBasis(view.priceBasis()).requestedUnitUsd(view.requestedUnitUsd())
                    .status(view.status()).statusReason(clean(view.statusReason(), 200)).photoUrl(view.photoUrl())
                    .build());
        }

        draft.setStatus(BuylistDraftService.SUBMITTED);
        draft.setSubmissionId(submission.getId());
        draft.setUpdatedAt(now);
        draftRepository.save(draft);

        BuylistSubmission saved = submission;
        afterCommit(() -> notifyNewSubmission(saved, lines.size()));
        return new ConfirmResponse(saved.getId(), saved.getTrackingToken(), saved.getLikelihoodPct(),
                BuylistDraftService.CURRENCY, saved.getTotalMarketUsd(), quoteExpires, BuylistDraftService.QUOTE_NOTICE);
    }

    /** Admin: lets the email submit again today by releasing today's slot (kept for audit). */
    @Transactional
    public int resetDailyLimit(String rawEmail) {
        String email = BuylistIdentityService.normalizeEmail(rawEmail);
        List<BuylistSubmission> today = submissionRepository.findByCustomerEmailAndLocalDate(email, draftService.today());
        Instant now = clock.instant();
        for (BuylistSubmission submission : today) {
            submission.setLocalDate(null);
            submission.setLimitResetAt(now);
            submissionRepository.save(submission);
        }
        log.info("Admin reset buylist daily limit ({} submission(s) released)", today.size());
        return today.size();
    }

    List<String> redFlags(BuylistDraftService.Evaluation evaluation, List<BuylistDraftLine> lines, long otherFromIp) {
        List<String> flags = new ArrayList<>();
        Map<String, Integer> counts = evaluation.summary().statusCounts();
        BigDecimal total = evaluation.summary().totalMarketUsd();
        long missingPhotos = evaluation.lineViews().stream().filter(v -> v.photoRequired() && v.photoUrl() == null).count();
        if (missingPhotos > 0 && total != null && total.compareTo(new BigDecimal("500")) >= 0) {
            flags.add("High value ($" + total.setScale(2, RoundingMode.HALF_UP) + " USD) with " + missingPhotos + " line(s) missing photos");
        }
        int unclear = counts.getOrDefault(LineStatus.UNIDENTIFIED.name(), 0) + counts.getOrDefault(LineStatus.NEEDS_REVIEW.name(), 0);
        if (!lines.isEmpty() && unclear * 10 >= lines.size() * 3) {
            flags.add(unclear + " of " + lines.size() + " lines unidentified or need review");
        }
        long atCap = lines.stream().filter(l -> "CARD".equals(l.getKind())
                && l.getQuantity() >= properties.getLimits().getMaxQuantityPerLine()).count();
        if (atCap > 0) {
            flags.add(atCap + " line(s) at the per-line quantity cap");
        }
        long damaged = lines.stream().filter(l -> "DMG".equals(l.getItemCondition())).count();
        if (damaged > 0) {
            flags.add(damaged + " damaged line(s)");
        }
        if (otherFromIp > 0) {
            flags.add("Same IP made " + otherFromIp + " other submission(s) today");
        }
        if (lines.size() >= 300) {
            flags.add("Large list (" + lines.size() + " lines)");
        }
        if (evaluation.likelihood().percent() < 30) {
            flags.add("Low likelihood (" + evaluation.likelihood().percent() + "%)");
        }
        return flags;
    }

    private static List<String> withDealFlag(List<String> flags, BuylistChatDtos.DealView deal) {
        if (deal.askRatio() != null && deal.askRatio().compareTo(new BigDecimal("1.15")) > 0) {
            int pct = deal.askRatio().subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
            flags.add(0, "Asks " + pct + "% above your rates (" + deal.dealType().toLowerCase(java.util.Locale.ROOT) + ")");
        }
        return flags;
    }

    private static List<String> withVerificationFlag(List<String> flags, boolean guest) {
        if (guest) {
            flags.add(0, "Email not verified (verification was turned off)");
        }
        return flags;
    }

    private void notifyNewSubmission(BuylistSubmission submission, int lineCount) {
        notifier.submit(() -> {
            String notifyTo = properties.getEmail().getNotifyTo();
            if (notifyTo != null && !notifyTo.isBlank()) {
                emailSender.send(notifyTo, "New buylist submission #" + submission.getId(),
                        "A customer submitted a buylist through the chat.\n\n"
                                + "Lines: " + lineCount + "\n"
                                + "Market reference: " + money(submission.getTotalMarketUsd()) + " USD (eligible "
                                + money(submission.getEligibleMarketUsd()) + ")\n"
                                + "Deal: " + submission.getDealType()
                                + (submission.getStoreTotalUsd() != null ? ", shop cards " + money(submission.getStoreTotalUsd()) + " USD" : "")
                                + (submission.getRequestedCashUsd() != null ? ", cash asked " + money(submission.getRequestedCashUsd()) + " USD" : "")
                                + (submission.getAskRatio() != null ? " (" + submission.getAskRatio() + "x your rates)" : "") + "\n"
                                + "Estimated chance: " + submission.getLikelihoodPct() + "%\n\n"
                                + "Review it in the admin buylist queue.");
            }
            emailSender.send(submission.getCustomerEmail(), "We received your Tailor Cards buylist",
                    "Thanks! We received your list (" + lineCount + " line(s)) and will review it.\n\n"
                            + "Tracking code: " + submission.getTrackingToken() + "\n\n"
                            + BuylistDraftService.QUOTE_NOTICE);
        });
    }

    private static String money(BigDecimal value) {
        return value == null ? "no price" : "$" + value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String clean(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\n]]", " ").trim();
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
