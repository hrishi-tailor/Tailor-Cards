import gzip
import json

import pytest

from price_ingest import app_client, http, signing
from warehouse import export

SECRET = "a-test-secret-that-is-long-enough-123"


class PagedApp:
    """Serves a dataset in pages, like the app's export endpoint."""

    def __init__(self, rows, page_size=2):
        self.rows = rows
        self.page_size = page_size
        self.requests = []

    def export_page(self, dataset, after, limit):
        self.requests.append((dataset, after))
        page = [r for r in self.rows if r["id"] > after][: self.page_size]
        more = len(page) == self.page_size
        return {"rows": page, "nextAfter": page[-1]["id"] if more else None}


def test_fetch_follows_the_cursor_to_the_end():
    app = PagedApp([{"id": i} for i in range(1, 6)])
    assert [r["id"] for r in export.fetch(app, "ledger_sales")] == [1, 2, 3, 4, 5]
    assert app.requests == [("ledger_sales", 0), ("ledger_sales", 2), ("ledger_sales", 4)]


def test_fetch_refuses_a_cursor_that_does_not_advance():
    class Stuck:
        def export_page(self, dataset, after, limit):
            return {"rows": [{"id": 1}], "nextAfter": 1}

    with pytest.raises(ValueError, match="did not advance"):
        export.fetch(Stuck(), "ledger_sales")


def test_conform_keeps_schema_columns_and_reports_new_ones():
    rows, extra = export.conform([{"id": 1, "title": "x", "secret_new_column": 5}], ["id", "title", "kind"])
    assert rows == [{"id": 1, "title": "x", "kind": None}]
    assert extra == {"secret_new_column"}


def test_export_table_writes_the_current_copy_and_the_day():
    written = {}

    class S3:
        def put_object(self, Bucket, Key, Body, **kwargs):
            written[Key] = Body

    spec = {"dataset": "ledger_lots", "columns": [["id", "bigint"], ["title", "string"]]}
    result = export.export_table(S3(), "lake", PagedApp([{"id": 1, "title": "Case"}]), "raw_ledger_lots", spec, "2026-10-20")
    assert result == {"table": "raw_ledger_lots", "rows": 1, "unknown_columns": []}
    assert set(written) == {"warehouse/raw/raw_ledger_lots/data.jsonl.gz",
                            "warehouse/history/raw_ledger_lots/dt=2026-10-20/data.jsonl.gz"}
    lines = gzip.decompress(written["warehouse/raw/raw_ledger_lots/data.jsonl.gz"]).decode().splitlines()
    assert [json.loads(line) for line in lines] == [{"id": 1, "title": "Case"}]


def test_app_client_signs_export_pages():
    calls = []

    def send(method, url, body=None, headers=None, timeout=None):
        calls.append((url, headers))
        return http.Response(200, b'{"dataset":"ledger_sales","rows":[],"nextAfter":null}')

    client = app_client.AppClient("https://api.example.test", SECRET, send=send)
    assert client.export_page("ledger_sales", 10, 500)["rows"] == []
    url, headers = calls[0]
    assert url == "https://api.example.test/api/internal/export/ledger_sales?after=10&limit=500"
    ts = int(headers[signing.TIMESTAMP_HEADER])
    assert headers[signing.SIGNATURE_HEADER] == signing.sign(
        SECRET, "GET", "/api/internal/export/ledger_sales?after=10&limit=500", b"", ts)
