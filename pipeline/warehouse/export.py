"""Copies each app dataset into the data lake as the warehouse's raw tables.

Layout:
  warehouse/raw/<table>/data.jsonl.gz                 the current copy, which the Glue table reads
  warehouse/history/<table>/dt=YYYY-MM-DD/data.jsonl.gz  that day's copy, kept for point-in-time analysis
"""

from __future__ import annotations

import gzip
import json
import logging
from decimal import Decimal

log = logging.getLogger(__name__)

PAGE_SIZE = 1_000
MAX_PAGES = 1_000  # 1 M rows: far beyond this store, and a guard against a cursor that never ends


def current_key(table: str) -> str:
    return f"warehouse/raw/{table}/data.jsonl.gz"


def history_key(table: str, day: str) -> str:
    return f"warehouse/history/{table}/dt={day}/data.jsonl.gz"


def fetch(app, dataset: str) -> list[dict]:
    rows: list[dict] = []
    after = 0
    for _ in range(MAX_PAGES):
        page = app.export_page(dataset, after, PAGE_SIZE)
        rows.extend(page["rows"])
        if page.get("nextAfter") is None:
            return rows
        if page["nextAfter"] <= after:
            raise ValueError(f"The {dataset} export's cursor did not advance past {after}")
        after = page["nextAfter"]
    raise ValueError(f"The {dataset} export has more than {MAX_PAGES} pages")


def conform(rows: list[dict], columns: list[str]) -> tuple[list[dict], set[str]]:
    """Keeps the schema's columns in order (missing ones as null); returns the columns the app sent that the schema
    doesn't have, so a new column is noticed instead of silently ignored."""
    extra: set[str] = set()
    out = []
    for row in rows:
        extra |= set(row) - set(columns)
        out.append({c: row.get(c) for c in columns})
    return out, extra


def encode(rows: list[dict]) -> bytes:
    text = "".join(json.dumps(r, separators=(",", ":"), default=_json_default) + "\n" for r in rows)
    return gzip.compress(text.encode("utf-8"), mtime=0)


def export_table(s3, bucket: str, app, table: str, spec: dict, day: str) -> dict:
    columns = [name for name, _ in spec["columns"]]
    rows, extra = conform(fetch(app, spec["dataset"]), columns)
    if extra:
        log.warning("%s: the app sent columns the schema doesn't have: %s", table, sorted(extra))
    body = encode(rows)
    for key in (history_key(table, day), current_key(table)):
        s3.put_object(Bucket=bucket, Key=key, Body=body, ContentType="application/x-ndjson", ContentEncoding="gzip")
    return {"table": table, "rows": len(rows), "unknown_columns": sorted(extra)}


def _json_default(value):
    if isinstance(value, Decimal):
        return float(value)
    raise TypeError(f"Not JSON serialisable: {type(value).__name__}")
