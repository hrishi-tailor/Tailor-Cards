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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/trade-assistant")
@RequiredArgsConstructor
@Tag(name = "Admin - Trade Assistant", description = "Endpoints for managing trade requests, buy rules, trade parameters, and card liquidity tiers")
@SecurityRequirement(name = "basicAuth")
public class AdminTradeAssistantController {

    private final TradeAssistantService tradeAssistantService;
    private final com.tailorcards.api.audit.AuditLog audit;

    @GetMapping("/requests")
    @Operation(summary = "List trade requests", description = "Retrieve paginated list of trade submissions with optional status filter")
    public ResponseEntity<Page<TradeSubmissionResponse>> getRequests(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(tradeAssistantService.getRequests(status, pageable));
    }

    @GetMapping("/requests/{id}")
    @Operation(summary = "Get trade request by ID", description = "View full details and rule trace for a trade submission")
    public ResponseEntity<TradeSubmissionResponse> getRequestById(@PathVariable Long id) {
        return ResponseEntity.ok(tradeAssistantService.getRequestById(id));
    }

    @PostMapping("/requests/{id}/approve")
    @Operation(summary = "Approve trade request", description = "Approve customer sell/trade quote")
    public ResponseEntity<TradeSubmissionResponse> approveRequest(
            @PathVariable Long id,
            @RequestBody(required = false) TradeAdminReviewRequest request
    ) {
        String before = audit.snapshotOf(() -> tradeAssistantService.getRequestById(id));
        TradeSubmissionResponse after = tradeAssistantService.approveRequest(id, request);
        audit.record("TRADE_APPROVE", "TRADE_REQUEST", id, before, after);
        return ResponseEntity.ok(after);
    }

    @PostMapping("/requests/{id}/counter")
    @Operation(summary = "Counter-offer trade request", description = "Counter customer quote with revised cash/credit amount and notes")
    public ResponseEntity<TradeSubmissionResponse> counterRequest(
            @PathVariable Long id,
            @RequestBody(required = false) TradeAdminReviewRequest request
    ) {
        String before = audit.snapshotOf(() -> tradeAssistantService.getRequestById(id));
        TradeSubmissionResponse after = tradeAssistantService.counterRequest(id, request);
        audit.record("TRADE_COUNTER", "TRADE_REQUEST", id, before, after);
        return ResponseEntity.ok(after);
    }

    @PostMapping("/requests/{id}/decline")
    @Operation(summary = "Decline trade request", description = "Decline customer sell/trade submission with reason")
    public ResponseEntity<TradeSubmissionResponse> declineRequest(
            @PathVariable Long id,
            @RequestBody(required = false) TradeAdminReviewRequest request
    ) {
        String before = audit.snapshotOf(() -> tradeAssistantService.getRequestById(id));
        TradeSubmissionResponse after = tradeAssistantService.declineRequest(id, request);
        audit.record("TRADE_DECLINE", "TRADE_REQUEST", id, before, after);
        return ResponseEntity.ok(after);
    }

    @GetMapping("/buy-rules")
    @Operation(summary = "List cash buy rules", description = "Retrieve buy rules and payout percentages by category tier")
    public ResponseEntity<List<BuyRule>> getBuyRules() {
        return ResponseEntity.ok(tradeAssistantService.getBuyRules());
    }

    @PutMapping("/buy-rules/{id}")
    @Operation(summary = "Update buy rule", description = "Modify buy rule rate, priority, or active status")
    public ResponseEntity<BuyRule> updateBuyRule(
            @PathVariable Long id,
            @Valid @RequestBody BuyRuleUpdateRequest request
    ) {
        String before = audit.snapshotOf(() -> tradeAssistantService.getBuyRules().stream()
                .filter(r -> id.equals(r.getId())).findFirst().orElse(null));
        BuyRule after = tradeAssistantService.updateBuyRule(id, request);
        audit.record("BUY_RULE_UPDATE", "BUY_RULE", id, before, after);
        return ResponseEntity.ok(after);
    }

    @GetMapping("/parameters")
    @Operation(summary = "List trade parameters", description = "Retrieve all configurable trade engine parameters")
    public ResponseEntity<List<TradeParameter>> getParameters() {
        return ResponseEntity.ok(tradeAssistantService.getTradeParameters());
    }

    @PutMapping("/parameters/{key}")
    @Operation(summary = "Update trade parameter", description = "Modify parameter value (e.g. fees, margins, consolidation thresholds)")
    public ResponseEntity<TradeParameter> updateParameter(
            @PathVariable String key,
            @Valid @RequestBody TradeParameterUpdateRequest request
    ) {
        String before = audit.snapshotOf(() -> tradeAssistantService.getTradeParameters().stream()
                .filter(p -> key.equals(p.getParamKey())).findFirst().orElse(null));
        TradeParameter after = tradeAssistantService.updateTradeParameter(key, request);
        audit.record("TRADE_PARAMETER_UPDATE", "TRADE_PARAMETER", key, before, after);
        return ResponseEntity.ok(after);
    }

    @GetMapping("/liquidity")
    @Operation(summary = "List card liquidity tags", description = "Retrieve card liquidity velocity tiers and haircuts")
    public ResponseEntity<List<CardLiquidity>> getLiquidityTags() {
        return ResponseEntity.ok(tradeAssistantService.getCardLiquidities());
    }

    @PostMapping("/liquidity")
    @Operation(summary = "Upsert card liquidity tag", description = "Tag a card with a liquidity tier (HIGH, MEDIUM, LOW) and haircut")
    public ResponseEntity<CardLiquidity> upsertLiquidity(
            @Valid @RequestBody CardLiquidityRequest request
    ) {
        String before = audit.snapshotOf(() -> tradeAssistantService.getCardLiquidities().stream()
                .filter(l -> request.pokemontcgId().equals(l.getPokemontcgId())).findFirst().orElse(null));
        CardLiquidity saved = tradeAssistantService.upsertCardLiquidity(request);
        audit.record("LIQUIDITY_UPSERT", "CARD_LIQUIDITY", saved.getPokemontcgId(), before, saved);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/liquidity/{pokemontcgId}")
    @Operation(summary = "Delete card liquidity tag", description = "Remove custom liquidity tag for a card")
    public ResponseEntity<Void> deleteLiquidity(@PathVariable String pokemontcgId) {
        String before = audit.snapshotOf(() -> tradeAssistantService.getCardLiquidities().stream()
                .filter(l -> pokemontcgId.equals(l.getPokemontcgId())).findFirst().orElse(null));
        tradeAssistantService.deleteCardLiquidity(pokemontcgId);
        audit.record("LIQUIDITY_DELETE", "CARD_LIQUIDITY", pokemontcgId, before, null);
        return ResponseEntity.noContent().build();
    }
}
