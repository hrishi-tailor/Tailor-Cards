package com.tailorcards.api.pipeline;

import com.tailorcards.api.repository.PriceSnapshotRepository;
import com.tailorcards.api.trade.service.ExchangeRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.pipeline.shared-secret=" + PipelineControllerIntegrationTest.SECRET)
@AutoConfigureMockMvc
@DisplayName("Price pipeline API")
class PipelineControllerIntegrationTest {

    static final String SECRET = "integration-test-secret-0123456789abcdef";
    private static final String SNAPSHOTS = "/api/internal/pricing/snapshots";
    private static final String WATCHLIST = "/api/internal/pricing/watchlist";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PipelineSignature signature;
    @Autowired
    private PriceSnapshotRepository snapshots;
    @MockitoBean
    private ExchangeRateService exchangeRates;

    @BeforeEach
    void setUp() {
        when(exchangeRates.getUsdToCadRate()).thenReturn(new BigDecimal("1.4000"));
        snapshots.deleteAll();
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request, String method, String path, String body) {
        long now = Instant.now().getEpochSecond();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return request.header(PipelineController.TIMESTAMP_HEADER, String.valueOf(now))
                .header(PipelineController.SIGNATURE_HEADER, signature.sign(method, path, bytes, now))
                .contentType(MediaType.APPLICATION_JSON).content(bytes);
    }

    private static String batch(String fetchedAt) {
        return """
                {"runId":"2026-10-20T08:00:00Z","source":"TCGDEX","prices":[
                  {"cardId":"sv8pt5-161","priceUsd":100.00,"fetchedAt":"%s"}]}""".formatted(fetchedAt);
    }

    @Test
    @DisplayName("A signed run is saved once, even when it's sent again")
    void signedRun() throws Exception {
        String body = batch(Instant.now().minusSeconds(60).toString());

        mvc.perform(signed(post(SNAPSHOTS), "POST", SNAPSHOTS, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(1));
        mvc.perform(signed(post(SNAPSHOTS), "POST", SNAPSHOTS, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(0))
                .andExpect(jsonPath("$.duplicates").value(1));

        assertThat(snapshots.findTopByCardIdOrderByFetchedAtDesc("sv8pt5-161").orElseThrow().getPriceCad())
                .isEqualByComparingTo("140.00");
    }

    @Test
    @DisplayName("The watchlist answers a signed request")
    void watchlist() throws Exception {
        mvc.perform(signed(get(WATCHLIST), "GET", WATCHLIST, ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardIds").isArray());
    }

    @Test
    @DisplayName("Unsigned, tampered or admin-only requests are refused and save nothing")
    void refused() throws Exception {
        String body = batch(Instant.now().toString());
        mvc.perform(post(SNAPSHOTS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(signed(post(SNAPSHOTS), "POST", SNAPSHOTS, body).content(body.replace("100.00", "1.00")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(WATCHLIST).with(httpBasic("admin", "test-admin-password")))
                .andExpect(status().isUnauthorized());
        assertThat(snapshots.count()).isZero();
    }

    @Test
    @DisplayName("A signed but invalid batch is a 400 that names the problem")
    void invalidBatch() throws Exception {
        String body = """
                {"runId":"r1","source":"TCGDEX","prices":[{"cardId":"","priceUsd":-1,"fetchedAt":"2026-10-20T08:00:00Z"}]}""";
        mvc.perform(signed(post(SNAPSHOTS), "POST", SNAPSHOTS, body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("prices[0].cardId")));
        mvc.perform(signed(post(SNAPSHOTS), "POST", SNAPSHOTS, "{not json"))
                .andExpect(status().isBadRequest());
    }
}
