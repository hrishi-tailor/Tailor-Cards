import gzip
import json
from datetime import datetime, timezone
from decimal import Decimal

import boto3
import pytest
from moto import mock_aws

from price_ingest import handler, tcgdex

BUCKET = "tc-staging-price-lake"
NOW = datetime(2026, 10, 20, 8, 0, tzinfo=timezone.utc)


class FakeTcgdex:
    def __init__(self, outcomes):
        self.outcomes = outcomes

    def price(self, card_id):
        status = self.outcomes[card_id]
        price = Decimal("10.00") if status == tcgdex.PRICED else None
        return tcgdex.CardResult(card_id, status, "2026-10-20T08:00:00Z", price, "normal" if price else None, "{}")


class FakeApp:
    def __init__(self, watchlist):
        self._watchlist = watchlist
        self.sent = []

    def watchlist(self):
        return self._watchlist

    def send_prices(self, run_id, source, prices):
        self.sent.append((run_id, source, prices))
        return {"saved": len(prices), "duplicates": 0}


@pytest.fixture
def aws(monkeypatch):
    monkeypatch.setenv("AWS_DEFAULT_REGION", "us-east-2")
    monkeypatch.setenv("APP_BASE_URL", "https://staging.tailorcards.test")
    monkeypatch.setenv("SHARED_SECRET_ARN", "pipeline-secret")
    monkeypatch.setenv("DATA_BUCKET", BUCKET)
    monkeypatch.setenv("ENVIRONMENT", "staging")
    with mock_aws():
        s3 = boto3.client("s3", region_name="us-east-2")
        s3.create_bucket(Bucket=BUCKET, CreateBucketConfiguration={"LocationConstraint": "us-east-2"})
        secrets = boto3.client("secretsmanager", region_name="us-east-2")
        secrets.create_secret(Name="pipeline-secret", SecretString="x" * 40)
        yield {"s3": s3, "secretsmanager": secrets}


def run(aws, watchlist, outcomes, **kwargs):
    app = FakeApp(watchlist)
    result = handler.handler({}, None, clients=aws, tcgdex_client=FakeTcgdex(outcomes), app_client=app, now=NOW, **kwargs)
    return result, app


def objects(s3, prefix):
    return [o["Key"] for o in s3.list_objects_v2(Bucket=BUCKET, Prefix=prefix).get("Contents", [])]


def test_a_good_run_lands_raw_data_sends_prices_and_logs(aws, capsys):
    result, app = run(aws, ["a", "b", "c"], {"a": tcgdex.PRICED, "b": tcgdex.PRICED, "c": tcgdex.NOT_FOUND})

    assert result["status"] == "succeeded"
    assert result["counts"] == {"priced": 2, "no_price": 0, "not_found": 1, "error": 0}
    run_id, source, prices = app.sent[0]
    assert source == "TCGDEX" and [p["cardId"] for p in prices] == ["a", "b"]

    raw = objects(aws["s3"], "raw/tcgdex/dt=2026-10-20/")
    assert len(raw) == 1
    body = aws["s3"].get_object(Bucket=BUCKET, Key=raw[0])["Body"].read()
    assert len(gzip.decompress(body).decode().splitlines()) == 3

    run_log = json.loads(aws["s3"].get_object(Bucket=BUCKET, Key=objects(aws["s3"], "runs/dt=2026-10-20/")[0])["Body"].read())
    assert run_log["status"] == "succeeded" and run_log["run_id"] == run_id
    assert run_log["problems"] == [{"card_id": "c", "status": "not_found", "error": None}]

    metrics = json.loads(capsys.readouterr().out.strip().splitlines()[-1])
    assert metrics["RunSucceeded"] == 1 and metrics["CardsPriced"] == 2 and metrics["PricesSaved"] == 2
    assert metrics["_aws"]["CloudWatchMetrics"][0]["Namespace"] == "TailorCards/PricePipeline"


def test_too_many_errors_fails_the_run_but_still_logs_it(aws, capsys):
    with pytest.raises(handler.RunFailed):
        run(aws, ["a", "b", "c"], {"a": tcgdex.PRICED, "b": tcgdex.ERROR, "c": tcgdex.ERROR})

    run_log = json.loads(aws["s3"].get_object(Bucket=BUCKET, Key=objects(aws["s3"], "runs/")[0])["Body"].read())
    assert run_log["status"] == "failed" and "2 of 3 cards failed" in run_log["error"]
    assert json.loads(capsys.readouterr().out.strip().splitlines()[-1])["RunSucceeded"] == 0


def test_nothing_priced_fails_the_run(aws):
    with pytest.raises(handler.RunFailed, match="None of the 2 cards"):
        run(aws, ["a", "b"], {"a": tcgdex.NO_PRICE, "b": tcgdex.NOT_FOUND})


def test_an_empty_watchlist_is_not_a_failure(aws):
    result, app = run(aws, [], {})
    assert result["status"] == "succeeded" and app.sent == []
    assert objects(aws["s3"], "raw/") == []


def test_reads_the_shared_secret_from_secrets_manager(aws, monkeypatch):
    seen = {}

    class CapturingApp(FakeApp):
        def __init__(self, base_url, secret):
            super().__init__([])
            seen.update(base_url=base_url, secret=secret)

    monkeypatch.setattr(handler, "AppClient", CapturingApp)
    handler.handler({}, None, clients=aws, tcgdex_client=FakeTcgdex({}), now=NOW)
    assert seen == {"base_url": "https://staging.tailorcards.test", "secret": "x" * 40}
