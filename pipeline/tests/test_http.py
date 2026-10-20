import io
import urllib.error

import pytest

from price_ingest import http


class FakeResponse:
    def __init__(self, status, body):
        self.status = status
        self._body = body

    def read(self):
        return self._body

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


def http_error(code, retry_after=None):
    headers = {"Retry-After": retry_after} if retry_after else {}
    return urllib.error.HTTPError("https://x.test", code, "err", headers, io.BytesIO(b"{}"))


def script(monkeypatch, outcomes):
    calls = []

    def urlopen(req, timeout):
        calls.append(req)
        outcome = outcomes.pop(0)
        if isinstance(outcome, Exception):
            raise outcome
        return outcome

    monkeypatch.setattr(http.urllib.request, "urlopen", urlopen)
    return calls


def test_retries_transient_failures_with_backoff(monkeypatch):
    calls = script(monkeypatch, [http_error(503), urllib.error.URLError("reset"), FakeResponse(200, b'{"ok":1}')])
    slept = []
    response = http.request("GET", "https://x.test", sleep=slept.append)
    assert response.json() == {"ok": 1}
    assert len(calls) == 3
    assert slept == [0.5, 1.0]
    assert calls[0].get_header("User-agent") == http.USER_AGENT


def test_honours_retry_after(monkeypatch):
    script(monkeypatch, [http_error(429, "7"), FakeResponse(200, b"{}")])
    slept = []
    http.request("GET", "https://x.test", sleep=slept.append)
    assert slept == [7.0]


def test_does_not_retry_client_errors(monkeypatch):
    calls = script(monkeypatch, [http_error(401)])
    with pytest.raises(http.HttpError) as err:
        http.request("POST", "https://x.test", body=b"{}", sleep=lambda s: None)
    assert err.value.status == 401
    assert len(calls) == 1


def test_accepted_error_status_is_returned(monkeypatch):
    script(monkeypatch, [http_error(404)])
    assert http.request("GET", "https://x.test", accept=(200, 404)).status == 404


def test_gives_up_after_the_last_attempt(monkeypatch):
    script(monkeypatch, [http_error(502), http_error(502), http_error(502)])
    with pytest.raises(http.HttpError) as err:
        http.request("GET", "https://x.test", sleep=lambda s: None)
    assert err.value.status == 502
