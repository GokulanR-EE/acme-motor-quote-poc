from app.mcp_client import parse_tool_result


class _Result:
    def __init__(self, structured=None, content=None):
        self.structuredContent = structured
        self.content = content or []


class _TextBlock:
    def __init__(self, text):
        self.text = text


def test_prefers_structured_content_dict():
    res = _Result(structured={"found": True, "make": "VW"})
    assert parse_tool_result(res) == {"found": True, "make": "VW"}


def test_structured_content_unwraps_result_key():
    res = _Result(structured={"result": {"quote_ref": "Q-1"}})
    assert parse_tool_result(res) == {"quote_ref": "Q-1"}


def test_falls_back_to_text_json():
    res = _Result(structured=None, content=[_TextBlock('{"currency": "GBP"}')])
    assert parse_tool_result(res) == {"currency": "GBP"}


def test_empty_when_nothing_parseable():
    res = _Result(structured=None, content=[])
    assert parse_tool_result(res) == {}
