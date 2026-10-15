# AI buylist and card scanner

When `CHATBOT_ENABLED=true`, `/sell` is a chat where customers list the cards they want to sell or trade, see the store's offer, and submit. Every submission lands in the admin buylist queue and is approved, countered or declined by a person. With the switch off, `/sell` shows the classic form (also at `/sell?form=1`).

## Who decides what

- **The model** converses, normalises item text (name, set, number, quantity, condition, variant, grade) and reads card photos. Its tools are read-only card and product lookups plus add and remove on the customer's own draft. There is no submit tool.
- **Java** resolves cards against TCGdex, prices them, filters what the store doesn't buy, computes offers and the approval estimate, enforces limits, and creates the submission only from the Confirm button (server-side draft id plus a content hash, so the list can't change between review and submit).

Customer messages, pasted lists, CSV cells, card text and photo contents are wrapped as untrusted data. A missing price is shown as "no price", never $0. Rule traces and thresholds are never shown to customers.

## The customer flow

1. **Start.** An anonymous session (10 per IP per hour, Cloudflare Turnstile if configured) and the customer types a contact email at Confirm. With `BUYLIST_CHAT_REQUIRE_EMAIL=true` they verify a 6-digit email code first (HMAC-hashed, 10-minute expiry, 5 attempts, 60 s resend cooldown, 5 codes per hour).
2. **Add cards.** Type or paste into the chat (a list longer than one message is parsed line by line), or use the **+** menu: *Scan a card*, *Upload a card* (a photo) or *Upload CSV* (up to 1000 lines). Resolution runs in the background and the page shows progress.
3. **Review.** Each line gets a status: `ELIGIBLE`, `BELOW_MINIMUM`, `NEEDS_PHOTO`, `NEEDS_REVIEW` (ambiguous match, no price, provider outage) or `UNIDENTIFIED`. Customers can change quantity, condition, grade and their asking price per line.
4. **Choose a deal.** Sell for cash, trade for in-stock shop cards, or trade plus cash.
5. **Confirm.** One submission per email per day. The quote is a market reference, valid 48 hours and re-checked when the cards arrive.

## Card scanner

The scanner lives in the chat's **+** menu.

**In the browser** ([`cardQuality.ts`](../frontend/src/components/cardQuality.ts)), about eight times a second on a small grayscale copy of the camera frame:
- **Find the card.** Each of the four edges is found by walking inwards from outside the on-screen guide in eight segments and fitting a line through the segments that agree, so smaller, offset or slightly tilted cards and busy backgrounds still work.
- **Check the shot** on the card itself: enough light, not washed out, no large glare patch, and in focus (Laplacian variance after a 3x3 smooth, divided by the card's contrast, so sensor noise and dim light don't skew it).
- The guide is white with no card, amber when a card is found but needs adjusting (with a tip such as "Glare on the card: tilt it slightly away from the light"), and green when the shot is good. The photo is taken on that frame, cropped to the card, and resized to at most 1600 px.
- An optional back photo follows, since whitening shows mostly on the back. Uploaded photos get the same light, glare and focus checks.

**Phone handoff.** Phone cameras focus on cards far better than webcams, so on a computer the scanner leads with a QR code. It opens `/sell/scan/<token>` on the phone, which runs the same scanner; every card confirmed there is added to the list open on the computer, which polls and shows "Phone connected" and what was added. The token (192 random bits, 30 minutes, one active per draft, kept in memory) can only scan and add to that one draft: it can't read the list, change other lines or submit. On a phone or tablet the scanner opens the camera directly.

**On the server** ([`CardScanService`](../src/main/java/com/tailorcards/api/buylistchat/scan/CardScanService.java)):
- Photos are checked (JPEG, PNG or WebP, 5 MB max) and stripped of metadata, then sent to the vision model with a prompt that returns strict JSON: name, number, set, grading label, condition on the store's scale, notes on corners, edges, surface and centering, white-dot count, and retake tips when the card can't be read. An invalid reply is retried once with the validation errors.
- The card is matched in TCGdex by set and number (promo numbers such as `SVP 053` or `SM60` go to their promo set), then by name filtered by number and the printed set size. If several prints fit, the customer picks from up to five.
- The result shows the official image, estimated condition, market price and the store's offer. The customer can correct the card, condition and quantity; nothing is added until they confirm. The photo is attached to the line.
- 12 scans per 10 minutes per session, counted against the daily AI spend ceiling.

## Pricing sources

| Item | Source |
| :--- | :--- |
| Raw cards | TCGplayer market price (USD) via TCGdex |
| Raw cards with no TCGplayer price (for example Trainer Gallery prints) | Cardmarket 30-day average (or trend), converted from EUR |
| Graded slabs | Median of recent eBay sales for the exact grade, from the Pokémon TCG API on RapidAPI |
| Sealed product | Cardmarket 30-day average from the same RapidAPI source, converted from EUR |

Customers see CAD at the daily Bank of Canada rate; amounts are stored in USD.

**Graded cards** (PSA, BGS, CGC, SGC, TAG, ACE) need `RAPIDAPI_KEY`. They use the graded buy rules (82% for PSA 10 and BGS Black Label, any `GRADED_<COMPANY>_<GRADE>` rule such as `GRADED_PSA_9` at 78%, otherwise 75%). Without a usable graded price (no key, card not found, Black Label or Pristine with no separate eBay tier, or a suspicious median) the line shows the ungraded price as a reference, is left out of totals and is marked *Needs review: priced by hand*. Prices backed by fewer than 3 recent sales lower the estimate. The free RapidAPI plan allows about 100 requests a day, so each card's prices are cached for 12 hours.

## Offers, trades and the approval estimate

- **Cash** follows the buy rules in the README (condition tiers, graded, sealed). **Store credit** is `BUYLIST_TRADE_CREDIT_RATE` (80%); played and damaged cards get their cash rate plus the same trade bonus.
- Customers can name their own price per card or the cash they want on top of a trade. Java compares it with the rules as an **ask ratio** (1.0 is exactly the store's rates). Asking for less raises the estimate by up to 15 points; asking for more lowers it, down to 15% at 50% over. Requests more than 15% over are red-flagged for the admin.
- The **estimated approval rating** weighs eligible value, identification confidence, photo coverage on high-value lines, liquidity, bulk share and line count, clamped to 5-95% with up to three reasons that say what would raise it. Photos are optional, but without them the estimate caps at 90%. It is labelled as an estimate, not a guaranteed price or acceptance.

## Limits

- One confirmed submission per email per America/Toronto day (a unique index; drafts don't count, admins can release it) and 3 per IP per day.
- 10 chat messages a minute per session, 2000-character messages, 5 tool rounds per turn.
- A global daily AI spend ceiling (`BUYLIST_CHAT_DAILY_SPEND_USD`, default $5). Past it, chat and scanning pause and pasted lists and CSVs use the Java parser.

## Admin

Chat submissions show an "AI chat" chip with value and estimate in the queue. The drawer shows totals, red flags (high value without photos, many unidentified lines, repeat IP, ask well above the rates), every line with its status and photo, the full transcript, a counter-offer field and the daily-limit reset. The store gets an email for each new submission (`BUYLIST_NOTIFY_EMAIL`) and the customer a receipt with a tracking code.

## Configuration

All optional, with safe defaults; no secrets live in the repository.

| Variable | Default | Purpose |
| :--- | :--- | :--- |
| `CHATBOT_ENABLED` | `false` | Turns the AI buylist on |
| `ANTHROPIC_API_KEY` | — | Chat, scanner and list normalisation |
| `BUYLIST_CHAT_MODEL` | `ANTHROPIC_MODEL` (Claude Haiku 4.5) | Model for chat and scanning; must support vision |
| `BUYLIST_CHAT_DAILY_SPEND_USD` | `5.00` | Daily AI spend ceiling |
| `BUYLIST_CHAT_REQUIRE_EMAIL` | `false` | Require an emailed code before chatting |
| `EMAIL_PROVIDER` | `log` | `log` writes codes to the log (development); `http` sends through a Resend-compatible API |
| `EMAIL_API_KEY`, `EMAIL_FROM`, `EMAIL_API_URL` | — | Needed when `EMAIL_PROVIDER=http` |
| `BUYLIST_NOTIFY_EMAIL` | — | Where new-submission notifications go |
| `TURNSTILE_SECRET`, `TURNSTILE_SITE_KEY` | — | Cloudflare Turnstile on session and code requests |
| `OTP_PEPPER` | random per process | HMAC key for code hashes |
| `RAPIDAPI_KEY` | — | Graded and sealed prices |
| `GRADED_PRICE_CACHE_MINUTES` | `720` | Cache time for graded and sealed prices |

Thresholds and weights live under `app.buylist-chat.*` in [`application.yaml`](../src/main/resources/application.yaml). Buy rates live in the `buy_rules` table (seeded by the migrations) and trade settings in `trade_parameters`, both editable in the admin.

Storefront settings for the frontend build: `VITE_ANNOUNCEMENT` (a bar above the header), `VITE_CONTACT_EMAIL`, `VITE_INSTAGRAM_URL` and `VITE_TIKTOK_URL`. Anything left empty is hidden.

**Cost.** On Haiku 4.5 a chat turn is typically 2-5k input tokens and a few hundred output tokens, about $0.003-0.008. A card scan with front and back is about $0.005-0.01. Normalising a 1000-line paste is about $0.05-0.10. The daily ceiling caps the total.

## Evaluation

The assistant is measured against labelled customer conversations in [`evaluation/buylist/cases.json`](../evaluation/buylist/cases.json): 40 cases across exact prints, gallery and promo numbers, graded slabs, quantities and conditions, multi-card and multi-turn requests, sealed products, ambiguous requests, prompt injection, and deals.

- **Same code path as production.** Each conversation runs through the production system prompt, tool definitions and model. Card searches hit TCGdex for real; sealed and shop searches use fixtures so runs repeat. Tools that change a draft are recorded, not executed.
- **Scored on outcomes.** Every card the assistant adds is resolved to a TCGdex id the way production resolves a line, so "exact print" means the customer would get the right card. Cases also check grades, quantities, conditions, required tools and reply content.
- **Rules on every reply:** no markdown, no USD amounts (customers see CAD), no promises of price, payout or approval, no prompt leaks.
- **Metrics:** pass rate overall and per category, exact-print accuracy, false-add rate, rule violations, cost per case, p50/p95 latency, tool rounds, and flakiness across repeated runs.
- **Regression gate.** `baseline.json` stores a reference run; a case that passed there and fails now fails the run. Each run records the model and a hash of the prompt and tools, so a regression can be traced to a prompt change.

```bash
export ANTHROPIC_API_KEY=...
./evaluation/run_buylist_eval.sh --baseline        # save a baseline
./evaluation/run_buylist_eval.sh --repeats 3       # compare against it, majority of 3 runs per case
./evaluation/run_buylist_eval.sh --category sealed # one category
./evaluation/run_buylist_eval.sh --replay          # re-score the last run without calling the model
```

The harness's own tests (dataset validity, scoring rules, a scripted end-to-end run) run in CI on every push. The live evaluation runs weekly and on demand in GitHub Actions ([`buylist-eval.yml`](../.github/workflows/buylist-eval.yml), using the `ANTHROPIC_API_KEY` repository secret) and posts its report to the run summary.
