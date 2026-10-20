"""Runs SQL on Athena and waits for it: view definitions (free DDL) and the data-quality query."""

from __future__ import annotations

import time
from typing import Callable


class QueryFailed(Exception):
    pass


class Athena:
    def __init__(self, client, workgroup: str, database: str, timeout: float = 120.0,
                 sleep: Callable[[float], None] = time.sleep, clock: Callable[[], float] = time.monotonic):
        self.client = client
        self.workgroup = workgroup
        self.database = database
        self.timeout = timeout
        self.sleep = sleep
        self.clock = clock

    def run(self, sql: str) -> str:
        """Starts a query, waits for it to finish and returns its id; raises QueryFailed if it doesn't succeed."""
        query_id = self.client.start_query_execution(
            QueryString=sql, WorkGroup=self.workgroup, QueryExecutionContext={"Database": self.database},
        )["QueryExecutionId"]
        deadline = self.clock() + self.timeout
        delay = 0.25
        while True:
            status = self.client.get_query_execution(QueryExecutionId=query_id)["QueryExecution"]["Status"]
            state = status["State"]
            if state == "SUCCEEDED":
                return query_id
            if state in ("FAILED", "CANCELLED"):
                raise QueryFailed(f"{state}: {status.get('StateChangeReason', 'no reason given')}")
            if self.clock() > deadline:
                self.client.stop_query_execution(QueryExecutionId=query_id)
                raise QueryFailed(f"Timed out after {self.timeout:.0f} s")
            self.sleep(delay)
            delay = min(delay * 2, 2.0)

    def rows(self, sql: str) -> list[dict]:
        """Runs a query and returns its result rows as dicts of strings (Athena returns every value as text)."""
        header, values = self.table(sql)
        return [dict(zip(header, row)) for row in values]

    def table(self, sql: str) -> tuple[list[str], list[list[str | None]]]:
        """Runs a query and returns its column names and rows, reading every result page. The names are kept even
        when there are no rows."""
        query_id = self.run(sql)
        header: list[str] | None = None
        out: list[list[str | None]] = []
        kwargs = {"QueryExecutionId": query_id}
        while True:
            page = self.client.get_query_results(**kwargs)
            for row in page["ResultSet"]["Rows"]:
                values = [cell.get("VarCharValue") for cell in row["Data"]]
                if header is None:
                    header = [v or "" for v in values]
                else:
                    out.append(values)
            if not page.get("NextToken"):
                return header or [], out
            kwargs["NextToken"] = page["NextToken"]
