# Listing generator

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
