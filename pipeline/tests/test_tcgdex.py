import json
import urllib.parse
from decimal import Decimal

from price_ingest import http, tcgdex

NOW = "2026-10-20T08:00:00Z"


class FakeSend:
    """Stands in for http.request, answering by card id."""

    def __init__(self, responses):
        self.responses = responses
        self.urls = []

    def __call__(self, method, url, **kwargs):
        self.urls.append(url)
        answer = self.responses[urllib.parse.unquote(url.rsplit("/", 1)[1])]
        if isinstance(answer, Exception):
            raise answer
        return answer


def card(tcgplayer):
    return http.Response(200, json.dumps({"id": "x", "pricing": {"tcgplayer": tcgplayer}}).encode())


def client(responses):
    send = FakeSend(responses)
    limiter = tcgdex.RateLimiter(1000, clock=lambda: 0.0, sleep=lambda s: None)
    return tcgdex.TcgdexClient("https://tcgdex.test/v2/en/", send=send, now=lambda: NOW, limiter=limiter), send


def test_prefers_variants_in_the_java_order():
    prices = tcgdex.variant_prices({"pricing": {"tcgplayer": {
        "unit": "USD", "updated": "2026-10-19",
        "normal": {"marketPrice": 3.1}, "reverse-holofoil": {"marketPrice": 9.99},
        "holofoil": {"marketPrice": 0}, "1st-edition-holofoil": {"market": 120.005},
    }}})
    assert prices == {"normal": Decimal("3.10"), "reverse-holofoil": Decimal("9.99"),
                      "1st-edition-holofoil": Decimal("120.01")}
    assert tcgdex.headline_price(prices) == ("normal", Decimal("3.10"))


def test_falls_back_to_the_first_unlisted_variant():
    prices = tcgdex.variant_prices({"pricing": {"tcgplayer": {"special-promo": {"marketPrice": 42}}}})
    assert tcgdex.headline_price(prices) == ("special-promo", Decimal("42.00"))
    assert tcgdex.headline_price({}) is None


def test_outcomes():
    c, send = client({
        "sv8pt5-161": card({"holofoil": {"marketPrice": 101.505}}),
        "unknown": http.Response(404, b'{"error":"not found"}'),
        "no-price": card({"unit": "USD"}),
        "down": http.HttpError("GET failed: 503", 503),
        "a b": card({"normal": {"marketPrice": 1}}),
    })
    priced = c.price("sv8pt5-161")
    assert (priced.status, priced.price_usd, priced.variant, priced.fetched_at) == (
        tcgdex.PRICED, Decimal("101.51"), "holofoil", NOW)
    assert priced.raw and "tcgplayer" in priced.raw
    assert c.price("unknown").status == tcgdex.NOT_FOUND
    assert c.price("no-price").status == tcgdex.NO_PRICE
    down = c.price("down")
    assert down.status == tcgdex.ERROR and "503" in down.error
    c.price("a b")
    assert send.urls[0] == "https://tcgdex.test/v2/en/cards/sv8pt5-161"
    assert send.urls[-1].endswith("/cards/a%20b")


def test_rate_limiter_spaces_calls():
    clock = [0.0]
    slept = []

    def sleep(seconds):
        slept.append(round(seconds, 3))
        clock[0] += seconds

    limiter = tcgdex.RateLimiter(8, clock=lambda: clock[0], sleep=sleep)
    for _ in range(3):
        limiter.wait()
    assert slept == [0.125, 0.125]
