from app.llm.agent import run_agent_turn


class _FakeMessage:
    def __init__(self, content=None, tool_calls=None):
        self.content = content
        self.tool_calls = tool_calls or []


class _FakeToolCall:
    def __init__(self, call_id, name, arguments):
        self.id = call_id
        self.type = "function"
        self.function = type("F", (), {"name": name, "arguments": arguments})


class _FakeChoice:
    def __init__(self, message):
        self.message = message


class _FakeResponse:
    def __init__(self, message):
        self.choices = [_FakeChoice(message)]


class FakeClient:
    """Returns a tool call first, then a final text answer."""

    def __init__(self):
        self._calls = 0
        self.chat = type("C", (), {"completions": self})

    def create(self, **kwargs):
        self._calls += 1
        if self._calls == 1:
            tc = _FakeToolCall(
                "call_1",
                "get_quote",
                '{"registration":"AB12CDE","age":34,"ncb_years":5,"postcode":"SW1A1AA"}',
            )
            return _FakeResponse(_FakeMessage(tool_calls=[tc]))
        return _FakeResponse(_FakeMessage(content="Here is your quote."))


def test_agent_emits_quote_then_text():
    session = {"history": [], "state": {}}
    events = list(run_agent_turn("Quote my AB12CDE", session, client=FakeClient()))
    types = [e["type"] for e in events]
    assert "quote" in types
    assert events[-1] == {"type": "text", "data": "Here is your quote."}
    assert session["state"]["quote_input"].voluntary_excess == 250


def test_mock_llm_mode_runs_without_client(monkeypatch):
    monkeypatch.setenv("MOCK_LLM", "1")
    session = {"history": [], "state": {}}
    events = list(run_agent_turn("I drive AB12CDE, age 34, 5 years NCB, SW1A1AA", session, client=None))
    assert any(e["type"] == "quote" for e in events)
    assert any(e["type"] == "text" for e in events)
