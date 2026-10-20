"""Signed calls to the Tailor Cards app's internal pricing endpoints."""

from __future__ import annotations

import json
import urllib.parse
from decimal import Decimal
from typing import Callable

from . import http, signing

WATCHLIST_PATH = "/api/internal/pricing/watchlist"
SNAPSHOTS_PATH = "/api/internal/pricing/snapshots"
# The app accepts up to 5,000 prices and 1 MB per request; smaller batches keep each request well inside both.
BATCH_SIZE = 1_000


class AppClient:
    def __init__(self, base_url: str, secret: str, send: Callable[..., http.Response] = http.request,
                 timeout: float = 30.0):
        self.base_url = base_url.rstrip("/")
        self.base_path = urllib.parse.urlparse(self.base_url).path
        self.secret = secret
        self.send = send
        self.timeout = timeout

    def watchlist(self) -> list[str]:
        response = self._call("GET", WATCHLIST_PATH, b"")
        card_ids = response.json().get("cardIds")
        if not isinstance(card_ids, list):
            raise ValueError("The app's watchlist has no cardIds list")
        return [str(card_id) for card_id in card_ids]

    def send_prices(self, run_id: str, source: str, prices: list[dict]) -> dict:
        """Posts prices in batches; returns the totals the app reports (saved, duplicates)."""
        totals = {"saved": 0, "duplicates": 0}
        for start in range(0, len(prices), BATCH_SIZE):
            body = json.dumps({"runId": run_id, "source": source, "prices": prices[start:start + BATCH_SIZE]},
                              default=_json_default, separators=(",", ":")).encode("utf-8")
            result = self._call("POST", SNAPSHOTS_PATH, body).json()
            totals["saved"] += int(result.get("saved", 0))
            totals["duplicates"] += int(result.get("duplicates", 0))
        return totals

    def _call(self, method: str, path: str, body: bytes) -> http.Response:
        signed_path = self.base_path + path  # path may carry a query string; it is signed with it
        headers = signing.signed_headers(self.secret, method, signed_path, body)
        # Signed per attempt would be stricter, but a retry within seconds stays well inside the 5-minute window
        return self.send(method, self.base_url + path, body=body if method != "GET" else None,
                         headers=headers, timeout=self.timeout)


def _json_default(value):
    if isinstance(value, Decimal):
        return float(value)  # prices are already rounded to cents
    raise TypeError(f"Not JSON serialisable: {type(value).__name__}")
