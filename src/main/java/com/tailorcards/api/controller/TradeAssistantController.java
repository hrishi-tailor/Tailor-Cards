package com.tailorcards.api.controller;

import com.tailorcards.api.trade.dto.TradeConversationRequest;
import com.tailorcards.api.trade.dto.TradeConversationResponse;
import com.tailorcards.api.trade.dto.TradeQuoteRequest;
import com.tailorcards.api.trade.dto.TradeQuoteResponse;
import com.tailorcards.api.trade.dto.TradeSubmissionRequest;
import com.tailorcards.api.trade.dto.TradeSubmissionResponse;
import com.tailorcards.api.trade.service.TradeAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade-assistant")
@RequiredArgsConstructor
@Tag(name = "Trade Assistant", description = "Public conversational assistant endpoints for selling and trading Pokémon cards")
public class TradeAssistantController {

    private final TradeAssistantService tradeAssistantService;

    @PostMapping("/messages")
    @Operation(
            summary = "Send chat message to assistant",
            description = "Takes conversation history and returns assistant reply with structured card items found so far"
    )
    public ResponseEntity<TradeConversationResponse> postMessage(
            @Valid @RequestBody TradeConversationRequest request
    ) {
        TradeConversationResponse response = tradeAssistantService.handleConversation(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/quote")
    @Operation(
            summary = "Compute card offer / trade quote",
            description = "Takes confirmed items, executes deterministic pricing engine, and returns decision, offers, top-up, and explanation"
    )
    public ResponseEntity<TradeQuoteResponse> getQuote(
            @Valid @RequestBody TradeQuoteRequest request
    ) {
        TradeQuoteResponse response = tradeAssistantService.computeQuote(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/requests")
    @Operation(
            summary = "Submit quote for review",
            description = "Submits the customer's confirmed quote to the store owner for appraisal and review"
    )
    public ResponseEntity<TradeSubmissionResponse> submitRequest(
            @Valid @RequestBody TradeSubmissionRequest request
    ) {
        TradeSubmissionResponse response = tradeAssistantService.submitQuote(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
