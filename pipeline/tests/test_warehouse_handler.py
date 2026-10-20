import json
from datetime import datetime, timezone

import boto3
import pytest
from moto import mock_aws

import warehouse
from warehouse import dashboard, handler

BUCKET = "tc-lake"
NOW = datetime(2026, 10, 20, 8, 30, tzinfo=timezone.utc)


class App:
    def __init__(self, fail=None):
        self.fail = fail

    def export_page(self, dataset, after, limit):
        if dataset == self.fail:
            raise RuntimeError("app is down")
        return {"rows": [{"id": 1}], "nextAfter": None}


class FakeAthena:
    def __init__(self, checks):
        self.statements = []
        self.checks = checks

    def run(self, sql):
        self.statements.append(sql)
        return "q"

    def rows(self, sql):
        self.statements.append(sql)
        return self.checks

    def table(self, sql):
        self.statements.append(sql)
        return ["sale_id", "item"], [["1", "Case, sealed"], ["2", None]]


CLEAN = [{"check_name": "oversold_lot", "severity": "error", "failing_rows": "0", "example": None},
         {"check_name": "price_spike", "severity": "warning", "failing_rows": "2", "example": "spk"}]


@pytest.fixture
def aws(monkeypatch):
    for name, value in {"AWS_DEFAULT_REGION": "us-east-2", "DATA_BUCKET": BUCKET, "ENVIRONMENT": "staging",
                        "APP_BASE_URL": "https://api.example.test", "SHARED_SECRET_ARN": "s",
                        "ATHENA_WORKGROUP": "wg", "GLUE_DATABASE": "db"}.items():
        monkeypatch.setenv(name, value)
    with mock_aws():
        s3 = boto3.client("s3", region_name="us-east-2")
        s3.create_bucket(Bucket=BUCKET, CreateBucketConfiguration={"LocationConstraint": "us-east-2"})
        yield {"s3": s3, "secretsmanager": boto3.client("secretsmanager", region_name="us-east-2")}


def keys(s3, prefix):
    return sorted(o["Key"] for o in s3.list_objects_v2(Bucket=BUCKET, Prefix=prefix).get("Contents", []))


def test_a_run_exports_every_table_refreshes_the_views_and_reports_quality(aws, capsys):
    athena = FakeAthena(CLEAN)
    result = handler.handler({}, None, clients=aws, app=App(), athena=athena, now=NOW)

    assert result["status"] == "succeeded"
    assert (result["dq_errors"], result["dq_warnings"]) == (0, 1)
    tables = list(warehouse.schema())
    assert keys(aws["s3"], "warehouse/raw/") == sorted(f"warehouse/raw/{t}/data.jsonl.gz" for t in tables)
    assert len(keys(aws["s3"], "warehouse/history/")) == len(tables)

    views = [s for s in athena.statements if s.startswith("CREATE OR REPLACE VIEW")]
    assert len(views) == len(warehouse.view_files())
    assert any(s.startswith("SELECT check_name") for s in athena.statements)
    assert keys(aws["s3"], "dashboard/") == sorted(f"dashboard/{v}.csv" for v in dashboard.DASHBOARD_VIEWS)
    sales_csv = aws["s3"].get_object(Bucket=BUCKET, Key="dashboard/dash_sales.csv")["Body"].read().decode()
    assert sales_csv == 'sale_id,item\n1,"Case, sealed"\n2,\n'

    run_log = json.loads(aws["s3"].get_object(Bucket=BUCKET, Key=keys(aws["s3"], "runs/warehouse/dt=2026-10-20/")[0])["Body"].read())
    assert run_log["status"] == "succeeded" and run_log["checks"] == CLEAN
    metrics = json.loads(capsys.readouterr().out.strip().splitlines()[-1])
    assert metrics["_aws"]["CloudWatchMetrics"][0]["Namespace"] == "TailorCards/Warehouse"
    assert (metrics["RunSucceeded"], metrics["RowsExported"], metrics["DataQualityWarnings"]) == (1, len(tables), 1)


def test_data_quality_errors_are_counted_not_raised(aws, capsys):
    checks = [{"check_name": "oversold_lot", "severity": "error", "failing_rows": "1", "example": "5"}]
    result = handler.handler({}, None, clients=aws, app=App(), athena=FakeAthena(checks), now=NOW)
    assert result["status"] == "succeeded" and result["dq_errors"] == 1
    assert json.loads(capsys.readouterr().out.strip().splitlines()[-1])["DataQualityErrors"] == 1


def test_an_export_failure_fails_the_run_and_is_logged(aws, capsys):
    athena = FakeAthena(CLEAN)
    with pytest.raises(RuntimeError, match="app is down"):
        handler.handler({}, None, clients=aws, app=App(fail="ledger_sales"), athena=athena, now=NOW)
    assert athena.statements == [], "views aren't touched when an export fails"
    run_log = json.loads(aws["s3"].get_object(Bucket=BUCKET, Key=keys(aws["s3"], "runs/warehouse/")[0])["Body"].read())
    assert run_log["status"] == "failed" and "app is down" in run_log["error"]
    assert json.loads(capsys.readouterr().out.strip().splitlines()[-1])["RunSucceeded"] == 0
