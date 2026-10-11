package com.tailorcards.api.trade.service;

import com.tailorcards.api.entity.BuyRule;
import com.tailorcards.api.entity.CardLiquidity;
import com.tailorcards.api.entity.TradeParameter;
import com.tailorcards.api.repository.BuyRuleRepository;
import com.tailorcards.api.repository.CardLiquidityRepository;
import com.tailorcards.api.repository.TradeParameterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional(readOnly = true)
public class TradeConfigService {

    public static final String PARAM_VARIABLE_RESALE_FEE = "VARIABLE_RESALE_FEE_FRACTION";
    public static final String PARAM_FIXED_HANDLING_FEE = "FIXED_HANDLING_FEE_PER_CARD";
    public static final String PARAM_TARGET_PROFIT_MARGIN = "TARGET_PROFIT_MARGIN";
    public static final String PARAM_DEFAULT_COST_BASIS_RATIO = "DEFAULT_COST_BASIS_RATIO";
    public static final String PARAM_HARD_CAP_RATE = "HARD_CAP_RATE";
    public static final String PARAM_COUNTER_FLOOR_RATE = "COUNTER_FLOOR_RATE";
    public static final String PARAM_COUNTER_MAX_TOPUP_RATIO = "COUNTER_MAX_TOPUP_RATIO";
    public static final String PARAM_OPENING_OFFER_DISCOUNT = "OPENING_OFFER_DISCOUNT";
    public static final String PARAM_CONSOLIDATION_THRESHOLD_COUNT = "CONSOLIDATION_THRESHOLD_COUNT";
    public static final String PARAM_CONSOLIDATION_MIN_LARGEST_RATIO = "CONSOLIDATION_MIN_LARGEST_RATIO";
    public static final String PARAM_CONSOLIDATION_PENALTY_THRESHOLD = "CONSOLIDATION_PENALTY_THRESHOLD";
    public static final String PARAM_CONSOLIDATION_PENALTY = "CONSOLIDATION_PENALTY";
    public static final String PARAM_MAX_CUSTOMER_CARDS = "MAX_CUSTOMER_CARDS_THRESHOLD";
    public static final String PARAM_HAIRCUT_HIGH = "DEFAULT_LIQUIDITY_HAIRCUT_HIGH";
    public static final String PARAM_HAIRCUT_MEDIUM = "DEFAULT_LIQUIDITY_HAIRCUT_MEDIUM";
    public static final String PARAM_HAIRCUT_LOW = "DEFAULT_LIQUIDITY_HAIRCUT_LOW";
    /** Buylist chat: store credit as a fraction of market value (cash rates come from buy rules). */
    public static final String PARAM_BUYLIST_TRADE_CREDIT_RATE = "BUYLIST_TRADE_CREDIT_RATE";

    private static final Map<String, BigDecimal> DEFAULTS = Map.ofEntries(
            Map.entry(PARAM_VARIABLE_RESALE_FEE, BigDecimal.valueOf(0.12)),
            Map.entry(PARAM_FIXED_HANDLING_FEE, BigDecimal.valueOf(0.50)),
            Map.entry(PARAM_TARGET_PROFIT_MARGIN, BigDecimal.valueOf(0.08)),
            Map.entry(PARAM_DEFAULT_COST_BASIS_RATIO, BigDecimal.valueOf(0.77)),
            Map.entry(PARAM_HARD_CAP_RATE, BigDecimal.valueOf(0.90)),
            Map.entry(PARAM_COUNTER_FLOOR_RATE, BigDecimal.valueOf(0.55)),
            Map.entry(PARAM_COUNTER_MAX_TOPUP_RATIO, BigDecimal.valueOf(0.25)),
            Map.entry(PARAM_OPENING_OFFER_DISCOUNT, BigDecimal.valueOf(0.03)),
            Map.entry(PARAM_CONSOLIDATION_THRESHOLD_COUNT, BigDecimal.valueOf(3)),
            Map.entry(PARAM_CONSOLIDATION_MIN_LARGEST_RATIO, BigDecimal.valueOf(0.25)),
            Map.entry(PARAM_CONSOLIDATION_PENALTY_THRESHOLD, BigDecimal.valueOf(0.50)),
            Map.entry(PARAM_CONSOLIDATION_PENALTY, BigDecimal.valueOf(0.05)),
            Map.entry(PARAM_MAX_CUSTOMER_CARDS, BigDecimal.valueOf(8)),
            Map.entry(PARAM_HAIRCUT_HIGH, BigDecimal.valueOf(0.00)),
            Map.entry(PARAM_HAIRCUT_MEDIUM, BigDecimal.valueOf(0.03)),
            Map.entry(PARAM_HAIRCUT_LOW, BigDecimal.valueOf(0.08)),
            Map.entry(PARAM_BUYLIST_TRADE_CREDIT_RATE, BigDecimal.valueOf(0.80))
    );

    private final TradeParameterRepository tradeParameterRepository;
    private final BuyRuleRepository buyRuleRepository;
    private final CardLiquidityRepository cardLiquidityRepository;

    public TradeConfigService(
            TradeParameterRepository tradeParameterRepository,
            BuyRuleRepository buyRuleRepository,
            CardLiquidityRepository cardLiquidityRepository
    ) {
        this.tradeParameterRepository = tradeParameterRepository;
        this.buyRuleRepository = buyRuleRepository;
        this.cardLiquidityRepository = cardLiquidityRepository;
    }

    public BigDecimal getParameter(String key) {
        return tradeParameterRepository.findByParamKey(key)
                .map(TradeParameter::getParamValue)
                .orElse(DEFAULTS.getOrDefault(key, BigDecimal.ZERO));
    }

    public List<BuyRule> getActiveBuyRules() {
        List<BuyRule> rules = buyRuleRepository.findByActiveTrueOrderByPriorityAsc();
        if (rules.isEmpty()) {
            return List.of(
                    BuyRule.builder().priority(1).categoryCode("PSA10_BGS_BLACK_LABEL").displayName("PSA 10 or BGS Black Label").rate(BigDecimal.valueOf(0.82)).active(true).build(),
                    BuyRule.builder().priority(1).categoryCode("GRADED_PSA_9").displayName("PSA 9").rate(BigDecimal.valueOf(0.78)).active(true).build(),
                    BuyRule.builder().priority(1).categoryCode("GRADED_CGC_10").displayName("CGC 10").rate(BigDecimal.valueOf(0.80)).active(true).build(),
                    BuyRule.builder().priority(2).categoryCode("SEALED").displayName("Sealed Product").rate(BigDecimal.valueOf(0.70)).active(true).build(),
                    BuyRule.builder().priority(3).categoryCode("RAW_NEAR_MINT").displayName("Near-Mint Raw Single").rate(BigDecimal.valueOf(0.77)).active(true).build(),
                    BuyRule.builder().priority(4).categoryCode("DEFAULT").displayName("Everything Else").rate(BigDecimal.valueOf(0.75)).active(true).build()
            );
        }
        return rules;
    }

    public BigDecimal getCardLiquidityHaircut(String pokemontcgId) {
        if (pokemontcgId != null && !pokemontcgId.isBlank()) {
            var found = cardLiquidityRepository.findByPokemontcgId(pokemontcgId.trim());
            if (found.isPresent()) {
                return found.get().getHaircut();
            }
        }
        // Default to MEDIUM liquidity haircut
        return getParameter(PARAM_HAIRCUT_MEDIUM);
    }
}
