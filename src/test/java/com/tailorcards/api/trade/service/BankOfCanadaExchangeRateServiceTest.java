package com.tailorcards.api.trade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("BankOfCanadaExchangeRateService Tests")
class BankOfCanadaExchangeRateServiceTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Successfully fetches and parses USD to CAD exchange rate from Valet API")
    void testFetchExchangeRateSuccess() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test.boc.ca/valet/observations/FXUSDCAD/json?recent=1");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        String bocJson = """
                {
                  "observations": [
                    {
                      "d": "2026-10-05",
                      "FXUSDCAD": {
                        "v": "1.3925"
                      }
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("https://test.boc.ca/valet/observations/FXUSDCAD/json?recent=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(bocJson, MediaType.APPLICATION_JSON));

        BankOfCanadaExchangeRateService testService = new BankOfCanadaExchangeRateService(restClient, objectMapper);
        testService.setCachedRateForTesting(new BigDecimal("1.3800"), Instant.EPOCH); // Expire cache

        BigDecimal rate = testService.getUsdToCadRate();

        assertThat(rate).isEqualByComparingTo(new BigDecimal("1.3925"));
        mockServer.verify();

        // Conversion test
        BigDecimal usd = new BigDecimal("100.00");
        BigDecimal cad = testService.convertUsdToCad(usd);
        assertThat(cad).isEqualByComparingTo(new BigDecimal("139.25"));
    }

    @Test
    @DisplayName("Falls back to safe default cached rate on API network error")
    void testFallbackOnNetworkError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test.boc.ca/valet/observations/FXUSDCAD/json?recent=1");
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        mockServer.expect(method(HttpMethod.GET))
                .andRespond(withServerError());

        BankOfCanadaExchangeRateService service = new BankOfCanadaExchangeRateService(restClient, objectMapper);
        service.setCachedRateForTesting(new BigDecimal("1.3800"), Instant.EPOCH);

        BigDecimal rate = service.getUsdToCadRate();

        assertThat(rate).isEqualByComparingTo(new BigDecimal("1.3800"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Uses in-memory cached rate within TTL without calling API again")
    void testCachedRateWithinTtl() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://test.boc.ca");
        RestClient restClient = builder.build();
        BankOfCanadaExchangeRateService service = new BankOfCanadaExchangeRateService(restClient, objectMapper);
        service.setCachedRateForTesting(new BigDecimal("1.3650"), Instant.now()); // fresh cache

        BigDecimal rate = service.getUsdToCadRate();
        assertThat(rate).isEqualByComparingTo(new BigDecimal("1.3650"));
    }
}
