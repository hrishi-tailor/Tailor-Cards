package com.tailorcards.api.buylistchat.pricing;

import com.tailorcards.api.entity.Product;
import com.tailorcards.api.repository.ProductRepository;
import com.tailorcards.api.trade.service.ExchangeRateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Shop cards a customer can pick for a trade: available, in stock, priced in CAD and shown in USD too. */
@Service
public class StoreCardService {

    public record StoreCard(Long productId, String name, String setName, String cardNumber, String condition,
                            String grading, String imageUrl, BigDecimal priceCad, BigDecimal priceUsd, int stock) {}

    private final ProductRepository productRepository;
    private final ExchangeRateService exchangeRateService;

    public StoreCardService(ProductRepository productRepository, ExchangeRateService exchangeRateService) {
        this.productRepository = productRepository;
        this.exchangeRateService = exchangeRateService;
    }

    /** CAD per 1 USD (Bank of Canada, with the service's own fallback). */
    public BigDecimal usdCadRate() {
        BigDecimal rate = exchangeRateService.getUsdToCadRate();
        return rate == null || rate.signum() <= 0 ? new BigDecimal("1.3800") : rate;
    }

    @Transactional(readOnly = true)
    public List<StoreCard> search(String query, int limit) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        BigDecimal rate = usdCadRate();
        return productRepository.findAll().stream()
                .filter(StoreCardService::isAvailable)
                .filter(p -> q.isEmpty() || (p.getName() + " " + (p.getSet() == null ? "" : p.getSet()) + " "
                        + (p.getCardNumber() == null ? "" : p.getCardNumber())).toLowerCase(Locale.ROOT).contains(q))
                .sorted(Comparator.comparing(Product::getPrice).reversed())
                .limit(Math.max(1, Math.min(limit, 100)))
                .map(p -> toStoreCard(p, rate))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<StoreCard> available(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        return productRepository.findById(productId).filter(StoreCardService::isAvailable)
                .map(p -> toStoreCard(p, usdCadRate()));
    }

    static boolean isAvailable(Product p) {
        return p.getPrice() != null && p.getStock() != null && p.getStock() > 0
                && (p.getStatus() == null || "AVAILABLE".equalsIgnoreCase(p.getStatus()));
    }

    private static StoreCard toStoreCard(Product p, BigDecimal usdCadRate) {
        BigDecimal usd = p.getPrice().divide(usdCadRate, 2, RoundingMode.HALF_UP);
        return new StoreCard(p.getId(), p.getName(), p.getSet(), p.getCardNumber(), p.getCondition(), p.getGrading(),
                p.getImageUrl(), p.getPrice(), usd, p.getStock());
    }
}
