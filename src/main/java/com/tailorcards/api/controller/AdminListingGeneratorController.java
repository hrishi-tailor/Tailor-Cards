package com.tailorcards.api.controller;

import com.tailorcards.api.listing.ListingCondition;
import com.tailorcards.api.listing.ListingGeneratorService;
import com.tailorcards.api.listing.dto.ListingDraftResponse;
import com.tailorcards.api.listing.dto.MarketReferenceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/listing-generator")
@RequiredArgsConstructor
@Tag(name = "Admin - Listing Generator", description = "Draft product listings from card photos for admin review. Never publishes or prices anything.")
@SecurityRequirement(name = "basicAuth")
public class AdminListingGeneratorController {

    private final ListingGeneratorService listingGeneratorService;

    @PostMapping(value = "/draft", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Generate a listing draft",
            description = "Reads 1-2 card photos (JPEG/PNG/WebP, max 5 MB each) and returns an editable text draft plus an optional market reference. Photos are not stored.")
    public ResponseEntity<ListingDraftResponse> generateDraft(
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @RequestParam(value = "note", required = false) String note,
            Authentication authentication
    ) {
        return ResponseEntity.ok(listingGeneratorService.generateDraft(authentication.getName(), images, note));
    }

    @GetMapping("/market-reference")
    @Operation(summary = "Market reference for a chosen card",
            description = "Resolves the market reference (CAD) and stock image for a candidate card picked by the admin.")
    public ResponseEntity<MarketReferenceResponse> marketReference(
            @RequestParam String cardId,
            @RequestParam(required = false) ListingCondition condition,
            @RequestParam(required = false) String gradingCompany,
            @RequestParam(required = false) String grade,
            @RequestParam(defaultValue = "false") boolean sealed
    ) {
        return ResponseEntity.ok(listingGeneratorService.marketReference(cardId, condition, gradingCompany, grade, sealed));
    }
}
