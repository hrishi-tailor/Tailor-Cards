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
    private static final String EUR_VALET_API_URL = "https://www.bankofcanada.ca/valet/observations/FXEURCAD/json?recent=1";
    private static final BigDecimal DEFAULT_FALLBACK_RATE = new BigDecimal("1.3800");
    private static final BigDecimal DEFAULT_EUR_FALLBACK_RATE = new BigDecimal("1.5000");
    private static final Duration CACHE_TTL = Duration.ofHours(12);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile BigDecimal cachedRate = DEFAULT_FALLBACK_RATE;
    private volatile Instant lastFetchedAt = Instant.EPOCH;
    private final RestClient eurClient;
    private volatile BigDecimal cachedEurRate = DEFAULT_EUR_FALLBACK_RATE;
    private volatile Instant eurFetchedAt = Instant.EPOCH;

    @org.springframework.beans.factory.annotation.Autowired
    public BankOfCanadaExchangeRateService(
            @Value("${app.exchange.boc-valet-url:" + VALET_API_URL + "}") String bocUrl,
            @Value("${app.exchange.boc-eur-valet-url:" + EUR_VALET_API_URL + "}") String bocEurUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(4));
        requestFactory.setReadTimeout(Duration.ofSeconds(4));

        this.restClient = RestClient.builder()
                .baseUrl(bocUrl)
                .requestFactory(requestFactory)
                .build();
        this.eurClient = RestClient.builder()
                .baseUrl(bocEurUrl)
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // Constructor for testing with custom RestClient
    public BankOfCanadaExchangeRateService(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.eurClient = null;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /** CAD per 1 EUR from the Bank of Canada (FXEURCAD), cached 12 hours; the last good or fallback rate on errors. */
    @Override
    public BigDecimal getEurToCadRate() {
        if (eurClient == null || Instant.now().isBefore(eurFetchedAt.plus(CACHE_TTL))) {
            return cachedEurRate;
        }
        try {
            String rawJson = eurClient.get().retrieve().body(String.class);
            JsonNode observations = objectMapper.readTree(rawJson == null ? "{}" : rawJson).path("observations");
            if (observations.isArray() && !observations.isEmpty()) {
                String rateVal = observations.get(observations.size() - 1).path("FXEURCAD").path("v").asText(null);
                if (rateVal != null && !rateVal.isBlank()) {
                    cachedEurRate = new BigDecimal(rateVal).setScale(4, RoundingMode.HALF_UP);
                    eurFetchedAt = Instant.now();
                    return cachedEurRate;
                }
            }
            log.warn("Bank of Canada EUR/CAD response had no rate. Using cached rate: {}", cachedEurRate);
        } catch (Exception ex) {
            log.warn("Failed to fetch EUR/CAD from Bank of Canada ({}). Using cached/fallback rate: {}",
                    ex.getMessage(), cachedEurRate);
        }
        return cachedEurRate;
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

    public void setCachedEurRateForTesting(BigDecimal rate, Instant fetchedAt) {
        this.cachedEurRate = rate;
        this.eurFetchedAt = fetchedAt;
    }
}
