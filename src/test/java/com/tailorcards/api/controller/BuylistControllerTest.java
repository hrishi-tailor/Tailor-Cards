package com.tailorcards.api.controller;

import com.tailorcards.api.dto.BuylistStatusUpdateRequest;
import com.tailorcards.api.dto.BuylistSubmissionRequest;
import com.tailorcards.api.dto.BuylistSubmissionResponse;
import com.tailorcards.api.dto.BuylistUploadResponse;
import com.tailorcards.api.dto.SubmissionMessageRequest;
import com.tailorcards.api.dto.SubmissionMessageResponse;
import com.tailorcards.api.service.BuylistService;
import com.tailorcards.api.service.BuylistStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuylistControllerTest {

    @Mock
    private BuylistService buylistService;

    @Mock
    private BuylistStorageService buylistStorageService;

    private BuylistController controller;

    @BeforeEach
    void setUp() {
        controller = new BuylistController(buylistService, buylistStorageService);
    }

    @Test
    void uploadImage_success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "dummy".getBytes());
        when(buylistStorageService.storeFile(file)).thenReturn("/uploads/buylist/test-uuid.jpg");

        ResponseEntity<BuylistUploadResponse> response = controller.uploadImage(file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("/uploads/buylist/test-uuid.jpg", response.getBody().url());
    }

    @Test
    void submitBuylist_returnsCreatedWithLocation() {
        BuylistSubmissionRequest request = new BuylistSubmissionRequest(
                "trainer@example.com",
                "Ash",
                "Charizard",
                "Base Set",
                BigDecimal.valueOf(500),
                "Clean copy",
                List.of("/uploads/buylist/img1.jpg")
        );

        BuylistSubmissionResponse serviceResponse = new BuylistSubmissionResponse(
                1L,
                "track-xyz-123",
                "trainer@example.com",
                "Ash",
                "Charizard",
                "Base Set",
                BigDecimal.valueOf(500),
                "Clean copy",
                List.of("/uploads/buylist/img1.jpg"),
                "PENDING",
                Instant.now(),
                List.of()
        );

        when(buylistService.createSubmission(any())).thenReturn(serviceResponse);

        ResponseEntity<BuylistSubmissionResponse> response = controller.submitBuylist(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("/api/buylist/track/track-xyz-123", response.getHeaders().getLocation().getPath());
        assertEquals("track-xyz-123", response.getBody().trackingToken());
    }

    @Test
    void trackSubmission_returnsOk() {
        BuylistSubmissionResponse serviceResponse = new BuylistSubmissionResponse(
                1L,
                "track-abc",
                "user@example.com",
                null,
                "Blastoise",
                "Base",
                null,
                null,
                List.of(),
                "PENDING",
                Instant.now(),
                List.of()
        );

        when(buylistService.getSubmissionByTrackingToken("track-abc")).thenReturn(serviceResponse);

        ResponseEntity<BuylistSubmissionResponse> response = controller.trackSubmission("track-abc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Blastoise", response.getBody().cardName());
    }

    @Test
    void getAdminSubmissions_returnsSubmissionsPage() {
        PageRequest pageRequest = PageRequest.of(0, 50);
        BuylistSubmissionResponse item = new BuylistSubmissionResponse(
                1L, "t-1", "e@e.com", null, "Card", null, null, null, List.of(), "PENDING", Instant.now(), List.of()
        );
        when(buylistService.getAdminSubmissions(eq("PENDING"), any()))
                .thenReturn(new PageImpl<>(List.of(item), pageRequest, 1));

        ResponseEntity<Page<BuylistSubmissionResponse>> response = controller.getAdminSubmissions("PENDING", pageRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
    }

    @Test
    void updateSubmissionStatus_returnsUpdated() {
        BuylistStatusUpdateRequest updateRequest = new BuylistStatusUpdateRequest("UNDER_REVIEW");
        BuylistSubmissionResponse updated = new BuylistSubmissionResponse(
                2L, "t-2", "e@e.com", null, "Venusaur", null, null, null, List.of(), "UNDER_REVIEW", Instant.now(), List.of()
        );

        when(buylistService.updateSubmissionStatus(2L, updateRequest)).thenReturn(updated);

        ResponseEntity<BuylistSubmissionResponse> response = controller.updateSubmissionStatus(2L, updateRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UNDER_REVIEW", response.getBody().status());
    }

    @Test
    void addCustomerMessage_returnsCreated() {
        SubmissionMessageRequest request = new SubmissionMessageRequest("c@c.com", null, "Hi");
        SubmissionMessageResponse messageResponse = new SubmissionMessageResponse(
                1L, "CUSTOMER", "c@c.com", "Hi", Instant.now()
        );

        when(buylistService.addCustomerMessage(eq("track-1"), any())).thenReturn(messageResponse);

        ResponseEntity<SubmissionMessageResponse> response = controller.addCustomerMessage("track-1", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("CUSTOMER", response.getBody().senderRole());
        assertEquals("Hi", response.getBody().message());
    }

    @Test
    void addAdminMessage_returnsCreated() {
        SubmissionMessageRequest request = new SubmissionMessageRequest(null, null, "Offer sent");
        SubmissionMessageResponse messageResponse = new SubmissionMessageResponse(
                2L, "ADMIN", "admin@tailorcards.com", "Offer sent", Instant.now()
        );

        when(buylistService.addAdminMessage(eq(10L), any())).thenReturn(messageResponse);

        ResponseEntity<SubmissionMessageResponse> response = controller.addAdminMessage(10L, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("ADMIN", response.getBody().senderRole());
        assertEquals("Offer sent", response.getBody().message());
    }
}
