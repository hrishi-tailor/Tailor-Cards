# Ledger and show mode

The ledger records what the store buys and what it sells from it, so margin, turnover and channel results come from real records instead of estimates. Most deals happen at card shows, where nothing used to be written down; show mode exists to capture them on a phone in a few taps.

Admin pages: **`/admin/ledger`** (results, purchases, CSV import) and **`/admin/show`** (show mode). Both are ADMIN-only and hidden for DEMO.

## Requirements

What the ledger has to answer, and how it answers each one:

| Question | Where it comes from |
| :--- | :--- |
| How much did I make, and at what margin? | Revenue minus cost of goods of realised sales, in total and for a date range |
| Which kinds of stock earn the most (cases, singles, slabs)? | Breakdown by item kind |
| Which channels work best (shows, Facebook, eBay)? | Breakdown by sales channel |
| How fast does stock sell? | Median days from listing to sale |
| How much money is tied up in unsold stock? | Units left, at what they cost |
| Can I log a show deal without signal? | Show mode's offline queue |

## How it works

A **lot** is one purchase at one total cost: a card, a slab, or a whole sealed case. **Sales** draw units from a lot at their own prices.

- **Cost of goods** for a sale is the lot's cost shared out by units: `total cost x units sold / units in the lot`, to the cent. A case of 10 bought for $1,350 costs $135 per ETB sold. Cent rounding across many sales of one lot can differ from the total by a few cents.
- **A giveaway** is a sale at $0. Its cost still counts, so it lowers profit as it should.
- **Revenue and profit count sales only.** Unsold stock is reported separately, at cost.
- **Days listed** is from the lot's listing date to the sale date. Lots without a listing date have no days listed.
- **Dates** are store dates in America/Toronto, so an 11 pm sale lands on that day.

### Rules

| Rule | Response |
| :--- | :--- |
| A sale can't take more units than the lot has left (the lot row is locked while a sale is recorded) | 409 |
| No sale in the future or before the purchase date | 400 |
| No purchase or listing date in the future; no listing before the purchase | 400 |
| A lot with sales can't be deleted, or shrunk below the units sold | 409 |
| A repeated `externalRef` (lot) or `clientRef` (sale) returns the record already saved | 201 with the existing record |

Every change through the API is written to the [audit log](security.md#5-audit-log-of-admin-actions) with the record before and after.

## API

All under `/api/admin/ledger`, ADMIN only (two-factor session required in production). OpenAPI docs at `/swagger-ui.html`.

| Method and path | Purpose |
| :--- | :--- |
| `GET /lots` | All lots, newest first, each with its sales |
| `GET /lots/{id}` | One lot |
| `POST /lots?source=MANUAL\|SHOW_MODE` | Record a purchase |
| `PUT /lots/{id}` | Correct a purchase |
| `DELETE /lots/{id}` | Delete a purchase with no sales |
| `POST /lots/{id}/sales?source=MANUAL\|SHOW_MODE` | Record units sold |
| `DELETE /sales/{id}` | Delete a sale recorded by mistake |
| `GET /summary?from=2026-01-01&to=2026-12-31` | Results for a date range (both optional) |
| `POST /import?dryRun=true` | Import a CSV (multipart field `file`) |

## CSV import

Use it for history kept elsewhere (a spreadsheet, a Marketplace export cleaned up by hand). On the ledger page, choosing a file checks it; nothing is saved until **Import** is pressed.

- One row per purchase. **Rows with the same `lot_ref` are one purchase**; its columns are read from the first of those rows and ignored on the others.
- **Any row can also carry one sale.** A case sold at two prices plus a giveaway is one purchase over three rows.
- **A real import is all or nothing.** If any row has an error, the response is 422 with every error and its line number (the header is line 1), and nothing is saved.
- **Importing the same file again adds nothing.** `lot_ref` becomes the purchase's external reference (`csv:<lot_ref>`), and purchases already imported are skipped with their sales.
- Limits: 2 MB and 5,000 rows.

| Column | Required | Example | Notes |
| :--- | :---: | :--- | :--- |
| `lot_ref` | yes | `PRE-1` | Your own id for the purchase, up to 60 characters |
| `title` | first row | `Prismatic Evolutions ETB (2 cases)` | |
| `kind` | first row | `case` | `single`, `graded` (or `slab`), `sealed`, `case` (or `sealed_case`), `lot` |
| `quantity` | first row | `20` | Units bought |
| `total_cost_cad` | first row | `1832` or `"$1,832"` | Paid for all units together |
| `acquired_on` | | `2026-08-14` | |
| `acquired_channel` | | `card show` | See channels below |
| `listed_on` | | `2026-08-16` | Needed for days listed |
| `listed_price_cad` | | `230` | Asking price per unit |
| `card_ref` | | `sv03.5-166` | TCGdex card id |
| `notes` | | | |
| `sold_on` | for a sale | `2026-08-16` | A row with `sold_on` adds a sale |
| `sold_quantity` | for a sale | `17` | |
| `sale_unit_price_cad` | for a sale | `230` | `0` for a giveaway |
| `sale_channel` | for a sale | `facebook` | See channels below |
| `payment_method` | | `e-transfer` | `cash`, `e-transfer` (or `interac`), `card`, `paypal`, `trade`, `other` |
| `sale_notes` | | `given to a kid` | |

Channels: `card show` (or `show`), `facebook` (or `marketplace`, `fb`), `instagram`, `ebay`, `website` (or `tailorcards`), `buylist`, `other`. Values are not case-sensitive, and spaces or hyphens can stand in for underscores.

```csv
lot_ref,title,kind,quantity,total_cost_cad,acquired_on,acquired_channel,listed_on,listed_price_cad,card_ref,notes,sold_on,sold_quantity,sale_unit_price_cad,sale_channel,payment_method,sale_notes
PRE-1,Prismatic Evolutions ETB (2 cases),case,20,1832,2026-08-14,card show,2026-08-16,230,,,2026-08-16,17,230,facebook,e-transfer,
PRE-1,,,,,,,,,,,2026-08-18,2,220,facebook,e-transfer,
PRE-1,,,,,,,,,,,2026-08-27,1,0,show,,given to a kid
GRD-1,Team Magma's Groudon EX 15/34,single,1,30,,,2026-04-24,200,,,2026-04-24,1,200,facebook,cash,
```

Keep ledger files out of this public repository: they hold the store's prices and costs.

## Show mode

`/admin/show`, built for one hand on a phone at a busy table.

- **Bought:** what, kind, units, total paid, payment. Recorded as bought today at a card show.
- **Sold:** search your stock, pick the item, units and price (prefilled from the asking price), payment. Recorded as sold today at a card show.

**Offline.** Every deal is saved on the phone first (browser storage) and then sent:
- **When it sends:** in order, when the page opens, when the browser comes back online, and on **Sync**.
- **No duplicates:** each entry carries an id made on the phone, so a retried send records it once.
- **Selling something bought while offline** is allowed; the sale is sent after the purchase.
- **Stock** is cached for selling offline and reduced as sales are logged.
- **Refused entries** stay listed with the server's reason until you discard them.
- **An expired session** keeps everything queued until you sign in again.

The queue lives in that phone's browser only. Clearing the browser's site data before syncing loses unsent deals, so sync before closing up.

## Test cases

Automated tests for the ledger, all in `src/test/java/com/tailorcards/api/ledger` and run in CI:

| ID | Case | Expected | Test |
| :--- | :--- | :--- | :--- |
| L-01 | A 20-unit case sold as 17 at $230, 2 at $220, 1 given away | Revenue $4,350, cost $1,832, profit $2,518; giveaway shows -$91.60 | `LedgerServiceTest.caseSoldInParts` |
| L-02 | Selling more units than are left | Refused, "Only 0 … left" | `LedgerServiceTest.noOverselling` |
| L-03 | Sale dated tomorrow, or before the purchase | Refused | `LedgerServiceTest.saleDates` |
| L-04 | The same lot or sale sent twice with the same reference | One record | `LedgerServiceTest.retriesAreIdempotent` |
| L-05 | Deleting or shrinking a lot that has sales | Refused | `LedgerServiceTest.soldLotsKeepTheirHistory` |
| L-06 | Listing date before purchase date | Refused | `LedgerServiceTest.listingAfterPurchase` |
| S-01 | Totals over cases, slabs and unsold stock | Revenue, cost, margin and on-hand cost match hand calculation | `LedgerSummaryServiceTest.totals` |
| S-02 | Breakdowns by kind, channel, month; median days | Ordered by profit; median 4 days | `LedgerSummaryServiceTest.breakdowns` |
| S-03 | Date range, and a range ending before it starts | Only October sales; refused | `LedgerSummaryServiceTest.dateRange` |
| I-01 | Dry run of a valid file | Correct counts, revenue and cost; nothing saved | `LedgerCsvImporterTest.dryRun` |
| I-02 | Import, then import the same file again | Saved once; second run skips 2 purchases | `LedgerCsvImporterTest.saveThenReimport` |
| I-03 | Bad amount, early sale, overselling, unknown channel, missing lot_ref | Five errors on lines 2–6; nothing saved | `LedgerCsvImporterTest.errorsByLine` |
| I-04 | Unknown and missing columns | Reported before rows are read | `LedgerCsvImporterTest.headerProblems` |
| A-01 | Anonymous access | 401 | `LedgerControllerIntegrationTest.adminOnly` |
| A-02 | Create a lot and a sale over HTTP | 201s, read back, audit entry written | `LedgerControllerIntegrationTest.lotAndSale` |
| A-03 | Invalid body, overselling, unknown lot, CSV source on the lot endpoint | 400, 409, 404, 400 | `LedgerControllerIntegrationTest.errors` |
| A-04 | Delete a sale, then its lot | Lot refused while it has a sale, then deleted | `LedgerControllerIntegrationTest.corrections` |
| A-05 | Summary range | 200, and 400 for a reversed range | `LedgerControllerIntegrationTest.summary` |
| A-06 | CSV over HTTP: dry run, bad row, good file, no login | 200, 422 with line 2, saved and audited, 401 | `LedgerControllerIntegrationTest.importCsv` |

Migration V12 is also checked by `FlywaySchemaValidationTest` (H2) and the PostgreSQL migration tests.

Show mode's offline queue was checked by hand: a sale logged with the backend stopped was queued on the phone, then sent once on Sync after the backend came back, with exactly one sale in the ledger.
