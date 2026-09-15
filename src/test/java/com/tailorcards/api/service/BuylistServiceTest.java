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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuylistServiceTest {

    @Mock
    private BuylistSubmissionRepository buylistSubmissionRepository;

    @Mock
    private SubmissionMessageRepository submissionMessageRepository;

    private BuylistMapper buylistMapper;
    private BuylistService buylistService;

    @BeforeEach
    void setUp() {
        buylistMapper = new BuylistMapper();
        buylistService = new BuylistService(
                buylistSubmissionRepository,
                submissionMessageRepository,
                buylistMapper
        );
    }

    @Test
    void createSubmission_shouldCreateWithPendingStatusAndTrackingToken() {
        BuylistSubmissionRequest request = new BuylistSubmissionRequest(
                "trainer@example.com",
                "Ash Ketchum",
                "Charizard 1st Edition",
                "Base Set",
                BigDecimal.valueOf(1500.00),
                "Slight edge wear on top back",
                List.of("https://example.com/front.jpg", "https://example.com/back.jpg")
        );

        when(buylistSubmissionRepository.save(any(BuylistSubmission.class))).thenAnswer(invocation -> {
            BuylistSubmission sub = invocation.getArgument(0);
            sub.setId(10L);
            return sub;
        });

        BuylistSubmissionResponse response = buylistService.createSubmission(request);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertNotNull(response.trackingToken());
        assertEquals("PENDING", response.status());
        assertEquals("trainer@example.com", response.customerEmail());
        assertEquals("Charizard 1st Edition", response.cardName());
        assertEquals(2, response.imageUrls().size());
        verify(buylistSubmissionRepository).save(any(BuylistSubmission.class));
    }

    @Test
    void getSubmissionByTrackingToken_whenFound_returnsResponse() {
        BuylistSubmission submission = BuylistSubmission.builder()
                .id(1L)
                .trackingToken("uuid-track-123")
                .customerEmail("collector@example.com")
                .cardName("Lugia Neo Genesis")
                .status("PENDING")
                .createdAt(Instant.now())
                .messages(new ArrayList<>())
                .imageUrls(new ArrayList<>())
                .build();

        when(buylistSubmissionRepository.findByTrackingToken("uuid-track-123"))
                .thenReturn(Optional.of(submission));

        BuylistSubmissionResponse response = buylistService.getSubmissionByTrackingToken("uuid-track-123");

        assertNotNull(response);
        assertEquals("uuid-track-123", response.trackingToken());
        assertEquals("Lugia Neo Genesis", response.cardName());
    }

    @Test
    void getSubmissionByTrackingToken_whenNotFound_throwsResourceNotFoundException() {
        when(buylistSubmissionRepository.findByTrackingToken("non-existent"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> buylistService.getSubmissionByTrackingToken("non-existent"));
    }

    @Test
    void getAdminSubmissions_withStatusFilter_queriesByStatus() {
        BuylistSubmission sub = BuylistSubmission.builder()
                .id(1L)
                .trackingToken("track-1")
                .customerEmail("c@c.com")
                .cardName("Pikachu")
                .status("UNDER_REVIEW")
                .createdAt(Instant.now())
                .build();

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(buylistSubmissionRepository.findByStatusIgnoreCase(eq("UNDER_REVIEW"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(sub), pageRequest, 1));

        Page<BuylistSubmissionResponse> result = buylistService.getAdminSubmissions("UNDER_REVIEW", pageRequest);

        assertEquals(1, result.getTotalElements());
        assertEquals("UNDER_REVIEW", result.getContent().get(0).status());
    }

    @Test
    void updateSubmissionStatus_validStatus_updatesAndSaves() {
        BuylistSubmission sub = BuylistSubmission.builder()
                .id(5L)
                .trackingToken("track-5")
                .customerEmail("buyer@example.com")
                .cardName("Gengar")
                .status("PENDING")
                .createdAt(Instant.now())
                .build();

        when(buylistSubmissionRepository.findById(5L)).thenReturn(Optional.of(sub));
        when(buylistSubmissionRepository.save(any(BuylistSubmission.class))).thenAnswer(inv -> inv.getArgument(0));

        BuylistSubmissionResponse response = buylistService.updateSubmissionStatus(5L, new BuylistStatusUpdateRequest("OFFERED"));

        assertEquals("OFFERED", response.status());
        verify(buylistSubmissionRepository).save(sub);
    }

    @Test
    void updateSubmissionStatus_invalidStatus_throwsException() {
        BuylistSubmission sub = BuylistSubmission.builder()
                .id(5L)
                .status("PENDING")
                .build();

        when(buylistSubmissionRepository.findById(5L)).thenReturn(Optional.of(sub));

        assertThrows(IllegalArgumentException.class, () ->
                buylistService.updateSubmissionStatus(5L, new BuylistStatusUpdateRequest("INVALID_STATUS")));
    }

    @Test
    void addCustomerMessage_addsMessageWithCustomerRole() {
        BuylistSubmission submission = BuylistSubmission.builder()
                .id(3L)
                .trackingToken("track-customer")
                .customerEmail("seller@example.com")
                .cardName("Umbreon VMAX")
                .build();

        when(buylistSubmissionRepository.findByTrackingToken("track-customer"))
                .thenReturn(Optional.of(submission));

        when(submissionMessageRepository.save(any(SubmissionMessage.class))).thenAnswer(inv -> {
            SubmissionMessage msg = inv.getArgument(0);
            msg.setId(99L);
            return msg;
        });

        SubmissionMessageRequest request = new SubmissionMessageRequest(
                null,
                null,
                "Can you review my submission please?"
        );

        SubmissionMessageResponse response = buylistService.addCustomerMessage("track-customer", request);

        assertNotNull(response);
        assertEquals(99L, response.id());
        assertEquals("CUSTOMER", response.senderRole());
        assertEquals("seller@example.com", response.senderEmail());
        assertEquals("Can you review my submission please?", response.message());
    }

    @Test
    void addAdminMessage_addsMessageWithAdminRole() {
        BuylistSubmission submission = BuylistSubmission.builder()
                .id(7L)
                .customerEmail("seller@example.com")
                .cardName("Rayquaza")
                .build();

        when(buylistSubmissionRepository.findById(7L))
                .thenReturn(Optional.of(submission));

        when(submissionMessageRepository.save(any(SubmissionMessage.class))).thenAnswer(inv -> {
            SubmissionMessage msg = inv.getArgument(0);
            msg.setId(100L);
            return msg;
        });

        SubmissionMessageRequest request = new SubmissionMessageRequest(
                null,
                null,
                "We can offer $400 for this card."
        );

        SubmissionMessageResponse response = buylistService.addAdminMessage(7L, request);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals("ADMIN", response.senderRole());
        assertEquals("admin@tailorcards.com", response.senderEmail());
        assertEquals("We can offer $400 for this card.", response.message());
    }
}
