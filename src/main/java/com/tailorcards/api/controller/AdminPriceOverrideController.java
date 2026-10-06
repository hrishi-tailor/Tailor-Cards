package com.tailorcards.api.controller;

import com.tailorcards.api.dto.ManualPriceOverrideRequest;
import com.tailorcards.api.dto.ManualPriceOverrideResponse;
import com.tailorcards.api.dto.PriceSnapshotResponse;
import com.tailorcards.api.entity.ManualPriceOverride;
import com.tailorcards.api.entity.PriceSnapshot;
import com.tailorcards.api.repository.ManualPriceOverrideRepository;
import com.tailorcards.api.repository.PriceSnapshotRepository;
import com.tailorcards.api.trade.scheduler.PriceSnapshotScheduler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Price Overrides", description = "Endpoints for managing manual card pricing overrides and historical snapshots")
@SecurityRequirement(name = "basicAuth")
public class AdminPriceOverrideController {

    private final ManualPriceOverrideRepository overrideRepository;
    private final PriceSnapshotRepository snapshotRepository;
    private final PriceSnapshotScheduler snapshotScheduler;

    @GetMapping("/price-overrides")
    @Operation(summary = "List all manual price overrides", description = "Retrieve all custom CAD price overrides for graded slabs, sealed items, or raw conditions")
    public ResponseEntity<List<ManualPriceOverrideResponse>> getAllOverrides() {
        List<ManualPriceOverrideResponse> responses = overrideRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(this::toOverrideResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/price-overrides/cards/{cardId}")
    @Operation(summary = "Get price overrides by card ID", description = "Retrieve overrides for a specific card (e.g. base1-4)")
    public ResponseEntity<List<ManualPriceOverrideResponse>> getOverridesByCardId(@PathVariable String cardId) {
        List<ManualPriceOverrideResponse> responses = overrideRepository.findByCardIdOrderByUpdatedAtDesc(cardId)
                .stream()
                .map(this::toOverrideResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/price-overrides")
    @Operation(summary = "Create or update manual price override", description = "Set a CAD price override for a card and condition/grade (e.g. PSA 10, BGS BL, SEALED)")
    public ResponseEntity<ManualPriceOverrideResponse> upsertOverride(@Valid @RequestBody ManualPriceOverrideRequest request) {
        String cleanCondition = request.conditionOrGrade().trim().toUpperCase();

        ManualPriceOverride override = overrideRepository
                .findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc(request.cardId(), cleanCondition)
                .orElseGet(() -> ManualPriceOverride.builder()
                        .cardId(request.cardId())
                        .conditionOrGrade(cleanCondition)
                        .build());

        override.setOverridePriceCad(request.overridePriceCad());
        override.setNotes(request.notes());
        override.setUpdatedAt(Instant.now());

        ManualPriceOverride saved = overrideRepository.save(override);
        return ResponseEntity.status(HttpStatus.CREATED).body(toOverrideResponse(saved));
    }

    @DeleteMapping("/price-overrides/{id}")
    @Operation(summary = "Delete manual price override", description = "Remove an existing manual price override by ID")
    public ResponseEntity<Void> deleteOverride(@PathVariable Long id) {
        if (!overrideRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        overrideRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/price-snapshots")
    @Operation(summary = "List recent price snapshots", description = "View the most recent card market price snapshots in USD and CAD")
    public ResponseEntity<List<PriceSnapshotResponse>> getRecentSnapshots() {
        List<PriceSnapshotResponse> responses = snapshotRepository.findTop100ByOrderByFetchedAtDesc()
                .stream()
                .map(this::toSnapshotResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/price-snapshots/cards/{cardId}")
    @Operation(summary = "Get snapshot history for a card", description = "Retrieve historical price snapshots for a specific card ID")
    public ResponseEntity<List<PriceSnapshotResponse>> getCardSnapshots(@PathVariable String cardId) {
        List<PriceSnapshotResponse> responses = snapshotRepository.findByCardIdOrderByFetchedAtDesc(cardId)
                .stream()
                .map(this::toSnapshotResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/price-snapshots/sync")
    @Operation(summary = "Trigger nightly snapshot sync job", description = "Manually run the market price snapshot synchronization for all listed cards")
    public ResponseEntity<Map<String, Object>> triggerSync() {
        int updated = snapshotScheduler.syncListedCardPrices();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "cardsUpdated", updated,
                "timestamp", Instant.now().toString()
        ));
    }

    private ManualPriceOverrideResponse toOverrideResponse(ManualPriceOverride override) {
        return new ManualPriceOverrideResponse(
                override.getId(),
                override.getCardId(),
                override.getConditionOrGrade(),
                override.getOverridePriceCad(),
                override.getNotes(),
                override.getUpdatedAt()
        );
    }

    private PriceSnapshotResponse toSnapshotResponse(PriceSnapshot snapshot) {
        return new PriceSnapshotResponse(
                snapshot.getId(),
                snapshot.getCardId(),
                snapshot.getPriceUsd(),
                snapshot.getPriceCad(),
                snapshot.getSource(),
                snapshot.getFetchedAt()
        );
    }
}
