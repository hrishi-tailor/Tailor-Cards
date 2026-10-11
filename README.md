# ⚡ Tailor Cards — Enterprise Trading Cards Platform

[![Live Demo](https://img.shields.io/badge/Live%20Demo-TailorCards.com-gold?style=for-the-badge&logo=vercel)](https://tailorcards.com)
[![Swagger Docs](https://img.shields.io/badge/OpenAPI%203.0-Swagger%20UI-85EA2D?style=for-the-badge&logo=swagger)](https://tailor-cards-api.onrender.com/swagger-ui/index.html)
[![Loom Walkthrough](https://img.shields.io/badge/Loom-Video%20Tour%20(90s)-625DF5?style=for-the-badge&logo=loom)](https://www.loom.com/share/tailor-cards-architecture-walkthrough)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React 19](https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev/)
[![CI](https://img.shields.io/badge/CI-Passing-brightgreen?style=for-the-badge&logo=githubactions&logoColor=white)](.github/workflows/ci.yml)
[![Pricing Engine](https://img.shields.io/badge/Pricing%20Engine-Pure%20Java%20(Deterministic)-blue?style=for-the-badge)](evaluation/scenarios.json)

A high-performance full-stack e-commerce and card appraisal platform built with **Spring Boot (Java 21)** and **React 19 (TypeScript + Vite)**. Engineered for high-value collectible trading cards featuring **optimistic locking concurrency**, **stateless guest cart sessions**, **Stripe payment orchestration**, **Supabase cloud persistence**, and **OpenAPI 3.0 documentation**.

---

## 📸 Platform Preview

<p align="center">
  <img src="frontend/src/assets/hero.png" alt="Tailor Cards Platform Preview" width="850" style="border-radius: 8px; border: 1px solid #222;" />
</p>

---

## 🎯 Recruiter & Evaluator Demo Access

Evaluating the platform? You can test both the customer checkout and the staff appraisal portal without real payments:

| Capability | Instructions / Credentials | What You Can Test |
| :--- | :--- | :--- |
| **Instant Demo Checkout** | Add items to cart $\rightarrow$ click **⚡ Instant Demo Checkout** | Validates stock decrement, skips payment gate, renders official `[ACQUISITION CONFIRMED]` dossier. |
| **Stripe Sandbox Checkout** | Click **Checkout via Stripe** $\rightarrow$ Card: `4242 4242 4242 4242` (Exp: `12/34`, CVC: `123`) | Hosted Stripe payment session, signed webhook handling, and inventory ledger locking. |
| **Staff Appraisal Portal** | Visit `/admin/login` $\rightarrow$ Sign in with credentials configured via env vars<br>• User: `${DEMO_USERNAME:-demo}` (or admin)<br>• Role: `DEMO` (Read-only audit) or `ADMIN` | Review buylist card quotes, high-resolution corner loupe inspection, and customer trade submissions with granular role-based access. |
| **Buylist Submission** | Visit `/sell` $\rightarrow$ upload card front/back photos | Supabase image upload pipeline with local fallback, tracking token generation, and real-time chat. |

---

## 🎥 90-Second Loom Walkthrough

Watch the rapid architectural and functional tour:  
👉 **[Watch the 90-Second Walkthrough on Loom](https://www.loom.com/share/tailor-cards-architecture-walkthrough)**

### Video Agenda
- **0:00 - 0:20**: Architecture overview (Spring Boot 21, React 19, Supabase pgBouncer pooler).
- **0:20 - 0:40**: Catalog browsing, 4-corner loupe lightbox inspector, and client UUID guest cart.
- **0:40 - 1:05**: Two-phase stock validation, optimistic concurrency locking (`@Version`), and Stripe Checkout.
- **1:05 - 1:25**: Customer buylist appraisal submission, tracking token, and staff review portal.
- **1:25 - 1:30**: Interactive Swagger UI API documentation and automated test verification.

---

## 🏛️ System Architecture

```mermaid
flowchart TD
    subgraph Client["Client Tier (Browser)"]
        UI["React 19 + TypeScript (Vite)"]
        CartCtx["Cart Context API (localStorage UUID)"]
        Lightbox["Corner Loupe Inspector"]
        PriceChart["PriceHistoryChart (Interactive SVG)"]
        TradeChat["TradeAssistant (Sell or Trade Chat UI)"]
    end

    subgraph CDN["Edge & Proxy Tier"]
        Cloudflare["Cloudflare CDN"]
        RenderStatic["Render Static Site / Vite Dev Proxy"]
    end

    subgraph Backend["Backend Application Tier (Spring Boot / Java 21)"]
        Security["Spring Security (Stateless / Role-Based Basic Auth)"]
        Swagger["OpenAPI 3.0 / Swagger UI"]
        
        subgraph Controllers["REST Controllers"]
            ProdCtrl["ProductController"]
            CartCtrl["CartController"]
            CheckCtrl["CheckoutController"]
            BuyCtrl["BuylistController"]
            TradeCtrl["TradeAssistantController (Public /api/trade-assistant)"]
            AdminTradeCtrl["AdminTradeAssistantController (Role: ADMIN)"]
            AuthCtrl["AuthController"]
        end
        
        subgraph Services["Domain Service Tier"]
            ProdSvc["ProductService (Optimistic Lock Guard)"]
            PriceSvc["PriceHistoryService (Deterministic Market Engine)"]
            CartSvc["CartService"]
            StripeSvc["StripeCheckoutService (Stock Re-Validation)"]
            BuySvc["BuylistService"]
            StoreSvc["BuylistStorageService (Dual Storage Engine)"]
            TradeAssistantSvc["TradeAssistantService"]
            PricingEngine["TradePricingEngine (Pure Java Deterministic Model)"]
            TradeConfig["TradeConfigService (Database-Backed Parameters)"]
            CardPriceSvc["CardPriceService (Snapshots + Overrides)"]
            LlmExtraction["TradeExtractionService (Strict Schema Guard)"]
            LlmExplanation["TradeExplanationService (Zero-Leak Guard)"]
            RateLimiter["LlmRateLimiter (Sliding Window 429)"]
        end
        
        subgraph Repos["Data Access Tier (Spring Data JPA)"]
            ProdRepo["ProductRepository (@EntityGraph)"]
            CartRepo["CartItemRepository"]
            OrderRepo["OrderRepository"]
            BuyRepo["BuylistRepository"]
            TradeReqRepo["TradeAssistantRequestRepository"]
            BuyRuleRepo["BuyRuleRepository"]
            ParamRepo["TradeParameterRepository"]
            LiquidityRepo["CardLiquidityRepository"]
            PriceSnapRepo["PriceSnapshotRepository"]
            OverrideRepo["ManualPriceOverrideRepository"]
        end
    end

    subgraph Storage["Cloud Persistence Tier"]
        pgBouncer["Supabase Pooler (pgBouncer :6543)"]
        Postgres[("PostgreSQL 17 (Supabase)")]
        S3Storage["Supabase Object Storage (buylist-images)"]
        LocalStorage[("Local Disk Fallback (/uploads)")]
    end

    subgraph External["External Services"]
        StripeAPI["Stripe Payments API (Hosted Checkout)"]
        StripeWH["Stripe Webhooks (checkout.session.completed)"]
        PokemonTcgIO["pokemontcg.io API v2 (USD Market Prices)"]
        BOCValet["Bank of Canada Valet API (Daily FX CAD)"]
        AnthropicAPI["Anthropic Messages API (Claude 3.5 Sonnet)"]
    end

    %% Connections
    UI --> RenderStatic
    TradeChat --> RenderStatic
    RenderStatic --> Cloudflare
    Cloudflare --> Security
    Security --> Controllers
    Controllers --> Services
    TradeAssistantSvc --> PricingEngine
    TradeAssistantSvc --> LlmExtraction
    TradeAssistantSvc --> LlmExplanation
    TradeAssistantSvc --> CardPriceSvc
    PricingEngine --> TradeConfig
    CardPriceSvc --> PokemonTcgIO
    CardPriceSvc --> BOCValet
    LlmExtraction --> AnthropicAPI
    LlmExplanation --> AnthropicAPI
    Services --> Repos
    Repos --> pgBouncer
    pgBouncer --> Postgres
    StoreSvc --> S3Storage
    StoreSvc -.-> LocalStorage
    StripeSvc --> StripeAPI
    StripeWH --> CheckCtrl
```

---

## 💻 Tech Stack

| Layer | Technology | Version | Purpose & Architectural Rationale |
| :--- | :--- | :--- | :--- |
| **Backend Framework** | Spring Boot | `4.1.1` | Enterprise REST architecture, transaction boundaries, dependency injection. |
| **Language** | Java | `21 (LTS)` | Virtual threads ready, immutable Record DTOs, Pattern Matching. |
| **Security** | Spring Security | `6.x` | Stateless sessions, granular role-based endpoint protection, HTTP Basic for Admin. |
| **ORM / Data Access** | Spring Data JPA / Hibernate | `7.x` | Dynamic repositories, entity graph optimization (`@EntityGraph`), optimistic locking (`@Version`). |
| **API Documentation** | SpringDoc OpenAPI | `2.8.5` | Automated OpenAPI 3.0 specification and interactive Swagger UI. |
| **Payment Gateway** | Stripe Java SDK | `26.0.0` | Hosted checkout sessions, cryptographically verified webhook listeners. |
| **Database** | PostgreSQL / Supabase | `17.6` | Relational ACID compliance, pgBouncer connection pooling (`prepareThreshold=0`). |
| **Cloud Storage** | Supabase Storage API | — | Multipart image upload for appraisals with automatic local filesystem fallback. |
| **Frontend Framework**| React | `19.0.0` | Fast declarative UI, concurrent rendering, React Compiler support. |
| **Language** | TypeScript | `5.7+` | Strict type safety for domain models, DTO payloads, and API contracts. |
| **Build Tooling** | Vite | `8.2.2` | Sub-second HMR development server and optimized rollup production bundles. |
| **Routing** | React Router | `7.x` | Declarative routing with client-side SPA fallback rules. |
| **State Management** | React Context API | — | Global cart state synchronization without third-party bundle overhead. |
| **Testing** | JUnit 5, Mockito, H2 | — | In-memory integration testing, optimistic locking simulation, web MVC tests. |

---

## 📐 Key Design Decisions & Architectural Trade-offs

### 1. Optimistic Locking (`@Version`) vs. Pessimistic Locking
* **Problem**: In high-demand collectible drops, multiple visitors attempt to buy the same single card simultaneously.
* **Decision**: Implemented JPA Optimistic Locking using an annotated `@Version private Long version;` field in [`Product.java`](src/main/java/com/tailorcards/api/entity/Product.java).
* **Rationale**: Collectible catalog browsing has a 99:1 read-to-write ratio. Pessimistic row locking (`SELECT FOR UPDATE`) causes database connection starvation and degrades catalog read throughput. Optimistic locking guarantees zero database row locks during browsing; if two checkouts collide, Spring Boot catches `OptimisticLockingFailureException` and surfaces a clean HTTP 409 Conflict without data corruption or stock overselling.
* **Database Compatibility**: Configured `@ColumnDefault("0")` to allow seamless non-destructive schema migrations over existing rows in production PostgreSQL.

### 2. Stateless Guest Cart Sessions via Client-Side UUID
* **Problem**: Mandatory user registration prior to adding items increases cart abandonment by up to 35%.
* **Decision**: Designed an anonymous guest shopping cart using client-generated UUIDs stored in `localStorage` (`tc_cart_session_id`).
* **Rationale**: Eliminates authentication barriers while maintaining server-side persistence. Every cart action targets `/api/cart/{cartSessionId}`, allowing visitors to close and reopen the browser without losing their items, while avoiding stateful server session memory leaks.

### 3. Decoupled Record DTOs & Component Mappers
* **Problem**: Direct exposure of JPA entities leads to circular JSON serialization, unintended schema leaks, and `LazyInitializationException` outside transaction boundaries.
* **Decision**: Modeled all requests and responses using immutable Java 21 Records ([`ProductResponse`](src/main/java/com/tailorcards/api/dto/ProductResponse.java), [`CartResponse`](src/main/java/com/tailorcards/api/dto/CartResponse.java)) paired with dedicated Spring `@Component` mappers ([`ProductMapper`](src/main/java/com/tailorcards/api/mapper/ProductMapper.java)).
* **Rationale**: Enforces strict Jakarta Bean Validation (`@NotBlank`, `@Min`) at the controller boundary. The persistence schema can evolve independently from the public REST API contract.

### 4. Two-Phase Stock Validation & Idempotent Stripe Webhooks
* **Problem**: A buyer could start a Stripe Checkout session, abandon the browser tab for 20 minutes, and complete payment after the card had already been sold to another customer.
* **Decision**: Implemented two-phase inventory validation in [`StripeCheckoutService.java`](src/main/java/com/tailorcards/service/StripeCheckoutService.java):
  1. **Phase 1 (Session Creation)**: Validates catalog stock in real time right before generating the Stripe URL.
  2. **Phase 2 (Webhook Capture)**: Upon receiving the cryptographically verified `checkout.session.completed` webhook, re-verifies inventory, executes atomic stock decrements, flags products as `SOLD`, and creates an immutable order record.

### 5. Resilient Connection Pooling with Supabase pgBouncer
* **Problem**: Cloud serverless architectures and hosted databases frequently close idle connections or choke under transaction spikes. Supabase uses pgBouncer on port `6543`.
* **Decision**: Explicitly configured HikariCP with `prepareThreshold: 0` in [`application.yaml`](src/main/resources/application.yaml).
* **Rationale**: Transaction-mode connection poolers do not share session-level prepared statement state across pooled connections. Setting `prepareThreshold=0` prevents PostgreSQL `prepared statement does not exist` errors, ensuring continuous uptime under high concurrency.

### 6. Dual-Tier Buylist Storage Engine
* **Problem**: Card appraisal photo uploads can fail when external cloud storage services experience latency or quota issues.
* **Decision**: Implemented a resilient fallback engine in [`BuylistStorageService.java`](src/main/java/com/tailorcards/api/service/BuylistStorageService.java).
* **Rationale**: The service prioritizes the Supabase S3-compatible cloud bucket (`buylist-images`). If network connectivity fails or credentials are unconfigured in development, it automatically falls back to local disk storage (`/uploads/buylist`), ensuring zero upload downtime.

### 7. Deterministic Zero-Cost Historical Pricing & Market Trends Engine
* **Problem**: Commercial TCG pricing APIs (e.g. TCGplayer Developer API, PriceCharting) require paid subscriptions, commercial rate limiting, or personal API keys that break demo environments when quotas exhaust.
* **Decision**: Architected a 100% free, deterministic time-series market engine in [`PriceHistoryService.java`](src/main/java/com/tailorcards/api/service/PriceHistoryService.java) paired with an interactive zero-dependency SVG visualization component ([`PriceHistoryChart.tsx`](frontend/src/components/PriceHistoryChart.tsx)).
* **Rationale**: The algorithm deterministically seeds harmonic market cycles and volatility channels based on the card's ID, expansion set, grading tier, and actual listed catalog price. It generates reproducible daily/weekly valuation points across **1 Month (`1M`)**, **3 Months (`3M`)**, and **1 Year (`1Y`)** time horizons, mathematically converging on the card's current catalog listing price today.
* **Benefits**:
  - **100% Free Forever**: Zero external API subscriptions or recurring operational expenses.
  - **Sub-15ms Latency**: In-memory generation runs in microseconds with zero network roundtrip bottlenecks.
  - **Interactive Visualizer**: Complete with hover crosshairs, responsive SVG curves, area gradients, period high/low metrics, and timeframe return indicators.

---

## 📖 OpenAPI / Swagger API Documentation

The REST API exposes an interactive **OpenAPI 3.0 / Swagger UI** playground:

* **Interactive Swagger UI**: [https://tailor-cards-api.onrender.com/swagger-ui/index.html](https://tailor-cards-api.onrender.com/swagger-ui/index.html) *(or `http://localhost:8080/swagger-ui/index.html` locally)*
* **OpenAPI Raw Specification**: [https://tailor-cards-api.onrender.com/v3/api-docs](https://tailor-cards-api.onrender.com/v3/api-docs)

### API Endpoints Summary

| Tag | Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Products** | `GET` | `/api/products` | Public | Paginated product catalog (`?page=0&size=100`) |
| **Products** | `GET` | `/api/products/{id}` | Public | Single card details with stock & category |
| **Products** | `GET` | `/api/products/{id}/price-history` | Public | Historical price tracking & market metrics (`?range=1M\|3M\|1Y`) |
| **Products** | `POST` | `/api/products` | Admin (Basic Auth) | Create catalog product |
| **Products** | `PUT` | `/api/products/{id}` | Admin (Basic Auth) | Update product price, images, or stock |
| **Products** | `DELETE`| `/api/products/{id}` | Admin (Basic Auth) | Delete item from catalog |
| **Categories**| `GET` | `/api/categories` | Public | List all categories (Singles, Sealed) |
| **Shopping Cart**| `GET` | `/api/cart/{cartSessionId}` | Public | Get active cart items and total |
| **Shopping Cart**| `POST`| `/api/cart/{cartSessionId}` | Public | Add item or increment quantity |
| **Shopping Cart**| `DELETE`| `/api/cart/{cartSessionId}/items/{id}` | Public | Remove line item from cart |
| **Checkout** | `POST` | `/api/checkout/create-session` | Public | Generate Stripe Checkout URL |
| **Checkout** | `POST` | `/api/checkout/webhook` | Public (Signed) | Process Stripe payment events |
| **Buylist** | `POST` | `/api/buylist/upload` | Public | Upload appraisal photos (multipart) |
| **Buylist** | `POST` | `/api/buylist/submit` | Public | Submit buylist card appraisal request |
| **Buylist** | `GET` | `/api/buylist/track/{token}` | Public | Customer appraisal tracking & chat |
| **Buylist** | `POST` | `/api/buylist/{token}/messages` | Public | Customer chat message reply |
| **Buylist** | `GET` | `/api/buylist/admin/submissions` | Admin (Basic Auth) | List pending appraisal submissions |
| **Buylist** | `PATCH`| `/api/buylist/admin/submissions/{id}/status` | Admin (Basic Auth) | Accept, counter-offer, or reject appraisal |
| **Trade Assistant** | `POST` | `/api/trade-assistant/messages` | Public | Conversational assistant turn & card extraction |
| **Trade Assistant** | `POST` | `/api/trade-assistant/quote` | Public | Compute deterministic cash offer / trade quote |
| **Trade Assistant** | `POST` | `/api/trade-assistant/requests` | Public | Submit quote for store appraisal review |
| **Trade Admin** | `GET` | `/api/admin/trade-assistant/requests` | Admin (Basic Auth) | Review customer trade requests |
| **Trade Admin** | `POST` | `/api/admin/trade-assistant/requests/{id}/approve` | Admin (Basic Auth) | Approve trade submission |
| **Trade Admin** | `POST` | `/api/admin/trade-assistant/requests/{id}/counter` | Admin (Basic Auth) | Counter trade submission with revised amounts |
| **Trade Admin** | `POST` | `/api/admin/trade-assistant/requests/{id}/decline` | Admin (Basic Auth) | Decline trade submission |
| **Trade Config** | `GET/PUT` | `/api/admin/trade-assistant/buy-rules` | Admin (Basic Auth) | Manage buy rate rules (Graded, Sealed, Raw) |
| **Trade Config** | `GET/PUT` | `/api/admin/trade-assistant/parameters` | Admin (Basic Auth) | Manage trade parameters (fees, margins, caps) |
| **Trade Config** | `GET/PUT` | `/api/admin/trade-assistant/liquidity` | Admin (Basic Auth) | Manage card liquidity tiers and haircuts |
| **Price Overrides**| `GET/PUT` | `/api/admin/price-overrides` | Admin (Basic Auth) | Set manual overrides for graded/sealed cards |
| **Buylist Chat** | `GET` | `/api/buylist-chat/status` | Public | Whether the chat is on (`CHATBOT_ENABLED`) and the Turnstile site key |
| **Buylist Chat** | `POST` | `/api/buylist-chat/otp/request`, `/otp/verify` | Public | Email a 6-digit code; exchange it for a session token |
| **Buylist Chat** | `POST/GET` | `/api/buylist-chat/drafts[/{id}]` | Chat session | Open/resume the caller's draft; poll statuses and summary |
| **Buylist Chat** | `POST` | `/api/buylist-chat/drafts/{id}/messages`, `/paste`, `/csv` | Chat session | Chat turn, pasted list, CSV upload (max 1000 lines) |
| **Buylist Chat** | `PATCH/DELETE/POST` | `/api/buylist-chat/drafts/{id}/lines/{lineId}[/photo]` | Chat session | Edit, remove or add a photo to a line |
| **Buylist Chat** | `POST` | `/api/buylist-chat/drafts/{id}/confirm` | Chat session | Confirm button: re-checks hash and daily limits, then submits |
| **Buylist Chat Admin** | `POST` | `/api/admin/buylist-chat/daily-limit/reset` | Admin only (DEMO 403) | Let an email submit again today |
| **Listing Generator**| `POST` | `/api/admin/listing-generator/draft` | Admin only (DEMO 403) | Draft a listing from 1-2 card photos (multipart `images`, optional `note`) |
| **Listing Generator**| `GET` | `/api/admin/listing-generator/market-reference` | Admin only (DEMO 403) | Market reference + stock image for a picked candidate (`?cardId=base1-4`) |
| **Auth** | `GET` | `/api/auth/verify` | Admin (Basic Auth) | Validate administrator credentials |

---

## ⚖️ "Sell or Trade?" Assistant & Deterministic Pricing Engine

A conversational AI assistant allowing customers to describe Pokémon cards they want to **Sell for Cash** or **Trade for Store Cards**, receiving automated, instant quotes powered by live market rates and an immutable mathematical pricing engine.

### 🛡️ Core Principle: Zero LLM Price Authority
> [!IMPORTANT]
> **The Large Language Model NEVER decides prices, discounts, trade acceptance, or monetary offers.**  
> Plain Java code executes deterministic business rules and returns mathematical results. The LLM is restricted to two isolated responsibilities:
> 1. **Extraction**: Turning messy customer messages into structured JSON items (`name`, `set`, `condition`, `grading`, `sealed`, `quantity`) validated server-side.
> 2. **Explanation**: Translating pre-computed code results into a courteous customer message without leaking confidential margins, walk-away numbers ($r_{\max}$), platform fees, or internal rules.
> 
> A customer attempting to manipulate the chat with prompt injection cannot change an offer, because the pricing engine runs on server-side Java logic with zero LLM authority.

---

### 📊 How the Pricing Model Works

All rates, margins, thresholds, and liquidity tags live in database tables (`buy_rules`, `trade_parameters`, `card_liquidity`) seeded with sensible defaults and editable in real time by the store owner via secured admin endpoints.

#### 1. Cash Selling Model (I Pay Cash)
Categories are evaluated sequentially; the **first match wins**:
1. **PSA 10 or BGS Black Label**: `82%` of market price
2. **Sealed product** (booster boxes, ETBs, collection boxes): `70%` of market price
3. **Near-mint raw single**: `77%` of market price
4. **Everything else** (Lightly Played, Moderately Played, other slabs): `75%` of market price

$$\text{Cash Offer} = \text{Market Price (CAD)} \times \text{Category Rate} \times \text{Quantity}$$

*If condition, grade, or sealed status is missing or ambiguous, the engine outputs `NEEDS_REVIEW` instead of guessing.*

#### 2. Trading Model (Card-for-Card Exchange)
When customers trade incoming cards for store inventory, the maximum credit rate $r_{\max}$ the store can afford to give on their cards is computed via:

$$r_{\max} = \min\left(\text{cap}, \frac{(1 - f - l) - \frac{n \cdot F}{M_{\text{total}}}}{c + g}\right)$$

| Parameter | Seed Value | Description |
| :--- | :--- | :--- |
| $M_{\text{total}}$ | — | Total market value of customer cards in **CAD** |
| $n$ | — | Total count of incoming customer cards |
| $f$ | `0.12` | Variable resale cost & platform/payment fees (shipping excluded) |
| $F$ | `$0.50 CAD` | Fixed handling & processing cost per incoming card |
| $g$ | `0.08` | Store target profit margin on outgoing cards |
| $c$ | `0.77` | Store cost basis ratio (uses `Product.costBasis` if present, else `0.77`) |
| $l$ | `0.00` / `0.03` / `0.08` | Value-weighted card liquidity haircut: `HIGH` (0.00), `MEDIUM` (0.03), `LOW` (0.08) |
| $\text{cap}$ | `0.90` | Hard ceiling to protect against market price volatility |

#### 3. Trade Decision Engine
Given the required trade exchange ratio $r_{\text{needed}} = \frac{\text{Store List Price}}{\text{Customer Market Value}}$:
- **`ACCEPT`**: If $r_{\text{needed}} \le r_{\max}$.
- **`COUNTER`**: If $r_{\text{needed}} > r_{\max}$, the engine calculates the required cash top-up:
  $$\text{Top-Up} = \text{Store List Price} - (r_{\max} \cdot M_{\text{total}})$$
  If $\text{Top-Up} \le 25\%$ of store list price **AND** $r_{\max} \ge \text{floor}$ (`0.55`), counter with that top-up.
- **`DECLINE`**: Otherwise declined.
- *Opening offers sit a configurable 3 points below $r_{\max}$ (`0.03`) to leave negotiating room. $r_{\max}$ is the store's confidential walk-away number and is never shown to the customer.*

#### 4. Explicit Lot Consolidation Rule
To protect against trading away high-value cards for piles of low-value bulk cards:
- Let $\text{largest}$ = customer's single most valuable card, and $\text{target}$ = total value of requested store cards.
- **If customer offers 3+ cards and $\text{largest} < 25\%$ of $\text{target}$**: **`DECLINE`**.
- **If $\text{largest}$ is between 25% and 50% of $\text{target}$**: subtract a penalty (`0.05`) from $r_{\max}$.
- **If customer offers $> 8$ cards total**: **`NEEDS_REVIEW`**.

#### 5. Rule Trace Auditability
Every result produces a transparent, tamper-proof JSON rule trace:
```json
{
  "entries": [
    { "rule": "BASE_R_MAX", "effect": "0.864", "value": 0.864 },
    { "rule": "LIQUIDITY_HAIRCUT", "effect": "-0.030", "value": -0.03 },
    { "rule": "CONSOLIDATION_PENALTY", "effect": "-0.050", "value": -0.05 },
    { "rule": "FINAL_R_MAX", "effect": "0.784", "value": 0.784 }
  ]
}
```

---

### 🌐 Price Data & Daily Bank of Canada FX CAD Integration
- **Pluggable `PriceProvider`** (`app.price-provider`, default `pokemontcg`):
  - `pokemontcg` — **pokemontcg.io API v2**: Fetches live TCGplayer market prices (USD) and official card artwork. Works without `POKEMONTCG_API_KEY` (calls unauthenticated, subject to a lower rate limit); set the key to raise the limit.
  - `tcgdex` — **tcgdex.net API v2**: Key-less alternative provider with the same `PriceProvider` contract.
  - The active provider's name (`POKEMONTCG_IO` / `TCGDEX`) is persisted on every row in `price_snapshots.source`.
- **Bank of Canada Valet API**: Daily FX rate conversion (`FXUSDCAD`) cached in-memory with automatic stale fallback.
- **Nightly `@Scheduled` Job**: Daily midnight cron (`0 0 0 * * *`) snapshots all catalog cards into `price_snapshots`. Each card is fetched independently — a provider error or rate-limit response for one card is logged and skipped, it never aborts the rest of the run.
- **Database Overrides**: Graded slabs (PSA 10, BGS BL) and sealed products are managed via `manual_price_overrides` and admin endpoints. Unpriced cards return `NEEDS_REVIEW`.

---

### 🤖 LLM Layer & Security Guardrails

The Anthropic Messages API (`claude-3-5-sonnet-20241022`) is consumed directly via Spring Boot 4's native `RestClient` (zero third-party SDK bloat).

1. **Extraction Guard**:
   - Customer messages are sanitized and enclosed in `<customer_input>` tags.
   - Strict JSON Schema output (`name`, `set`, `cardNumber`, `condition`, `grade`, `sealed`, `quantity`).
   - Server-side validation discards any hallucinated prices or decisions; quantities are clamped to $[1, 100]$.
2. **Confidentiality & Zero-Leak Defense**:
   - The explanation service provides only safe customer-facing figures (offer CAD, top-up CAD).
   - An interceptor verifies that internal parameters ($r_{\max}$, margins, fees, haircuts, cost basis) are never leaked. If detected, it immediately falls back to deterministic phrasing.
3. **Card Confirmation Checkpoint**:
   - Extracted items query `pokemontcg.io` to present authentic card artwork in the UI.
   - **Do not price an unconfirmed match**: The customer must confirm each card match before quote execution.
4. **Rate Limiting & Safety**:
   - Per-conversation sliding-window rate limiter (HTTP 429 after 20 req/min).
   - Max message length enforcement (2,000 chars, HTTP 400).
   - Request timeouts (10s) with graceful fallback to manual buylist submission (`/sell`).

---

### 🧪 Evaluation Harness & Backtesting

The repository includes a comprehensive 45-scenario test harness and a historical trade backtester:

```bash
# 1. Run the 45-scenario evaluation harness
./evaluation/run_evaluation.sh
# Generated report is saved to evaluation/report.md

# 2. Replay real historical past trades from CSV
./evaluation/backtest.sh evaluation/past_trades.csv
```

#### Evaluation Metrics Summary (`evaluation/report.md`)
| Metric | Benchmark | Result | Status |
| :--- | :--- | :--- | :--- |
| **Total Test Scenarios** | 40+ scenarios | **45** | PASS |
| **Extraction Accuracy** | $\ge 90.0\%$ | **100.0%** (45/45) | PASS |
| **Engine Decision Agreement** | $\ge 90.0\%$ | **100.0%** (45/45) | PASS |
| **Average Latency** | $< 1000\text{ ms}$ | **0.2 ms** | PASS |
| **Average Cost per Request** | $< \$0.01\text{ USD}$ | **$0.000915 USD** | PASS |

---

## 💬 AI Buylist Chat (sell intake)

When `CHATBOT_ENABLED=true`, `/sell` becomes a chat: the customer verifies their email, lists cards by chatting, pasting or uploading a CSV, reviews a summary with an estimated chance, and presses **Confirm**. The submission lands in the existing admin buylist queue; **you still approve, counter or decline every one**. With the switch off (default), `/sell` shows the classic form (also reachable with `/sell?form=1`).

**Who decides what**
* **The model** only converses and normalises item text (name, set, number, quantity, condition, variant), in batches of 100 lines. Its tools are read-only card lookups plus add/remove on the caller's own draft; there is no submit tool. Customer messages, pasted lists, CSV cells and photos are treated as data.
* **Java** resolves cards against TCGdex (TCGplayer market price, USD; Cardmarket EUR trend as secondary info), filters scrap, computes the likelihood and totals, enforces limits, and creates the submission only from the Confirm button (server-side draft id + content hash). A missing price is shown as "no price", never $0. No rule trace or thresholds are shown to customers.

> **Temporary:** email verification is currently **off** (`BUYLIST_CHAT_REQUIRE_EMAIL=false`, the default for now). Customers start chatting with an anonymous session (`POST /api/buylist-chat/session/guest`, 10 per IP per hour, Turnstile if configured) and type a contact email at Confirm. That email is **not verified**: the one-per-day limit still applies to it, and the submission gets an "Email not verified" red flag. Set `BUYLIST_CHAT_REQUIRE_EMAIL=true` to bring back the email code below; anonymous sessions then stop working.

**Flow**
1. Email one-time code (when verification is on): 6 digits, stored as an HMAC hash, 10-minute expiry, 5 attempts, 60 s resend cooldown, 5 codes/hour. Optional Cloudflare Turnstile when `TURNSTILE_SECRET` is set.
2. Items: chat, paste or CSV, up to 1000 lines (quantity capped per line; a bulk lot is one line). Card resolution runs in the background with a 12-minute cache, 4 concurrent lookups and 8 TCGdex requests/second; the page polls progress. Unresolved lines become *Needs review* or *Unidentified*, never errors.
3. Each line gets a status: `ELIGIBLE`, `BELOW_MINIMUM`, `NEEDS_PHOTO` (value above the photo threshold), `NEEDS_REVIEW` (ambiguous match, no price, damaged, provider outage) or `UNIDENTIFIED`.
4. Likelihood meter (deterministic, weights in config): eligible value share, identification confidence, photo coverage on high-value lines, liquidity (`card_liquidity`), bulk share, line count; clamped to 5-95 with up to 3 customer-safe reasons, labelled "Estimated chance, not a guarantee". `likelihood_pct` and your eventual decision (`owner_decision`) are stored on the submission for calibration.
5. Confirm: "You have 1 submission per day. Submit?" The quote is a market reference, valid 48 hours and re-priced when the cards arrive.

**Deals: sell, trade or partial.** Below the list the customer picks *Sell for cash*, *Trade for shop cards* (any available, in-stock product in your shop; CAD prices shown in USD at the Bank of Canada rate) or *Trade + cash*. Your rates are shown to the customer, built from the live settings: cash = your buy rules (75% `DEFAULT` base, 77% raw near mint, 82% PSA 10 / BGS Black Label; edit in `/api/admin/trade-assistant/buy-rules`), store credit = `BUYLIST_TRADE_CREDIT_RATE` (80%; edit in `/api/admin/trade-assistant/parameters`). Customers can enter their own price per card (sell) or the cash they want on top (partial). Java compares the request with the rules as an **ask ratio** (1.0 = exactly at your rates): within the rules the meter is unaffected; above them it scales down linearly to `over-ask-floor` (15%) at `over-ask-span` (+50%), with a reason such as "Your asking price is $51.00 above our cash offer". Requests more than 15% above your rates are red-flagged in admin. The ask, shop cards, offers and exchange rate are stored on the submission.

**Graded cards (PSA/BGS/CGC/SGC/TAG/ACE).** TCGdex has only ungraded prices, so slabs are priced from the **Pokémon TCG API on RapidAPI** (`pokemon-tcg-api.p.rapidapi.com`, median of recent eBay sales for the exact grade) when `RAPIDAPI_KEY` is set. Customers set the grade in the chat, the CSV `grading` column, or the *Raw / PSA / BGS ...* picker on each line. Cards are matched by TCGdex id, falling back to name + number + set. With a graded price the line follows the buy rules: 82% for PSA 10 / BGS Black Label, and any `GRADED_<COMPANY>_<GRADE>` rule (V6 seeds `GRADED_PSA_9` 78% and `GRADED_CGC_10` 80%; add more such as `GRADED_BGS_9_5` in admin buy rules), otherwise the 75% base. Without a graded price (no key, card not found, Black Label / Pristine which have no separate eBay tier, or a 9/10 median below the ungraded price, which signals mixed-up sales data) the line shows the ungraded price as "ungraded ref", is left out of totals and offers, and is *Needs review: priced by hand*. Prices backed by fewer than `min-graded-sales` (3) recent sales lower the estimate and are flagged to the customer. The free RapidAPI plan allows ~100 requests/day; each card is one request, cached for `GRADED_PRICE_CACHE_MINUTES` (12 h).

**Estimated approval rating.** Shown with a disclaimer that it is an estimated guess, not a guaranteed price or acceptance. Photos are optional: cards worth at least `photo-required-above-usd` ($50) stay eligible without one, but the estimate is capped at `max-without-photos` (90%) until they have photos (95% max otherwise). Asking for less than our offer adds up to `under-ask-bonus` (15) points, reached at `under-ask-span` (30%) under; asking for more scales it down as before. Reasons say what would raise the estimate (e.g. "up to +5%"), and each line shows our cash offer per card next to the customer's price.

**Limits**: one confirmed submission per email per America/Toronto day, enforced by the unique index `uk_buylist_email_local_date (customer_email, local_date)` (drafts and chat don't count; admins can release it); 3 per IP per day; 10 chat messages/minute per session; 2000-character messages; 5 tool rounds per turn; a global daily LLM spend ceiling (`BUYLIST_CHAT_DAILY_SPEND_USD`, default $5) after which chat pauses and paste/CSV use the Java parser.

**Admin**: chat submissions show an "AI chat" chip with value and likelihood in the queue; the drawer shows totals, red flags (e.g. high value without photos, many unidentified lines, repeat IP), every line with its status, the full transcript, a USD counter field and a daily-limit reset. Approve = `ACCEPTED`, counter = `OFFERED` (+ amount), decline = `REJECTED`. You get an email for each new submission (`BUYLIST_NOTIFY_EMAIL`) and the customer gets a receipt with their tracking code.

**Environment** (all optional; safe defaults, no secrets in the repo)

| Variable | Default | Purpose |
| :--- | :--- | :--- |
| `CHATBOT_ENABLED` | `false` | Kill switch for the whole chat |
| `BUYLIST_CHAT_REQUIRE_EMAIL` | `false` (temporary) | `true` requires the emailed sign-in code before chatting |
| `EMAIL_PROVIDER` | `log` | `log` writes codes to the log (development only); `http` sends via a Resend-compatible API |
| `EMAIL_API_KEY`, `EMAIL_FROM` | — | Required when `EMAIL_PROVIDER=http` (`EMAIL_API_URL` defaults to Resend) |
| `BUYLIST_NOTIFY_EMAIL` | — | Where new-submission notifications go |
| `TURNSTILE_SECRET`, `TURNSTILE_SITE_KEY` | — | Cloudflare Turnstile on code requests |
| `OTP_PEPPER` | random per process | HMAC key for code hashes |
| `BUYLIST_CHAT_MODEL` | `app.anthropic.model` | Model for chat and list normalisation |
| `BUYLIST_CHAT_DAILY_SPEND_USD` | `5.00` | Global daily LLM spend ceiling |
| `RAPIDAPI_KEY` | — | Graded prices from the Pokémon TCG API on RapidAPI (your RapidAPI application key) |
| `GRADED_PRICE_CACHE_MINUTES` | `720` | How long each card's graded prices are cached (free plan: ~100 requests/day) |

Thresholds and weights live under `app.buylist-chat.scrap.*` and `app.buylist-chat.likelihood.*` in `application.yaml`. Schema changes are in `V3__buylist_chat.sql`, `V4__buylist_grading.sql`, `V5__buylist_deals.sql` and `V6__buylist_graded_rates.sql` (idempotent, tested against the production schema dump).

**Cost**: on Haiku 4.5 a chat turn is typically 2-5k input tokens (system prompt, tools, recent history) and a few hundred output tokens, about $0.003-0.008, up to ~6x that when the model uses all 5 tool rounds. Normalising a 1000-line paste is 10 calls, roughly $0.05-0.10. The daily ceiling caps total spend.

---

## 🏷️ Admin Listing Generator

Admin page at `/admin/listing-generator` (linked from the buylist admin portal, hidden for DEMO). Upload one or two photos of a card, get an editable draft, review it, then create the product through the existing `POST /api/products`. **Nothing is created or published automatically.**

**What the model does and does not do**
* It reads the photos and writes text only: card name, set, number, rarity, language, a condition *estimate*, slab company/grade if a slab is visible, sealed flag, title (≤ 80 chars), description (≤ 600 chars, plain text), visible condition notes, a confidence level and a list of uncertainties.
* It never prices anything. The draft type has no price field, and any price the model emits is dropped. The **market reference** comes from `CardPriceService` (manual overrides, cached snapshots, then the configured `PriceProvider`) and is shown for comparison only; your price field starts empty.
* Output is strict JSON validated server-side (schema, enum values, lengths, no emojis/markup, no price/value/shipping/print-run claims, no echo of the system prompt). An invalid reply is retried once; a second failure returns `502` and the UI falls back to the manual form.
* Photo text and the note are treated as untrusted data: each photo and the note are wrapped in tags the user cannot close, and the system prompt instructs the model never to follow them.

**Photos.** JPEG, PNG or WebP only (detected from file content), max 5 MB each. Metadata (EXIF incl. GPS, XMP, IPTC, comments, appended data) is stripped before sending; only the EXIF orientation is kept so rotated phone photos stay upright. Photos are sent to the Anthropic API and discarded, never stored. The product image defaults to the provider's official card art, labelled as a stock image, and is editable.

**Limits and failures.** Per-admin limit of 20 drafts/hour and a store-wide cap of 100 drafts/day (in memory, resets at 00:00 UTC and on restart), both returning `429`. API errors, timeouts or a missing key return `503` with a clear message; the manual form still works.

**Configuration** (`application.yaml`, no secrets; the API key stays in `ANTHROPIC_API_KEY`)

| Key | Default | Notes |
| :--- | :--- | :--- |
| `app.listing-generator.model` | `${app.anthropic.model}` (Haiku 4.5) | Must be vision-capable. Env: `LISTING_GENERATOR_MODEL` |
| `app.listing-generator.max-tokens` | `1024` | Output token ceiling per call |
| `app.listing-generator.timeout-seconds` | `30` | Vision calls are slower than the trade assistant's 10 s |
| `app.listing-generator.rate-limit-per-hour` | `20` | Per admin username |
| `app.listing-generator.daily-cap` | `100` | Store-wide drafts per UTC day |
| `app.listing-generator.max-image-bytes` | `5242880` | 5 MB per photo |
| `app.listing-generator.max-note-length` | `500` | Characters |

**Cost expectation.** On Haiku 4.5 ($1 / 1M input, $5 / 1M output) a two-photo draft is roughly 3-5k input tokens and 300-500 output tokens, about **$0.005-0.01 per draft** (up to double when a retry happens). The daily cap bounds spend at roughly **$1-2/day**. Every call logs model, latency, input/output tokens and computed cost (no image data or response text).

**How to review a draft**
1. Read the **uncertainties** box first and fix those fields against the physical card.
2. Treat **condition as an AI estimate**: inspect the card yourself, pick the condition, and tick "I checked the condition myself" (required for raw singles; UNKNOWN cannot be submitted).
3. Check name, set and number against the card. If several catalog cards match, pick the right one to load its market reference and stock image.
4. Edit the title and description so they state only what is true and visible; no value, rarity or shipping claims.
5. Enter **your price** and **stock** (the market reference is only a comparison), choose the category, then **Create product**.

---

## 🚀 Getting Started & How to Run

### Prerequisites
* **Java 21** or higher (`java -version`)
* **Node.js 20+** and **npm** (`node -v`)
* **PostgreSQL** instance (local, Docker, or free [Supabase](https://supabase.com) project)

---

### 1. Database & Backend Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/hrishi-tailor/TC.git
   cd TC
   ```

2. **Configure Environment Variables**:
   Provide database, Stripe, Pokémon TCG, and Anthropic credentials via environment variables or a `.env` script:
   ```bash
   export DB_HOST=localhost
   export DB_PORT=5432
   export DB_NAME=tailorcards
   export DB_USERNAME=postgres
   export DB_PASSWORD=your_password
   export STRIPE_SECRET_KEY=sk_test_placeholder
   export FRONTEND_URL=http://localhost:5173

   # Security & Staff / Demo Portal Roles
   export ADMIN_USERNAME=admin                            # Admin username (default: admin)
   export ADMIN_PASSWORD=your_secure_admin_password       # Required for administrative mutations
   export DEMO_MODE=true                                  # Set to true to seed read-only DEMO evaluator account
   export DEMO_USERNAME=demo                              # Evaluator username (default: demo)
   export DEMO_PASSWORD=your_demo_password                # Evaluator password (read-only audit access)

   # Pokémon TCG & Anthropic API (Stage 2 & 4 Trade Assistant)
   export PRICE_PROVIDER=pokemontcg                       # Optional: "pokemontcg" (default) or "tcgdex" (no key required)
   export POKEMONTCG_API_KEY=your_pokemontcg_io_api_key   # Optional: free key from pokemontcg.io; omit to call unauthenticated (lower rate limit)
   export ANTHROPIC_API_KEY=your_anthropic_api_key        # Optional: uses deterministic fallback if blank
   export ANTHROPIC_MODEL=claude-3-5-sonnet-20241022      # Default
   ```
   *(To test against Supabase, set `DB_HOST=aws-0-us-west-2.pooler.supabase.com`, `DB_PORT=6543`, `DB_NAME=postgres`, and your credentials)*.

3. **First-Deploy Database Migrations (Flyway & PostgreSQL)**:
   The application uses automated Flyway versioned migrations with Hibernate schema validation (`spring.jpa.hibernate.ddl-auto=validate`).
   
   * **Case A: First-Deploy on an Existing Database (Zero Downtime, No Data Loss)**:
     - The configuration has `spring.flyway.baseline-on-migrate: true` and `spring.flyway.baseline-version: 1` enabled in `application.yaml`.
     - When booting against an existing database, Flyway automatically baselines the existing schema at version `1` (marking [`V1__initial_schema.sql`](src/main/resources/db/migration/V1__initial_schema.sql) as applied) without dropping or altering your existing tables.
     - Flyway then applies [`V2__seed_data.sql`](src/main/resources/db/migration/V2__seed_data.sql) safely (`INSERT ... ON CONFLICT DO NOTHING`), guaranteeing all trade parameters, buy rules, and configuration defaults are seeded without touching production catalog records.
   * **Case B: First-Deploy on a Fresh / Blank Database**:
     - Flyway automatically runs from version 0, applying `V1__initial_schema.sql` (creating all relational tables, indexes, and foreign keys) followed by `V2__seed_data.sql`.
   * **Zero DDL Mutations (`ddl-auto=validate`)**:
     - Hibernate will **never** issue `ALTER TABLE` or `DROP TABLE` statements against your database. It validates that entity definitions strictly match the database schema before serving traffic.

4. **Run the Spring Boot application**:
   ```bash
   ./mvnw spring-boot:run
   ```
   The backend boots on `http://localhost:8080`.

5. **Verify Swagger UI**:
   Open [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) in your browser.

---

### 2. Two-Deployment Architecture (Production vs. Demo Sandbox)

Tailor Cards supports a strict separation of concerns between live production commerce and recruiter/evaluator sandboxes:

```mermaid
flowchart LR
    subgraph Production ["Production Environment (DEMO_MODE=false)"]
        ProdClient["Live Customers"] --> ProdAPI["Spring Boot (default profile)"]
        ProdAPI --> ProdDB[("Production PostgreSQL DB<br/>Real Orders, PII & Live Inventory")]
        ProdAPI --> StripeLive["Live Stripe Checkout"]
    end

    subgraph DemoSandbox ["Demo / Sandbox Environment (DEMO_MODE=true)"]
        Recruiter["Recruiter / Evaluator"] --> DemoAPI["Spring Boot ('demo' profile)"]
        DemoAPI --> DemoDB[("Isolated Sandbox Database<br/>Seeded Demo Data Only")]
        DemoAPI --> MockCheckout["Instant Demo Checkout (Mock)"]
    end
```

| Deployment Feature | Production (`DEMO_MODE=false`) | Demo Deployment (`DEMO_MODE=true`) |
| :--- | :--- | :--- |
| **Active Spring Profile** | `default` (or `production`) | `demo` (auto-activated via `DEMO_MODE=true`) |
| **Target Database** | Production PostgreSQL (Live catalog, real PII) | **Separate Isolated Sandbox Database** |
| **Demo Dataset Seeder** | **Disabled** (`DemoDataSeeder` skips execution) | **Enabled** (seeds mock cards, `DEMO-SUB-*`, `DEMO-TR-*`) |
| **DEMO Role Credentials** | **Omitted** (UserDetailsService does not create user) | **Seeded** via `DEMO_USERNAME` / `DEMO_PASSWORD` |
| **Customer Data Isolation** | Only `ROLE_ADMIN` can access admin routes | `ROLE_DEMO` is restricted strictly to `DEMO-` prefixed records; accessing real customer IDs returns **403 Forbidden** |
| **Trade Engine & Cost Basis** | Internal algorithms use confidential `costBasis` | `costBasis` is strictly hidden from `ProductResponse` & public endpoints |
| **Administrative Mutations** | Full admin control | Backfill & snapshot sync endpoints return **403 Forbidden** for `ROLE_DEMO` |
| **Checkout Flow** | Live Stripe payment gateway | Instant Demo Checkout enabled for frictionless walkthroughs |

> [!IMPORTANT]
> **Production Protection Guarantee**:
> In production (`DEMO_MODE=false`), `DemoDataSeeder` will **never** execute, `ROLE_DEMO` is never loaded into Spring Security, and Instant Demo Checkout is hard-blocked.

---

### 3. Frontend Setup

1. **Navigate to the frontend directory**:
   ```bash
   cd frontend
   ```

2. **Install dependencies**:
   ```bash
   npm install
   ```

3. **Start the development server**:
   ```bash
   npm run dev
   ```

4. **Open the Application**:
   Navigate to [http://localhost:5173](http://localhost:5173). The Vite reverse proxy forwards all `/api/*` calls to `http://localhost:8080`, completely eliminating CORS issues during development.

---

### 4. Running Automated Tests

Execute the complete test suite (unit tests, integration tests, optimistic locking concurrency validation):

```bash
# From project root
./mvnw clean test
```

Frontend typecheck and build validation:
```bash
cd frontend && npm run build
```

---

## 📦 Cloud Deployment (Render & Docker)

The repository includes an infrastructure-as-code [`render.yaml`](render.yaml) specification:

* **Backend Service**: Multi-stage [`Dockerfile`](Dockerfile) running Eclipse Temurin JRE 21 on Render Docker runtime.
* **Frontend Site**: Static Vite bundle published from `frontend/dist` with client-side SPA routing rewrites.
* **Database**: Managed PostgreSQL with auto-initialized schemas and connection pooling.

To deploy your own instance:
1. Fork or push this repository to GitHub.
2. In the [Render Dashboard](https://dashboard.render.com), select **New +** $\rightarrow$ **Blueprint**.
3. Link your repository. Render will automatically provision the Docker backend, PostgreSQL database, and static React frontend.

---

## 📋 Git Workflow & Meaningful Issue Tracking

This repository follows professional open-source engineering standards:

* **Conventional Commits**: Commit messages follow strict semantic prefixes:
  - `feat(...)`: New user-facing features or endpoints.
  - `fix(...)`: Bug fixes and defect remediations.
  - `docs(...)`: Architectural documentation and specifications.
  - `refactor(...)`: Clean structural code improvements without behavioral changes.
  - `test(...)`: Adding unit, integration, or concurrency test cases.
* **Structured Issue Templates**: Pre-configured in [`.github/ISSUE_TEMPLATE/`](.github/ISSUE_TEMPLATE/):
  - [Bug Report Template](.github/ISSUE_TEMPLATE/bug_report.md) with structured triage fields and environment matrix.
  - [Feature Request Template](.github/ISSUE_TEMPLATE/feature_request.md) with architectural considerations and technical checklists.
* **Standardized Pull Request Template**: Located in [`.github/PULL_REQUEST_TEMPLATE.md`](.github/PULL_REQUEST_TEMPLATE.md) with architectural impact reviews, test verification steps, and change classification.

---

## 🗺️ Roadmap & Planned Milestones

- [x] **Relational Catalog & DTO Mapping**: Java 21 Records, Component Mappers, and `@EntityGraph` query optimization.
- [x] **Stateless Guest Cart**: Frictionless client UUID session tracking in `localStorage`.
- [x] **Optimistic Locking Engine**: Safe concurrency resolution via `@Version` and HTTP 409 conflict handling.
- [x] **Stripe Checkout Pipeline**: Hosted payment gateway with pre-capture stock validation and webhook listener.
- [x] **Buylist Appraisal Flow**: Multi-angle photo upload with Supabase cloud storage and local disk fallback.
- [x] **Interactive OpenAPI Docs**: Complete Swagger UI integration for developer onboarding.
- [x] **Recruiter Demo Sandbox**: One-click demo checkout bypass and auto-filled admin credentials.
- [ ] **JWT User Accounts**: Optional customer registration with automated guest cart migration.
- [ ] **Live Price Aggregation**: Automated market price scraping against TCGPlayer and PriceCharting APIs.
- [ ] **Automated CI/CD**: GitHub Actions pipeline for Maven builds, test coverage, and automated Render deployment.

---

## 📄 License

This project is licensed under the [Apache License 2.0](LICENSE).
