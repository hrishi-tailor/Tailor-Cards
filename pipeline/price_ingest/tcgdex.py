"""TCGdex prices, read the same way as the app's TcgdexPriceProvider.

The headline price is TCGplayer's market price in USD for the first variant found in PREFERRED_VARIANTS (else the
first variant with a price), rounded half-up to cents. Keeping this identical to the Java provider means a
snapshot is the same whether the app or the pipeline recorded it.
"""

from __future__ import annotations

import json
import time
import urllib.parse
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal
from typing import Callable

from . import http

DEFAULT_BASE_URL = "https://api.tcgdex.net/v2/en"
SOURCE = "TCGDEX"

PREFERRED_VARIANTS = (
    "holofoil", "normal", "reverse-holofoil", "1st-edition-holofoil", "1st-edition-normal",
    "unlimited-holofoil", "unlimited-normal", "reverseHolofoil", "1stEditionHolofoil", "unlimitedHolofoil",
)

CENT = Decimal("0.01")

# Outcomes of pricing one card
PRICED = "priced"
NO_PRICE = "no_price"  # the card exists but TCGplayer has no market price for it
NOT_FOUND = "not_found"  # TCGdex doesn't know the id (often a pokemontcg.io id, which differs)
ERROR = "error"  # TCGdex failed after retries


@dataclass
class CardResult:
    card_id: str
    status: str
    fetched_at: str
    price_usd: Decimal | None = None
    variant: str | None = None
    raw: str | None = None  # the response body as received, for the data lake
    error: str | None = None


def variant_prices(card: dict) -> dict[str, Decimal]:
    """TCGplayer market price (USD) per variant; variants without a positive market price are left out."""
    tcgplayer = (card.get("pricing") or {}).get("tcgplayer") or {}
    prices: dict[str, Decimal] = {}
    for key, variant in tcgplayer.items():
        if not isinstance(variant, dict):
            continue  # "unit", "updated"
        market = variant.get("marketPrice")
        if not _is_number(market):
            market = variant.get("market")
        if _is_number(market) and market > 0:
            prices[key] = Decimal(str(market)).quantize(CENT, rounding=ROUND_HALF_UP)
    return prices


def headline_price(prices: dict[str, Decimal]) -> tuple[str, Decimal] | None:
    for key in PREFERRED_VARIANTS:
        if key in prices:
            return key, prices[key]
    return next(iter(prices.items()), None)


def _is_number(value) -> bool:
    return isinstance(value, (int, float, Decimal)) and not isinstance(value, bool)


class RateLimiter:
    """Spaces calls at least 1/rate seconds apart (TCGdex asks clients to be gentle)."""

    def __init__(self, per_second: float, clock: Callable[[], float] = time.monotonic,
                 sleep: Callable[[float], None] = time.sleep):
        self.interval = 1.0 / per_second
        self.clock = clock
        self.sleep = sleep
        self.next_at = 0.0

    def wait(self) -> None:
        now = self.clock()
        if now < self.next_at:
            self.sleep(self.next_at - now)
            now = self.next_at
        self.next_at = now + self.interval


class TcgdexClient:
    def __init__(self, base_url: str = DEFAULT_BASE_URL, requests_per_second: float = 8.0,
                 send: Callable[..., http.Response] = http.request, now: Callable[[], str] | None = None,
                 limiter: RateLimiter | None = None):
        self.base_url = base_url.rstrip("/")
        self.send = send
        self.limiter = limiter or RateLimiter(requests_per_second)
        self.now = now or _utc_now

    def price(self, card_id: str) -> CardResult:
        self.limiter.wait()
        url = f"{self.base_url}/cards/{urllib.parse.quote(card_id, safe='')}"
        fetched_at = self.now()
        try:
            response = self.send("GET", url, accept=(200, 404))
        except http.HttpError as err:
            return CardResult(card_id, ERROR, fetched_at, error=str(err))
        raw = response.body.decode("utf-8", errors="replace")
        if response.status == 404 or not raw.strip():
            return CardResult(card_id, NOT_FOUND, fetched_at, raw=raw or None)
        try:
            card = json.loads(raw)
        except json.JSONDecodeError:
            return CardResult(card_id, ERROR, fetched_at, raw=raw, error="TCGdex returned invalid JSON")
        best = headline_price(variant_prices(card)) if isinstance(card, dict) else None
        if best is None:
            return CardResult(card_id, NO_PRICE, fetched_at, raw=raw)
        return CardResult(card_id, PRICED, fetched_at, price_usd=best[1], variant=best[0], raw=raw)


def _utc_now() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
