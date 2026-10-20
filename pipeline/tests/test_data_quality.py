"""Data-quality rules: the model's keys and joins hold, and every dq_results check catches what it is meant to."""

import pytest

from fixtures import clean, dirty
from warehouse.local import build, query

UNIQUE_KEYS = {
    "dim_date": ["date_key"],
    "dim_card": ["card_id"],
    "dim_lot": ["lot_id"],
    "dim_channel": ["channel"],
    "fact_sales": ["sale_id"],
    "fact_price_daily": ["card_id", "date_key"],
    "mart_weekly_pnl": ["week_start"],
    "dash_sales": ["sale_id"],
    "dash_prices": ["card_id", "price_date"],
}

FOREIGN_KEYS = [
    ("fact_sales", "date_key", "dim_date", "date_key"),
    ("fact_sales", "lot_id", "dim_lot", "lot_id"),
    ("fact_sales", "channel", "dim_channel", "channel"),
    ("fact_price_daily", "date_key", "dim_date", "date_key"),
    ("fact_price_daily", "card_id", "dim_card", "card_id"),
]


@pytest.fixture(scope="module")
def db():
    return build(clean())


@pytest.fixture(scope="module")
def checks():
    return {r["check_name"]: r for r in query(build(dirty()), "SELECT * FROM dq_results")}


@pytest.mark.parametrize("view,key", UNIQUE_KEYS.items())
def test_keys_are_unique_and_present(db, view, key):
    cols = ", ".join(key)
    nulls = " OR ".join(f"{c} IS NULL" for c in key)
    assert query(db, f"SELECT {cols} FROM {view} GROUP BY {cols} HAVING count(*) > 1") == []
    assert query(db, f"SELECT * FROM {view} WHERE {nulls}") == []


@pytest.mark.parametrize("fact,column,dim,key", FOREIGN_KEYS)
def test_facts_join_to_their_dimensions(db, fact, column, dim, key):
    orphans = query(db, f"SELECT f.{column} FROM {fact} f LEFT JOIN {dim} d ON d.{key} = f.{column} WHERE d.{key} IS NULL")
    assert orphans == []


def test_clean_data_passes_every_check(db):
    failing = [r for r in query(db, "SELECT * FROM dq_results") if r["failing_rows"]]
    assert failing == []


@pytest.mark.parametrize("check,severity,example", [
    ("oversold_lot", "error", "5"),
    ("orphan_sale", "error", "8"),
    ("sale_before_purchase", "error", "9"),
    ("future_sale", "error", "10"),
    ("negative_amount", "error", "6"),
    ("unpriced_snapshot", "error", "20"),
    ("duplicate_snapshot", "warning", "svp-001"),
    ("exchange_rate_out_of_range", "warning", "22"),
    ("price_spike", "warning", "spk"),
    ("stale_held_price", "warning", "old-1"),
])
def test_each_check_catches_its_problem(checks, check, severity, example):
    result = checks[check]
    assert (result["failing_rows"], result["severity"], result["example"]) == (1, severity, example)
    assert result["description"]


def test_every_check_is_tested(checks):
    assert len(checks) == 10
