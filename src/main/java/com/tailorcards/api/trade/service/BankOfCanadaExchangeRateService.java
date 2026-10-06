package com.tailorcards.api.trade.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
public class BankOfCanadaExchangeRateService implements ExchangeRateService {

    private static final String VALET_API_URL = "https://www.bankofcanada.ca/valet/observations/FXUSDCAD/json?recent=1";
    private static final BigDecimal DEFAULT_FALLBACK_RATE = new BigDecimal("1.3800");
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile BigDecimal cachedRate = DEFAULT_FALLBACK_RATE;
    private volatile Instant lastFetchedAt = Instant.EPOCH;

    @org.springframework.beans.factory.annotation.Autowired
    public BankOfCanadaExchangeRateService(
            @Value("${app.exchange.boc-valet-url:" + VALET_API_URL + "}") String bocUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(4));
        requestFactory.setReadTimeout(Duration.ofSeconds(4));

        this.restClient = RestClient.builder()
                .baseUrl(bocUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // Constructor for testing with custom RestClient
    public BankOfCanadaExchangeRateService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public BigDecimal getUsdToCadRate() {
        if (Instant.now().isBefore(lastFetchedAt.plus(CACHE_TTL))) {
            return cachedRate;
        }

        try {
            log.info("Fetching latest USD/CAD exchange rate from Bank of Canada Valet API...");
            String rawJson = restClient.get()
                    .retrieve()
                    .body(String.class);

            if (rawJson != null && !rawJson.isBlank()) {
                JsonNode root = objectMapper.readTree(rawJson);
                JsonNode observations = root.path("observations");
                if (observations.isArray() && !observations.isEmpty()) {
                    JsonNode latestObs = observations.get(observations.size() - 1);
                    String rateVal = latestObs.path("FXUSDCAD").path("v").asText(null);
                    if (rateVal != null && !rateVal.isBlank()) {
                        BigDecimal parsedRate = new BigDecimal(rateVal).setScale(4, RoundingMode.HALF_UP);
                        this.cachedRate = parsedRate;
                        this.lastFetchedAt = Instant.now();
                        log.info("Updated Bank of Canada USD/CAD rate to: {}", parsedRate);
                        return parsedRate;
                    }
                }
            }
            log.warn("Bank of Canada response did not contain expected rate structure. Using cached rate: {}", cachedRate);
        } catch (Exception ex) {
            log.warn("Failed to fetch USD/CAD exchange rate from Bank of Canada ({}). Using cached/fallback rate: {}",
                    ex.getMessage(), cachedRate);
        }

        return cachedRate;
    }

    @Override
    public BigDecimal convertUsdToCad(BigDecimal amountUsd) {
        if (amountUsd == null) {
            return null;
        }
        BigDecimal rate = getUsdToCadRate();
        return amountUsd.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    // Helper for testing
    public void setCachedRateForTesting(BigDecimal rate, Instant fetchedAt) {
        this.cachedRate = rate;
        this.lastFetchedAt = fetchedAt;
    }
}
