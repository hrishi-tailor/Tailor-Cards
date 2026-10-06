package com.tailorcards.api.service;

import com.tailorcards.api.dto.ProductPokemontcgCandidateDto;
import com.tailorcards.api.dto.SnapshotSyncResponse;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.repository.ProductRepository;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.PriceProvider;
import com.tailorcards.api.trade.scheduler.PriceSnapshotScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductPokemontcgBackfillService {

    private final ProductRepository productRepository;
    private final PriceProvider priceProvider;
    private final PriceSnapshotScheduler priceSnapshotScheduler;

    private static final Pattern CARD_NUMBER_PREFIX = Pattern.compile("^(\\d+)");

    @Transactional(readOnly = true)
    public List<Product> getUnlinkedProducts() {
        return productRepository.findAll().stream()
                .filter(p -> p.getPokemontcgId() == null || p.getPokemontcgId().trim().isEmpty())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductPokemontcgCandidateDto> findCandidates(Long productId, String queryOverride) {
        if (queryOverride != null && !queryOverride.isBlank()) {
            return executeSearch(queryOverride.trim());
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        String cleanName = sanitizeCardName(product.getName());
        String number = extractNumber(product.getCardNumber());

        // 1. Try name + number query
        if (number != null && !number.isBlank()) {
            String query = String.format("name:\"*%s*\" number:\"%s\"", cleanName, number);
            List<ProductPokemontcgCandidateDto> results = executeSearch(query);
            if (!results.isEmpty()) {
                return results;
            }
        }

        // 2. Try name + set query if set is present
        if (product.getSet() != null && !product.getSet().isBlank()) {
            String cleanSet = product.getSet().replaceAll("[\"\\\\]", "").trim();
            String query = String.format("name:\"*%s*\" set.name:\"*%s*\"", cleanName, cleanSet);
            List<ProductPokemontcgCandidateDto> results = executeSearch(query);
            if (!results.isEmpty()) {
                return results;
            }
        }

        // 3. Fallback: search by name wildcard
        return executeSearch("name:\"*" + cleanName + "*\"");
    }

    private List<ProductPokemontcgCandidateDto> executeSearch(String query) {
        try {
            List<CardMarketPrice> matches = priceProvider.searchCards(query, 10);
            if (matches == null || matches.isEmpty()) {
                return Collections.emptyList();
            }
            return matches.stream()
                    .map(m -> new ProductPokemontcgCandidateDto(
                            m.cardId(),
                            m.name(),
                            m.setName(),
                            m.cardNumber(),
                            m.imageUrl(),
                            m.marketPriceUsd()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.warn("Failed candidate search for query '{}': {}", query, ex.getMessage());
            return Collections.emptyList();
        }
    }

    @Transactional
    public Product updateProductPokemontcgId(Long productId, String pokemontcgId) {
        if (pokemontcgId == null || pokemontcgId.trim().isEmpty()) {
            throw new IllegalArgumentException("pokemontcgId cannot be blank");
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with id: " + productId));

        product.setPokemontcgId(pokemontcgId.trim());
        Product saved = productRepository.save(product);
        log.info("Admin updated Product [id={}] '{}' with pokemontcg_id='{}'",
                saved.getId(), saved.getName(), saved.getPokemontcgId());
        return saved;
    }

    public SnapshotSyncResponse triggerSnapshotSync() {
        log.info("Admin manually triggered price snapshot sync job");
        int syncedCount = priceSnapshotScheduler.syncListedCardPrices();
        return new SnapshotSyncResponse(
                "SUCCESS",
                syncedCount,
                String.format("Price snapshot job completed. Synced %d distinct cards.", syncedCount)
        );
    }

    private String sanitizeCardName(String raw) {
        if (raw == null) return "";
        // Strip out grade labels or parenthesis e.g. "Charizard (PSA 10)" -> "Charizard"
        String cleaned = raw.replaceAll("\\s*\\([^)]*\\)", "")
                .replaceAll("(?i)\\b(psa\\s*\\d+|bgs\\s*\\d+|cgc\\s*\\d+|holo|reverse)\\b", "")
                .replaceAll("[\"\\\\]", "")
                .trim();
        return cleaned.isEmpty() ? raw.replaceAll("[\"\\\\]", "").trim() : cleaned;
    }

    private String extractNumber(String rawNumber) {
        if (rawNumber == null || rawNumber.isBlank()) return null;
        // If "4/102", extract "4"
        Matcher m = CARD_NUMBER_PREFIX.matcher(rawNumber.trim());
        if (m.find()) {
            return m.group(1);
        }
        return rawNumber.trim();
    }
}
