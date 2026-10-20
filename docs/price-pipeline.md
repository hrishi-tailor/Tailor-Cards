# Price pipeline

Every night the store needs a market price for each card it lists or holds, so the ledger and the buylist can value stock. The app used to do this itself, with a 3 AM job inside the web server. The price pipeline moves that work to AWS. A scheduled Lambda asks the app which cards to price and prices them from TCGdex. It keeps every raw response in an S3 data lake, then sends the prices back to the app.

Moving the job out of the app gives three things:
- Pricing no longer competes with web requests.
- The raw data is kept and can be queried with Athena.
- A missed or failed run raises an alarm, where before it only wrote a log line.

## Requirements

| Requirement | How it's met |
| :--- | :--- |
| Price every card the store lists **or holds** | The watchlist joins site products (`pokemontcgId`) with ledger lots that have a card id and units left |
| Same price as the app's own job | The Python parser follows `TcgdexPriceProvider`: TCGplayer market price in USD, the same variant order, rounded half-up to cents. The app converts USD to CAD at its own exchange rate |
| No AWS credentials or database access on the app's host | The Lambda talks to the app's public API over HTTPS, signed with a shared secret |
| Safe to retry | The app skips a price it already has (same card, source and time to the second) |
| Keep the raw data | Every response lands in S3, one gzipped JSON line per card, partitioned by day |
| Know when it breaks | One alarm fires when the function errors, another when no run succeeds for 26 hours. Every run writes a log and metrics |
| Be gentle with TCGdex | At most 8 requests a second, with retries and backoff |

## Architecture

```text
EventBridge Scheduler (3 AM Toronto)
        │ invoke (retries twice)
        ▼
Lambda  price_ingest.handler  (Python 3.13, arm64)
   1. Secrets Manager ── shared secret
   2. GET  /api/internal/pricing/watchlist   ──► app   (signed)
   3. GET  api.tcgdex.net/v2/en/cards/{id}  × N  (8/s, retries)
   4. S3   raw/tcgdex/dt=YYYY-MM-DD/<run>.jsonl.gz
   5. POST /api/internal/pricing/snapshots   ──► app   (signed) ──► price_snapshots (USD + CAD)
   6. S3   runs/dt=YYYY-MM-DD/<run>.json  +  CloudWatch metrics (EMF)
```

| Part | Where |
| :--- | :--- |
| Signature check | `src/main/java/com/tailorcards/api/pipeline/PipelineSignature.java` |
| Endpoints | `PipelineController` (`/api/internal/pricing/**`), `PipelinePricingService` |
| Turning off the app's job | `PriceSnapshotScheduler.nightlySync`, setting `app.pricing.snapshot-source` |
| Lambda | `pipeline/price_ingest/` (`handler`, `tcgdex`, `app_client`, `lake`, `signing`, `http`) |
| Infrastructure | `infra/modules/price_pipeline`, enabled with `enable_price_pipeline` |

The Lambda uses only the Python standard library plus the boto3 that comes with the runtime, so nothing is bundled and there are no dependencies to patch.

## Signed requests

The two endpoints sit outside the admin login. Instead, every request carries two headers:

```text
X-TC-Timestamp: 1792500000                       (Unix seconds)
X-TC-Signature: v1=<hex HMAC-SHA256(secret, payload)>

payload = timestamp + "\n" + METHOD + "\n" + path + "\n" + hex(SHA-256(body))
```

Before parsing anything, the app reads the raw body (up to 1 MB) and recomputes the signature. It compares the two in constant time.
- A request that is unsigned, tampered with, or more than five minutes from the app's clock gets a **401**.
- Admin credentials don't open these endpoints.
- Without `PIPELINE_SHARED_SECRET` (32+ characters), both endpoints answer **404**.

The Java and Python sides share a test vector, so a change to either one breaks a test.

### `GET /api/internal/pricing/watchlist`

```json
{ "cardIds": ["sv8pt5-161", "swsh7-215"], "generatedAt": "2026-10-20T07:00:00Z" }
```

### `POST /api/internal/pricing/snapshots`

```json
{ "runId": "20261020T070000Z-1a2b3c4d", "source": "TCGDEX",
  "prices": [ { "cardId": "swsh7-215", "priceUsd": 2264.70, "fetchedAt": "2026-10-20T07:00:04Z" } ] }
```

The app replies `{ "runId": "…", "saved": 1, "duplicates": 0 }`.
- A batch holds at most 5,000 prices; the pipeline sends 1,000 at a time.
- A batch with a price dated more than five minutes in the future is refused whole (400).
- `source` must be an upper-case name. It is stored on the snapshot exactly as the app's own job would store it.

## Data layout

| Key | Contents |
| :--- | :--- |
| `raw/tcgdex/dt=YYYY-MM-DD/<run>.jsonl.gz` | One line per card: `run_id`, `card_id`, `status`, `fetched_at`, `price_usd`, `variant`, `error`, and `response` (the body as received) |
| `runs/dt=YYYY-MM-DD/<run>.json` | The run log: status, counts, what the app saved, up to 200 problem cards, duration, error |

`status` is one of:
- `priced`
- `no_price`: the card exists but has no TCGplayer market price
- `not_found`: TCGdex doesn't know the id
- `error`: failed after retries

The `dt=` partitions are Hive-style, so Glue and Athena read them directly. Run logs expire after 400 days. Raw data is kept, and overwritten versions are kept for 30 days.

**Card ids.** The watchlist uses the ids stored on products (`pokemontcgId`) and lots (`cardRef`). Modern pokemontcg.io and TCGdex ids usually match (`swsh7-215`). Some sets differ: pokemontcg.io `sv3pt5-199` is `sv03.5-199` on TCGdex. Those cards come back `not_found`; the run log lists them and the `CardsNotFound` metric counts them. They don't fail the run.

## When a run fails

A run **fails** (the Lambda raises an error, and the errors alarm fires) when:
- none of the cards on a non-empty watchlist could be priced, or
- more than 20% of them errored (`MAX_ERROR_RATIO`).

The run log and the metrics are written either way. An empty watchlist is a success with nothing to do.

Metrics are in namespace `TailorCards/PricePipeline`, dimension `Environment`:
- `RunSucceeded`
- `WatchlistSize`
- `CardsPriced`
- `CardsNoPrice`
- `CardsNotFound`
- `CardsFailed`
- `PricesSaved`
- `DurationMs`

## Turning it on

**1. Apply the infrastructure.** In `infra/envs/staging/terraform.tfvars`, set `enable_price_pipeline = true`, then:
```bash
terraform init -backend-config=backend.hcl -upgrade
```
```bash
terraform apply
```
The pipeline points at the environment's own API by default. Set `price_pipeline_app_url` to point it at another app.

**2. Create the shared secret** and give it to both sides. The Lambda reads it from Secrets Manager. The app gets it as `PIPELINE_SHARED_SECRET`, together with `PRICE_SNAPSHOT_SOURCE=pipeline`; on AWS staging, both go in the `tailorcards-staging/app` secret JSON next to the other keys, and on Render they are environment variables.
```bash
openssl rand -hex 32 > /tmp/pipeline-secret
```
```bash
aws secretsmanager put-secret-value --secret-id tailorcards-staging/price-pipeline --secret-string file:///tmp/pipeline-secret
```
Add the same value to the app, then restart or redeploy it. Delete `/tmp/pipeline-secret` afterwards.

**3. Run it once by hand** instead of waiting for 3 AM:
```bash
aws lambda invoke --function-name tailorcards-staging-price-pipeline /tmp/run.json
```
```bash
cat /tmp/run.json
```

**4. Confirm** that the run log is in `s3://<bucket>/runs/` and that `GET /api/admin/price-snapshots` (as admin) lists new `TCGDEX` rows.

## Runbook

| Symptom | Check | Fix |
| :--- | :--- | :--- |
| Errors alarm | Newest file under `runs/`, then the function's log group `/aws/lambda/<name>` | Read `error` in the run log; rerun with `aws lambda invoke` once fixed (reruns are safe) |
| `401` from the app | The secrets differ, or the Lambda's clock is more than 5 minutes off | Put the same value in Secrets Manager and in the app's `PIPELINE_SHARED_SECRET`, then restart the app |
| `404` from the app | The app has no `PIPELINE_SHARED_SECRET`, or it's under 32 characters | Set it and restart |
| No-success alarm with no errors | The schedule didn't fire, or the function wasn't invoked | Check the schedule in EventBridge Scheduler and its role's `lambda:InvokeFunction` permission |
| Many `not_found` | Card ids from pokemontcg.io that TCGdex names differently | Correct the product's or lot's card id to the TCGdex id |
| Prices recorded twice a night | The app's own job still runs | Set `PRICE_SNAPSHOT_SOURCE=pipeline` on the app |
| Turning it off | | Remove `PRICE_SNAPSHOT_SOURCE` (the app's job resumes), then set `enable_price_pipeline = false` and apply |

## Cost

At a few hundred cards a night, a run takes under a minute at 256 MB.

| Item | Monthly cost |
| :--- | :--- |
| Lambda, EventBridge Scheduler, X-Ray | Free tier |
| S3 | A few cents |
| CloudWatch alarms | 2 more alarms; staging stays within the 10 free |
| Secrets Manager (the one new secret) | $0.40 |

## Test cases

| ID | Case | Expected | Test |
| :--- | :--- | :--- | :--- |
| P-01 | Correctly signed request | Accepted | `PipelineSignatureTest.validSignature` |
| P-02 | Body, path, method or secret changed | Refused | `PipelineSignatureTest.tampering` |
| P-03 | Timestamp 301 s old or ahead; 299 s old | Refused; accepted | `PipelineSignatureTest.staleTimestamps` |
| P-04 | Java and Python sign the same request | Identical `v1=2284e0ec…` | `PipelineSignatureTest.knownVector`, `test_signing.py::test_matches_the_java_verifier` |
| P-05 | No secret, or one under 32 characters | Endpoints 404 | `PipelineSignatureTest.disabledWithoutSecret`, `PipelineDisabledIntegrationTest` |
| P-06 | Watchlist from products and stock | Trimmed, each id once, sorted; sold-out lots left out | `PipelinePricingServiceTest.watchlist`, `LedgerLotRepositoryTest` |
| P-07 | US$101.505 at 1.38 | Saved as US$101.51 / C$140.08 under `TCGDEX` | `PipelinePricingServiceTest.recordsPrices` |
| P-08 | Same run sent twice | Second send saves 0, reports duplicates | `PipelinePricingServiceTest.idempotent`, `PipelineControllerIntegrationTest.signedRun` |
| P-09 | Price dated 10 minutes ahead | Whole batch refused | `PipelinePricingServiceTest.futurePrice` |
| P-10 | Unsigned, tampered or admin-only request | 401, nothing saved | `PipelineControllerIntegrationTest.refused` |
| P-11 | Signed but invalid batch | 400 naming the field | `PipelineControllerIntegrationTest.invalidBatch` |
| P-12 | `snapshot-source: pipeline` | 3 AM job skips; manual sync still works | `PriceSnapshotSchedulerTest` |
| T-01 | Variant order and rounding | Same as the Java provider | `test_tcgdex.py` |
| T-02 | 503 then reset then 200; Retry-After; 401 | Retries with backoff; honours the header; no retry | `test_http.py` |
| H-01 | Good run | Raw file, prices sent, run log, `RunSucceeded=1` | `test_handler.py::test_a_good_run_…` |
| H-02 | 2 of 3 cards error | Run fails, log and metrics still written | `test_handler.py::test_too_many_errors_…` |
| H-03 | Nothing priced / empty watchlist | Fails / succeeds | `test_handler.py` |

The Java tests run with `./mvnw test`. The Python tests run with:
```bash
cd pipeline && uv run --python 3.13 --group dev pytest
```
Both run in CI, and Terraform is checked by `fmt`, `validate`, TFLint and Checkov in the infrastructure workflow.

The whole loop was also verified end to end against a local app and the live TCGdex API:
- The watchlist returned a stocked lot (`swsh7-215`) and a bad id.
- TCGdex priced `swsh7-215` at US$2,264.70 (holofoil); the bad id came back `not_found`.
- The app saved one `TCGDEX` snapshot in USD and CAD.
- Sending the run again saved nothing and reported one duplicate.
