package com.tailorcards.api.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Ledger API")
class LedgerControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    private static RequestPostProcessor admin() {
        return httpBasic("admin", "test-admin-password");
    }

    private JsonNode createLot(String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/admin/ledger/lots").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("The ledger is admin-only")
    void adminOnly() throws Exception {
        mvc.perform(get("/api/admin/ledger/lots")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/ledger/summary")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Recording a lot and a sale, then reading them back, with each change audited")
    void lotAndSale() throws Exception {
        JsonNode lot = createLot("""
                {"title":"Chaos Rising PKC ETB","kind":"SEALED","quantity":2,"totalCostCad":100,
                 "acquiredOn":"2026-08-18","acquiredChannel":"CARD_SHOW","listedOn":"2026-08-19","listedPriceCad":145}""");
        long id = lot.path("id").asLong();
        assertThat(lot.path("unitCostCad").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(lot.path("source").asText()).isEqualTo("MANUAL");

        mvc.perform(post("/api/admin/ledger/lots/" + id + "/sales").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"soldOn":"2026-08-20","quantity":2,"unitPriceCad":145,"channel":"FACEBOOK_MARKETPLACE",
                                 "paymentMethod":"E_TRANSFER"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grossProfitCad").value(190.0))
                .andExpect(jsonPath("$.daysListed").value(1));

        mvc.perform(get("/api/admin/ledger/lots/" + id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitsLeft").value(0))
                .andExpect(jsonPath("$.sales.length()").value(1));

        JsonNode audit = json.readTree(mvc.perform(get("/api/admin/audit-log").with(admin())
                        .param("entityType", "LEDGER_LOT").param("entityId", String.valueOf(id)))
                .andReturn().getResponse().getContentAsString()).path("items");
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).path("action").asText()).isEqualTo("LEDGER_LOT_CREATE");
    }

    @Test
    @DisplayName("Bad input is 400, overselling is 409, unknown lots are 404")
    void errors() throws Exception {
        mvc.perform(post("/api/admin/ledger/lots").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"kind\":\"SINGLE\",\"quantity\":0,\"totalCostCad\":-1}"))
                .andExpect(status().isBadRequest());

        long id = createLot("{\"title\":\"Mewtwo 151 promo\",\"kind\":\"SINGLE\",\"quantity\":1,\"totalCostCad\":42}")
                .path("id").asLong();
        String sale = "{\"soldOn\":\"2026-10-01\",\"quantity\":2,\"unitPriceCad\":60,\"channel\":\"CARD_SHOW\"}";
        mvc.perform(post("/api/admin/ledger/lots/" + id + "/sales").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(sale))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only 1 of \"Mewtwo 151 promo\" left to sell."));

        mvc.perform(get("/api/admin/ledger/lots/999999").with(admin())).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/ledger/lots").with(admin()).param("source", "CSV_IMPORT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"X\",\"kind\":\"SINGLE\",\"quantity\":1,\"totalCostCad\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A sale recorded by mistake can be deleted, and the lot can then be deleted")
    void corrections() throws Exception {
        long id = createLot("{\"title\":\"Marshadow GX\",\"kind\":\"SINGLE\",\"quantity\":1,\"totalCostCad\":3}")
                .path("id").asLong();
        long saleId = json.readTree(mvc.perform(post("/api/admin/ledger/lots/" + id + "/sales").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"soldOn\":\"2026-10-01\",\"quantity\":1,\"unitPriceCad\":5,\"channel\":\"CARD_SHOW\"}"))
                .andReturn().getResponse().getContentAsString()).path("id").asLong();

        mvc.perform(delete("/api/admin/ledger/lots/" + id).with(admin())).andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/ledger/sales/" + saleId).with(admin())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/ledger/lots/" + id).with(admin())).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("The summary endpoint accepts a date range")
    void summary() throws Exception {
        mvc.perform(get("/api/admin/ledger/summary").with(admin()).param("from", "2026-01-01").param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.key").value("TOTAL"))
                .andExpect(jsonPath("$.onHand").exists());
        mvc.perform(get("/api/admin/ledger/summary").with(admin()).param("from", "2026-12-31").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CSV import: dry run by default, 422 and nothing saved when a row is bad, audited when saved")
    void importCsv() throws Exception {
        String header = "lot_ref,title,kind,quantity,total_cost_cad,sold_on,sold_quantity,sale_unit_price_cad,sale_channel\n";
        MockMultipartFile good = new MockMultipartFile("file", "ledger.csv", "text/csv",
                (header + "IMP-1,Celebrations ETB,sealed,1,315,2026-10-17,1,450,facebook\n").getBytes());
        MockMultipartFile bad = new MockMultipartFile("file", "ledger.csv", "text/csv",
                (header + "IMP-2,Celebrations ETB,sealed,1,315,2026-10-17,1,450,garage sale\n").getBytes());

        mvc.perform(multipart("/api/admin/ledger/import").file(good).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.lots").value(1));
        mvc.perform(multipart("/api/admin/ledger/import").file(bad).param("dryRun", "false").with(admin()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].line").value(2));
        mvc.perform(multipart("/api/admin/ledger/import").file(good).param("dryRun", "false").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(true));

        JsonNode audit = json.readTree(mvc.perform(get("/api/admin/audit-log").with(admin())
                .param("action", "LEDGER_IMPORT")).andReturn().getResponse().getContentAsString()).path("items");
        assertThat(audit).isNotEmpty();
        mvc.perform(multipart("/api/admin/ledger/import").file(good)).andExpect(status().isUnauthorized());
    }
}
