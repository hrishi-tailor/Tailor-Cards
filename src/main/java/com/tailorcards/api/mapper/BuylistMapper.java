package com.tailorcards.api.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.buylistchat.entity.BuylistSubmissionLine;
import com.tailorcards.api.buylistchat.repository.BuylistSubmissionLineRepository;
import com.tailorcards.api.dto.BuylistChatDetailsResponse;
import com.tailorcards.api.dto.BuylistSubmissionRequest;
import com.tailorcards.api.dto.BuylistSubmissionResponse;
import com.tailorcards.api.dto.SubmissionMessageResponse;
import com.tailorcards.api.entity.BuylistSubmission;
import com.tailorcards.api.entity.SubmissionMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class BuylistMapper {

    private final BuylistSubmissionLineRepository lineRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BuylistMapper() {
        this(null);
    }

    @Autowired
    public BuylistMapper(BuylistSubmissionLineRepository lineRepository) {
        this.lineRepository = lineRepository;
    }

    /** Customer-safe view: chat lines and likelihood, but no red flags, transcript or owner decision. */
    public BuylistSubmissionResponse toResponse(BuylistSubmission submission) {
        return toResponse(submission, false, true);
    }

    /** Admin view; {@code detailed} also loads lines and the chat transcript. */
    public BuylistSubmissionResponse toAdminResponse(BuylistSubmission submission, boolean detailed) {
        return toResponse(submission, true, detailed);
    }

    private BuylistSubmissionResponse toResponse(BuylistSubmission submission, boolean admin, boolean detailed) {
        if (submission == null) {
            return null;
        }

        List<SubmissionMessageResponse> messageResponses = submission.getMessages() != null
                ? submission.getMessages().stream()
                    .map(this::toMessageResponse)
                    .toList()
                : Collections.emptyList();

        List<String> images = submission.getImageUrls() != null
                ? new ArrayList<>(submission.getImageUrls())
                : Collections.emptyList();

        return new BuylistSubmissionResponse(
                submission.getId(),
                submission.getTrackingToken(),
                submission.getCustomerEmail(),
                submission.getCustomerName(),
                submission.getCardName(),
                submission.getCardSet(),
                submission.getAskingPrice(),
                submission.getAdditionalComments(),
                images,
                submission.getStatus(),
                submission.getCreatedAt(),
                messageResponses,
                chatDetails(submission, admin, detailed)
        );
    }

    private BuylistChatDetailsResponse chatDetails(BuylistSubmission s, boolean admin, boolean detailed) {
        if (s.getSource() == null) {
            return null;
        }
        List<BuylistSubmissionLine> lines = detailed && lineRepository != null && s.getId() != null
                ? lineRepository.findBySubmissionIdOrderByLineNoAsc(s.getId()) : null;
        List<BuylistChatDetailsResponse.Line> lineViews = lines == null ? null : lines.stream()
                .map(l -> new BuylistChatDetailsResponse.Line(l.getLineNo(), l.getKind(), l.getName(), l.getSetName(),
                        l.getCardNumber(), l.getVariant(), l.getItemCondition(), l.getGrading(), l.getQuantity(), l.getCardId(),
                        l.getMatchedName(), l.getMatchedSet(), l.getRarity(), l.getImageUrl(), l.getCurrency(),
                        l.getUnitMarketUsd(), l.getLineMarketUsd(), l.getEurTrend(), l.getPriceUpdatedAt(),
                        l.getPriceBasis(), l.getRequestedUnitUsd(), l.getStatus(), l.getStatusReason(), l.getPhotoUrl()))
                .toList();
        return new BuylistChatDetailsResponse(
                s.getSource(), s.getCurrency(), s.getTotalMarketUsd(), s.getEligibleMarketUsd(), s.getLikelihoodPct(),
                "Estimated chance, not a guarantee", readList(s.getLikelihoodReasons(), new TypeReference<List<String>>() {}),
                s.getQuoteExpiresAt(), lines == null ? null : lines.size(), lineViews,
                admin ? readList(s.getRedFlags(), new TypeReference<List<String>>() {}) : null,
                admin && detailed ? readList(s.getChatTranscript(), new TypeReference<List<Map<String, Object>>>() {}) : null,
                admin ? s.getOwnerDecision() : null,
                s.getCounterAmountUsd(),
                s.getDealType(), s.getRequestedCashUsd(),
                readList(s.getStoreCardsJson(), new TypeReference<List<Map<String, Object>>>() {}),
                s.getStoreTotalUsd(), s.getCashOfferUsd(), s.getTradeCreditUsd(),
                admin ? s.getAskRatio() : null, s.getUsdCadRate());
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return List.of();
        }
    }

    public SubmissionMessageResponse toMessageResponse(SubmissionMessage message) {
        if (message == null) {
            return null;
        }
        return new SubmissionMessageResponse(
                message.getId(),
                message.getSenderRole(),
                message.getSenderEmail(),
                message.getMessage(),
                message.getCreatedAt()
        );
    }

    public BuylistSubmission toEntity(BuylistSubmissionRequest request) {
        if (request == null) {
            return null;
        }

        List<String> images = request.imageUrls() != null
                ? new ArrayList<>(request.imageUrls())
                : new ArrayList<>();

        return BuylistSubmission.builder()
                .customerEmail(request.customerEmail())
                .customerName(request.customerName())
                .cardName(request.cardName())
                .cardSet(request.cardSet())
                .askingPrice(request.askingPrice())
                .additionalComments(request.additionalComments())
                .imageUrls(images)
                .status("PENDING")
                .build();
    }
}
