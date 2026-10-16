package com.tailorcards.api.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Admin audit log")
class AuditLogIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private com.tailorcards.api.repository.BuyRuleRepository buyRules;
    private final ObjectMapper json = new ObjectMapper();

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return httpBasic("admin", "test-admin-password");
    }

    private String product(long categoryId, String price) {
        return "{\"name\":\"Audit Charizard\",\"price\":" + price + ",\"stock\":1,\"categoryId\":" + categoryId + "}";
    }

    @Test
    @DisplayName("Price changes are recorded with who, what, when, IP and the before/after values")
    void priceChangeRecorded() throws Exception {
        long categoryId = categories.save(com.tailorcards.api.entity.Category.builder()
                .name("Audit test " + System.nanoTime()).build()).getId();
        JsonNode created = json.readTree(mvc.perform(post("/api/products").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(product(categoryId, "100.00")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String id = created.path("id").asText();
        mvc.perform(put("/api/products/" + id).with(admin()).header("X-Forwarded-For", "198.51.100.23")
                .contentType(MediaType.APPLICATION_JSON).content(product(categoryId, "125.00"))).andExpect(status().isOk());
        mvc.perform(delete("/api/products/" + id).with(admin())).andExpect(status().isNoContent());

        JsonNode log = json.readTree(mvc.perform(get("/api/admin/audit-log").with(admin())
                        .param("entityType", "PRODUCT").param("entityId", id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("items");
        assertThat(log).hasSize(3);
        JsonNode delete = log.get(0);
        JsonNode update = log.get(1);
        JsonNode create = log.get(2);
        assertThat(create.path("action").asText()).isEqualTo("PRODUCT_CREATE");
        assertThat(create.path("beforeJson").isNull()).isTrue();
        assertThat(update.path("action").asText()).isEqualTo("PRODUCT_UPDATE");
        assertThat(update.path("actor").asText()).isEqualTo("admin");
        assertThat(update.path("actorRole").asText()).isEqualTo("ADMIN");
        assertThat(update.path("ip").asText()).isEqualTo("198.51.100.23");
        assertThat(update.path("requestPath").asText()).isEqualTo("PUT /api/products/" + id);
        assertThat(update.path("occurredAt").isMissingNode()).isFalse();
        assertThat(json.readTree(update.path("beforeJson").asText()).path("price").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(json.readTree(update.path("afterJson").asText()).path("price").decimalValue()).isEqualByComparingTo("125.00");
        assertThat(delete.path("action").asText()).isEqualTo("PRODUCT_DELETE");
        assertThat(json.readTree(delete.path("beforeJson").asText()).path("price").decimalValue()).isEqualByComparingTo("125.00");
        assertThat(delete.path("afterJson").isNull()).isTrue();
    }

    @Test
    @DisplayName("Buy rate changes are recorded; the log is admin-only and read-only")
    void buyRuleChangeAndAccess() throws Exception {
        com.tailorcards.api.entity.BuyRule saved = buyRules.save(com.tailorcards.api.entity.BuyRule.builder()
                .priority(99).categoryCode("AUDIT_TEST_" + System.nanoTime()).displayName("Audit test")
                .rate(new java.math.BigDecimal("0.7700")).build());
        long ruleId = saved.getId();
        JsonNode rule = json.readTree(json.writeValueAsString(java.util.Map.of("rate", 0.77, "priority", 99)));
        mvc.perform(put("/api/admin/trade-assistant/buy-rules/" + ruleId).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rate\":0.5,\"priority\":" + rule.path("priority").asInt() + ",\"active\":true}")).andExpect(status().isOk());

        JsonNode entry = json.readTree(mvc.perform(get("/api/admin/audit-log").with(admin()).param("action", "BUY_RULE_UPDATE"))
                .andReturn().getResponse().getContentAsString()).path("items").get(0);
        assertThat(json.readTree(entry.path("beforeJson").asText()).path("rate").decimalValue())
                .isEqualByComparingTo(rule.path("rate").decimalValue());
        assertThat(json.readTree(entry.path("afterJson").asText()).path("rate").decimalValue()).isEqualByComparingTo("0.5");

        mvc.perform(get("/api/admin/audit-log")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/audit-log").with(admin())).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Nothing in the code can change or remove entries: no update or delete on the repository, no setters")
    void appendOnlyInCode() {
        assertThat(Arrays.stream(AdminAuditRepository.class.getMethods()).map(Method::getName))
                .noneMatch(n -> n.startsWith("delete") || n.startsWith("update") || n.startsWith("saveAll"));
        assertThat(Arrays.stream(AdminAuditEntry.class.getMethods()).map(Method::getName)).noneMatch(n -> n.startsWith("set"));
        assertThat(AdminAuditEntry.class.isAnnotationPresent(org.hibernate.annotations.Immutable.class)).isTrue();
    }
}
