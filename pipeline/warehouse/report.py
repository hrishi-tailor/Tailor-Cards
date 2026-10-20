"""The Monday email: last week's results, stock at market and what needs attention, from the warehouse views.

gather() takes any function that runs SQL and returns rows as dicts, so the same queries run on Athena in
production and on DuckDB in the tests. Values may arrive as text (Athena) or typed (DuckDB); num() reads both.
"""

from __future__ import annotations

import html
from datetime import date, timedelta
from typing import Callable

Query = Callable[[str], list[dict]]


def last_week(today: date) -> date:
    """The Monday that starts the last full week before today."""
    return today - timedelta(days=today.weekday() + 7)


def num(value, default: float = 0.0) -> float:
    if value is None or value == "":
        return default
    return float(value)


def _week(query: Query, start: date) -> dict:
    rows = query(f"SELECT * FROM mart_weekly_pnl WHERE week_start = DATE '{start.isoformat()}'")
    row = rows[0] if rows else {}
    totals = {k: num(row.get(k)) for k in ("sales", "units", "revenue_cad", "cost_cad", "gross_profit_cad")}
    margin = row.get("margin_pct")
    return totals | {"margin_pct": None if margin in (None, "") else float(margin)}


def gather(query: Query, week_start: date) -> dict:
    day = week_start.isoformat()
    stock = query("""
        SELECT count(*) AS lots, sum(units_on_hand) AS units, sum(cost_on_hand_cad) AS cost,
               sum(market_value_cad) AS market_value, sum(unrealised_gain_cad) AS gain,
               sum(CASE WHEN market_price_cad IS NULL THEN 1 ELSE 0 END) AS unpriced
        FROM mart_inventory_valuation""")[0]
    return {
        "week_start": week_start,
        "week": _week(query, week_start),
        "previous": _week(query, week_start - timedelta(days=7)),
        "top_sales": query(f"""
            SELECT item, channel_name, units, revenue_cad, gross_profit_cad
            FROM dash_sales WHERE week_start = DATE '{day}'
            ORDER BY gross_profit_cad DESC, sale_id LIMIT 5"""),
        "stock": {k: num(v) for k, v in stock.items()},
        "movers": query("""
            SELECT card_name, price_cad, change_pct
            FROM mart_price_movers WHERE in_ledger AND change_pct IS NOT NULL
            ORDER BY abs(change_pct) DESC, card_name LIMIT 5"""),
        "problems": query("""
            SELECT check_name, severity, failing_rows, example
            FROM dq_results WHERE failing_rows > 0 ORDER BY severity, check_name"""),
    }


def money(value) -> str:
    amount = num(value)
    return f"-${abs(amount):,.2f}" if amount < 0 else f"${amount:,.2f}"


def count(n: float, noun: str) -> str:
    n = int(n)
    return f"{n} {noun}" + ("" if n == 1 else "s")


def change(now: float, before: float) -> str:
    if not before:
        return "new" if now else "no change"
    pct = 100 * (now - before) / abs(before)
    return f"{'+' if pct >= 0 else ''}{pct:.0f}% vs the week before"


def render(data: dict) -> tuple[str, str, str]:
    """Returns (subject, html, text)."""
    start: date = data["week_start"]
    end = start + timedelta(days=6)
    week, previous, stock = data["week"], data["previous"], data["stock"]
    period = f"{start:%b} {start.day} – {end:%b} {end.day}"
    margin = f"{week['margin_pct']:.1f}%" if week["margin_pct"] is not None else "–"
    subject = f"Tailor Cards week of {start:%b} {start.day}: {money(week['gross_profit_cad'])} profit on {money(week['revenue_cad'])}"

    headline = [
        ("Revenue", money(week["revenue_cad"]), change(week["revenue_cad"], previous["revenue_cad"])),
        ("Gross profit", money(week["gross_profit_cad"]), change(week["gross_profit_cad"], previous["gross_profit_cad"])),
        ("Margin", margin, f"{count(week['sales'], 'sale')}, {count(week['units'], 'unit')}"),
        ("Stock at cost", money(stock["cost"]), f"{count(stock['units'], 'unit')} in {count(stock['lots'], 'lot')}"),
        ("Stock at market", money(stock["market_value"]), f"{money(stock['gain'])} unrealised; {count(stock['unpriced'], 'lot')} unpriced"),
    ]
    sales = [(s["item"], s["channel_name"], str(int(num(s["units"]))), money(s["revenue_cad"]), money(s["gross_profit_cad"]))
             for s in data["top_sales"]]
    movers = [(m["card_name"], money(m["price_cad"]), f"{num(m['change_pct']):+.1f}%") for m in data["movers"]]
    problems = [(p["check_name"], p["severity"], str(int(num(p["failing_rows"]))), p["example"] or "") for p in data["problems"]]

    text_lines = [f"Tailor Cards, {period}", ""]
    text_lines += [f"{label}: {value} ({note})" for label, value, note in headline]
    for title, rows in (("Best sales", sales), ("Price moves in stock (7 days)", movers), ("Data to check", problems)):
        text_lines += ["", title + ":"] + ([f"- {' | '.join(r)}" for r in rows] or ["- none"])
    text = "\n".join(text_lines) + "\n"

    def table(title, columns, rows):
        if not rows:
            return f"<h3>{title}</h3><p style='color:#666'>None this week.</p>"
        head = "".join(f"<th align='left' style='padding:4px 8px'>{c}</th>" for c in columns)
        body = "".join("<tr>" + "".join(f"<td style='padding:4px 8px'>{html.escape(str(v))}</td>" for v in r) + "</tr>"
                       for r in rows)
        return f"<h3>{title}</h3><table style='border-collapse:collapse'><tr>{head}</tr>{body}</table>"

    cards = "".join(
        f"<td style='padding:8px 16px 8px 0;vertical-align:top'><div style='color:#666;font-size:12px'>{label}</div>"
        f"<div style='font-size:20px;font-weight:600'>{html.escape(value)}</div>"
        f"<div style='color:#666;font-size:12px'>{html.escape(note)}</div></td>"
        for label, value, note in headline)
    body = (
        f"<div style='font-family:-apple-system,Segoe UI,sans-serif;color:#111'>"
        f"<h2 style='margin-bottom:4px'>Tailor Cards weekly report</h2><p style='color:#666;margin-top:0'>{period}</p>"
        f"<table><tr>{cards}</tr></table>"
        + table("Best sales", ["Item", "Channel", "Units", "Revenue", "Profit"], sales)
        + table("Price moves in stock (7 days)", ["Card", "Price", "Change"], movers)
        + table("Data to check", ["Check", "Severity", "Rows", "Example"], problems)
        + "<p style='color:#666;font-size:12px'>From the warehouse views in Athena (docs/warehouse.md).</p></div>"
    )
    return subject, body, text


def send(ses, sender: str, recipients: list[str], subject: str, html_body: str, text_body: str) -> str:
    response = ses.send_email(
        FromEmailAddress=sender,
        Destination={"ToAddresses": recipients},
        Content={"Simple": {
            "Subject": {"Data": subject, "Charset": "UTF-8"},
            "Body": {"Html": {"Data": html_body, "Charset": "UTF-8"}, "Text": {"Data": text_body, "Charset": "UTF-8"}},
        }},
    )
    return response["MessageId"]
