package com.tailorcards.api.config;

import com.tailorcards.api.entity.BuylistSubmission;
import com.tailorcards.api.entity.TradeAssistantRequest;
import com.tailorcards.api.repository.BuylistSubmissionRepository;
import com.tailorcards.api.repository.TradeAssistantRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("DEMO Role Customer Data Isolation Integration Tests")
class DemoRoleDataIsolationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BuylistSubmissionRepository buylistSubmissionRepository;

    @Autowired
    private TradeAssistantRequestRepository tradeAssistantRequestRepository;

    private Long realSubmissionId;
    private Long demoSubmissionId;
    private Long realTradeRequestId;
    private Long demoTradeRequestId;

    @BeforeEach
    void setUpTestData() {
        buylistSubmissionRepository.deleteAll();
        tradeAssistantRequestRepository.deleteAll();

        // 1. Seed Real Customer Buylist Submission
        BuylistSubmission realSub = BuylistSubmission.builder()
                .trackingToken("REAL-SUB-999")
                .customerName("Real Sensitive Customer")
                .customerEmail("real.customer@confidential.com")
                .cardName("Charizard 1st Edition")
                .cardSet("Base Set")
                .status("PENDING")
                .askingPrice(new BigDecimal("2500.00"))
                .createdAt(Instant.now())
                .build();
        realSubmissionId = buylistSubmissionRepository.save(realSub).getId();

        // 2. Seed Demo Buylist Submission
        BuylistSubmission demoSub = BuylistSubmission.builder()
                .trackingToken("DEMO-SUB-001")
                .customerName("Morgan Reed (Demo Evaluator)")
                .customerEmail("morgan.demo@example.com")
                .cardName("Demo Pikachu")
                .cardSet("Base Set")
                .status("UNDER_REVIEW")
                .askingPrice(new BigDecimal("100.00"))
                .createdAt(Instant.now())
                .build();
        demoSubmissionId = buylistSubmissionRepository.save(demoSub).getId();

        // 3. Seed Real Customer Trade Request
        TradeAssistantRequest realTrade = TradeAssistantRequest.builder()
                .referenceCode("REAL-TR-888")
                .flowType("TRADE")
                .status("PENDING_REVIEW")
                .decision("ACCEPT")
                .customerName("Private Customer VIP")
                .customerEmail("private.vip@confidential.com")
                .customerPhone("+1 416-555-0188")
                .offeredAmount(new BigDecimal("1800.00"))
                .customerCardsJson("[]")
                .storeProductsJson("[]")
                .createdAt(Instant.now())
                .build();
        realTradeRequestId = tradeAssistantRequestRepository.save(realTrade).getId();

        // 4. Seed Demo Trade Request
        TradeAssistantRequest demoTrade = TradeAssistantRequest.builder()
                .referenceCode("DEMO-TR-001")
                .flowType("TRADE")
                .status("PENDING_REVIEW")
                .decision("COUNTER")
                .customerName("Alex Hunter (Demo Evaluator)")
                .customerEmail("alex.trade.demo@example.com")
                .customerPhone("+1 555-010-0002")
                .offeredAmount(new BigDecimal("450.00"))
                .customerCardsJson("[]")
                .storeProductsJson("[]")
                .createdAt(Instant.now())
                .build();
        demoTradeRequestId = tradeAssistantRequestRepository.save(demoTrade).getId();
    }

    @Test
    @WithMockUser(username = "demo", roles = {"DEMO"})
    @DisplayName("DEMO role sees ONLY DEMO submissions and NEVER real customer submissions")
    void demoRole_seesOnlyDemoBuylistSubmissions() throws Exception {
        mockMvc.perform(get("/api/buylist/admin/submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].trackingToken", everyItem(startsWith("DEMO-"))))
                .andExpect(jsonPath("$.content[*].customerEmail", not(hasItem("real.customer@confidential.com"))));
    }

    @Test
    @WithMockUser(username = "demo", roles = {"DEMO"})
    @DisplayName("DEMO role is FORBIDDEN from viewing a real customer submission by ID")
    void demoRole_cannotViewRealCustomerSubmissionById() throws Exception {
        mockMvc.perform(get("/api/buylist/admin/submissions/" + realSubmissionId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "demo", roles = {"DEMO"})
    @DisplayName("DEMO role CAN view a seeded DEMO submission by ID")
    void demoRole_canViewDemoSubmissionById() throws Exception {
        mockMvc.perform(get("/api/buylist/admin/submissions/" + demoSubmissionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingToken").value("DEMO-SUB-001"));
    }

    @Test
    @WithMockUser(username = "demo", roles = {"DEMO"})
    @DisplayName("DEMO role sees ONLY DEMO trade requests and NEVER real customer trade requests")
    void demoRole_seesOnlyDemoTradeRequests() throws Exception {
        mockMvc.perform(get("/api/admin/trade-assistant/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].referenceCode", everyItem(startsWith("DEMO-"))))
                .andExpect(jsonPath("$.content[*].customerEmail", not(hasItem("private.vip@confidential.com"))));
    }

    @Test
    @WithMockUser(username = "demo", roles = {"DEMO"})
    @DisplayName("DEMO role is FORBIDDEN from viewing a real customer trade request by ID")
    void demoRole_cannotViewRealCustomerTradeRequestById() throws Exception {
        mockMvc.perform(get("/api/admin/trade-assistant/requests/" + realTradeRequestId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "demo", roles = {"DEMO"})
    @DisplayName("DEMO role CAN view a seeded DEMO trade request by ID")
    void demoRole_canViewDemoTradeRequestById() throws Exception {
        mockMvc.perform(get("/api/admin/trade-assistant/requests/" + demoTradeRequestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceCode").value("DEMO-TR-001"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("ADMIN role sees both real and demo records")
    void adminRole_seesAllRecords() throws Exception {
        mockMvc.perform(get("/api/buylist/admin/submissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].trackingToken", hasItem("REAL-SUB-999")))
                .andExpect(jsonPath("$.content[*].trackingToken", hasItem("DEMO-SUB-001")));

        mockMvc.perform(get("/api/admin/trade-assistant/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].referenceCode", hasItem("REAL-TR-888")))
                .andExpect(jsonPath("$.content[*].referenceCode", hasItem("DEMO-TR-001")));
    }

    @Test
    @WithMockUser(username = "demo", roles = {"DEMO"})
    @DisplayName("DEMO role gets 404 for nonexistent buylist or trade request ID")
    void demoRole_nonexistentIdReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/buylist/admin/submissions/999999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/admin/trade-assistant/requests/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "demo", roles = {"DEMO"})
    @DisplayName("DEMO role is FORBIDDEN (403) from accessing backfill and snapshot sync endpoints")
    void demoRole_forbiddenFromBackfillAndSnapshotEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/products/unlinked"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/products/1/candidates"))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/products/1/pokemontcg-id")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"pokemontcgId\":\"base1-4\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/pricing/sync-snapshots"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Public product listing does not expose costBasis")
    void publicProducts_doNotExposeCostBasis() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].costBasis").doesNotExist())
                .andExpect(jsonPath("$[*].costBasisCad").doesNotExist());
    }
}
