"""A small HTTP helper on urllib: JSON in and out, timeouts, and retries for transient failures."""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Callable

USER_AGENT = "TailorCards-PricePipeline/1.0"
RETRYABLE_STATUS = {429, 500, 502, 503, 504}


@dataclass
class Response:
    status: int
    body: bytes

    def json(self, **kwargs):
        return json.loads(self.body, **kwargs)


class HttpError(Exception):
    """A request that failed after its retries, or with a status that isn't worth retrying."""

    def __init__(self, message: str, status: int | None = None, retry_after: str | None = None):
        super().__init__(message)
        self.status = status
        self.retry_after = retry_after


def request(
    method: str,
    url: str,
    *,
    body: bytes | None = None,
    headers: dict[str, str] | None = None,
    timeout: float = 10.0,
    attempts: int = 3,
    backoff: float = 0.5,
    sleep: Callable[[float], None] = time.sleep,
    accept: tuple[int, ...] = (200,),
) -> Response:
    """Sends a request, retrying timeouts, connection errors, 429 and 5xx with exponential backoff.

    A status in ``accept`` is returned; any other raises HttpError. Retry-After is honoured, up to 30 seconds.
    """
    all_headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    if body is not None:
        all_headers["Content-Type"] = "application/json"
    all_headers.update(headers or {})

    last_error: HttpError | None = None
    for attempt in range(attempts):
        if attempt:
            sleep(_retry_delay(last_error, backoff * 2 ** (attempt - 1)))
        req = urllib.request.Request(url, data=body, method=method, headers=all_headers)
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:  # noqa: S310 (https URLs from config)
                return _accepted(Response(resp.status, resp.read()), accept)
        except urllib.error.HTTPError as err:
            response = Response(err.code, err.read() or b"")
            if err.code in accept:
                return response
            retry_after = err.headers.get("Retry-After") if err.headers else None
            last_error = HttpError(f"{method} {url} returned {err.code}", err.code, retry_after)
            if err.code not in RETRYABLE_STATUS:
                raise last_error from None
        except (urllib.error.URLError, TimeoutError, ConnectionError) as err:
            last_error = HttpError(f"{method} {url} failed: {getattr(err, 'reason', err)}")
    assert last_error is not None
    raise last_error


def _accepted(response: Response, accept: tuple[int, ...]) -> Response:
    if response.status not in accept:
        raise HttpError(f"Unexpected status {response.status}", response.status)
    return response


def _retry_delay(error: HttpError | None, default: float) -> float:
    if error is not None and error.retry_after and error.retry_after.strip().isdigit():
        return min(float(error.retry_after), 30.0)
    return default
