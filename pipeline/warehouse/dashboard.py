"""Publishes the dashboard views as CSV files for Tableau.

  dashboard/<view>.csv   replaced every night; Tableau Desktop or Public opens them as text files
"""

from __future__ import annotations

import csv
import io

DASHBOARD_VIEWS = (
    "dash_sales",
    "dash_prices",
    "mart_monthly_pnl",
    "mart_weekly_pnl",
    "mart_kind_channel",
    "mart_inventory_valuation",
    "mart_price_movers",
    "dq_results",
)


def key(view: str) -> str:
    return f"dashboard/{view}.csv"


def to_csv(header: list[str], rows: list[list]) -> bytes:
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(header)
    writer.writerows([["" if v is None else v for v in row] for row in rows])
    return buffer.getvalue().encode("utf-8")


def publish(athena, s3, bucket: str) -> list[dict]:
    published = []
    for view in DASHBOARD_VIEWS:
        header, rows = athena.table(f"SELECT * FROM {view}")
        s3.put_object(Bucket=bucket, Key=key(view), Body=to_csv(header, rows), ContentType="text/csv")
        published.append({"view": view, "rows": len(rows)})
    return published
