package com.tailorcards.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Security Role & DEMO Access Control Tests")
class SecurityRoleAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Public can access demo status endpoint without authentication")
    void publicCanAccessDemoStatus() throws Exception {
        mockMvc.perform(get("/api/auth/demo-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoMode").exists());
    }

    @Test
    @WithMockUser(roles = "DEMO")
    @DisplayName("DEMO role can verify authentication and receives DEMO role string")
    void demoRoleCanVerifyAuth() throws Exception {
        mockMvc.perform(get("/api/auth/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.role", is("DEMO")));
    }

    @Test
    @WithMockUser(roles = "DEMO")
    @DisplayName("DEMO role can view read-only buylist submissions dashboard")
    void demoRoleCanViewBuylistSubmissions() throws Exception {
        mockMvc.perform(get("/api/buylist/admin/submissions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DEMO")
    @DisplayName("DEMO role can view read-only trade assistant submissions")
    void demoRoleCanViewTradeAssistantRequests() throws Exception {
        mockMvc.perform(get("/api/admin/trade-assistant/requests"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DEMO")
    @DisplayName("DEMO role is strictly forbidden from viewing trade parameters and margins (403)")
    void demoRoleCannotAccessTradeParameters() throws Exception {
        mockMvc.perform(get("/api/admin/trade-assistant/parameters"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DEMO")
    @DisplayName("DEMO role is strictly forbidden from viewing cash buy rules (403)")
    void demoRoleCannotAccessBuyRules() throws Exception {
        mockMvc.perform(get("/api/admin/trade-assistant/buy-rules"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DEMO")
    @DisplayName("DEMO role is strictly forbidden from viewing card liquidity tiers (403)")
    void demoRoleCannotAccessLiquidity() throws Exception {
        mockMvc.perform(get("/api/admin/trade-assistant/liquidity"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DEMO")
    @DisplayName("DEMO role is strictly forbidden from viewing price overrides (403)")
    void demoRoleCannotAccessPriceOverrides() throws Exception {
        mockMvc.perform(get("/api/admin/price-overrides"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DEMO")
    @DisplayName("DEMO role is forbidden from updating buylist submission status (403)")
    void demoRoleCannotUpdateBuylistStatus() throws Exception {
        mockMvc.perform(patch("/api/buylist/admin/submissions/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFERED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DEMO")
    @DisplayName("DEMO role is forbidden from sending admin chat messages (403)")
    void demoRoleCannotPostAdminMessage() throws Exception {
        mockMvc.perform(post("/api/buylist/admin/submissions/1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Test reply\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN role can access parameters, buy rules, liquidity, and overrides")
    void adminCanAccessSensitiveEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/trade-assistant/parameters"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/trade-assistant/buy-rules"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/trade-assistant/liquidity"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/price-overrides"))
                .andExpect(status().isOk());
    }
}
