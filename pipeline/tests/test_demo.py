import csv
from datetime import date

from warehouse import dashboard, demo
from warehouse.local import build, query

END = date(2026, 10, 18)


def test_demo_data_is_repeatable():
    assert demo.generate(END) == demo.generate(END)
    assert demo.generate(END, seed=1) != demo.generate(END)


def test_demo_data_is_clean_and_looks_like_the_business():
    db = build(demo.generate(END))
    errors = query(db, "SELECT check_name FROM dq_results WHERE severity = 'error' AND failing_rows > 0")
    assert errors == []
    kinds = {r["kind_name"]: r for r in query(db, """
        SELECT kind_name, sum(revenue_cad) AS revenue, sum(gross_profit_cad) / sum(revenue_cad) AS margin
        FROM dash_sales GROUP BY kind_name""")}
    assert kinds["Sealed case"]["margin"] > kinds["Graded slab"]["margin"] > 0
    months = query(db, "SELECT count(*) AS n FROM mart_monthly_pnl")[0]["n"]
    assert months >= 12
    assert query(db, "SELECT count(*) AS n FROM mart_inventory_valuation")[0]["n"] > 0, "some stock is unsold"
    assert query(db, "SELECT max(sold_on) AS last FROM dash_sales")[0]["last"] <= END


def test_writes_every_dashboard_csv(tmp_path):
    counts = demo.write_dashboard(tmp_path, END)
    assert set(counts) == set(dashboard.DASHBOARD_VIEWS)
    with open(tmp_path / "dash_sales.csv", newline="") as f:
        rows = list(csv.DictReader(f))
    assert len(rows) == counts["dash_sales"] > 100
    assert {"sold_on", "item", "kind_name", "channel_name", "revenue_cad", "gross_profit_cad"} <= set(rows[0])
