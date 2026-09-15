package com.tailorcards.api.mapper;

import com.tailorcards.api.dto.BuylistSubmissionRequest;
import com.tailorcards.api.dto.BuylistSubmissionResponse;
import com.tailorcards.api.dto.SubmissionMessageResponse;
import com.tailorcards.api.entity.BuylistSubmission;
import com.tailorcards.api.entity.SubmissionMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class BuylistMapper {

    public BuylistSubmissionResponse toResponse(BuylistSubmission submission) {
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
                messageResponses
        );
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
