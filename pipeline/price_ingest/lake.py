"""Writes to the S3 data lake.

Layout (Hive-style partitions, so Athena and Glue can read them directly):
  raw/tcgdex/dt=YYYY-MM-DD/<run_id>.jsonl.gz   one line per card: id, outcome, fetch time, response body
  runs/dt=YYYY-MM-DD/<run_id>.json             the run log: counts, timings, failures
"""

from __future__ import annotations

import gzip
import json
from typing import Iterable

from .tcgdex import CardResult


def raw_key(day: str, run_id: str) -> str:
    return f"raw/tcgdex/dt={day}/{_safe(run_id)}.jsonl.gz"


def run_key(day: str, run_id: str) -> str:
    return f"runs/dt={day}/{_safe(run_id)}.json"


def raw_lines(results: Iterable[CardResult], run_id: str) -> bytes:
    lines = (json.dumps({
        "run_id": run_id,
        "card_id": r.card_id,
        "status": r.status,
        "fetched_at": r.fetched_at,
        "price_usd": str(r.price_usd) if r.price_usd is not None else None,
        "variant": r.variant,
        "error": r.error,
        "response": r.raw,
    }, separators=(",", ":")) for r in results)
    return gzip.compress(("\n".join(lines) + "\n").encode("utf-8"), mtime=0)


def put_raw(s3, bucket: str, day: str, run_id: str, results: list[CardResult]) -> str:
    key = raw_key(day, run_id)
    s3.put_object(Bucket=bucket, Key=key, Body=raw_lines(results, run_id),
                  ContentType="application/x-ndjson", ContentEncoding="gzip")
    return key


def put_run(s3, bucket: str, day: str, run_id: str, run_log: dict) -> str:
    key = run_key(day, run_id)
    s3.put_object(Bucket=bucket, Key=key, Body=json.dumps(run_log, indent=2, default=str).encode("utf-8"),
                  ContentType="application/json")
    return key


def _safe(run_id: str) -> str:
    return "".join(c if c.isalnum() or c in "-_." else "-" for c in run_id)
