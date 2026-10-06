package com.tailorcards.api.trade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorcards.api.entity.BuyRule;
import com.tailorcards.api.entity.CardLiquidity;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.entity.TradeAssistantRequest;
import com.tailorcards.api.entity.TradeParameter;
import com.tailorcards.api.exception.ResourceNotFoundException;
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
import com.tailorcards.api.trade.model.StoreCardItem;
import com.tailorcards.api.trade.model.TradeDecision;
import com.tailorcards.api.trade.model.TradeFlowType;
import com.tailorcards.api.trade.provider.CardMarketPrice;
import com.tailorcards.api.trade.provider.PriceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.tailorcards.api.trade.llm.TradeExplanationService;
import com.tailorcards.api.trade.llm.TradeExtractionService;
import com.tailorcards.api.trade.model.RuleTrace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class TradeAssistantService {

    private final TradePricingEngine pricingEngine;
    private final CardPriceService cardPriceService;
    private final PriceProvider priceProvider;
    private final ProductRepository productRepository;
    private final TradeAssistantRequestRepository requestRepository;
    private final BuyRuleRepository buyRuleRepository;
    private final TradeParameterRepository parameterRepository;
    private final CardLiquidityRepository cardLiquidityRepository;
    private final TradeExtractionService extractionService;
    private final TradeExplanationService explanationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public TradeAssistantService(
            TradePricingEngine pricingEngine,
            CardPriceService cardPriceService,
            PriceProvider priceProvider,
            ProductRepository productRepository,
            TradeAssistantRequestRepository requestRepository,
            BuyRuleRepository buyRuleRepository,
            TradeParameterRepository parameterRepository,
            CardLiquidityRepository cardLiquidityRepository,
            TradeExtractionService extractionService,
            TradeExplanationService explanationService
    ) {
        this.pricingEngine = pricingEngine;
        this.cardPriceService = cardPriceService;
        this.priceProvider = priceProvider;
        this.productRepository = productRepository;
        this.requestRepository = requestRepository;
        this.buyRuleRepository = buyRuleRepository;
        this.parameterRepository = parameterRepository;
        this.cardLiquidityRepository = cardLiquidityRepository;
        this.extractionService = extractionService;
        this.explanationService = explanationService;
    }

    public TradeAssistantService(
            TradePricingEngine pricingEngine,
            CardPriceService cardPriceService,
            PriceProvider priceProvider,
            ProductRepository productRepository,
            TradeAssistantRequestRepository requestRepository,
            BuyRuleRepository buyRuleRepository,
            TradeParameterRepository parameterRepository,
            CardLiquidityRepository cardLiquidityRepository
    ) {
        this(
                pricingEngine,
                cardPriceService,
                priceProvider,
                productRepository,
                requestRepository,
                buyRuleRepository,
                parameterRepository,
                cardLiquidityRepository,
                new TradeExtractionService(new com.tailorcards.api.trade.llm.AnthropicClient("http://localhost", "", "mock", 100, 1), priceProvider, new com.tailorcards.api.trade.llm.LlmRateLimiter(100), 2000),
                new TradeExplanationService(new com.tailorcards.api.trade.llm.AnthropicClient("http://localhost", "", "mock", 100, 1))
        );
    }

    /**
     * Handles conversation turn, extracting items and building assistant response.
     * Integrates with Stage 4 LLM layer via extraction & explanation services.
     */
    public TradeConversationResponse handleConversation(TradeConversationRequest request) {
        String conversationId = request.conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }

        List<CustomerCardItem> confirmedItems = request.confirmedItems() != null ?
                new ArrayList<>(request.confirmedItems()) : new ArrayList<>();

        List<TradeChatMessageDto> messages = request.messages() != null ?
                request.messages() : List.of();

        String lastUserMessage = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equalsIgnoreCase(messages.get(i).role())) {
                lastUserMessage = messages.get(i).content();
                break;
            }
        }

        List<CustomerCardItem> extracted = new ArrayList<>(confirmedItems);

        // Structured extraction via LLM layer or fallback provider
        if (extracted.isEmpty() && !lastUserMessage.isBlank()) {
            List<CustomerCardItem> extractedCards = extractionService.extractCards(conversationId, lastUserMessage);
            extracted.addAll(extractedCards);
        }

        boolean requiresConfirmation = !extracted.isEmpty() && extracted.stream().anyMatch(item -> !item.isConfirmed());
        String replyText;

        if (extracted.isEmpty()) {
            replyText = "Could you tell me the card name, set, or card number you'd like to " +
                    (request.flowType() == TradeFlowType.SELL ? "sell for cash" : "trade") + "?";
        } else if (requiresConfirmation) {
            replyText = "I found " + extracted.size() + " matching card(s). Please review and confirm the card match and condition before I compute your quote.";
        } else {
            replyText = "Great! Your card details are confirmed. Generating your official quote now.";
        }

        return TradeConversationResponse.builder()
                .conversationId(conversationId)
                .reply(replyText)
                .extractedItems(extracted)
                .requiresConfirmation(requiresConfirmation)
                .build();
    }

    /**
     * Computes deterministic quote using pure Java TradePricingEngine.
     * Price data is resolved from manual overrides, database snapshots, or provider conversion.
     */
    @Transactional
    public TradeQuoteResponse computeQuote(TradeQuoteRequest request) {
        log.info("Computing quote for flowType={}, customerCardsCount={}",
                request.flowType(), request.customerCards() != null ? request.customerCards().size() : 0);

        // Do not price an unconfirmed match or missing card ID
        if (request.customerCards() != null) {
            for (CustomerCardItem item : request.customerCards()) {
                if (Boolean.FALSE.equals(item.confirmed()) || item.cardId() == null || item.cardId().isBlank()) {
                    RuleTrace trace = new RuleTrace();
                    trace.add("VALIDATION", "Unconfirmed card item or missing card ID: " + item.name(), "NEEDS_REVIEW");
                    return TradeQuoteResponse.builder()
                            .flowType(request.flowType())
                            .decision(TradeDecision.NEEDS_REVIEW)
                            .cashOffer(BigDecimal.ZERO)
                            .tradeCredit(BigDecimal.ZERO)
                            .counterTopUp(BigDecimal.ZERO)
                            .customerTotalMarketCad(BigDecimal.ZERO)
                            .storeTotalListPriceCad(BigDecimal.ZERO)
                            .explanation("Please confirm all card matches before generating a final quote.")
                            .ruleTrace(trace)
                            .customerCards(request.customerCards())
                            .storeCards(List.of())
                            .build();
                }
            }
        }

        List<CustomerCardItem> customerCards = new ArrayList<>();
        if (request.customerCards() != null) {
            for (CustomerCardItem item : request.customerCards()) {
                CustomerCardItem enriched = enrichCustomerCard(item);
                customerCards.add(enriched);
            }
        }

        List<StoreCardItem> storeCards = new ArrayList<>();
        if (request.flowType() == TradeFlowType.TRADE && request.storeProductIds() != null) {
            for (Long prodId : request.storeProductIds()) {
                productRepository.findById(prodId).ifPresent(prod -> {
                    BigDecimal costBasis = prod.getCostBasis();
                    storeCards.add(new StoreCardItem(
                            prod.getId(),
                            prod.getName(),
                            prod.getPrice(),
                            costBasis,
                            1
                    ));
                });
            }
        }

        PricingResult result = pricingEngine.evaluate(request.flowType(), customerCards, storeCards);
        String explanation = explanationService.explainResult(result, request.flowType());

        return TradeQuoteResponse.builder()
                .flowType(request.flowType())
                .decision(result.decision())
                .cashOffer(result.cashOffer())
                .tradeCredit(result.tradeCredit())
                .counterTopUp(result.counterTopUp())
                .customerTotalMarketCad(result.customerTotalMarketCad())
                .storeTotalListPriceCad(result.storeTotalListPriceCad())
                .explanation(explanation)
                .ruleTrace(result.ruleTrace())
                .customerCards(customerCards)
                .storeCards(storeCards)
                .build();
    }

    /**
     * Submits a confirmed quote for admin review.
     * Re-evaluates quote server-side to prevent client tampering.
     */
    @Transactional
    public TradeSubmissionResponse submitQuote(TradeSubmissionRequest request) {
        TradeQuoteResponse verifiedQuote = computeQuote(request.quote());

        BigDecimal offered = verifiedQuote.flowType() == TradeFlowType.SELL ?
                verifiedQuote.cashOffer() : verifiedQuote.tradeCredit();

        String customerCardsJson = writeJson(verifiedQuote.customerCards());
        String storeProductsJson = writeJson(verifiedQuote.storeCards());
        String ruleTraceJson = writeJson(verifiedQuote.ruleTrace());

        TradeAssistantRequest entity = TradeAssistantRequest.builder()
                .flowType(verifiedQuote.flowType().name())
                .status("PENDING_REVIEW")
                .customerName(request.customerName())
                .customerEmail(request.customerEmail())
                .customerPhone(request.customerPhone())
                .decision(verifiedQuote.decision().name())
                .offeredAmount(offered)
                .counterTopUp(verifiedQuote.counterTopUp())
                .customerTotalMarketCad(verifiedQuote.customerTotalMarketCad())
                .storeTotalListPriceCad(verifiedQuote.storeTotalListPriceCad())
                .customerCardsJson(customerCardsJson)
                .storeProductsJson(storeProductsJson)
                .ruleTraceJson(ruleTraceJson)
                .explanation(verifiedQuote.explanation())
                .customerNotes(request.customerNotes())
                .build();

        TradeAssistantRequest saved = requestRepository.save(entity);
        log.info("Saved trade submission request ref={}, id={}", saved.getReferenceCode(), saved.getId());

        return mapToSubmissionResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TradeSubmissionResponse> getRequests(String status, Pageable pageable) {
        Page<TradeAssistantRequest> page;
        boolean isDemo = com.tailorcards.api.security.SecurityUtils.isCurrentUserDemoRole();

        if (isDemo) {
            // DEMO role can only see seeded DEMO trade requests (prefixed with DEMO-)
            if (status != null && !status.isBlank()) {
                page = requestRepository.findByStatusAndReferenceCodeStartingWithIgnoreCase(
                        status.trim().toUpperCase(), "DEMO-", pageable);
            } else {
                page = requestRepository.findByReferenceCodeStartingWithIgnoreCase("DEMO-", pageable);
            }
        } else if (status != null && !status.isBlank()) {
            page = requestRepository.findByStatus(status.trim().toUpperCase(), pageable);
        } else {
            page = requestRepository.findAll(pageable);
        }

        return page.map(this::mapToSubmissionResponse);
    }

    @Transactional(readOnly = true)
    public TradeSubmissionResponse getRequestById(Long id) {
        TradeAssistantRequest req = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trade assistant request not found with id: " + id));

        if (com.tailorcards.api.security.SecurityUtils.isCurrentUserDemoRole()) {
            if (req.getReferenceCode() == null || !req.getReferenceCode().toUpperCase().startsWith("DEMO-")) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "DEMO role is not permitted to view real customer trade requests.");
            }
        }

        return mapToSubmissionResponse(req);
    }

    @Transactional
    public TradeSubmissionResponse approveRequest(Long id, TradeAdminReviewRequest review) {
        TradeAssistantRequest req = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trade assistant request not found with id: " + id));

        req.setStatus("APPROVED");
        if (review != null) {
            if (review.revisedAmount() != null) {
                req.setOfferedAmount(review.revisedAmount());
            }
            if (review.revisedTopUp() != null) {
                req.setCounterTopUp(review.revisedTopUp());
            }
            if (review.adminNotes() != null) {
                req.setAdminNotes(review.adminNotes());
            }
        }

        TradeAssistantRequest saved = requestRepository.save(req);
        return mapToSubmissionResponse(saved);
    }

    @Transactional
    public TradeSubmissionResponse counterRequest(Long id, TradeAdminReviewRequest review) {
        TradeAssistantRequest req = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trade assistant request not found with id: " + id));

        req.setStatus("COUNTERED");
        if (review != null) {
            if (review.revisedAmount() != null) {
                req.setOfferedAmount(review.revisedAmount());
            }
            if (review.revisedTopUp() != null) {
                req.setCounterTopUp(review.revisedTopUp());
            }
            if (review.adminNotes() != null) {
                req.setAdminNotes(review.adminNotes());
            }
        }

        TradeAssistantRequest saved = requestRepository.save(req);
        return mapToSubmissionResponse(saved);
    }

    @Transactional
    public TradeSubmissionResponse declineRequest(Long id, TradeAdminReviewRequest review) {
        TradeAssistantRequest req = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trade assistant request not found with id: " + id));

        req.setStatus("DECLINED");
        if (review != null && review.adminNotes() != null) {
            req.setAdminNotes(review.adminNotes());
        }

        TradeAssistantRequest saved = requestRepository.save(req);
        return mapToSubmissionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BuyRule> getBuyRules() {
        return buyRuleRepository.findAllByOrderByPriorityAsc();
    }

    @Transactional
    public BuyRule updateBuyRule(Long id, BuyRuleUpdateRequest update) {
        BuyRule rule = buyRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Buy rule not found with id: " + id));

        rule.setRate(update.rate());
        if (update.priority() != null) {
            rule.setPriority(update.priority());
        }
        if (update.active() != null) {
            rule.setActive(update.active());
        }

        return buyRuleRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public List<TradeParameter> getTradeParameters() {
        return parameterRepository.findAll();
    }

    @Transactional
    public TradeParameter updateTradeParameter(String key, TradeParameterUpdateRequest update) {
        TradeParameter param = parameterRepository.findByParamKey(key)
                .orElseThrow(() -> new ResourceNotFoundException("Trade parameter not found with key: " + key));

        param.setParamValue(update.paramValue());
        if (update.description() != null) {
            param.setDescription(update.description());
        }

        return parameterRepository.save(param);
    }

    @Transactional(readOnly = true)
    public List<CardLiquidity> getCardLiquidities() {
        return cardLiquidityRepository.findAll();
    }

    @Transactional
    public CardLiquidity upsertCardLiquidity(CardLiquidityRequest req) {
        CardLiquidity liquidity = cardLiquidityRepository.findByPokemontcgId(req.pokemontcgId())
                .orElseGet(() -> CardLiquidity.builder().pokemontcgId(req.pokemontcgId()).build());

        liquidity.setLiquidityTier(req.liquidityTier().trim().toUpperCase());
        liquidity.setHaircut(req.haircut());
        liquidity.setNotes(req.notes());

        return cardLiquidityRepository.save(liquidity);
    }

    @Transactional
    public void deleteCardLiquidity(String pokemontcgId) {
        cardLiquidityRepository.findByPokemontcgId(pokemontcgId)
                .ifPresent(cardLiquidityRepository::delete);
    }

    private CustomerCardItem enrichCustomerCard(CustomerCardItem item) {
        Optional<BigDecimal> resolvedPriceCad = cardPriceService.resolvePriceCad(
                item.cardId(),
                item.condition(),
                item.grading(),
                item.sealed()
        );

        String imageUrl = item.imageUrl();
        if ((imageUrl == null || imageUrl.isBlank()) && item.cardId() != null) {
            Optional<CardMarketPrice> fetched = priceProvider.fetchPrice(item.cardId());
            if (fetched.isPresent()) {
                imageUrl = fetched.get().imageUrl();
            }
        }

        return CustomerCardItem.builder()
                .pokemontcgId(item.cardId())
                .name(item.name())
                .set(item.set())
                .cardNumber(item.cardNumber())
                .condition(item.condition())
                .grading(item.grading())
                .isSealed(item.sealed())
                .marketPriceCad(resolvedPriceCad.orElse(null))
                .quantity(item.effectiveQuantity())
                .imageUrl(imageUrl)
                .build();
    }

    private String generateDeterministicExplanation(PricingResult result, TradeFlowType flowType) {
        if (result.decision() == TradeDecision.NEEDS_REVIEW) {
            return "Your submission requires an in-person or manual appraisal. Please submit it so our staff can verify card condition and pricing.";
        }

        if (flowType == TradeFlowType.SELL) {
            if (result.cashOffer() != null && result.cashOffer().compareTo(BigDecimal.ZERO) > 0) {
                return String.format("We can offer $%.2f CAD cash for your card(s) based on current market value.",
                        result.cashOffer());
            } else {
                return "We are currently unable to make a cash offer for these items.";
            }
        }

        // TRADE
        return switch (result.decision()) {
            case ACCEPT -> String.format(
                    "Trade accepted! Your trade credit of $%.2f CAD fully covers the store inventory value of $%.2f CAD.",
                    result.tradeCredit(), result.storeTotalListPriceCad()
            );
            case COUNTER -> String.format(
                    "We can accept this trade with a cash top-up of $%.2f CAD (trade credit $%.2f CAD applied towards $%.2f CAD).",
                    result.counterTopUp(), result.tradeCredit(), result.storeTotalListPriceCad()
            );
            case DECLINE -> "The current market disparity or lot consolidation ratio prevents us from accepting this trade.";
            default -> "Trade status under review.";
        };
    }

    private TradeSubmissionResponse mapToSubmissionResponse(TradeAssistantRequest req) {
        return TradeSubmissionResponse.builder()
                .id(req.getId())
                .referenceCode(req.getReferenceCode())
                .status(req.getStatus())
                .flowType(req.getFlowType())
                .decision(req.getDecision())
                .offeredAmount(req.getOfferedAmount())
                .counterTopUp(req.getCounterTopUp())
                .customerTotalMarketCad(req.getCustomerTotalMarketCad())
                .storeTotalListPriceCad(req.getStoreTotalListPriceCad())
                .customerName(req.getCustomerName())
                .customerEmail(req.getCustomerEmail())
                .explanation(req.getExplanation())
                .adminNotes(req.getAdminNotes())
                .createdAt(req.getCreatedAt())
                .build();
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            log.warn("Failed to serialize object to JSON: {}", ex.getMessage());
            return "{}";
        }
    }
}
