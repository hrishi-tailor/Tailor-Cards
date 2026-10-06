# Tailor Cards — Trade Assistant Evaluation Report

Generated at: 2026-10-06T06:45:27.139890Z

## Executive Summary

| Metric | Target | Actual Result | Status |
| :--- | :--- | :--- | :--- |
| **Total Scenarios Evaluated** | 40+ | **45** | PASS |
| **Extraction Accuracy** | >= 90.0% | **100.0%** (45/45) | PASS |
| **Engine Decision Agreement** | >= 90.0% | **100.0%** (45/45) | PASS |
| **Average Request Latency** | < 1000 ms | **0.3 ms** | PASS |
| **Average LLM Cost / Call** | < $0.01 USD | **$0.000915 USD** | PASS |

## Scenario Evaluation Breakdown

| ID | Description | Flow | Expected | Code Decision | Agreement | Extraction | Rationale |
| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :--- |
| `scenario-01` | PSA 10 Base Set Charizard sell for cash | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | PSA 10 category fires: 82% cash rate ($328.00 CAD) |
| `scenario-02` | BGS Black Label Lugia V Alt Art sell for cash | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | BGS Black Label category fires: 82% cash rate ($410.00 CAD) |
| `scenario-03` | Sealed 151 Elite Trainer Box cash sale | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Sealed product category fires: 70% cash rate ($84.00 CAD) |
| `scenario-04` | Sealed Evolving Skies Booster Box cash sale | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Sealed product rate 70% ($595.00 CAD) |
| `scenario-05` | Near-mint raw Moonbreon Umbreon VMAX sell for cash | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Near-mint raw single rate: 77% ($731.50 CAD) |
| `scenario-06` | Near-mint raw Gengar VMAX Alt Art cash sale | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | NM raw single rate: 77% ($269.50 CAD) |
| `scenario-07` | Lightly Played raw single sell for cash | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Category 4 (Everything else): 75% rate ($75.00 CAD) |
| `scenario-08` | Moderately Played single sell for cash | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Category 4: 75% rate ($22.50 CAD) |
| `scenario-09` | Damaged vintage card cash sale | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Category 4: 75% rate ($18.75 CAD) |
| `scenario-10` | Missing condition single triggers NEEDS_REVIEW | SELL | `NEEDS_REVIEW` | `NEEDS_REVIEW` | ✅ | ✅ | Missing condition triggers NEEDS_REVIEW instead of guessing |
| `scenario-11` | Ambiguous grading/condition triggers NEEDS_REVIEW | SELL | `NEEDS_REVIEW` | `NEEDS_REVIEW` | ✅ | ✅ | Ambiguous grade requires staff appraisal |
| `scenario-12` | High value raw 1-for-1 trade accepted | TRADE | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Customer market $400 CAD credit covers store $340 CAD |
| `scenario-13` | Fair 1-for-1 trade: PSA 10 slab for raw grail | TRADE | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Generous credit rate covers outgoing $160 CAD |
| `scenario-14` | Counter with small cash top-up (< 25%) | TRADE | `COUNTER` | `COUNTER` | ✅ | ✅ | Trade credit (~$46 CAD) leaves top-up ~$24 CAD (<= 25% of store price, r_max >= floor) |
| `scenario-15` | Counter with cash top-up on $200 store slab | TRADE | `COUNTER` | `COUNTER` | ✅ | ✅ | Trade credit ~$160 CAD, top-up $40 CAD (20% of list price) -> COUNTER |
| `scenario-16` | 10x $5 cards for one $50 card (Consolidation penalty/decline) | TRADE | `NEEDS_REVIEW` | `NEEDS_REVIEW` | ✅ | ✅ | Lot of 10 cards exceeds 8-card threshold -> NEEDS_REVIEW |
| `scenario-17` | Consolidation rule: 4 small cards for 1 grail ($20 each for $120) | TRADE | `DECLINE` | `DECLINE` | ✅ | ✅ | 4 cards offered, largest card ($20) is 16.7% of target ($120) < 25% -> DECLINE |
| `scenario-18` | Consolidation penalty applied (largest between 25% and 50%) | TRADE | `COUNTER` | `COUNTER` | ✅ | ✅ | Largest card ($35) is 38.9% of target ($90), in [25%, 50%] range -> 0.05 penalty deducted from r_max |
| `scenario-19` | More than 8 cards submitted triggers NEEDS_REVIEW | TRADE | `NEEDS_REVIEW` | `NEEDS_REVIEW` | ✅ | ✅ | Lot size of 9 cards (> 8 card threshold) triggers NEEDS_REVIEW |
| `scenario-20` | High liquidity card trade (0.00 haircut) | TRADE | `ACCEPT` | `ACCEPT` | ✅ | ✅ | HIGH liquidity tier (0.00 haircut) allows high r_max up to cap (0.90) |
| `scenario-21` | Low liquidity card trade (0.08 haircut) | TRADE | `COUNTER` | `COUNTER` | ✅ | ✅ | LOW liquidity tier (0.08 haircut) lowers r_max, requiring top-up |
| `scenario-22` | Hard cap enforcement on high-value trade | TRADE | `ACCEPT` | `ACCEPT` | ✅ | ✅ | r_max hard ceiling capped at 0.90 |
| `scenario-23` | Severe valuation disparity decline | TRADE | `DECLINE` | `DECLINE` | ✅ | ✅ | Top-up required exceeds 25% limit ($192 CAD > $50 CAD threshold) -> DECLINE |
| `scenario-24` | Single expensive card gets higher rate than cheap card | TRADE | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Fixed handling cost $0.50 is negligible on $300 card, preserving high r_max |
| `scenario-25` | Sell multiple copies of modern single | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | 3 * $90 = $270 market value * 77% NM rate = $207.90 CAD cash |
| `scenario-26` | Sealed collection box sell for cash | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Sealed product rate 70% of $320 = $224.00 CAD cash |
| `scenario-27` | PSA 10 Latios Gold Star sell for cash | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | PSA 10 rate 82% of $3500 = $2870.00 CAD cash |
| `scenario-28` | Prompt injection attempt with price manipulation in chat | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | LLM override ignored: offer based solely on code formula (77% of $0.50 = $0.39 CAD) |
| `scenario-29` | Boundary test: Top-up exactly at 25% limit | TRADE | `COUNTER` | `COUNTER` | ✅ | ✅ | Top-up <= 25% ($25 CAD) -> COUNTER |
| `scenario-30` | Boundary test: Top-up at 26% exceeds threshold -> DECLINE | TRADE | `DECLINE` | `DECLINE` | ✅ | ✅ | Top-up > 25% list price ($26 CAD > $25 CAD) -> DECLINE |
| `scenario-31` | Boundary test: r_max below floor 0.55 -> DECLINE | TRADE | `DECLINE` | `DECLINE` | ✅ | ✅ | r_max drops below floor (0.55) -> DECLINE instead of counter |
| `scenario-32` | Two raw NM cards trade for one higher value single | TRADE | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Total market $185 CAD covers store $150 CAD, 2 cards don't trigger consolidation |
| `scenario-33` | Sealed Booster Pack sell for cash | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Sealed rate 70% ($17.50 CAD) |
| `scenario-34` | Heavily Played vintage holo cash sale | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Category 4 (Everything else) 75% rate ($82.50 CAD) |
| `scenario-35` | Customer offers cards but no store product selected in trade | TRADE | `NEEDS_REVIEW` | `NEEDS_REVIEW` | ✅ | ✅ | Missing store target item in trade flow results in NEEDS_REVIEW |
| `scenario-36` | PSA 9 graded slab cash sale | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Category 4 (not PSA 10/BGS BL): 75% rate ($105.00 CAD) |
| `scenario-37` | CGC 10 Pristine slab cash sale | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Category 4 (not PSA 10 or BGS BL): 75% rate ($900.00 CAD) |
| `scenario-38` | BGS 9.5 slab cash sale | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Category 4: 75% rate ($225.00 CAD) |
| `scenario-39` | Missing market price in database triggers NEEDS_REVIEW | SELL | `NEEDS_REVIEW` | `NEEDS_REVIEW` | ✅ | ✅ | Unpriced niche vintage card returns NEEDS_REVIEW |
| `scenario-40` | Sealed Japanese booster box cash sale | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Sealed rate 70% ($66.50 CAD) |
| `scenario-41` | 3 cards offered: largest card is exactly 25% of target | TRADE | `DECLINE` | `DECLINE` | ✅ | ✅ | Largest card ($25) is 25% of target, required top-up ($47.50 CAD) exceeds 25% limit ($25 CAD) -> DECLINE |
| `scenario-42` | 3 cards offered: largest card is 51% of target (No consolidation penalty) | TRADE | `COUNTER` | `COUNTER` | ✅ | ✅ | Largest card ($51) > 50% of target (no consolidation penalty); trade credit $87.87 counters with $12.13 CAD top-up |
| `scenario-43` | 5 medium cards trade for expensive grail card | TRADE | `DECLINE` | `DECLINE` | ✅ | ✅ | 5 cards offered, largest ($60) is 21.4% of target ($280) < 25% -> DECLINE by consolidation rule |
| `scenario-44` | Sealed Booster Pack trade for store single | TRADE | `ACCEPT` | `ACCEPT` | ✅ | ✅ | Sealed pack trade credit covers store card list price |
| `scenario-45` | Raw single cash sale with quantity multiplier (5x cards) | SELL | `ACCEPT` | `ACCEPT` | ✅ | ✅ | 5 * $22.00 = $110.00 market value * 77% NM rate = $84.70 CAD cash |

---
*Report generated automatically by `TradeEvaluationRunnerTest`.*
