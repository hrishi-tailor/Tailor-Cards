"""Runs the warehouse SQL on DuckDB, so the Athena views can be tested on a laptop and in CI.

The views are written in Trino SQL (Athena). DuckDB understands nearly all of it; the macros below supply the few
Trino functions it lacks, with the same results for the way the views use them.
"""

from __future__ import annotations

import duckdb

import warehouse

TRINO_SHIMS = [
    "CREATE MACRO sequence(lo, hi, step) AS generate_series(lo, hi, step)",
    "CREATE MACRO day_of_week(d) AS isodow(CAST(d AS DATE))",
    # The views only take the median (checked by test_sql_is_portable)
    "CREATE MACRO approx_percentile(x, p) AS median(x)",
    "CREATE MACRO from_iso8601_timestamp(s) AS CAST(replace(s, 'Z', '') AS TIMESTAMP)",
]

DUCK_TYPES = {"string": "VARCHAR", "int": "INTEGER", "bigint": "BIGINT", "date": "DATE"}


def duck_type(glue_type: str) -> str:
    return DUCK_TYPES.get(glue_type, glue_type.upper())


def build(rows: dict[str, list[dict]]) -> duckdb.DuckDBPyConnection:
    """Creates the raw tables, loads the given rows (by table name), and creates every view."""
    db = duckdb.connect()
    for shim in TRINO_SHIMS:
        db.execute(shim)
    for table, spec in warehouse.schema().items():
        columns = spec["columns"]
        db.execute(f"CREATE TABLE {table} ({', '.join(f'{n} {duck_type(t)}' for n, t in columns)})")
        names = [n for n, _ in columns]
        for row in rows.get(table, []):
            unknown = set(row) - set(names)
            assert not unknown, f"{table} has no columns {unknown}"
            db.execute(f"INSERT INTO {table} VALUES ({', '.join('?' for _ in names)})", [row.get(n) for n in names])
    for path in warehouse.view_files():
        db.execute(warehouse.statement(path))
    return db


def query(db: duckdb.DuckDBPyConnection, sql: str) -> list[dict]:
    cursor = db.execute(sql)
    names = [d[0] for d in cursor.description]
    return [dict(zip(names, row)) for row in cursor.fetchall()]
