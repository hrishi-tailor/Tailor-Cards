package com.tailorcards.api.controller;

import com.tailorcards.api.dto.BuylistStatusUpdateRequest;
import com.tailorcards.api.dto.BuylistSubmissionRequest;
import com.tailorcards.api.dto.BuylistSubmissionResponse;
import com.tailorcards.api.dto.BuylistUploadResponse;
import com.tailorcards.api.dto.SubmissionMessageRequest;
import com.tailorcards.api.dto.SubmissionMessageResponse;
import com.tailorcards.api.service.BuylistService;
import com.tailorcards.api.service.BuylistStorageService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;

@RestController
@RequestMapping("/api/buylist")
@Tag(name = "Buylist & Appraisals", description = "Endpoints for card photo appraisals, buylist quote submissions, tracking, and messaging")
public class BuylistController {

    private final BuylistService buylistService;
    private final BuylistStorageService buylistStorageService;

    public BuylistController(BuylistService buylistService, BuylistStorageService buylistStorageService) {
        this.buylistService = buylistService;
        this.buylistStorageService = buylistStorageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload buylist card image", description = "Uploads a card photo to Supabase Storage with local filesystem fallback")
    public ResponseEntity<BuylistUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = buylistStorageService.storeFile(file);
        return ResponseEntity.ok(new BuylistUploadResponse(url));
    }

    @PostMapping("/submit")
    @Operation(summary = "Submit buylist quote request", description = "Submit card details and photos for appraisal with a unique tracking token")
    public ResponseEntity<BuylistSubmissionResponse> submitBuylist(@Valid @RequestBody BuylistSubmissionRequest request) {
        BuylistSubmissionResponse response = buylistService.createSubmission(request);
        URI location = URI.create("/api/buylist/track/" + response.trackingToken());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/track/{trackingToken}")
    @Operation(summary = "Track buylist submission", description = "Fetch appraisal progress, offer amount, and message history using tracking token")
    public ResponseEntity<BuylistSubmissionResponse> trackSubmission(@PathVariable String trackingToken) {
        return ResponseEntity.ok(buylistService.getSubmissionByTrackingToken(trackingToken));
    }

    @GetMapping("/admin/submissions")
    @Operation(summary = "List buylist submissions (Admin)", description = "View and filter pending card appraisals. Requires ADMIN role.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<Page<BuylistSubmissionResponse>> getAdminSubmissions(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(buylistService.getAdminSubmissions(status, pageable));
    }

    @PatchMapping("/admin/submissions/{id}/status")
    @Operation(summary = "Update appraisal status / offer (Admin)", description = "Accept, counter-offer, or reject a submission. Requires ADMIN role.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<BuylistSubmissionResponse> updateSubmissionStatus(
            @PathVariable Long id,
            @Valid @RequestBody BuylistStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(buylistService.updateSubmissionStatus(id, request));
    }

    @PostMapping("/{trackingToken}/messages")
    @Operation(summary = "Post customer reply", description = "Append customer message or counter-offer reply to submission conversation")
    public ResponseEntity<SubmissionMessageResponse> addCustomerMessage(
            @PathVariable String trackingToken,
            @Valid @RequestBody SubmissionMessageRequest request
    ) {
        SubmissionMessageResponse response = buylistService.addCustomerMessage(trackingToken, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/admin/submissions/{id}/messages")
    @Operation(summary = "Post admin reply (Admin)", description = "Send an appraisal review note or counter-offer message to the customer. Requires ADMIN role.", security = @SecurityRequirement(name = "basicAuth"))
    public ResponseEntity<SubmissionMessageResponse> addAdminMessage(
            @PathVariable Long id,
            @Valid @RequestBody SubmissionMessageRequest request
    ) {
        SubmissionMessageResponse response = buylistService.addAdminMessage(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
