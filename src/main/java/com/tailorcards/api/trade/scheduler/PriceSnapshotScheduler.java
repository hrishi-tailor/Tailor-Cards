package com.tailorcards.api.trade.scheduler;

import com.tailorcards.api.entity.Product;
import com.tailorcards.api.repository.ProductRepository;
import com.tailorcards.api.trade.service.CardPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceSnapshotScheduler {

    private final ProductRepository productRepository;
    private final CardPriceService cardPriceService;

    /**
     * Nightly scheduled sync job (default 3:00 AM) that pulls latest market prices
     * for all listed products in inventory and records snapshots.
     */
    @Scheduled(cron = "${app.pricing.sync-cron:0 0 3 * * ?}")
    public int syncListedCardPrices() {
        log.info("Starting nightly price snapshot sync job...");

        List<Product> products = productRepository.findByPokemontcgIdIsNotNull();
        Set<String> distinctCardIds = products.stream()
                .map(Product::getPokemontcgId)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toSet());

        if (distinctCardIds.isEmpty()) {
            log.info("No products found with pokemontcg_id configured. Skipping snapshot sync.");
            return 0;
        }

        log.info("Found {} distinct card IDs across {} listed products to sync.", distinctCardIds.size(), products.size());

        int successCount = 0;
        for (String cardId : distinctCardIds) {
            try {
                Optional<BigDecimal> priceCad = cardPriceService.fetchConvertAndSnapshot(cardId);
                if (priceCad.isPresent()) {
                    successCount++;
                }
                // Gentle delay between requests to respect rate limits
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Snapshot sync job interrupted: {}", ie.getMessage());
                break;
            } catch (Exception ex) {
                log.error("Error syncing price snapshot for cardId={}: {}", cardId, ex.getMessage());
            }
        }

        log.info("Completed nightly price snapshot sync job. Successfully updated {}/{} cards.",
                successCount, distinctCardIds.size());
        return successCount;
    }
}
