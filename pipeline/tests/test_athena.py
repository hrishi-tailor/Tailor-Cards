import pytest

from warehouse.athena import Athena, QueryFailed


class FakeAthena:
    def __init__(self, states, pages=None):
        self.states = list(states)
        self.pages = list(pages or [])
        self.started = []
        self.stopped = []

    def start_query_execution(self, **kwargs):
        self.started.append(kwargs)
        return {"QueryExecutionId": "q1"}

    def get_query_execution(self, QueryExecutionId):
        state = self.states.pop(0) if len(self.states) > 1 else self.states[0]
        return {"QueryExecution": {"Status": {"State": state, "StateChangeReason": "SYNTAX_ERROR"}}}

    def stop_query_execution(self, QueryExecutionId):
        self.stopped.append(QueryExecutionId)

    def get_query_results(self, **kwargs):
        return self.pages.pop(0)


def athena(fake, **kwargs):
    return Athena(fake, "tc-wg", "tc_db", sleep=lambda s: None, **kwargs)


def test_waits_for_success_in_the_workgroup_and_database():
    fake = FakeAthena(["QUEUED", "RUNNING", "SUCCEEDED"])
    assert athena(fake).run("SELECT 1") == "q1"
    assert fake.started == [{"QueryString": "SELECT 1", "WorkGroup": "tc-wg",
                             "QueryExecutionContext": {"Database": "tc_db"}}]


def test_a_failed_query_raises_with_its_reason():
    with pytest.raises(QueryFailed, match="FAILED: SYNTAX_ERROR"):
        athena(FakeAthena(["RUNNING", "FAILED"])).run("SELEC 1")


def test_a_slow_query_is_stopped():
    ticks = iter(range(0, 1000, 50))
    fake = FakeAthena(["RUNNING"])
    with pytest.raises(QueryFailed, match="Timed out"):
        athena(fake, timeout=100, clock=lambda: next(ticks)).run("SELECT 1")
    assert fake.stopped == ["q1"]


def test_rows_reads_every_page_and_skips_the_header():
    def row(*values):
        return {"Data": [{"VarCharValue": v} if v is not None else {} for v in values]}

    pages = [
        {"ResultSet": {"Rows": [row("check_name", "failing_rows"), row("oversold_lot", "0")]}, "NextToken": "t"},
        {"ResultSet": {"Rows": [row("price_spike", None)]}},
    ]
    assert athena(FakeAthena(["SUCCEEDED"], pages)).rows("SELECT ...") == [
        {"check_name": "oversold_lot", "failing_rows": "0"},
        {"check_name": "price_spike", "failing_rows": None},
    ]


def test_table_keeps_the_header_when_there_are_no_rows():
    pages = [{"ResultSet": {"Rows": [{"Data": [{"VarCharValue": "week_start"}, {"VarCharValue": "sales"}]}]}}]
    assert athena(FakeAthena(["SUCCEEDED"], pages)).table("SELECT ...") == (["week_start", "sales"], [])
