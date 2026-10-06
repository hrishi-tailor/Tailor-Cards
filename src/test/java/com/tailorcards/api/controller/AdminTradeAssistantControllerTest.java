package com.tailorcards.api.controller;

import com.tailorcards.api.entity.BuyRule;
import com.tailorcards.api.entity.CardLiquidity;
import com.tailorcards.api.entity.TradeParameter;
import com.tailorcards.api.trade.dto.BuyRuleUpdateRequest;
import com.tailorcards.api.trade.dto.CardLiquidityRequest;
import com.tailorcards.api.trade.dto.TradeAdminReviewRequest;
import com.tailorcards.api.trade.dto.TradeParameterUpdateRequest;
import com.tailorcards.api.trade.dto.TradeSubmissionResponse;
import com.tailorcards.api.trade.service.TradeAssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTradeAssistantController Unit Tests")
class AdminTradeAssistantControllerTest {

    @Mock
    private TradeAssistantService tradeAssistantService;

    private AdminTradeAssistantController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminTradeAssistantController(tradeAssistantService);
    }

    @Test
    @DisplayName("Admin can list and retrieve requests")
    void testGetRequests() {
        TradeSubmissionResponse item = TradeSubmissionResponse.builder()
                .id(1L)
                .referenceCode("TR-001")
                .status("PENDING_REVIEW")
                .offeredAmount(new BigDecimal("150.00"))
                .build();

        Page<TradeSubmissionResponse> page = new PageImpl<>(List.of(item));
        when(tradeAssistantService.getRequests("PENDING_REVIEW", PageRequest.of(0, 50))).thenReturn(page);

        ResponseEntity<Page<TradeSubmissionResponse>> response = controller.getRequests("PENDING_REVIEW", PageRequest.of(0, 50));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).hasSize(1);
        assertThat(response.getBody().getContent().getFirst().referenceCode()).isEqualTo("TR-001");
    }

    @Test
    @DisplayName("Admin can approve, counter, and decline requests")
    void testReviewActions() {
        TradeSubmissionResponse approved = TradeSubmissionResponse.builder().id(1L).status("APPROVED").build();
        TradeSubmissionResponse countered = TradeSubmissionResponse.builder().id(1L).status("COUNTERED").build();
        TradeSubmissionResponse declined = TradeSubmissionResponse.builder().id(1L).status("DECLINED").build();

        TradeAdminReviewRequest req = new TradeAdminReviewRequest(new BigDecimal("150.00"), null, "Notes");

        when(tradeAssistantService.approveRequest(1L, req)).thenReturn(approved);
        when(tradeAssistantService.counterRequest(1L, req)).thenReturn(countered);
        when(tradeAssistantService.declineRequest(1L, req)).thenReturn(declined);

        assertThat(controller.approveRequest(1L, req).getBody().status()).isEqualTo("APPROVED");
        assertThat(controller.counterRequest(1L, req).getBody().status()).isEqualTo("COUNTERED");
        assertThat(controller.declineRequest(1L, req).getBody().status()).isEqualTo("DECLINED");
    }

    @Test
    @DisplayName("Admin can manage buy rules, trade parameters, and card liquidity")
    void testConfigurationEndpoints() {
        // Buy rules
        BuyRule rule = BuyRule.builder().id(1L).rate(new BigDecimal("0.82")).build();
        when(tradeAssistantService.getBuyRules()).thenReturn(List.of(rule));
        when(tradeAssistantService.updateBuyRule(1L, new BuyRuleUpdateRequest(new BigDecimal("0.85"), 1, true))).thenReturn(rule);

        assertThat(controller.getBuyRules().getBody()).hasSize(1);
        assertThat(controller.updateBuyRule(1L, new BuyRuleUpdateRequest(new BigDecimal("0.85"), 1, true)).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Parameters
        TradeParameter param = TradeParameter.builder().paramKey("CAP").paramValue(new BigDecimal("0.90")).build();
        when(tradeAssistantService.getTradeParameters()).thenReturn(List.of(param));
        when(tradeAssistantService.updateTradeParameter("CAP", new TradeParameterUpdateRequest(new BigDecimal("0.92"), "test"))).thenReturn(param);

        assertThat(controller.getParameters().getBody()).hasSize(1);
        assertThat(controller.updateParameter("CAP", new TradeParameterUpdateRequest(new BigDecimal("0.92"), "test")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Liquidity
        CardLiquidity liquidity = CardLiquidity.builder().pokemontcgId("base1-4").liquidityTier("HIGH").build();
        when(tradeAssistantService.getCardLiquidities()).thenReturn(List.of(liquidity));
        when(tradeAssistantService.upsertCardLiquidity(new CardLiquidityRequest("base1-4", "HIGH", BigDecimal.ZERO, "notes"))).thenReturn(liquidity);

        assertThat(controller.getLiquidityTags().getBody()).hasSize(1);
        assertThat(controller.upsertLiquidity(new CardLiquidityRequest("base1-4", "HIGH", BigDecimal.ZERO, "notes")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Void> delResponse = controller.deleteLiquidity("base1-4");
        assertThat(delResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(tradeAssistantService).deleteCardLiquidity("base1-4");
    }
}
