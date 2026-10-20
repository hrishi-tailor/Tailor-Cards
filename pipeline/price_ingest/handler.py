"""Lambda entry point: one daily run of the price pipeline.

1. Read the shared secret from Secrets Manager and fetch the app's watchlist (signed request).
2. Price each card from TCGdex, gently (8 requests a second, with retries).
3. Land every raw response in S3, partitioned by day.
4. Send the prices back to the app, which converts them to CAD and stores them as snapshots.
5. Write a run log to S3 and emit CloudWatch metrics (embedded metric format).

The run fails (so the Lambda Errors alarm fires) when nothing could be priced or too many cards errored.
"""

from __future__ import annotations

import json
import logging
import os
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone

from . import lake, tcgdex
from .app_client import AppClient

log = logging.getLogger()
log.setLevel(logging.INFO)

METRIC_NAMESPACE = "TailorCards/PricePipeline"


class RunFailed(Exception):
    """The run finished but its result isn't good enough to count as a success."""


@dataclass(frozen=True)
class Config:
    environment: str
    app_base_url: str
    secret_arn: str
    bucket: str
    tcgdex_base_url: str = tcgdex.DEFAULT_BASE_URL
    requests_per_second: float = 8.0
    max_error_ratio: float = 0.2

    @classmethod
    def from_env(cls, env=os.environ) -> "Config":
        return cls(
            environment=env.get("ENVIRONMENT", "staging"),
            app_base_url=env["APP_BASE_URL"],
            secret_arn=env["SHARED_SECRET_ARN"],
            bucket=env["DATA_BUCKET"],
            tcgdex_base_url=env.get("TCGDEX_BASE_URL", tcgdex.DEFAULT_BASE_URL),
            requests_per_second=float(env.get("MAX_REQUESTS_PER_SECOND", "8")),
            max_error_ratio=float(env.get("MAX_ERROR_RATIO", "0.2")),
        )


def handler(event, context, *, clients=None, tcgdex_client=None, app_client=None, now=None):
    import boto3  # provided by the Lambda runtime

    config = Config.from_env()
    clients = clients or {"s3": boto3.client("s3"), "secretsmanager": boto3.client("secretsmanager")}
    started = now or datetime.now(timezone.utc)
    run_id = f"{started.strftime('%Y%m%dT%H%M%SZ')}-{uuid.uuid4().hex[:8]}"
    day = started.strftime("%Y-%m-%d")
    clock_start = time.monotonic()

    if app_client is None:
        secret = clients["secretsmanager"].get_secret_value(SecretId=config.secret_arn)["SecretString"]
        app_client = AppClient(config.app_base_url, secret)
    tcgdex_client = tcgdex_client or tcgdex.TcgdexClient(config.tcgdex_base_url, config.requests_per_second)

    run_log = {"run_id": run_id, "environment": config.environment, "started_at": started.isoformat(),
               "source": tcgdex.SOURCE, "status": "running"}
    try:
        watchlist = app_client.watchlist()
        results = [tcgdex_client.price(card_id) for card_id in watchlist]
        counts = {status: sum(1 for r in results if r.status == status)
                  for status in (tcgdex.PRICED, tcgdex.NO_PRICE, tcgdex.NOT_FOUND, tcgdex.ERROR)}
        run_log.update(watchlist_size=len(watchlist), counts=counts,
                       raw_key=lake.put_raw(clients["s3"], config.bucket, day, run_id, results) if results else None)

        prices = [{"cardId": r.card_id, "priceUsd": r.price_usd, "fetchedAt": r.fetched_at}
                  for r in results if r.status == tcgdex.PRICED]
        run_log["app"] = app_client.send_prices(run_id, tcgdex.SOURCE, prices) if prices else {"saved": 0, "duplicates": 0}
        run_log["problems"] = [{"card_id": r.card_id, "status": r.status, "error": r.error}
                               for r in results if r.status != tcgdex.PRICED][:200]

        _check(len(watchlist), counts, config.max_error_ratio)
        run_log["status"] = "succeeded"
        return {"run_id": run_id, "status": "succeeded", "counts": counts, "app": run_log["app"]}
    except Exception as err:
        run_log.update(status="failed", error=f"{type(err).__name__}: {err}")
        raise
    finally:
        run_log["duration_ms"] = int((time.monotonic() - clock_start) * 1000)
        run_log["finished_at"] = datetime.now(timezone.utc).isoformat()
        try:
            run_log["run_key"] = lake.put_run(clients["s3"], config.bucket, day, run_id, run_log)
        except Exception:  # the run's own outcome matters more than its log
            log.exception("Could not write the run log")
        emit_metrics(config.environment, run_log)


def _check(watchlist_size: int, counts: dict, max_error_ratio: float) -> None:
    if watchlist_size == 0:
        return  # nothing to price is not a failure (e.g. a fresh environment)
    if counts[tcgdex.PRICED] == 0:
        raise RunFailed(f"None of the {watchlist_size} cards could be priced")
    if counts[tcgdex.ERROR] / watchlist_size > max_error_ratio:
        raise RunFailed(f"{counts[tcgdex.ERROR]} of {watchlist_size} cards failed (limit {max_error_ratio:.0%})")


def emit_metrics(environment: str, run_log: dict) -> None:
    """Prints one CloudWatch embedded-metric-format record; Lambda's log stream turns it into metrics."""
    counts = run_log.get("counts") or {}
    record = {
        "_aws": {
            "Timestamp": int(time.time() * 1000),
            "CloudWatchMetrics": [{
                "Namespace": METRIC_NAMESPACE,
                "Dimensions": [["Environment"]],
                "Metrics": [
                    {"Name": "RunSucceeded", "Unit": "Count"},
                    {"Name": "WatchlistSize", "Unit": "Count"},
                    {"Name": "CardsPriced", "Unit": "Count"},
                    {"Name": "CardsNoPrice", "Unit": "Count"},
                    {"Name": "CardsNotFound", "Unit": "Count"},
                    {"Name": "CardsFailed", "Unit": "Count"},
                    {"Name": "PricesSaved", "Unit": "Count"},
                    {"Name": "DurationMs", "Unit": "Milliseconds"},
                ],
            }],
        },
        "Environment": environment,
        "RunId": run_log.get("run_id"),
        "RunSucceeded": 1 if run_log.get("status") == "succeeded" else 0,
        "WatchlistSize": run_log.get("watchlist_size", 0),
        "CardsPriced": counts.get(tcgdex.PRICED, 0),
        "CardsNoPrice": counts.get(tcgdex.NO_PRICE, 0),
        "CardsNotFound": counts.get(tcgdex.NOT_FOUND, 0),
        "CardsFailed": counts.get(tcgdex.ERROR, 0),
        "PricesSaved": (run_log.get("app") or {}).get("saved", 0),
        "DurationMs": run_log.get("duration_ms", 0),
    }
    print(json.dumps(record))
