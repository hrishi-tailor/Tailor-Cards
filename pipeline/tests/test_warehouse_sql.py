"""The star schema and marts, run on DuckDB against synthetic data with hand-checked answers."""

import re

import pytest

import warehouse
from fixtures import TODAY, clean, day
from warehouse.local import build, query


@pytest.fixture(scope="module")
def db():
    return build(clean())


def one(db, sql):
    rows = query(db, sql)
    assert len(rows) == 1, rows
    return rows[0]


def test_every_file_defines_the_view_it_is_named_after():
    for path in warehouse.view_files():
        sql = warehouse.statement(path)
        assert re.match(rf"CREATE OR REPLACE VIEW {warehouse.view_name(path)} AS\b", sql), path.name
        assert ";" not in sql, f"{path.name}: Athena runs one statement per query"


def test_sql_is_portable():
    """Keeps the views to Trino SQL that the DuckDB shims cover, so the tests mean something on Athena."""
    duckdb_only = r"::|\b(generate_series|strftime|median|list_\w+|struct_pack|isodow|epoch)\s*\("
    for path in warehouse.view_files():
        sql = warehouse.statement(path).lower()
        found = re.search(duckdb_only, sql)
        assert found is None, f"{path.name} uses DuckDB-only {found.group(0)!r}"
        for call in re.findall(r"approx_percentile\([^)]*\)", sql):
            assert call.endswith(", 0.5)"), f"{path.name}: the DuckDB shim only covers the median"


def test_case_sold_in_parts_matches_the_app(db):
    # The same case as the app's LedgerServiceTest.caseSoldInParts (L-01)
    lot = one(db, """SELECT sum(units) AS units, sum(revenue_cad) AS revenue, sum(cost_cad) AS cost,
                            sum(gross_profit_cad) AS profit FROM fact_sales WHERE lot_id = 1""")
    assert lot == {"units": 20, "revenue": pytest.approx(4350), "cost": pytest.approx(1832),
                   "profit": pytest.approx(2518)}
    giveaway = one(db, "SELECT cost_cad, gross_profit_cad, is_giveaway FROM fact_sales WHERE sale_id = 3")
    assert giveaway == {"cost_cad": pytest.approx(91.6), "gross_profit_cad": pytest.approx(-91.6), "is_giveaway": True}


def test_sale_facts(db):
    sale = one(db, "SELECT * FROM fact_sales WHERE sale_id = 5")
    assert sale["card_id"] == "sv3pt5-199" and sale["kind"] == "GRADED" and sale["channel"] == "EBAY"
    assert sale["cost_cad"] == pytest.approx(200) and sale["gross_profit_cad"] == pytest.approx(100)
    assert (sale["days_listed"], sale["days_held"]) == (25, 25)
    assert sale["date_key"] == day(-5)


def test_monthly_pnl_adds_up_to_the_facts(db):
    total = one(db, """SELECT sum(sales) AS sales, sum(units) AS units, sum(revenue_cad) AS revenue,
                              sum(cost_cad) AS cost, sum(gross_profit_cad) AS profit, sum(units_given_away) AS given
                       FROM mart_monthly_pnl""")
    assert total == {"sales": 5, "units": 22, "revenue": pytest.approx(5950), "cost": pytest.approx(2932),
                     "profit": pytest.approx(3018), "given": 1}


def test_kind_and_channel(db):
    row = one(db, "SELECT * FROM mart_kind_channel WHERE kind_name = 'Sealed case' AND channel_name = 'Facebook Marketplace'")
    assert (row["sales"], row["units"]) == (2, 19)
    assert row["revenue_cad"] == pytest.approx(4350)
    assert row["gross_profit_cad"] == pytest.approx(4350 - 1740.4)
    assert row["margin_pct"] == pytest.approx(60.0)
    assert row["median_days_listed"] == pytest.approx(6)
    assert one(db, "SELECT channel_type FROM mart_kind_channel WHERE channel_name = 'eBay'")["channel_type"] == "online"


def test_inventory_valuation_uses_the_latest_market_price(db):
    rows = {r["lot_id"]: r for r in query(db, "SELECT * FROM mart_inventory_valuation")}
    assert set(rows) == {3, 4}, "sold-out lots are left out"
    slab = rows[3]
    assert slab["units_on_hand"] == 1 and slab["cost_on_hand_cad"] == pytest.approx(200)
    assert slab["market_price_cad"] == pytest.approx(322), "latest snapshot of the latest day"
    assert slab["unrealised_gain_cad"] == pytest.approx(122)
    assert slab["price_age_days"] == 1
    promo = rows[4]
    assert promo["market_value_cad"] == pytest.approx(21) and promo["unrealised_gain_cad"] == pytest.approx(-9)


def test_price_movers_compare_with_a_week_earlier(db):
    rows = {r["card_id"]: r for r in query(db, "SELECT * FROM mart_price_movers")}
    charizard = rows["sv3pt5-199"]
    assert charizard["previous_price_cad"] == pytest.approx(280) and charizard["compared_with"] == day(-9)
    assert charizard["change_cad"] == pytest.approx(42) and charizard["change_pct"] == pytest.approx(15.0)
    assert rows["swsh7-215"]["previous_price_cad"] is None, "no price a week earlier"


def test_cards_are_named_from_the_site_then_the_ledger(db):
    cards = {r["card_id"]: r for r in query(db, "SELECT * FROM dim_card")}
    assert cards["sv3pt5-199"]["card_name"] == "Charizard ex SIR"
    assert cards["swsh7-215"]["card_name"] == "Umbreon VMAX 215/203"
    assert cards["swsh7-215"]["listed_on_site"] is False and cards["swsh7-215"]["in_ledger"] is True


def test_calendar_runs_to_today_without_gaps(db):
    cal = one(db, "SELECT min(date_key) AS first, max(date_key) AS last, count(*) AS days FROM dim_date")
    assert cal["first"] == day(-60) and cal["last"] == TODAY
    assert cal["days"] == 61
    weekend = one(db, f"SELECT day_of_week, is_weekend FROM dim_date WHERE date_key = DATE '{TODAY.isoformat()}'")
    assert weekend["is_weekend"] == (TODAY.isoweekday() >= 6)


def test_weekly_pnl_starts_weeks_on_monday_and_adds_up(db):
    weeks = query(db, "SELECT * FROM mart_weekly_pnl")
    assert all(w["week_start"].isoweekday() == 1 for w in weeks)
    assert sum(w["revenue_cad"] for w in weeks) == pytest.approx(5950)
    assert sum(w["sales"] for w in weeks) == 5


def test_dashboard_sales_extract_is_flat_and_complete(db):
    rows = query(db, "SELECT * FROM dash_sales ORDER BY sale_id")
    assert [r["sale_id"] for r in rows] == [1, 2, 3, 4, 5]
    ebay = rows[4]
    assert (ebay["item"], ebay["kind_name"], ebay["card_name"], ebay["channel_name"]) == (
        "Charizard ex 199 PSA 10", "Graded slab", "Charizard ex SIR", "eBay")
    assert rows[0]["card_name"] == "Prismatic Evolutions ETB case x2", "lots without a card id use their title"


def test_dashboard_prices_cover_held_or_listed_cards(db):
    cards = {r["card_id"] for r in query(db, "SELECT DISTINCT card_id FROM dash_prices")}
    assert cards == {"swsh7-215", "sv3pt5-199", "svp-001"}
