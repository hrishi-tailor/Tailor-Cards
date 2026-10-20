"""Request signing shared with the app (com.tailorcards.api.pipeline.PipelineSignature).

The signature is ``v1=`` + hex HMAC-SHA256, keyed with the shared secret, of
``timestamp \\n METHOD \\n path \\n sha256-hex(body)``, where path includes the query string when there
is one (``/x?after=10``). The app refuses a timestamp more than five minutes
from its own clock, so a captured request can't be replayed later.
"""

from __future__ import annotations

import hashlib
import hmac
import time

TIMESTAMP_HEADER = "X-TC-Timestamp"
SIGNATURE_HEADER = "X-TC-Signature"
MIN_SECRET_LENGTH = 32


def sign(secret: str, method: str, path: str, body: bytes, timestamp: int) -> str:
    payload = f"{timestamp}\n{method.upper()}\n{path}\n{hashlib.sha256(body).hexdigest()}"
    digest = hmac.new(secret.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256).hexdigest()
    return f"v1={digest}"


def signed_headers(secret: str, method: str, path: str, body: bytes, now: float | None = None) -> dict[str, str]:
    if len(secret.strip()) < MIN_SECRET_LENGTH:
        raise ValueError(f"The shared secret must be at least {MIN_SECRET_LENGTH} characters")
    timestamp = int(now if now is not None else time.time())
    return {
        TIMESTAMP_HEADER: str(timestamp),
        SIGNATURE_HEADER: sign(secret.strip(), method, path, body, timestamp),
    }
