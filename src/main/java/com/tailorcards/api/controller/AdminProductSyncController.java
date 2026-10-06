package com.tailorcards.api.controller;

import com.tailorcards.api.dto.ProductPokemontcgCandidateDto;
import com.tailorcards.api.dto.ProductResponse;
import com.tailorcards.api.dto.SnapshotSyncResponse;
import com.tailorcards.api.dto.UpdatePokemontcgIdRequest;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.mapper.ProductMapper;
import com.tailorcards.api.service.ProductPokemontcgBackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Product Sync & Pricing", description = "Endpoints for backfilling pokemontcg IDs and triggering price snapshot synchronizations")
@SecurityRequirement(name = "basicAuth")
public class AdminProductSyncController {

    private final ProductPokemontcgBackfillService backfillService;
    private final ProductMapper productMapper;

    @GetMapping("/products/unlinked")
    @Operation(summary = "List unlinked products", description = "Retrieve all inventory products that currently lack a pokemontcg_id")
    public ResponseEntity<List<ProductResponse>> getUnlinkedProducts() {
        List<ProductResponse> dtos = backfillService.getUnlinkedProducts().stream()
                .map(productMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/products/{productId}/candidates")
    @Operation(summary = "Search pokemontcg.io candidates", description = "Query pokemontcg.io for candidate cards matching product name, set, or number")
    public ResponseEntity<List<ProductPokemontcgCandidateDto>> getCandidates(
            @PathVariable Long productId,
            @RequestParam(required = false) String query
    ) {
        List<ProductPokemontcgCandidateDto> candidates = backfillService.findCandidates(productId, query);
        return ResponseEntity.ok(candidates);
    }

    @PutMapping("/products/{productId}/pokemontcg-id")
    @Operation(summary = "Confirm and update pokemontcg ID", description = "Persist confirmed pokemontcg_id match on product record")
    public ResponseEntity<ProductResponse> updatePokemontcgId(
            @PathVariable Long productId,
            @Valid @RequestBody UpdatePokemontcgIdRequest request
    ) {
        Product updated = backfillService.updateProductPokemontcgId(productId, request.pokemontcgId());
        return ResponseEntity.ok(productMapper.toResponse(updated));
    }

    @PostMapping("/pricing/sync-snapshots")
    @Operation(summary = "Trigger snapshot sync job", description = "Admin endpoint to trigger the nightly price snapshot job once on-demand")
    public ResponseEntity<SnapshotSyncResponse> triggerSnapshotSync() {
        SnapshotSyncResponse response = backfillService.triggerSnapshotSync();
        return ResponseEntity.ok(response);
    }
}
