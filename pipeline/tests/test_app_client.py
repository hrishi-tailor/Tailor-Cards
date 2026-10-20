import json
from decimal import Decimal

from price_ingest import app_client, http, signing

SECRET = "a-test-secret-that-is-long-enough-123"


class FakeApp:
    def __init__(self, replies):
        self.replies = replies
        self.calls = []

    def __call__(self, method, url, body=None, headers=None, timeout=None):
        self.calls.append({"method": method, "url": url, "body": body, "headers": headers})
        return http.Response(200, json.dumps(self.replies.pop(0)).encode())


def test_watchlist_is_a_signed_get():
    fake = FakeApp([{"cardIds": ["sv8pt5-161", "swsh7-215"], "generatedAt": "2026-10-20T08:00:00Z"}])
    client = app_client.AppClient("https://api.tailorcards.test/", SECRET, send=fake)

    assert client.watchlist() == ["sv8pt5-161", "swsh7-215"]
    call = fake.calls[0]
    assert call["url"] == "https://api.tailorcards.test/api/internal/pricing/watchlist"
    assert call["body"] is None
    ts = int(call["headers"][signing.TIMESTAMP_HEADER])
    assert call["headers"][signing.SIGNATURE_HEADER] == signing.sign(SECRET, "GET", app_client.WATCHLIST_PATH, b"", ts)


def test_prices_are_sent_in_signed_batches(monkeypatch):
    monkeypatch.setattr(app_client, "BATCH_SIZE", 2)
    fake = FakeApp([{"saved": 2, "duplicates": 0}, {"saved": 0, "duplicates": 1}])
    client = app_client.AppClient("https://api.tailorcards.test", SECRET, send=fake)
    prices = [{"cardId": f"c{i}", "priceUsd": Decimal("1.50"), "fetchedAt": "2026-10-20T08:00:00Z"} for i in range(3)]

    assert client.send_prices("run-1", "TCGDEX", prices) == {"saved": 2, "duplicates": 1}
    assert len(fake.calls) == 2
    first = fake.calls[0]
    payload = json.loads(first["body"])
    assert payload["runId"] == "run-1" and payload["source"] == "TCGDEX"
    assert payload["prices"][0] == {"cardId": "c0", "priceUsd": 1.5, "fetchedAt": "2026-10-20T08:00:00Z"}
    ts = int(first["headers"][signing.TIMESTAMP_HEADER])
    assert first["headers"][signing.SIGNATURE_HEADER] == signing.sign(
        SECRET, "POST", app_client.SNAPSHOTS_PATH, first["body"], ts)


def test_signs_the_full_path_behind_a_prefix():
    fake = FakeApp([{"cardIds": []}])
    app_client.AppClient("https://example.test/tc", SECRET, send=fake).watchlist()
    call = fake.calls[0]
    ts = int(call["headers"][signing.TIMESTAMP_HEADER])
    assert call["headers"][signing.SIGNATURE_HEADER] == signing.sign(
        SECRET, "GET", "/tc" + app_client.WATCHLIST_PATH, b"", ts)
