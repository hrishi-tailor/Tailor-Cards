package com.tailorcards.api.demo;

import com.tailorcards.api.entity.BuylistSubmission;
import com.tailorcards.api.entity.Category;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.entity.SubmissionMessage;
import com.tailorcards.api.entity.TradeAssistantRequest;
import com.tailorcards.api.repository.BuylistSubmissionRepository;
import com.tailorcards.api.repository.CategoryRepository;
import com.tailorcards.api.repository.ProductRepository;
import com.tailorcards.api.repository.SubmissionMessageRepository;
import com.tailorcards.api.repository.TradeAssistantRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BuylistSubmissionRepository buylistSubmissionRepository;
    private final SubmissionMessageRepository submissionMessageRepository;
    private final TradeAssistantRequestRepository tradeAssistantRequestRepository;
    private final Environment environment;

    @Value("${DEMO_MODE:${app.demo-mode:false}}")
    private boolean demoMode;

    @Override
    @Transactional
    public void run(String... args) {
        if (!demoMode) {
            log.info("Production profile / DEMO_MODE=false: Skipping demo dataset seeding.");
            return;
        }

        log.info("DEMO_MODE=true / 'demo' profile active: Seeding isolated evaluator demo dataset...");
        seedDemoProducts();
        seedDemoBuylistSubmissions();
        seedDemoTradeRequests();
        log.info("Evaluator demo dataset seeded successfully.");
    }

    private void seedDemoProducts() {
        Category singlesCat = categoryRepository.findByName("Singles")
                .orElseGet(() -> categoryRepository.save(Category.builder().name("Singles").description("Single cards").build()));

        List<Product> demoProducts = List.of(
                Product.builder()
                        .name("[DEMO] Charizard - Shadowless Holo (Base Set #4)")
                        .description("Demo Sandbox Product: Near-mint raw single with rich holographic foil.")
                        .price(new BigDecimal("1450.00"))
                        .costBasis(new BigDecimal("1100.00"))
                        .imageUrl("https://images.pokemontcg.io/base1/4_hires.png")
                        .stock(2)
                        .category(singlesCat)
                        .set("Base Set")
                        .cardNumber("4/102")
                        .condition("NEAR_MINT")
                        .grading("RAW")
                        .pokemontcgId("base1-4")
                        .status("AVAILABLE")
                        .build(),
                Product.builder()
                        .name("[DEMO] Blastoise - 1st Edition Holo (Base Set #2)")
                        .description("Demo Sandbox Product: Light play condition with clean front surface.")
                        .price(new BigDecimal("850.00"))
                        .costBasis(new BigDecimal("650.00"))
                        .imageUrl("https://images.pokemontcg.io/base1/2_hires.png")
                        .stock(1)
                        .category(singlesCat)
                        .set("Base Set")
                        .cardNumber("2/102")
                        .condition("LIGHT_PLAY")
                        .grading("RAW")
                        .pokemontcgId("base1-2")
                        .status("AVAILABLE")
                        .build(),
                Product.builder()
                        .name("[DEMO] Pikachu - Red Cheeks Promo")
                        .description("Demo Sandbox Product: Near-mint promotional card.")
                        .price(new BigDecimal("120.00"))
                        .costBasis(new BigDecimal("80.00"))
                        .imageUrl("https://images.pokemontcg.io/base1/58_hires.png")
                        .stock(3)
                        .category(singlesCat)
                        .set("Base Set")
                        .cardNumber("58/102")
                        .condition("NEAR_MINT")
                        .grading("RAW")
                        .pokemontcgId("base1-58")
                        .status("AVAILABLE")
                        .build()
        );

        for (Product dp : demoProducts) {
            boolean exists = productRepository.findByNameContainingIgnoreCase(dp.getName()).stream().findAny().isPresent();
            if (!exists) {
                productRepository.save(dp);
                log.info("Seeded demo product: {}", dp.getName());
            }
        }
    }

    private void seedDemoBuylistSubmissions() {
        if (buylistSubmissionRepository.existsByTrackingToken("DEMO-SUB-001")) {
            return;
        }

        BuylistSubmission sub1 = BuylistSubmission.builder()
                .trackingToken("DEMO-SUB-001")
                .customerName("Morgan Reed (Demo Evaluator)")
                .customerEmail("morgan.demo@example.com")
                .cardName("Charizard 1st Edition Shadowless")
                .cardSet("Base Set")
                .askingPrice(new BigDecimal("1250.00"))
                .status("UNDER_REVIEW")
                .additionalComments("Demo submission for evaluator queue testing.")
                .imageUrls(List.of("https://images.pokemontcg.io/base1/4_hires.png", "https://images.pokemontcg.io/base1/4.png"))
                .createdAt(Instant.now().minusSeconds(86400))
                .build();

        BuylistSubmission savedSub1 = buylistSubmissionRepository.save(sub1);

        submissionMessageRepository.save(SubmissionMessage.builder()
                .submission(savedSub1)
                .senderRole("CUSTOMER")
                .senderEmail("morgan.demo@example.com")
                .message("Submitted high-res scans of my Charizard. Centering is near 50/50.")
                .createdAt(Instant.now().minusSeconds(80000))
                .build());

        submissionMessageRepository.save(SubmissionMessage.builder()
                .submission(savedSub1)
                .senderRole("ADMIN")
                .senderEmail("admin@tailorcards.com")
                .message("Inspected under 250% optical loupe. Minor corner whitening on back bottom-right. Offer is $1,250 CAD.")
                .createdAt(Instant.now().minusSeconds(70000))
                .build());

        BuylistSubmission sub2 = BuylistSubmission.builder()
                .trackingToken("DEMO-SUB-002")
                .customerName("Taylor Brooks (Demo Evaluator)")
                .customerEmail("taylor.demo@example.com")
                .cardName("Blastoise Holo")
                .cardSet("Base Set")
                .askingPrice(new BigDecimal("420.00"))
                .status("ACCEPTED")
                .additionalComments("Demo submission: customer accepted appraisal quote.")
                .imageUrls(List.of("https://images.pokemontcg.io/base1/2_hires.png"))
                .createdAt(Instant.now().minusSeconds(172800))
                .build();
        buylistSubmissionRepository.save(sub2);

        BuylistSubmission sub3 = BuylistSubmission.builder()
                .trackingToken("DEMO-SUB-003")
                .customerName("Sam Chen (Demo Evaluator)")
                .customerEmail("sam.demo@example.com")
                .cardName("Gengar VMAX Alt Art")
                .cardSet("Fusion Strike")
                .askingPrice(new BigDecimal("290.00"))
                .status("PENDING")
                .additionalComments("New incoming demo appraisal submission.")
                .createdAt(Instant.now().minusSeconds(3600))
                .build();
        buylistSubmissionRepository.save(sub3);

        log.info("Seeded 3 demo buylist submissions (DEMO-SUB-001, DEMO-SUB-002, DEMO-SUB-003).");
    }

    private void seedDemoTradeRequests() {
        if (tradeAssistantRequestRepository.existsByReferenceCode("DEMO-TR-001")) {
            return;
        }

        TradeAssistantRequest req1 = TradeAssistantRequest.builder()
                .referenceCode("DEMO-TR-001")
                .flowType("TRADE")
                .status("PENDING_REVIEW")
                .decision("COUNTER")
                .customerName("Alex Hunter (Demo Evaluator)")
                .customerEmail("alex.trade.demo@example.com")
                .customerPhone("+1 (555) 876-5432")
                .offeredAmount(new BigDecimal("450.00"))
                .counterTopUp(new BigDecimal("35.00"))
                .customerTotalMarketCad(new BigDecimal("450.00"))
                .storeTotalListPriceCad(new BigDecimal("240.00"))
                .ruleTraceJson("[DEMO RULE TRACE] Base r_max: 0.86, Liquidity: -0.03, Consolidation: 0.00 -> Credit rate: 0.83. Cash top-up: $35.00 CAD")
                .customerCardsJson("[{\"name\":\"Gengar Holo\",\"set\":\"Fossil\",\"cardNumber\":\"5/62\",\"marketPriceCad\":110.00,\"quantity\":1},{\"name\":\"Dragonite Holo\",\"set\":\"Fossil\",\"cardNumber\":\"4/62\",\"marketPriceCad\":95.00,\"quantity\":1}]")
                .storeProductsJson("[{\"name\":\"Rayquaza VMAX Alt Art\",\"listPriceCad\":240.00,\"quantity\":1}]")
                .explanation("Great cards! Based on current market prices and trade parameters, I can offer an acceptance with a $35 CAD top-up.")
                .customerNotes("Looking to trade my Gengar and Dragonite for your Rayquaza VMAX.")
                .createdAt(Instant.now().minusSeconds(43200))
                .build();
        tradeAssistantRequestRepository.save(req1);

        TradeAssistantRequest req2 = TradeAssistantRequest.builder()
                .referenceCode("DEMO-TR-002")
                .flowType("SELL")
                .status("APPROVED")
                .decision("ACCEPT")
                .customerName("Casey Jordan (Demo Evaluator)")
                .customerEmail("casey.sell.demo@example.com")
                .customerPhone("+1 (555) 987-6543")
                .offeredAmount(new BigDecimal("385.00"))
                .counterTopUp(BigDecimal.ZERO)
                .customerTotalMarketCad(new BigDecimal("500.00"))
                .ruleTraceJson("[DEMO RULE TRACE] Category: Near-mint raw single (77% rate) -> Direct cash buyout offer: $385.00 CAD")
                .customerCardsJson("[{\"name\":\"Mewtwo GX Secret Rare\",\"set\":\"Shining Legends\",\"cardNumber\":\"78/73\",\"marketPriceCad\":500.00,\"quantity\":1}]")
                .explanation("We offer 77% of market price for Near Mint raw singles, which comes to $385.00 CAD.")
                .customerNotes("I have a Near Mint Mewtwo GX Secret Rare I want to sell.")
                .createdAt(Instant.now().minusSeconds(86400))
                .build();
        tradeAssistantRequestRepository.save(req2);

        log.info("Seeded 2 demo trade requests (DEMO-TR-001, DEMO-TR-002).");
    }
}
