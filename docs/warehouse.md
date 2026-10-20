# Warehouse

The warehouse answers business questions with SQL instead of spreadsheets. Each question below is a view you can query in Athena:

| Question | View |
| :--- | :--- |
| What did each month earn, and at what margin? | `mart_monthly_pnl` |
| Which kinds of stock sell best, and where? | `mart_kind_channel` |
| What is my unsold stock worth today against what I paid? | `mart_inventory_valuation` |
| Which cards I hold or list moved in price this week? | `mart_price_movers` |

Every night a Lambda copies the app's ledger, products and price history into S3. It then rebuilds the views in Athena and runs ten data-quality checks. The tables are registered in the Glue Data Catalog.

## Architecture

```text
3 AM  price pipeline ──► app: price_snapshots          (docs/price-pipeline.md)
4 AM  warehouse Lambda
        1. GET /api/internal/export/{dataset}?after=…   ──► app (signed, paged)
        2. S3  warehouse/raw/<table>/data.jsonl.gz          (current copy, read by Glue tables)
           S3  warehouse/history/<table>/dt=YYYY-MM-DD/     (that night's copy)
        3. Athena: CREATE OR REPLACE VIEW × 18              (DDL is free)
        4. Athena: SELECT … FROM dq_results                 ──► run log + metrics + alarm
        5. S3  dashboard/<view>.csv                         ──► Tableau (docs/dashboard.md)
Mon 8 AM  report Lambda ──► SES email                   (docs/weekly-report.md)
```

| Part | Where |
| :--- | :--- |
| Export endpoint | `WarehouseExportController`, `WarehouseExportService`, `ExportDataset` (app) |
| Raw table definitions | `pipeline/warehouse/schema.json`, read by Terraform and by the tests |
| Views | `pipeline/warehouse/sql/*.sql`, applied in file-name order |
| Lambda | `pipeline/warehouse/` (`handler`, `export`, `athena`) |
| Infrastructure | `infra/modules/warehouse`, enabled with `enable_warehouse` |

**Data source.** The warehouse reads production, because that is where the real ledger lives. Set `price_pipeline_app_url` to the production API; both the price pipeline and the warehouse then use it. Exports land only in the private, encrypted, TLS-only data-lake bucket. Nothing exported is committed to the repository; the tests use synthetic data.

**What is exported.** Each dataset is a fixed query in the app; no SQL comes from the caller. Free-text notes, product descriptions and photo links are never exported. The export requests are signed like the price pipeline's, with the query string included in the signature (see [price-pipeline.md](price-pipeline.md#signed-requests)).

## Model

```text
                     dim_date ─────────────┐
                        │                  │
 dim_channel ── fact_sales ── dim_lot      fact_price_daily ── dim_card
                        │                                         │
                        └──────────── (card_id) ──────────────────┘
```

| Layer | View | Grain / purpose |
| :--- | :--- | :--- |
| Staging | `stg_lots`, `stg_sales`, `stg_products`, `stg_prices` | Typed, trimmed copies of the raw exports. Money is cast to DOUBLE for arithmetic, and snapshots without a positive price are dropped |
| Dimension | `dim_date` | One row per day, from the first record to today, with year, quarter, month, weekday and weekend flag |
| Dimension | `dim_card` | One row per card id seen anywhere. The name comes from the site's product, else the ledger lot. Flags `listed_on_site` and `in_ledger` |
| Dimension | `dim_lot` | One row per purchase, with unit cost and kind name |
| Dimension | `dim_channel` | Sales channels with an online / in-person type |
| Fact | `fact_sales` | One row per sale: revenue, cost of goods, gross profit, days listed and held, giveaway flag |
| Fact | `fact_price_daily` | One price per card per day: the latest snapshot fetched that day |
| Mart | `mart_monthly_pnl` | Sales, units, revenue, cost, profit, margin and giveaways by month |
| Mart | `mart_kind_channel` | The same measures by item kind × channel, with median days listed |
| Mart | `mart_inventory_valuation` | Lots with units on hand: cost on hand, latest market price and its age, market value, unrealised gain |
| Mart | `mart_price_movers` | Latest price against the latest price at least 7 days earlier, for cards held or listed |
| Mart | `mart_weekly_pnl` | The monthly measures by Monday-start week, for the Monday email |
| Dashboard | `dash_sales`, `dash_prices` | Flat copies of the sales and price facts with names and calendar fields joined, for Tableau |
| Quality | `dq_results` | One row per check (below) |

**Cost of goods** follows the app's rule. A lot's cost is shared by units, rounded to the cent, so a $1,832 case of 20 costs $91.60 per unit sold. A giveaway still carries its cost.

**`raw_tcgdex_responses`** exposes every raw TCGdex response the price pipeline kept, partitioned by `dt` with partition projection, so no crawler is needed. For example:

```sql
SELECT status, count(*) FROM raw_tcgdex_responses WHERE dt = '2026-10-20' GROUP BY status;
```

## Data-quality checks

`dq_results` has one row per check. `failing_rows` is 0 when the check passes, and `example` names one offending id.

| Check | Severity | Catches |
| :--- | :--- | :--- |
| `oversold_lot` | error | More units sold from a lot than it holds |
| `orphan_sale` | error | A sale whose lot is missing |
| `sale_before_purchase` | error | A sale dated before its lot was bought |
| `future_sale` | error | A sale dated after today |
| `negative_amount` | error | A negative cost or sale price |
| `unpriced_snapshot` | error | A price snapshot with no positive CAD price |
| `duplicate_snapshot` | warning | The same card, source and fetch time recorded twice |
| `exchange_rate_out_of_range` | warning | A CAD/USD ratio outside 1.20–1.60 |
| `price_spike` | warning | A card's price moved more than 50% from its previous day |
| `stale_held_price` | warning | A card in stock with no price in 3 days |

Errors are counted in the `DataQualityErrors` metric, which has its own alarm; they don't fail the run, because the data is at fault rather than the job. Warnings appear in the run log (`runs/warehouse/`) and the `DataQualityWarnings` metric.

## Testing the SQL

The views are written in Trino SQL for Athena. The tests run them on DuckDB against synthetic data, so they run in seconds on a laptop and in CI.

- `warehouse/local.py` creates the raw tables from `schema.json` and loads rows into them. It then creates every view.
- DuckDB lacks four Trino functions the views use: `sequence`, `day_of_week`, `approx_percentile` (median only) and `from_iso8601_timestamp`. The harness defines each one as a small macro with the same result.
- `test_sql_is_portable` fails if a view uses a DuckDB-only function or a percentile the shim doesn't cover. This keeps a passing test meaningful for Athena.

| ID | Case | Expected | Test |
| :--- | :--- | :--- | :--- |
| W-01 | Every file defines one view, named after the file | One `CREATE OR REPLACE VIEW`, no `;` | `test_warehouse_sql.py::test_every_file_…` |
| W-02 | Views stay portable | No DuckDB-only functions; median only | `test_sql_is_portable` |
| W-03 | The 20-unit case sold as 17 + 2 + 1 giveaway | $4,350 / $1,832 / $2,518, the same as the app's ledger test L-01 | `test_case_sold_in_parts_matches_the_app` |
| W-04 | Monthly P&L | Adds up to the facts: 5 sales, $5,950 revenue, $3,018 profit | `test_monthly_pnl_adds_up_to_the_facts` |
| W-05 | Kind × channel | Sealed case on Facebook: 60.0% margin, median 6 days listed | `test_kind_and_channel` |
| W-06 | Inventory valuation | Sold-out lots excluded; latest snapshot of the latest day used; gain $122 and −$9 | `test_inventory_valuation_…` |
| W-07 | Price movers | +$42 (+15.0%) against 9 days earlier; none without history | `test_price_movers_…` |
| W-08 | Card names, calendar | Site name first; calendar continuous to today | `test_cards_…`, `test_calendar_…` |
| Q-01 | Keys | Unique and non-null in every dimension and fact | `test_data_quality.py::test_keys_…` |
| Q-02 | Joins | Every fact row joins to its dimensions | `test_facts_join_to_their_dimensions` |
| Q-03 | Clean data | Every check passes | `test_clean_data_passes_every_check` |
| Q-04 | One planted violation of each kind | Each check reports exactly 1 row, with the right severity and example | `test_each_check_catches_its_problem` |
| E-01 | Export paging | Follows the cursor; refuses one that doesn't advance | `test_export.py` |
| E-02 | Schema drift | New app columns are reported; missing ones become null | `test_conform_…` |
| E-03 | Run | Exports every table, applies every view, records checks | `test_warehouse_handler.py` |
| E-04 | Failures | An export failure fails the run before views change; DQ errors are counted, not raised | `test_warehouse_handler.py` |
| A-01 | Export API | Paging, snake_case ISO rows, no notes; unknown dataset 404; bad limit 400; unsigned 401 | `WarehouseExportIntegrationTest` |

**End to end.** I ran the whole flow against a local copy of the app with Postgres:
- I seeded lots and sales through the admin API and ran one live price run.
- I exported the four datasets over the signed API. The app's columns matched `schema.json` exactly.
- I ran every view on the exported rows. The month showed $4,350 revenue, $1,832 cost and $2,518 profit. The Umbreon in stock was valued at its live market price. All ten checks passed.

## Turning it on

The price pipeline must be on first (see [price-pipeline.md](price-pipeline.md#turning-it-on)).

1. In `infra/envs/staging/terraform.tfvars`, set:
   ```hcl
   enable_price_pipeline  = true
   enable_warehouse       = true
   price_pipeline_app_url = "https://tailor-cards-api.onrender.com"
   ```
   Then run `terraform apply`.
2. On Render, set `PIPELINE_SHARED_SECRET` to the value in the `tailorcards-staging/price-pipeline` secret, set `PRICE_SNAPSHOT_SOURCE=pipeline`, and redeploy.
3. Run the refresh once:
   ```bash
   aws lambda invoke --function-name tailorcards-staging-warehouse /tmp/warehouse.json
   ```
4. Query it in the Athena console. Choose workgroup `tailorcards-staging-warehouse`, then:
   ```sql
   SELECT * FROM tailorcards_staging_warehouse.mart_monthly_pnl ORDER BY month_start DESC;
   ```

## Runbook

| Symptom | Check | Fix |
| :--- | :--- | :--- |
| Errors alarm | Newest run log under `runs/warehouse/`, then `/aws/lambda/tailorcards-staging-warehouse` | Read `error`; rerun with `aws lambda invoke` (a rerun replaces the same files) |
| Data-quality alarm | `SELECT * FROM dq_results WHERE failing_rows > 0` | Correct the record in the ledger (the `example` column names it); the next run clears the alarm |
| `unknown_columns` in the run log | The app now exports a column the schema lacks | Add it to `schema.json` (and a view if it's needed), then apply Terraform |
| View fails to create | Athena's reason is in the run log's `error` | Fix the SQL and run the tests; DuckDB catches most mistakes before Athena does |
| Query cancelled | The workgroup refuses any query over 1 GB scanned | Filter by `dt`, or raise `bytes_scanned_cutoff` |
| First call times out | Render's free plan can sleep the app | The Lambda waits up to 60 s (`app_timeout_seconds`); raise it if needed |

## Cost

At this store's size, the exports and query results are a few megabytes a day.

| Item | Monthly cost |
| :--- | :--- |
| Athena (DDL is free; data-quality queries are billed at the 10 MB minimum) | Under $0.01 |
| Glue Data Catalog (under a million objects) | Free |
| Lambda, EventBridge Scheduler | Free tier |
| S3 | Cents |
| CloudWatch alarms (3 more) | Free tier |
