package com.tailorcards.api.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.ledger.LedgerLotRepository;
import com.tailorcards.api.ledger.LedgerSaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.pipeline.shared-secret=" + WarehouseExportIntegrationTest.SECRET)
@AutoConfigureMockMvc
@DisplayName("Warehouse export API")
class WarehouseExportIntegrationTest {

    static final String SECRET = "integration-test-secret-0123456789abcdef";
    private static final String BASE = "/api/internal/export/";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PipelineSignature signature;
    @Autowired
    private LedgerSaleRepository sales;
    @Autowired
    private LedgerLotRepository lots;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        sales.deleteAll();
        lots.deleteAll();
        for (int i = 1; i <= 3; i++) {
            String lot = mvc.perform(post("/api/admin/ledger/lots").with(httpBasic("admin", "test-admin-password"))
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"title":"Lot %d","kind":"SINGLE","cardRef":"swsh7-215","quantity":2,"totalCostCad":100,
                                     "acquiredOn":"2026-09-01","acquiredChannel":"CARD_SHOW","notes":"bought from Sam"}""".formatted(i)))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            long id = json.readTree(lot).path("id").asLong();
            mvc.perform(post("/api/admin/ledger/lots/" + id + "/sales").with(httpBasic("admin", "test-admin-password"))
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"soldOn":"2026-09-05","quantity":1,"unitPriceCad":80,"channel":"CARD_SHOW",
                                     "paymentMethod":"CASH","notes":"to a regular"}"""))
                    .andExpect(status().isCreated());
        }
    }

    private ResultActions signedGet(String pathAndQuery) throws Exception {
        long now = Instant.now().getEpochSecond();
        return mvc.perform(get(pathAndQuery)
                .header(PipelineRequestGuard.TIMESTAMP_HEADER, String.valueOf(now))
                .header(PipelineRequestGuard.SIGNATURE_HEADER, signature.sign("GET", pathAndQuery, new byte[0], now)));
    }

    private JsonNode page(String pathAndQuery) throws Exception {
        return json.readTree(signedGet(pathAndQuery).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Pages through a dataset in id order until nextAfter is null")
    void paging() throws Exception {
        JsonNode first = page(BASE + "ledger_lots?limit=2");
        assertThat(first.path("rows")).hasSize(2);
        long after = first.path("nextAfter").asLong();
        JsonNode second = page(BASE + "ledger_lots?after=" + after + "&limit=2");
        assertThat(second.path("rows")).hasSize(1);
        assertThat(second.path("nextAfter").isNull()).isTrue();
        assertThat(second.path("rows").get(0).path("id").asLong()).isGreaterThan(after);
    }

    @Test
    @DisplayName("Rows are snake_case with ISO dates, and free-text notes are never exported")
    void rowShape() throws Exception {
        JsonNode lot = page(BASE + "ledger_lots").path("rows").get(0);
        assertThat(lot.path("card_ref").asText()).isEqualTo("swsh7-215");
        assertThat(lot.path("acquired_on").asText()).isEqualTo("2026-09-01");
        assertThat(lot.path("created_at").asText()).endsWith("Z");
        assertThat(lot.has("notes")).isFalse();

        JsonNode sale = page(BASE + "ledger_sales").path("rows").get(0);
        assertThat(sale.path("sold_on").asText()).isEqualTo("2026-09-05");
        assertThat(sale.path("unit_price_cad").decimalValue()).isEqualByComparingTo("80");
        assertThat(sale.has("notes")).isFalse();

        signedGet(BASE + "products").andExpect(status().isOk());
        signedGet(BASE + "price_snapshots").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Unknown datasets, bad limits and unsigned or altered requests are refused")
    void refused() throws Exception {
        signedGet(BASE + "users").andExpect(status().isNotFound());
        signedGet(BASE + "ledger_sales?limit=0").andExpect(status().isBadRequest());
        signedGet(BASE + "ledger_sales?limit=5001").andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "ledger_sales")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "ledger_sales").with(httpBasic("admin", "test-admin-password"))).andExpect(status().isUnauthorized());
    }
}
