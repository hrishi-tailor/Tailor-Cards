from datetime import date, timedelta

import boto3
import pytest
from moto import mock_aws

from fixtures import clean, day
from warehouse import report, report_handler
from warehouse.local import build, query


@pytest.fixture(scope="module")
def db():
    return build(clean())


def week_of(d: date) -> date:
    return d - timedelta(days=d.weekday())


def test_last_week_is_the_previous_full_monday_to_sunday():
    assert report.last_week(date(2026, 10, 19)) == date(2026, 10, 12)  # a Monday
    assert report.last_week(date(2026, 10, 25)) == date(2026, 10, 12)  # the Sunday after


def test_gather_reads_the_week_stock_and_movers(db):
    data = report.gather(lambda sql: query(db, sql), week_of(day(-5)))
    assert data["week"]["revenue_cad"] == pytest.approx(300)
    assert [s["item"] for s in data["top_sales"]] == ["Charizard ex 199 PSA 10"]
    assert data["stock"]["lots"] == 2 and data["stock"]["gain"] == pytest.approx(113)
    assert [m["card_name"] for m in data["movers"]] == ["Charizard ex SIR"]
    assert data["problems"] == []


def test_gather_handles_a_week_without_sales(db):
    data = report.gather(lambda sql: query(db, sql), week_of(day(400)))
    assert data["week"]["revenue_cad"] == 0 and data["week"]["margin_pct"] is None and data["top_sales"] == []


def test_render_reads_values_given_as_text_like_athena():
    data = {
        "week_start": date(2026, 10, 12),
        "week": {"sales": 3, "units": 4, "revenue_cad": 1200, "cost_cad": 700, "gross_profit_cad": 500, "margin_pct": 41.7},
        "previous": {"sales": 1, "units": 1, "revenue_cad": 1000, "cost_cad": 600, "gross_profit_cad": 400, "margin_pct": 40.0},
        "top_sales": [{"item": "Umbreon <VMAX>", "channel_name": "Card show", "units": "1", "revenue_cad": "1300.00",
                       "gross_profit_cad": "400.00"}],
        "stock": {"lots": 2, "units": 3, "cost": 230, "market_value": 343, "gain": 113, "unpriced": 0},
        "movers": [{"card_name": "Charizard ex SIR", "price_cad": "322.0", "change_pct": "15.0"}],
        "problems": [{"check_name": "price_spike", "severity": "warning", "failing_rows": "1", "example": "spk"}],
    }
    subject, html_body, text = report.render(data)
    assert subject == "Tailor Cards week of Oct 12: $500.00 profit on $1,200.00"
    assert "Revenue: $1,200.00 (+20% vs the week before)" in text
    assert "Margin: 41.7% (3 sales, 4 units)" in text
    assert "- Charizard ex SIR | $322.00 | +15.0%" in text
    assert "- price_spike | warning | 1 | spk" in text
    assert "Umbreon &lt;VMAX&gt;" in html_body and "<VMAX>" not in html_body


def test_money_and_change():
    assert report.money(-9) == "-$9.00" and report.money("1234.5") == "$1,234.50"
    assert report.change(0, 0) == "no change" and report.change(5, 0) == "new"
    assert (report.count(1, "sale"), report.count(0, "lot"), report.count(3.0, "unit")) == ("1 sale", "0 lots", "3 units")


def test_handler_emails_the_report_and_keeps_a_copy(db, monkeypatch):
    monkeypatch.setenv("AWS_DEFAULT_REGION", "us-east-2")
    for name, value in {"DATA_BUCKET": "lake", "REPORT_FROM": "no-reply@staging.example.test",
                        "REPORT_RECIPIENTS": "owner@example.test, partner@example.test",
                        "ATHENA_WORKGROUP": "wg", "GLUE_DATABASE": "db"}.items():
        monkeypatch.setenv(name, value)

    class Rows:
        def rows(self, sql):
            return query(db, sql)

    sent = []

    class Ses:
        def send_email(self, **kwargs):
            sent.append(kwargs)
            return {"MessageId": "m-1"}

    with mock_aws():
        s3 = boto3.client("s3", region_name="us-east-2")
        s3.create_bucket(Bucket="lake", CreateBucketConfiguration={"LocationConstraint": "us-east-2"})
        today = week_of(day(-5)) + timedelta(days=7)
        result = report_handler.handler({}, None, athena=Rows(), ses=Ses(), s3=s3, today=today)
        stored = s3.get_object(Bucket="lake", Key=f"reports/weekly/{week_of(day(-5)).isoformat()}.html")["Body"].read()

    assert result["message_id"] == "m-1"
    assert sent[0]["Destination"]["ToAddresses"] == ["owner@example.test", "partner@example.test"]
    assert sent[0]["FromEmailAddress"] == "no-reply@staging.example.test"
    assert stored.decode() == sent[0]["Content"]["Simple"]["Body"]["Html"]["Data"]
