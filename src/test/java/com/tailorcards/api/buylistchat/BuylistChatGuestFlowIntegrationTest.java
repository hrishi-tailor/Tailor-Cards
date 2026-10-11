package com.tailorcards.api.buylistchat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.buylistchat.email.EmailSender;
import com.tailorcards.api.entity.BuylistSubmission;
import com.tailorcards.api.repository.BuylistSubmissionRepository;
import com.tailorcards.api.service.BuylistStorageService;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.TcgdexPriceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:buylist_guest_${random.value};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;"
                + "DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "app.buylist-chat.enabled=true",
        "app.buylist-chat.require-email-verification=false"
})
@DisplayName("Buylist chat with email verification turned off (temporary mode)")
class BuylistChatGuestFlowIntegrationTest {

    private static final String SESSION = "X-Buylist-Session";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private BuylistSubmissionRepository submissions;
    @MockitoBean
    private EmailSender emailSender;
    @MockitoBean
    private TcgdexPriceProvider tcgdex;
    @MockitoBean
    private BuylistStorageService storage;
    @MockitoBean
    private com.tailorcards.api.trade.service.ExchangeRateService exchangeRates;
    @Autowired
    private com.tailorcards.api.repository.ProductRepository products;
    @Autowired
    private com.tailorcards.api.repository.CategoryRepository categories;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void cards() {
        when(exchangeRates.getUsdToCadRate()).thenReturn(new BigDecimal("1.4000"));
        when(tcgdex.searchCardsStrict(eq("Pikachu"), anyInt()))
                .thenReturn(List.of(CardMarketPrice.builder().cardId("base1-58").name("Pikachu").cardNumber("58").build()));
        when(tcgdex.fetchCardStrict("base1-58")).thenReturn(Optional.of(CardMarketPrice.builder()
                .cardId("base1-58").name("Pikachu").setName("Base Set").cardNumber("58").category("Pokemon")
                .marketPriceUsd(new BigDecimal("9.95")).variantPricesUsd(Map.of("normal", new BigDecimal("9.95")))
                .setOfficialCount(102).source("TCGDEX").build()));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String guestToken(String ip) throws Exception {
        return body(mvc.perform(post("/api/buylist-chat/session/guest").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk()))
                .path("sessionToken").asText();
    }

    private JsonNode readyDraft(String token) throws Exception {
        String draftId = body(mvc.perform(post("/api/buylist-chat/drafts").header(SESSION, token))).path("draftId").asText();
        mvc.perform(post("/api/buylist-chat/drafts/" + draftId + "/paste").header(SESSION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"3 Pikachu 58/102 LP\"}")).andExpect(status().isOk());
        return body(mvc.perform(get("/api/buylist-chat/drafts/" + draftId).header(SESSION, token)));
    }

    private ResultActions confirm(String token, JsonNode draft, String email, String ip) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("contentHash", draft.path("summary").path("contentHash").asText());
        if (email != null) {
            request.put("email", email);
        }
        return mvc.perform(post("/api/buylist-chat/drafts/" + draft.path("draftId").asText() + "/confirm")
                .header(SESSION, token).header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
    }

    @Test
    @DisplayName("Status reports verification off; chat starts without an email code")
    void startsWithoutCode() throws Exception {
        mvc.perform(get("/api/buylist-chat/status")).andExpect(jsonPath("$.emailVerificationRequired").value(false));
        String token = guestToken("10.1.0.1");
        JsonNode draft = readyDraft(token);

        assertThat(draft.path("lines").get(0).path("status").asText()).isEqualTo("ELIGIBLE");
        verify(emailSender, never()).send(anyString(), eq("Your Tailor Cards sign-in code"), anyString());
    }

    @Test
    @DisplayName("Confirm needs a contact email; it's stored, flagged as unverified, and limited to one per day")
    void contactEmailAtConfirm() throws Exception {
        String ip = "10.1.1." + (int) (Math.random() * 200);
        String email = "guest-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String token = guestToken(ip);
        JsonNode draft = readyDraft(token);

        confirm(token, draft, null, ip).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("email")));
        confirm(token, draft, "not-an-email", ip).andExpect(status().isBadRequest());
        long id = body(confirm(token, draft, " " + email.toUpperCase() + " ", ip).andExpect(status().isOk()))
                .path("submissionId").asLong();

        BuylistSubmission saved = submissions.findById(id).orElseThrow();
        assertThat(saved.getCustomerEmail()).isEqualTo(email);
        assertThat(saved.getRedFlags()).contains("Email not verified");

        // Same email from a new anonymous session is still limited to one per day
        String token2 = guestToken(ip);
        confirm(token2, readyDraft(token2), email, ip).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Anonymous drafts are private to their session")
    void ownership() throws Exception {
        String a = guestToken("10.1.2.1");
        String b = guestToken("10.1.2.2");
        String draftA = body(mvc.perform(post("/api/buylist-chat/drafts").header(SESSION, a))).path("draftId").asText();
        mvc.perform(get("/api/buylist-chat/drafts/" + draftA).header(SESSION, b)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Anonymous sessions are rate limited per IP")
    void guestRateLimit() throws Exception {
        String ip = "10.1.3." + (int) (Math.random() * 200);
        for (int i = 0; i < 10; i++) {
            guestToken(ip);
        }
        mvc.perform(post("/api/buylist-chat/session/guest").header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("A graded line is NEEDS_REVIEW and its ungraded price is left out of the totals")
    void gradedLineExcludedFromTotals() throws Exception {
        String token = guestToken("10.1.4.1");
        String draftId = body(mvc.perform(post("/api/buylist-chat/drafts").header(SESSION, token))).path("draftId").asText();
        mvc.perform(post("/api/buylist-chat/drafts/" + draftId + "/paste").header(SESSION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Pikachu 58/102 PSA 10\"}")).andExpect(status().isOk());
        JsonNode draft = body(mvc.perform(get("/api/buylist-chat/drafts/" + draftId).header(SESSION, token)));

        JsonNode line = draft.path("lines").get(0);
        assertThat(line.path("grading").asText()).isEqualTo("PSA 10");
        assertThat(line.path("status").asText()).isEqualTo("NEEDS_REVIEW");
        assertThat(line.path("unitMarketUsd").decimalValue()).isEqualByComparingTo("9.95");
        assertThat(draft.path("summary").path("totalMarketUsd").isNull()).isTrue();
    }

    private Long shopCard(String name, String priceCad) {
        com.tailorcards.api.entity.Category category = categories.findAll().stream().findFirst().orElseGet(() ->
                categories.save(com.tailorcards.api.entity.Category.builder().name("Singles").build()));
        return products.save(com.tailorcards.api.entity.Product.builder().name(name).price(new BigDecimal(priceCad))
                .stock(1).category(category).status("AVAILABLE").build()).getId();
    }

    @Test
    @DisplayName("Trade: pick shop cards (CAD shown in USD); within 80% credit fits, over it lowers the meter and is flagged")
    void tradeWithShopCards() throws Exception {
        Long fits = shopCard("Shop Eevee " + UUID.randomUUID(), "28.00");    // $20.00 USD at 1.40
        Long pricey = shopCard("Shop Lugia " + UUID.randomUUID(), "70.00");  // $50.00 USD
        String ip = "10.1.5." + (int) (Math.random() * 200);
        String token = guestToken(ip);
        JsonNode draft = readyDraft(token);  // 3x Pikachu $9.95 LP: cash 75% = 22.39, trade 80% = 23.88
        String draftId = draft.path("draftId").asText();
        int sellPct = draft.path("summary").path("likelihoodPct").asInt();
        assertThat(draft.path("summary").path("deal").path("ratesText").asText()).contains("75%").contains("80% in store credit");

        JsonNode shop = body(mvc.perform(get("/api/buylist-chat/store-cards").param("q", "Shop Eevee")));
        assertThat(shop.get(0).path("priceUsd").decimalValue()).isEqualByComparingTo("20.00");

        JsonNode within = body(mvc.perform(post("/api/buylist-chat/drafts/" + draftId + "/trade-items").header(SESSION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":" + fits + "}")).andExpect(status().isOk()));
        JsonNode deal = within.path("summary").path("deal");
        assertThat(deal.path("dealType").asText()).isEqualTo("TRADE");
        assertThat(deal.path("tradeCreditUsd").decimalValue()).isEqualByComparingTo("23.88");
        assertThat(deal.path("withinRules").asBoolean()).isTrue();

        JsonNode over = body(mvc.perform(post("/api/buylist-chat/drafts/" + draftId + "/trade-items").header(SESSION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":" + pricey + "}")));
        JsonNode overDeal = over.path("summary").path("deal");
        assertThat(overDeal.path("withinRules").asBoolean()).isFalse();
        assertThat(overDeal.path("overByUsd").decimalValue()).isEqualByComparingTo("46.12");
        assertThat(over.path("summary").path("likelihoodPct").asInt()).isLessThan(sellPct);
        assertThat(over.path("summary").path("likelihoodReasons").get(0).asText()).contains("over your trade credit");

        long id = body(confirm(token, over, "trader-" + UUID.randomUUID().toString().substring(0, 6) + "@example.com", ip)
                .andExpect(status().isOk())).path("submissionId").asLong();
        BuylistSubmission saved = submissions.findById(id).orElseThrow();
        assertThat(saved.getDealType()).isEqualTo("TRADE");
        assertThat(saved.getStoreTotalUsd()).isEqualByComparingTo("70.00");
        assertThat(saved.getTradeCreditUsd()).isEqualByComparingTo("23.88");
        assertThat(saved.getUsdCadRate()).isEqualByComparingTo("1.4000");
        assertThat(saved.getStoreCardsJson()).contains("Shop Lugia");
        assertThat(saved.getRedFlags()).contains("above your rates");
    }

    @Test
    @DisplayName("Sell: an asking price above the cash offer is recorded and moves the meter")
    void sellWithAskingPrice() throws Exception {
        String token = guestToken("10.1.6.1");
        JsonNode draft = readyDraft(token);
        String draftId = draft.path("draftId").asText();
        long lineId = draft.path("lines").get(0).path("id").asLong();
        int before = draft.path("summary").path("likelihoodPct").asInt();

        JsonNode asked = body(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/buylist-chat/drafts/" + draftId + "/lines/" + lineId).header(SESSION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"requestedUnitUsd\":12.00}")).andExpect(status().isOk()));

        assertThat(asked.path("lines").get(0).path("requestedUnitUsd").decimalValue()).isEqualByComparingTo("12.00");
        JsonNode deal = asked.path("summary").path("deal");
        assertThat(deal.path("askTotalUsd").decimalValue()).isEqualByComparingTo("36.00");
        assertThat(deal.path("askRatio").decimalValue()).isEqualByComparingTo("1.608");
        assertThat(asked.path("summary").path("likelihoodPct").asInt()).isLessThan(before);
        assertThat(asked.path("summary").path("contentHash").asText()).isNotEqualTo(draft.path("summary").path("contentHash").asText());
    }

    @Test
    @DisplayName("A picked shop card that sells before confirm blocks the confirm with a clear message")
    void soldOutPick() throws Exception {
        Long id = shopCard("Shop Mew " + UUID.randomUUID(), "14.00");
        String ip = "10.1.7." + (int) (Math.random() * 200);
        String token = guestToken(ip);
        JsonNode draft = readyDraft(token);
        String draftId = draft.path("draftId").asText();
        mvc.perform(post("/api/buylist-chat/drafts/" + draftId + "/trade-items").header(SESSION, token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":" + id + "}")).andExpect(status().isOk());
        var product = products.findById(id).orElseThrow();
        product.setStock(0);
        product.setStatus("SOLD");
        products.save(product);
        JsonNode refreshed = body(mvc.perform(get("/api/buylist-chat/drafts/" + draftId).header(SESSION, token)));

        confirm(token, refreshed, "late@example.com", ip).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("no longer available")));
    }
}
