package com.tailorcards.api.trade.service;

import com.tailorcards.api.entity.BuyRule;
import com.tailorcards.api.entity.CardLiquidity;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.entity.TradeAssistantRequest;
import com.tailorcards.api.entity.TradeParameter;
import com.tailorcards.api.repository.BuyRuleRepository;
import com.tailorcards.api.repository.CardLiquidityRepository;
import com.tailorcards.api.repository.ProductRepository;
import com.tailorcards.api.repository.TradeAssistantRequestRepository;
import com.tailorcards.api.repository.TradeParameterRepository;
import com.tailorcards.api.trade.dto.BuyRuleUpdateRequest;
import com.tailorcards.api.trade.dto.CardLiquidityRequest;
import com.tailorcards.api.trade.dto.TradeAdminReviewRequest;
import com.tailorcards.api.trade.dto.TradeChatMessageDto;
import com.tailorcards.api.trade.dto.TradeConversationRequest;
import com.tailorcards.api.trade.dto.TradeConversationResponse;
import com.tailorcards.api.trade.dto.TradeParameterUpdateRequest;
import com.tailorcards.api.trade.dto.TradeQuoteRequest;
import com.tailorcards.api.trade.dto.TradeQuoteResponse;
import com.tailorcards.api.trade.dto.TradeSubmissionRequest;
import com.tailorcards.api.trade.dto.TradeSubmissionResponse;
import com.tailorcards.api.trade.model.CustomerCardItem;
import com.tailorcards.api.trade.model.PricingResult;
import com.tailorcards.api.trade.model.RuleTrace;
import com.tailorcards.api.trade.model.TradeDecision;
import com.tailorcards.api.trade.model.TradeFlowType;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.PriceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradeAssistantService Tests")
class TradeAssistantServiceTest {

    @Mock
    private TradePricingEngine pricingEngine;

    @Mock
    private CardPriceService cardPriceService;

    @Mock
    private PriceProvider priceProvider;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TradeAssistantRequestRepository requestRepository;

    @Mock
    private BuyRuleRepository buyRuleRepository;

    @Mock
    private TradeParameterRepository parameterRepository;

    @Mock
    private CardLiquidityRepository cardLiquidityRepository;

    private TradeAssistantService service;

    @BeforeEach
    void setUp() {
        service = new TradeAssistantService(
                pricingEngine,
                cardPriceService,
                priceProvider,
                productRepository,
                requestRepository,
                buyRuleRepository,
                parameterRepository,
                cardLiquidityRepository
        );
    }

    @Test
    @DisplayName("Conversation handles card search and marks requiresConfirmation")
    void testHandleConversationWithCardSearch() {
        CardMarketPrice foundCard = CardMarketPrice.builder()
                .cardId("base1-4")
                .name("Charizard")
                .setName("Base Set")
                .cardNumber("4")
                .imageUrl("https://img.png")
                .build();

        when(priceProvider.searchCards("Charizard", 3)).thenReturn(List.of(foundCard));

        TradeConversationRequest request = new TradeConversationRequest(
                "conv-1",
                TradeFlowType.SELL,
                List.of(new TradeChatMessageDto("user", "Charizard")),
                List.of(),
                List.of()
        );

        TradeConversationResponse response = service.handleConversation(request);

        assertThat(response.conversationId()).isEqualTo("conv-1");
        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.extractedItems()).hasSize(1);
        assertThat(response.extractedItems().getFirst().getCardId()).isEqualTo("base1-4");
    }

    @Test
    @DisplayName("Computes quote for SELL cash offer")
    void testComputeQuoteSell() {
        CustomerCardItem item = CustomerCardItem.builder()
                .cardId("base1-4")
                .name("Charizard")
                .condition("NEAR_MINT")
                .quantity(1)
                .build();

        when(cardPriceService.resolvePriceCad("base1-4", "NEAR_MINT", null, null))
                .thenReturn(Optional.of(new BigDecimal("300.00")));

        PricingResult pricingResult = PricingResult.builder()
                .flowType(TradeFlowType.SELL)
                .decision(TradeDecision.ACCEPT)
                .cashOffer(new BigDecimal("231.00"))
                .customerTotalMarketValueCad(new BigDecimal("300.00"))
                .ruleTrace(new RuleTrace())
                .build();

        when(pricingEngine.evaluate(eq(TradeFlowType.SELL), any(), any()))
                .thenReturn(pricingResult);

        TradeQuoteRequest request = new TradeQuoteRequest(
                TradeFlowType.SELL,
                List.of(item),
                null
        );

        TradeQuoteResponse response = service.computeQuote(request);

        assertThat(response.decision()).isEqualTo(TradeDecision.ACCEPT);
        assertThat(response.cashOffer()).isEqualByComparingTo(new BigDecimal("231.00"));
        assertThat(response.explanation()).contains("$231.00 CAD cash");
    }

    @Test
    @DisplayName("Computes quote for TRADE with store cards and cash top-up")
    void testComputeQuoteTrade() {
        CustomerCardItem customerCard = CustomerCardItem.builder()
                .cardId("base1-2")
                .name("Blastoise")
                .condition("NEAR_MINT")
                .quantity(1)
                .build();

        Product storeProduct = Product.builder()
                .id(10L)
                .name("Charizard")
                .price(new BigDecimal("200.00"))
                .costBasis(new BigDecimal("150.00"))
                .build();

        when(cardPriceService.resolvePriceCad("base1-2", "NEAR_MINT", null, null))
                .thenReturn(Optional.of(new BigDecimal("100.00")));
        when(productRepository.findById(10L)).thenReturn(Optional.of(storeProduct));

        PricingResult pricingResult = PricingResult.builder()
                .flowType(TradeFlowType.TRADE)
                .decision(TradeDecision.COUNTER)
                .tradeCredit(new BigDecimal("70.00"))
                .counterTopUp(new BigDecimal("130.00"))
                .customerTotalMarketValueCad(new BigDecimal("100.00"))
                .storeTotalListPriceCad(new BigDecimal("200.00"))
                .ruleTrace(new RuleTrace())
                .build();

        when(pricingEngine.evaluate(eq(TradeFlowType.TRADE), any(), any()))
                .thenReturn(pricingResult);

        TradeQuoteRequest request = new TradeQuoteRequest(
                TradeFlowType.TRADE,
                List.of(customerCard),
                List.of(10L)
        );

        TradeQuoteResponse response = service.computeQuote(request);

        assertThat(response.decision()).isEqualTo(TradeDecision.COUNTER);
        assertThat(response.counterTopUp()).isEqualByComparingTo(new BigDecimal("130.00"));
        assertThat(response.explanation()).contains("cash top-up of $130.00 CAD");
    }

    @Test
    @DisplayName("Submits quote for review and verifies server-side computation")
    void testSubmitQuote() {
        CustomerCardItem item = CustomerCardItem.builder()
                .cardId("base1-4")
                .name("Charizard")
                .condition("NEAR_MINT")
                .quantity(1)
                .build();

        when(cardPriceService.resolvePriceCad("base1-4", "NEAR_MINT", null, null))
                .thenReturn(Optional.of(new BigDecimal("300.00")));

        PricingResult pricingResult = PricingResult.builder()
                .flowType(TradeFlowType.SELL)
                .decision(TradeDecision.ACCEPT)
                .cashOffer(new BigDecimal("231.00"))
                .customerTotalMarketValueCad(new BigDecimal("300.00"))
                .ruleTrace(new RuleTrace())
                .build();

        when(pricingEngine.evaluate(eq(TradeFlowType.SELL), any(), any()))
                .thenReturn(pricingResult);

        TradeAssistantRequest savedEntity = TradeAssistantRequest.builder()
                .id(1L)
                .referenceCode("TR-ABC12345")
                .flowType("SELL")
                .status("PENDING_REVIEW")
                .customerName("Ash Ketchum")
                .customerEmail("ash@pallet.town")
                .decision("ACCEPT")
                .offeredAmount(new BigDecimal("231.00"))
                .customerTotalMarketCad(new BigDecimal("300.00"))
                .createdAt(Instant.now())
                .build();

        when(requestRepository.save(any(TradeAssistantRequest.class))).thenReturn(savedEntity);

        TradeSubmissionRequest request = new TradeSubmissionRequest(
                new TradeQuoteRequest(TradeFlowType.SELL, List.of(item), null),
                "Ash Ketchum",
                "ash@pallet.town",
                "555-0199",
                "Looking to sell quickly"
        );

        TradeSubmissionResponse response = service.submitQuote(request);

        assertThat(response.referenceCode()).isEqualTo("TR-ABC12345");
        assertThat(response.status()).isEqualTo("PENDING_REVIEW");
        assertThat(response.offeredAmount()).isEqualByComparingTo(new BigDecimal("231.00"));
    }

    @Test
    @DisplayName("Admin can approve, counter, and decline requests")
    void testAdminRequestActions() {
        TradeAssistantRequest existing = TradeAssistantRequest.builder()
                .id(5L)
                .referenceCode("TR-555")
                .flowType("SELL")
                .status("PENDING_REVIEW")
                .offeredAmount(new BigDecimal("100.00"))
                .build();

        when(requestRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Approve
        TradeSubmissionResponse approved = service.approveRequest(5L, new TradeAdminReviewRequest(new BigDecimal("110.00"), null, "Approved slightly higher"));
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.offeredAmount()).isEqualByComparingTo(new BigDecimal("110.00"));

        // Counter
        TradeSubmissionResponse countered = service.counterRequest(5L, new TradeAdminReviewRequest(new BigDecimal("95.00"), null, "Counteroffer"));
        assertThat(countered.status()).isEqualTo("COUNTERED");

        // Decline
        TradeSubmissionResponse declined = service.declineRequest(5L, new TradeAdminReviewRequest(null, null, "Condition is too worn"));
        assertThat(declined.status()).isEqualTo("DECLINED");
    }

    @Test
    @DisplayName("Admin can manage buy rules, trade parameters, and card liquidity")
    void testAdminConfigManagement() {
        // Buy Rule update
        BuyRule rule = BuyRule.builder().id(1L).categoryCode("SEALED").rate(new BigDecimal("0.70")).build();
        when(buyRuleRepository.findById(1L)).thenReturn(Optional.of(rule));
        when(buyRuleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BuyRule updatedRule = service.updateBuyRule(1L, new BuyRuleUpdateRequest(new BigDecimal("0.72"), 2, true));
        assertThat(updatedRule.getRate()).isEqualByComparingTo(new BigDecimal("0.72"));

        // Trade Parameter update
        TradeParameter param = TradeParameter.builder().paramKey("HARD_CAP_RATE").paramValue(new BigDecimal("0.90")).build();
        when(parameterRepository.findByParamKey("HARD_CAP_RATE")).thenReturn(Optional.of(param));
        when(parameterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TradeParameter updatedParam = service.updateTradeParameter("HARD_CAP_RATE", new TradeParameterUpdateRequest(new BigDecimal("0.92"), "Revised cap"));
        assertThat(updatedParam.getParamValue()).isEqualByComparingTo(new BigDecimal("0.92"));

        // Card Liquidity upsert & delete
        CardLiquidity liquidity = CardLiquidity.builder().pokemontcgId("base1-4").liquidityTier("HIGH").haircut(BigDecimal.ZERO).build();
        when(cardLiquidityRepository.findByPokemontcgId("base1-4")).thenReturn(Optional.of(liquidity));
        when(cardLiquidityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CardLiquidity savedLiquidity = service.upsertCardLiquidity(new CardLiquidityRequest("base1-4", "MEDIUM", new BigDecimal("0.02"), "Updated"));
        assertThat(savedLiquidity.getLiquidityTier()).isEqualTo("MEDIUM");
        assertThat(savedLiquidity.getHaircut()).isEqualByComparingTo(new BigDecimal("0.02"));

        service.deleteCardLiquidity("base1-4");
        verify(cardLiquidityRepository).delete(liquidity);
    }
}
