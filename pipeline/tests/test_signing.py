import pytest

from price_ingest import signing

SECRET = "a-test-secret-that-is-long-enough-123"
NOW = 1_792_500_000


def test_matches_the_java_verifier():
    # Same vector as PipelineSignatureTest.knownVector: both sides must produce this exact value
    assert signing.sign(SECRET, "GET", "/api/internal/pricing/watchlist", b"", NOW) == (
        "v1=2284e0ec1dd0e72c0e143053d801f5d6d1566f4eb9564040f0d4aaebc4518134"
    )


def test_body_and_path_change_the_signature():
    base = signing.sign(SECRET, "POST", "/a", b"{}", NOW)
    assert signing.sign(SECRET, "POST", "/a", b"{ }", NOW) != base
    assert signing.sign(SECRET, "POST", "/b", b"{}", NOW) != base
    assert signing.sign(SECRET, "post", "/a", b"{}", NOW) == base


def test_signed_headers():
    headers = signing.signed_headers(SECRET, "GET", "/p", b"", now=NOW)
    assert headers[signing.TIMESTAMP_HEADER] == str(NOW)
    assert headers[signing.SIGNATURE_HEADER] == signing.sign(SECRET, "GET", "/p", b"", NOW)


def test_short_secret_is_refused():
    with pytest.raises(ValueError):
        signing.signed_headers("too-short", "GET", "/p", b"", now=NOW)
