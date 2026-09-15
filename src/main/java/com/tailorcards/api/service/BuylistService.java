package com.tailorcards.api.service;

import com.tailorcards.api.dto.BuylistStatusUpdateRequest;
import com.tailorcards.api.dto.BuylistSubmissionRequest;
import com.tailorcards.api.dto.BuylistSubmissionResponse;
import com.tailorcards.api.dto.SubmissionMessageRequest;
import com.tailorcards.api.dto.SubmissionMessageResponse;
import com.tailorcards.api.entity.BuylistSubmission;
import com.tailorcards.api.entity.SubmissionMessage;
import com.tailorcards.api.exception.ResourceNotFoundException;
import com.tailorcards.api.mapper.BuylistMapper;
import com.tailorcards.api.repository.BuylistSubmissionRepository;
import com.tailorcards.api.repository.SubmissionMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class BuylistService {

    private final BuylistSubmissionRepository buylistSubmissionRepository;
    private final SubmissionMessageRepository submissionMessageRepository;
    private final BuylistMapper buylistMapper;

    public BuylistService(
            BuylistSubmissionRepository buylistSubmissionRepository,
            SubmissionMessageRepository submissionMessageRepository,
            BuylistMapper buylistMapper
    ) {
        this.buylistSubmissionRepository = buylistSubmissionRepository;
        this.submissionMessageRepository = submissionMessageRepository;
        this.buylistMapper = buylistMapper;
    }

    @Transactional
    public BuylistSubmissionResponse createSubmission(BuylistSubmissionRequest request) {
        BuylistSubmission submission = buylistMapper.toEntity(request);
        submission.setTrackingToken(UUID.randomUUID().toString());
        submission.setStatus("PENDING");
        submission.setCreatedAt(Instant.now());

        BuylistSubmission saved = buylistSubmissionRepository.save(submission);
        return buylistMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BuylistSubmissionResponse getSubmissionByTrackingToken(String trackingToken) {
        BuylistSubmission submission = buylistSubmissionRepository.findByTrackingToken(trackingToken)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with tracking token: " + trackingToken));
        return buylistMapper.toResponse(submission);
    }

    @Transactional(readOnly = true)
    public BuylistSubmissionResponse getSubmissionById(Long id) {
        BuylistSubmission submission = buylistSubmissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + id));
        return buylistMapper.toResponse(submission);
    }

    @Transactional(readOnly = true)
    public Page<BuylistSubmissionResponse> getAdminSubmissions(String status, Pageable pageable) {
        Page<BuylistSubmission> page;
        if (status != null && !status.isBlank()) {
            page = buylistSubmissionRepository.findByStatusIgnoreCase(status.trim(), pageable);
        } else {
            page = buylistSubmissionRepository.findAll(pageable);
        }
        return page.map(buylistMapper::toResponse);
    }

    @Transactional
    public BuylistSubmissionResponse updateSubmissionStatus(Long id, BuylistStatusUpdateRequest request) {
        BuylistSubmission submission = buylistSubmissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + id));

        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new IllegalArgumentException("Status cannot be empty");
        }

        String normalizedStatus = request.status().trim().toUpperCase(Locale.ROOT);
        if (!BuylistSubmission.VALID_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Invalid status: " + request.status() + ". Allowed statuses: " + BuylistSubmission.VALID_STATUSES);
        }

        submission.setStatus(normalizedStatus);
        BuylistSubmission saved = buylistSubmissionRepository.save(submission);
        return buylistMapper.toResponse(saved);
    }

    @Transactional
    public SubmissionMessageResponse addCustomerMessage(String trackingToken, SubmissionMessageRequest request) {
        BuylistSubmission submission = buylistSubmissionRepository.findByTrackingToken(trackingToken)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with tracking token: " + trackingToken));

        String senderEmail = (request.senderEmail() != null && !request.senderEmail().isBlank())
                ? request.senderEmail().trim()
                : submission.getCustomerEmail();

        SubmissionMessage message = SubmissionMessage.builder()
                .submission(submission)
                .senderRole("CUSTOMER")
                .senderEmail(senderEmail)
                .message(request.message())
                .createdAt(Instant.now())
                .build();

        SubmissionMessage saved = submissionMessageRepository.save(message);
        return buylistMapper.toMessageResponse(saved);
    }

    @Transactional
    public SubmissionMessageResponse addAdminMessage(Long id, SubmissionMessageRequest request) {
        BuylistSubmission submission = buylistSubmissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found with id: " + id));

        String senderEmail = (request.senderEmail() != null && !request.senderEmail().isBlank())
                ? request.senderEmail().trim()
                : "admin@tailorcards.com";

        SubmissionMessage message = SubmissionMessage.builder()
                .submission(submission)
                .senderRole("ADMIN")
                .senderEmail(senderEmail)
                .message(request.message())
                .createdAt(Instant.now())
                .build();

        SubmissionMessage saved = submissionMessageRepository.save(message);
        return buylistMapper.toMessageResponse(saved);
    }
}
