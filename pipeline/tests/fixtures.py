"""Synthetic warehouse data (never real store data), dated relative to today so date checks stay meaningful."""

from __future__ import annotations

from datetime import date, timedelta

TODAY = date.today()


def day(offset: int) -> date:
    return TODAY + timedelta(days=offset)


def at(offset: int, hour: int = 8) -> str:
    return f"{day(offset).isoformat()}T{hour:02d}:00:00Z"


def lot(id, title, kind, card_ref, quantity, cost, acquired, listed=None, channel="CARD_SHOW"):
    return {"id": id, "title": title, "kind": kind, "card_ref": card_ref, "quantity": quantity,
            "total_cost_cad": cost, "acquired_on": day(acquired), "acquired_channel": channel,
            "listed_on": day(listed) if listed is not None else None, "source": "MANUAL"}


def sale(id, lot_id, sold, quantity, price, channel="CARD_SHOW"):
    return {"id": id, "lot_id": lot_id, "sold_on": day(sold), "quantity": quantity, "unit_price_cad": price,
            "channel": channel, "payment_method": "CASH", "source": "MANUAL"}


def price(id, card_id, usd, cad, fetched, hour=8, source="TCGDEX"):
    return {"id": id, "card_id": card_id, "price_usd": usd, "price_cad": cad, "source": source,
            "fetched_at": at(fetched, hour)}


def clean() -> dict[str, list[dict]]:
    return {
        "raw_ledger_lots": [
            lot(1, "Prismatic Evolutions ETB case x2", "SEALED_CASE", None, 20, 1832, -60, -55),
            lot(2, "Umbreon VMAX 215/203", "SINGLE", "swsh7-215", 1, 900, -40, -38),
            lot(3, "Charizard ex 199 PSA 10", "GRADED", "sv3pt5-199", 2, 400, -30, -30),
            lot(4, "Pikachu promo", "SINGLE", "svp-001", 3, 30, -10),
        ],
        "raw_ledger_sales": [
            sale(1, 1, -50, 17, 230, "FACEBOOK_MARKETPLACE"),
            sale(2, 1, -48, 2, 220, "FACEBOOK_MARKETPLACE"),
            sale(3, 1, -45, 1, 0),
            sale(4, 2, -20, 1, 1300),
            sale(5, 3, -5, 1, 300, "EBAY"),
        ],
        "raw_products": [
            {"id": 1, "name": "Pikachu Promo", "category": "Singles", "price": 9.99, "stock": 3, "pokemontcg_id": "svp-001",
             "status": "AVAILABLE"},
            {"id": 2, "name": "Charizard ex SIR", "category": "Graded", "card_set": "151", "card_number": "199",
             "price": 450, "stock": 1, "pokemontcg_id": "sv3pt5-199", "status": "AVAILABLE"},
        ],
        "raw_price_snapshots": [
            price(1, "swsh7-215", 2000, 2800, -2),
            price(2, "sv3pt5-199", 200, 280, -9),
            price(3, "sv3pt5-199", 230, 300, -1, hour=7),
            price(4, "sv3pt5-199", 230, 322, -1, hour=8),
            price(5, "svp-001", 5, 7, -1),
        ],
    }


def dirty() -> dict[str, list[dict]]:
    """The clean data plus exactly one violation of each data-quality check."""
    data = clean()
    data["raw_ledger_lots"] += [
        lot(5, "Oversold slab", "GRADED", None, 1, 100, -20),
        lot(6, "Negative cost", "SINGLE", None, 1, -5, -20),
        lot(7, "Stale price card", "SINGLE", "old-1", 1, 10, -20),
    ]
    data["raw_ledger_sales"] += [
        sale(6, 5, -10, 1, 150),
        sale(7, 5, -9, 1, 150),          # oversold_lot
        sale(8, 999, -9, 1, 10),         # orphan_sale
        sale(9, 4, -20, 1, 5),           # sale_before_purchase (lot 4 bought 10 days ago)
        sale(10, 4, 2, 1, 5),            # future_sale
    ]
    data["raw_price_snapshots"] += [
        price(20, "zero-1", 0, 0, -1),                # unpriced_snapshot
        price(21, "svp-001", 5, 7, -1),               # duplicate_snapshot of id 5
        price(22, "x-1", 10, 20, -1),                 # exchange_rate_out_of_range (2.0)
        price(23, "spk", 7, 10, -3),
        price(24, "spk", 21, 30, -2),                 # price_spike (+200%)
        price(25, "old-1", 7, 10, -10),               # stale_held_price
    ]
    return data
