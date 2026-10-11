package com.tailorcards.api.service;

import com.tailorcards.api.buylistchat.resolution.CardLookupService;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Official card images (TCGdex, the same card database the buylist search uses) for products:
 * found by the product's linked card id, otherwise by its set and card number. Sealed products and
 * cards that can't be matched have no official image.
 */
@Slf4j
@Service
public class ProductImageService {

    private final CardLookupService lookup;

    public ProductImageService(CardLookupService lookup) {
        this.lookup = lookup;
    }

    public Optional<String> officialImage(Product product) {
        try {
            Optional<CardMarketPrice> card = Optional.empty();
            String cardId = product.getPokemontcgId();
            if (cardId != null && !cardId.isBlank()) {
                card = lookup.card(cardId.trim());
            }
            if (card.isEmpty() && notBlank(product.getSet()) && notBlank(product.getCardNumber())) {
                card = lookup.findBySetAndNumber(lookup.setIdsFor(product.getSet()), product.getCardNumber());
            }
            return card.map(c -> c.largeImageUrl() != null ? c.largeImageUrl() : c.imageUrl());
        } catch (Exception e) {
            log.warn("Official image lookup failed for product {}: {}", product.getId(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** True for card-database images (TCGdex, pokemontcg.io, tcggo) as opposed to the seller's own photos. */
    public static boolean isOfficialImage(String url) {
        if (url == null) {
            return false;
        }
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("tcgdex.net") || u.contains("pokemontcg.io") || u.contains("tcggo.com");
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
