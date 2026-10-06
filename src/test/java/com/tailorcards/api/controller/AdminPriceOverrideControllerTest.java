package com.tailorcards.api.controller;

import com.tailorcards.api.dto.ManualPriceOverrideRequest;
import com.tailorcards.api.dto.ManualPriceOverrideResponse;
import com.tailorcards.api.dto.PriceSnapshotResponse;
import com.tailorcards.api.entity.ManualPriceOverride;
import com.tailorcards.api.entity.PriceSnapshot;
import com.tailorcards.api.repository.ManualPriceOverrideRepository;
import com.tailorcards.api.repository.PriceSnapshotRepository;
import com.tailorcards.api.trade.scheduler.PriceSnapshotScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPriceOverrideController Unit Tests")
class AdminPriceOverrideControllerTest {

    @Mock
    private ManualPriceOverrideRepository overrideRepository;

    @Mock
    private PriceSnapshotRepository snapshotRepository;

    @Mock
    private PriceSnapshotScheduler snapshotScheduler;

    private AdminPriceOverrideController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminPriceOverrideController(overrideRepository, snapshotRepository, snapshotScheduler);
    }

    @Test
    @DisplayName("Admin can list all price overrides")
    void testGetAllOverrides() {
        ManualPriceOverride override = ManualPriceOverride.builder()
                .id(1L)
                .cardId("base1-4")
                .conditionOrGrade("PSA 10")
                .overridePriceCad(new BigDecimal("4500.00"))
                .notes("Charizard gem mint")
                .updatedAt(Instant.now())
                .build();

        when(overrideRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of(override));

        ResponseEntity<List<ManualPriceOverrideResponse>> response = controller.getAllOverrides();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().getFirst().cardId()).isEqualTo("base1-4");
        assertThat(response.getBody().getFirst().overridePriceCad()).isEqualByComparingTo(new BigDecimal("4500.00"));
    }

    @Test
    @DisplayName("Admin can create or update a manual price override")
    void testUpsertOverride() {
        ManualPriceOverrideRequest request = new ManualPriceOverrideRequest(
                "base1-4",
                "PSA 10",
                new BigDecimal("4800.00"),
                "Updated benchmark"
        );

        when(overrideRepository.findTopByCardIdAndConditionOrGradeIgnoreCaseOrderByUpdatedAtDesc("base1-4", "PSA 10"))
                .thenReturn(Optional.empty());

        ManualPriceOverride saved = ManualPriceOverride.builder()
                .id(2L)
                .cardId("base1-4")
                .conditionOrGrade("PSA 10")
                .overridePriceCad(new BigDecimal("4800.00"))
                .notes("Updated benchmark")
                .updatedAt(Instant.now())
                .build();

        when(overrideRepository.save(any(ManualPriceOverride.class))).thenReturn(saved);

        ResponseEntity<ManualPriceOverrideResponse> response = controller.upsertOverride(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().cardId()).isEqualTo("base1-4");
        assertThat(response.getBody().overridePriceCad()).isEqualByComparingTo(new BigDecimal("4800.00"));
    }

    @Test
    @DisplayName("Admin can delete an override by ID")
    void testDeleteOverride() {
        when(overrideRepository.existsById(1L)).thenReturn(true);

        ResponseEntity<Void> response = controller.deleteOverride(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(overrideRepository).deleteById(1L);
    }

    @Test
    @DisplayName("Admin can view card snapshots")
    void testGetCardSnapshots() {
        PriceSnapshot snapshot = PriceSnapshot.builder()
                .id(1L)
                .cardId("base1-4")
                .priceUsd(new BigDecimal("280.00"))
                .priceCad(new BigDecimal("386.40"))
                .source("POKEMONTCG_IO")
                .fetchedAt(Instant.now())
                .build();

        when(snapshotRepository.findByCardIdOrderByFetchedAtDesc("base1-4")).thenReturn(List.of(snapshot));

        ResponseEntity<List<PriceSnapshotResponse>> response = controller.getCardSnapshots("base1-4");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().getFirst().priceUsd()).isEqualByComparingTo(new BigDecimal("280.00"));
        assertThat(response.getBody().getFirst().priceCad()).isEqualByComparingTo(new BigDecimal("386.40"));
    }

    @Test
    @DisplayName("Admin can trigger manual snapshot sync")
    void testTriggerSync() {
        when(snapshotScheduler.syncListedCardPrices()).thenReturn(5);

        ResponseEntity<Map<String, Object>> response = controller.triggerSync();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("SUCCESS");
        assertThat(response.getBody().get("cardsUpdated")).isEqualTo(5);
    }
}
