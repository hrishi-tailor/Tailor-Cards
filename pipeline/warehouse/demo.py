"""A synthetic year of trading for demos and a public Tableau workbook. No real store data is used.

Purchases arrive every week and sell over the following weeks across card shows, Facebook Marketplace, eBay and the
website, at margins typical of each kind of stock. Cards with ids get a daily price that drifts. The data goes
through the real warehouse views (on DuckDB) and comes out as the same CSV files the nightly run publishes:

    uv run --group dev python -m warehouse.demo demo-output
"""

from __future__ import annotations

import random
import sys
from datetime import date, timedelta
from pathlib import Path

from . import dashboard
from .local import build

USD_TO_CAD = 1.38

# (title, kind, card id or None, units per lot, market price CAD per unit, cost as a share of market)
CATALOG = [
    ("Prismatic Evolutions ETB case", "SEALED_CASE", None, 10, 230, 0.40),
    ("Destined Rivals ETB case", "SEALED_CASE", None, 10, 150, 0.53),
    ("Surging Sparks booster box", "SEALED", None, 2, 260, 0.70),
    ("151 Ultra Premium Collection", "SEALED", None, 1, 210, 0.70),
    ("Umbreon VMAX 215/203", "SINGLE", "swsh7-215", 1, 2400, 0.70),
    ("Charizard ex 199/165", "SINGLE", "sv3pt5-199", 1, 330, 0.70),
    ("Pikachu with Grey Felt Hat", "SINGLE", "svp-085", 2, 1100, 0.70),
    ("Mew ex 232/091", "SINGLE", "sv4pt5-232", 1, 260, 0.70),
    ("Giratina V 186/196 PSA 10", "GRADED", "swsh11-186", 1, 1350, 0.80),
    ("Rayquaza VMAX 218/203 PSA 10", "GRADED", "swsh7-218", 1, 1050, 0.80),
    ("Bulk holo lot", "LOT", None, 50, 4, 0.50),
]
CHANNELS = [("CARD_SHOW", 0.40), ("FACEBOOK_MARKETPLACE", 0.50), ("EBAY", 0.07), ("WEBSITE", 0.03)]
PAYMENTS = {"CARD_SHOW": "CASH", "FACEBOOK_MARKETPLACE": "E_TRANSFER", "EBAY": "PAYPAL", "WEBSITE": "CARD"}


def generate(end: date, seed: int = 7, days: int = 365) -> dict[str, list[dict]]:
    rng = random.Random(seed)
    start = end - timedelta(days=days)
    lots, sales, prices = [], [], []
    market = {card: price for _, _, card, _, price, _ in CATALOG if card}
    price_history: dict[str, dict[date, float]] = {card: {} for card in market}

    # A daily price for every card, drifting a little each day
    for offset in range(days + 1):
        day = start + timedelta(days=offset)
        for card in market:
            market[card] *= 1 + rng.gauss(0.0005, 0.015)
            price_history[card][day] = round(market[card], 2)

    for week in range(days // 7):
        for _ in range(rng.randint(2, 4)):
            title, kind, card, units, base, cost_share = rng.choice(CATALOG)
            bought = start + timedelta(days=week * 7 + rng.randint(0, 6))
            unit_market = price_history[card][bought] if card else base * rng.uniform(0.9, 1.1)
            lot_id = len(lots) + 1
            listed = bought + timedelta(days=rng.randint(0, 5))
            lots.append({
                "id": lot_id, "title": title, "kind": kind, "card_ref": card, "quantity": units,
                "total_cost_cad": round(unit_market * units * cost_share * rng.uniform(0.95, 1.05), 2),
                "acquired_on": bought, "acquired_channel": rng.choice(["CARD_SHOW", "FACEBOOK_MARKETPLACE"]),
                "listed_on": listed, "listed_price_cad": round(unit_market, 2), "source": "MANUAL",
            })
            left = units
            sold_on = listed
            while left > 0:
                sold_on += timedelta(days=rng.randint(1, 12 if kind != "GRADED" else 30))
                if sold_on > end:
                    break  # still in stock
                quantity = min(left, rng.randint(1, max(1, units // 3)))
                channel = rng.choices([c for c, _ in CHANNELS], [w for _, w in CHANNELS])[0]
                giveaway = kind == "SEALED_CASE" and rng.random() < 0.02
                price = price_history[card][sold_on] if card else unit_market
                sales.append({
                    "id": len(sales) + 1, "lot_id": lot_id, "sold_on": sold_on, "quantity": quantity,
                    "unit_price_cad": 0 if giveaway else round(price * rng.uniform(0.92, 1.0), 2),
                    "channel": channel, "payment_method": PAYMENTS[channel], "source": "MANUAL",
                })
                left -= quantity

    for card, history in price_history.items():
        for day, cad in history.items():
            prices.append({"id": len(prices) + 1, "card_id": card, "price_cad": cad,
                           "price_usd": round(cad / USD_TO_CAD, 2), "source": "TCGDEX",
                           "fetched_at": f"{day.isoformat()}T07:00:00Z"})

    products = [{"id": i + 1, "name": title, "category": kind.title(), "price": round(base * 1.1, 2), "stock": 1,
                 "pokemontcg_id": card, "status": "AVAILABLE"}
                for i, (title, kind, card, _, base, _) in enumerate(CATALOG) if card]
    return {"raw_ledger_lots": lots, "raw_ledger_sales": sales, "raw_products": products, "raw_price_snapshots": prices}


def write_dashboard(out_dir: Path, end: date | None = None) -> dict[str, int]:
    """Writes every dashboard view's CSV for the demo data; returns the row count of each."""
    from .local import query

    db = build(generate(end or date.today()))
    out_dir.mkdir(parents=True, exist_ok=True)
    counts = {}
    for view in dashboard.DASHBOARD_VIEWS:
        cursor = db.execute(f"SELECT * FROM {view}")
        header = [d[0] for d in cursor.description]
        rows = cursor.fetchall()
        (out_dir / f"{view}.csv").write_bytes(dashboard.to_csv(header, [list(r) for r in rows]))
        counts[view] = len(rows)
    failing = [r["check_name"] for r in query(db, "SELECT * FROM dq_results WHERE failing_rows > 0 AND severity = 'error'")]
    if failing:
        raise RuntimeError(f"The demo data fails data-quality checks: {failing}")
    return counts


if __name__ == "__main__":
    target = Path(sys.argv[1] if len(sys.argv) > 1 else "demo-output")
    for view, rows in write_dashboard(target).items():
        print(f"{target / (view + '.csv')}: {rows} rows")
