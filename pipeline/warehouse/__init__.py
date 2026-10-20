"""The Tailor Cards warehouse: raw tables exported from the app, and the SQL views (staging, star schema, marts and
data-quality checks) built on them in Athena. See docs/warehouse.md."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).parent
SQL_DIR = ROOT / "sql"


def schema() -> dict[str, dict]:
    """Raw tables by name: {"dataset": export dataset, "columns": [[name, glue type], ...]}."""
    return json.loads((ROOT / "schema.json").read_text())["tables"]


def view_files() -> list[Path]:
    """The view definitions in dependency order (their numeric prefix)."""
    return sorted(SQL_DIR.glob("*.sql"))


def view_name(path: Path) -> str:
    return path.stem.split("_", 1)[1]


def statement(path: Path) -> str:
    """The SQL of one view file, comments and trailing semicolon removed (Athena runs one statement at a time)."""
    lines = [line for line in path.read_text().splitlines() if not line.lstrip().startswith("--")]
    return "\n".join(lines).strip().rstrip(";")
