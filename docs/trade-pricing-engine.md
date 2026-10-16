# Trade pricing engine

> The customer-facing "Sell or Trade" page was replaced by the [AI buylist](ai-buylist.md) at `/sell` (old links redirect there). This deterministic engine and its `/api/trade-assistant` API are still in the codebase. The AI buylist uses the same `buy_rules` table, which now also has per-condition rates (see the README).

The first version of selling to the store: a chat where customers described the cards they wanted to **sell for cash** or **trade for store cards**, and a deterministic Java engine priced the deal from live market rates.

## The model never sets a price
> Plain Java code executes deterministic business rules and returns mathematical results. The LLM is restricted to two isolated responsibilities:
> 1. **Extraction**: Turning messy customer messages into structured JSON items (`name`, `set`, `condition`, `grading`, `sealed`, `quantity`) validated server-side.
> 2. **Explanation**: Translating pre-computed code results into a plain customer message without leaking confidential margins, walk-away numbers ($r_{\max}$), platform fees, or internal rules.
> 
> A customer who tries prompt injection cannot change an offer, because the price is computed in server-side Java and the model has no say in it.


## How the pricing model works

All rates, margins, thresholds, and liquidity tags live in database tables (`buy_rules`, `trade_parameters`, `card_liquidity`) seeded with sensible defaults and editable in real time by the store owner via secured admin endpoints.

### 1. Cash offers
Categories are evaluated sequentially; the **first match wins**:
1. **PSA 10 or BGS Black Label**: `82%` of market price
2. **Sealed product** (booster boxes, ETBs, collection boxes): `70%` of market price
3. **Near-mint raw single**: `77%` of market price
4. **Everything else** (Lightly Played, Moderately Played, other slabs): `75%` of market price

$$\text{Cash Offer} = \text{Market Price (CAD)} \times \text{Category Rate} \times \text{Quantity}$$

*If condition, grade, or sealed status is missing or ambiguous, the engine outputs `NEEDS_REVIEW` instead of guessing.*

### 2. Trades
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

### 3. Trade decisions
Given the required trade exchange ratio $r_{\text{needed}} = \frac{\text{Store List Price}}{\text{Customer Market Value}}$:
- **`ACCEPT`**: If $r_{\text{needed}} \le r_{\max}$.
- **`COUNTER`**: If $r_{\text{needed}} > r_{\max}$, the engine calculates the required cash top-up:
  $$\text{Top-Up} = \text{Store List Price} - (r_{\max} \cdot M_{\text{total}})$$
  If $\text{Top-Up} \le 25\%$ of store list price **AND** $r_{\max} \ge \text{floor}$ (`0.55`), counter with that top-up.
- **`DECLINE`**: Otherwise declined.
- *Opening offers sit a configurable 3 points below $r_{\max}$ (`0.03`) to leave negotiating room. $r_{\max}$ is the store's confidential walk-away number and is never shown to the customer.*

### 4. Lot consolidation
To protect against trading away high-value cards for piles of low-value bulk cards:
- Let $\text{largest}$ = customer's single most valuable card, and $\text{target}$ = total value of requested store cards.
- **If customer offers 3+ cards and $\text{largest} < 25\%$ of $\text{target}$**: **`DECLINE`**.
- **If $\text{largest}$ is between 25% and 50% of $\text{target}$**: subtract a penalty (`0.05`) from $r_{\max}$.
- **If customer offers $> 8$ cards total**: **`NEEDS_REVIEW`**.

### 5. Rule trace
Every result records a JSON rule trace showing each rule that changed the rate:
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


## Price data and exchange rates
- **Pluggable `PriceProvider`** (`app.price-provider`, default `pokemontcg`):
  - `pokemontcg` — **pokemontcg.io API v2**: Fetches live TCGplayer market prices (USD) and official card artwork. Works without `POKEMONTCG_API_KEY` (calls unauthenticated, subject to a lower rate limit); set the key to raise the limit.
  - `tcgdex` — **tcgdex.net API v2**: Key-less alternative provider with the same `PriceProvider` contract.
  - The active provider's name (`POKEMONTCG_IO` / `TCGDEX`) is persisted on every row in `price_snapshots.source`.
- **Bank of Canada Valet API**: Daily FX rate conversion (`FXUSDCAD`) cached in-memory with automatic stale fallback.
- **Nightly `@Scheduled` Job**: Daily midnight cron (`0 0 0 * * *`) snapshots all catalog cards into `price_snapshots`. Each card is fetched independently — a provider error or rate-limit response for one card is logged and skipped, it never aborts the rest of the run.
- **Database Overrides**: Graded slabs (PSA 10, BGS BL) and sealed products are managed via `manual_price_overrides` and admin endpoints. Unpriced cards return `NEEDS_REVIEW`.


## Model layer and guardrails

The Anthropic Messages API is called directly with Spring's `RestClient` (model from `ANTHROPIC_MODEL`, Claude Haiku 4.5 by default).

1. **Extraction Guard**:
   - Customer messages are sanitized and enclosed in `<customer_input>` tags.
   - Strict JSON Schema output (`name`, `set`, `cardNumber`, `condition`, `grade`, `sealed`, `quantity`).
   - Server-side validation drops any price or decision the model outputs; quantities are clamped to $[1, 100]$.
2. **Keeping internal numbers private**:
   - The explanation service provides only safe customer-facing figures (offer CAD, top-up CAD).
   - An interceptor verifies that internal parameters ($r_{\max}$, margins, fees, haircuts, cost basis) are never leaked. If detected, it immediately falls back to deterministic phrasing.
3. **Card Confirmation Checkpoint**:
   - Extracted items query `pokemontcg.io` to present authentic card artwork in the UI.
   - **Do not price an unconfirmed match**: The customer must confirm each card match before quote execution.
4. **Rate Limiting & Safety**:
   - Per-conversation sliding-window rate limiter (HTTP 429 after 20 req/min).
   - Max message length enforcement (2,000 chars, HTTP 400).
   - Request timeouts (10s) with graceful fallback to manual buylist submission (`/sell`).


## Scenario suite and backtesting

A 45-scenario suite checks the engine's decisions, and a backtester replays past trades from a CSV:

```bash
# 1. Run the 45-scenario evaluation harness
./evaluation/run_evaluation.sh
# Generated report is saved to evaluation/report.md

# 2. Replay real historical past trades from CSV
./evaluation/backtest.sh evaluation/past_trades.csv
```
