import gzip
import json
from decimal import Decimal

from price_ingest import lake, tcgdex


def test_partitioned_keys():
    assert lake.raw_key("2026-10-20", "20261020T080000Z-ab12") == "raw/tcgdex/dt=2026-10-20/20261020T080000Z-ab12.jsonl.gz"
    assert lake.run_key("2026-10-20", "a/b c") == "runs/dt=2026-10-20/a-b-c.json"


def test_raw_lines_keep_one_card_per_line():
    results = [
        tcgdex.CardResult("sv8pt5-161", tcgdex.PRICED, "2026-10-20T08:00:00Z", Decimal("101.51"), "holofoil", '{"id":"x"}'),
        tcgdex.CardResult("gone", tcgdex.NOT_FOUND, "2026-10-20T08:00:01Z"),
    ]
    lines = gzip.decompress(lake.raw_lines(results, "run-1")).decode().splitlines()
    first, second = (json.loads(line) for line in lines)
    assert first == {"run_id": "run-1", "card_id": "sv8pt5-161", "status": "priced", "fetched_at": "2026-10-20T08:00:00Z",
                     "price_usd": "101.51", "variant": "holofoil", "error": None, "response": '{"id":"x"}'}
    assert second["status"] == "not_found" and second["price_usd"] is None
