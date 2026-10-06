package com.tailorcards.api.controller;

import com.tailorcards.api.trade.dto.TradeChatMessageDto;
import com.tailorcards.api.trade.dto.TradeConversationRequest;
import com.tailorcards.api.trade.dto.TradeConversationResponse;
import com.tailorcards.api.trade.dto.TradeQuoteRequest;
import com.tailorcards.api.trade.dto.TradeQuoteResponse;
import com.tailorcards.api.trade.dto.TradeSubmissionRequest;
import com.tailorcards.api.trade.dto.TradeSubmissionResponse;
import com.tailorcards.api.trade.model.CustomerCardItem;
import com.tailorcards.api.trade.model.TradeDecision;
import com.tailorcards.api.trade.model.TradeFlowType;
import com.tailorcards.api.trade.service.TradeAssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradeAssistantController Unit Tests")
class TradeAssistantControllerTest {

    @Mock
    private TradeAssistantService tradeAssistantService;

    private TradeAssistantController controller;

    @BeforeEach
    void setUp() {
        controller = new TradeAssistantController(tradeAssistantService);
    }

    @Test
    @DisplayName("POST /messages returns assistant turn response")
    void testPostMessage() {
        TradeConversationRequest request = new TradeConversationRequest(
                "session-1",
                TradeFlowType.SELL,
                List.of(new TradeChatMessageDto("user", "Charizard Base")),
                List.of(),
                List.of()
        );

        TradeConversationResponse expected = TradeConversationResponse.builder()
                .conversationId("session-1")
                .reply("I found 1 card.")
                .requiresConfirmation(true)
                .build();

        when(tradeAssistantService.handleConversation(request)).thenReturn(expected);

        ResponseEntity<TradeConversationResponse> response = controller.postMessage(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().conversationId()).isEqualTo("session-1");
        assertThat(response.getBody().requiresConfirmation()).isTrue();
    }

    @Test
    @DisplayName("POST /quote computes and returns quote details")
    void testGetQuote() {
        TradeQuoteRequest request = new TradeQuoteRequest(
                TradeFlowType.SELL,
                List.of(CustomerCardItem.builder().cardId("base1-4").name("Charizard").build()),
                null
        );

        TradeQuoteResponse expected = TradeQuoteResponse.builder()
                .flowType(TradeFlowType.SELL)
                .decision(TradeDecision.ACCEPT)
                .cashOffer(new BigDecimal("231.00"))
                .customerTotalMarketCad(new BigDecimal("300.00"))
                .explanation("Cash offer ready.")
                .build();

        when(tradeAssistantService.computeQuote(request)).thenReturn(expected);

        ResponseEntity<TradeQuoteResponse> response = controller.getQuote(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().decision()).isEqualTo(TradeDecision.ACCEPT);
        assertThat(response.getBody().cashOffer()).isEqualByComparingTo(new BigDecimal("231.00"));
    }

    @Test
    @DisplayName("POST /requests creates and submits quote for review")
    void testSubmitRequest() {
        TradeQuoteRequest quote = new TradeQuoteRequest(
                TradeFlowType.SELL,
                List.of(CustomerCardItem.builder().cardId("base1-4").name("Charizard").build()),
                null
        );
        TradeSubmissionRequest request = new TradeSubmissionRequest(
                quote,
                "Brock",
                "brock@pewter.gym",
                "555-1234",
                "Review please"
        );

        TradeSubmissionResponse expected = TradeSubmissionResponse.builder()
                .id(1L)
                .referenceCode("TR-999")
                .status("PENDING_REVIEW")
                .offeredAmount(new BigDecimal("231.00"))
                .createdAt(Instant.now())
                .build();

        when(tradeAssistantService.submitQuote(request)).thenReturn(expected);

        ResponseEntity<TradeSubmissionResponse> response = controller.submitRequest(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().referenceCode()).isEqualTo("TR-999");
        assertThat(response.getBody().status()).isEqualTo("PENDING_REVIEW");
    }
}
