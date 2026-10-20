"""Lambda entry point: the nightly warehouse refresh.

1. Export ledger lots and sales, products and price snapshots from the app (signed API) into the data lake.
2. Create or replace every warehouse view in Athena (DDL, which Athena doesn't charge for).
3. Run the data-quality checks and report them.
4. Publish the dashboard views as CSV files for Tableau.

The run fails if an export or a view fails. Data-quality errors don't fail it (the data is the problem, not the
job): they are counted in the DataQualityErrors metric, which has its own alarm, and listed in the run log.
"""

from __future__ import annotations

import json
import logging
import os
import time
import uuid
from datetime import datetime, timezone

import warehouse
from price_ingest.app_client import AppClient

from . import dashboard, export
from .athena import Athena

log = logging.getLogger()
log.setLevel(logging.INFO)

METRIC_NAMESPACE = "TailorCards/Warehouse"


def handler(event, context, *, clients=None, app=None, athena=None, now=None):
    import boto3  # provided by the Lambda runtime

    env = os.environ
    environment = env.get("ENVIRONMENT", "staging")
    bucket = env["DATA_BUCKET"]
    clients = clients or {"s3": boto3.client("s3"), "secretsmanager": boto3.client("secretsmanager")}
    if app is None:
        secret = clients["secretsmanager"].get_secret_value(SecretId=env["SHARED_SECRET_ARN"])["SecretString"]
        # A host that sleeps when idle (Render's free tier) can take most of a minute to answer the first call
        app = AppClient(env["APP_BASE_URL"], secret, timeout=float(env.get("APP_TIMEOUT_SECONDS", "60")))
    athena = athena or Athena(boto3.client("athena"), env["ATHENA_WORKGROUP"], env["GLUE_DATABASE"])

    started = now or datetime.now(timezone.utc)
    day = started.strftime("%Y-%m-%d")
    run_id = f"{started.strftime('%Y%m%dT%H%M%SZ')}-{uuid.uuid4().hex[:8]}"
    clock_start = time.monotonic()
    run_log: dict = {"run_id": run_id, "environment": environment, "started_at": started.isoformat(), "status": "running"}
    try:
        run_log["tables"] = [export.export_table(clients["s3"], bucket, app, table, spec, day)
                             for table, spec in warehouse.schema().items()]
        for path in warehouse.view_files():
            athena.run(warehouse.statement(path))
        run_log["views"] = [warehouse.view_name(p) for p in warehouse.view_files()]
        checks = athena.rows("SELECT check_name, severity, failing_rows, example FROM dq_results ORDER BY check_name")
        run_log["checks"] = checks
        run_log["dq_errors"] = sum(1 for c in checks if c["severity"] == "error" and int(c["failing_rows"]) > 0)
        run_log["dq_warnings"] = sum(1 for c in checks if c["severity"] == "warning" and int(c["failing_rows"]) > 0)
        run_log["dashboard"] = dashboard.publish(athena, clients["s3"], bucket)
        run_log["status"] = "succeeded"
        return {"run_id": run_id, "status": "succeeded", "tables": run_log["tables"],
                "dq_errors": run_log["dq_errors"], "dq_warnings": run_log["dq_warnings"]}
    except Exception as err:
        run_log.update(status="failed", error=f"{type(err).__name__}: {err}")
        raise
    finally:
        run_log["duration_ms"] = int((time.monotonic() - clock_start) * 1000)
        run_log["finished_at"] = datetime.now(timezone.utc).isoformat()
        try:
            clients["s3"].put_object(Bucket=bucket, Key=f"runs/warehouse/dt={day}/{run_id}.json",
                                     Body=json.dumps(run_log, indent=2, default=str).encode("utf-8"),
                                     ContentType="application/json")
        except Exception:
            log.exception("Could not write the run log")
        emit_metrics(environment, run_log)


def emit_metrics(environment: str, run_log: dict) -> None:
    """One CloudWatch embedded-metric-format record per run."""
    metrics = {
        "RunSucceeded": 1 if run_log.get("status") == "succeeded" else 0,
        "RowsExported": sum(t["rows"] for t in run_log.get("tables", [])),
        "DataQualityErrors": run_log.get("dq_errors", 0),
        "DataQualityWarnings": run_log.get("dq_warnings", 0),
        "DurationMs": run_log.get("duration_ms", 0),
    }
    print(json.dumps({
        "_aws": {
            "Timestamp": int(time.time() * 1000),
            "CloudWatchMetrics": [{
                "Namespace": METRIC_NAMESPACE,
                "Dimensions": [["Environment"]],
                "Metrics": [{"Name": name, "Unit": "Milliseconds" if name == "DurationMs" else "Count"}
                            for name in metrics],
            }],
        },
        "Environment": environment,
        "RunId": run_log.get("run_id"),
        **metrics,
    }))
