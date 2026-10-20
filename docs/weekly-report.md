# Monday email

Every Monday at 8 AM (Toronto) the owner gets last week's results by email, built from the warehouse views. No one has to open a dashboard to know how the week went.

## What it says

- **Headline:** revenue and gross profit for the last full week (Monday to Sunday), against the week before; margin; sales and units.
- **Stock:** cost on hand, market value today and unrealised gain; how many lots have no market price.
- **Best sales:** the five most profitable sales of the week, with channel.
- **Price moves in stock:** the five biggest 7-day price changes among cards held.
- **Data to check:** any failing data-quality check, with an example id.

The subject line carries the headline, e.g. *Tailor Cards week of Oct 12: $500.00 profit on $1,200.00*. The email has an HTML and a plain-text part. A copy of the HTML is kept at `reports/weekly/<week start>.html` in the data lake.

## How it works

| Part | Where |
| :--- | :--- |
| Queries and rendering | `pipeline/warehouse/report.py` (`gather`, `render`, `send`) |
| Lambda | `pipeline/warehouse/report_handler.py` |
| Weekly view | `mart_weekly_pnl` (`pipeline/warehouse/sql/44_mart_weekly_pnl.sql`) |
| Infrastructure | `infra/modules/warehouse` (report section), enabled with `enable_weekly_report` |

`gather()` takes any function that runs SQL, so the same queries run on Athena in production and on DuckDB in the tests. Athena returns every value as text, and `num()` reads text and numbers alike. Item names are HTML-escaped.

The Lambda's role can only:
- query the warehouse through its workgroup
- write `reports/weekly/`
- send from `no-reply@<domain>` on the store's SES identity

The schedule retries once, so a partial failure can't send two emails.

## Turning it on

1. The warehouse must be on (see [warehouse.md](warehouse.md#turning-it-on)).
2. Set `enable_weekly_report = true` in `terraform.tfvars` and apply. The email goes to `alert_email`; set `report_recipients` in the stack to change that.
3. **SES sandbox:** until the account has SES production access, SES only delivers to verified addresses. Verify the recipient:
   ```bash
   aws sesv2 create-email-identity --email-identity you@example.com
   ```
   Then click the link in the email. Alternatively, request production access (infra/README.md, step 6).
4. Send one now instead of waiting for Monday:
   ```bash
   aws lambda invoke --function-name tailorcards-staging-weekly-report /tmp/report.json
   ```

If the email fails, the `…-weekly-report-errors` alarm fires; an unverified recipient is the usual cause.

## Test cases

| ID | Case | Expected | Test |
| :--- | :--- | :--- | :--- |
| R-01 | Report date on a Monday, and on the Sunday after | Both report the previous Monday–Sunday | `test_report.py::test_last_week_…` |
| R-02 | Gather on synthetic data | Week revenue $300; one best sale; stock gain $113; one mover; no problems | `test_gather_reads_the_week_stock_and_movers` |
| R-03 | A week with no sales | Zeros, no margin, no rows | `test_gather_handles_a_week_without_sales` |
| R-04 | Values as text (Athena) | Correct subject, +20% change, plurals; `<VMAX>` escaped in HTML | `test_render_reads_values_given_as_text_like_athena` |
| R-05 | Handler | Sends to every recipient from the store address; the stored copy equals the email | `test_handler_emails_the_report_and_keeps_a_copy` |
