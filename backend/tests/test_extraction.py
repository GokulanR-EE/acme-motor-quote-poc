import json

import pytest

from app.extraction import extract_document


@pytest.fixture(autouse=True)
def _mock_mode(monkeypatch):
    monkeypatch.setenv("MOCK_LLM", "1")


def test_mock_defaults_to_gb():
    result = extract_document(b"some bytes", "application/pdf", "doc.pdf")
    assert result["country_code"] == "GB"
    assert result["fields"]["registration"] == "AB12CDE"
    assert result["fields"]["ncb_years"] == 5
    assert result["_source"] == "document"


def test_mock_infers_fr_from_filename():
    result = extract_document(b"bytes", "application/pdf", "carte_grise.pdf")
    assert result["country_code"] == "FR"
    assert result["fields"]["immatriculation"] == "AB123CD"
    assert result["fields"]["bonus_malus"] == 0.90
    assert result["_source"] == "document"


def test_mock_infers_fr_from_bytes():
    result = extract_document(b"CARTE grise scan", "application/pdf", "scan.pdf")
    assert result["country_code"] == "FR"


class _FakeChoice:
    def __init__(self, content):
        self.message = type("M", (), {"content": content})()


class _FakeCompletions:
    def __init__(self, content):
        self._content = content

    def create(self, **kwargs):
        return type("R", (), {"choices": [_FakeChoice(self._content)]})()


class _FakeClient:
    def __init__(self, content):
        self.chat = type("C", (), {"completions": _FakeCompletions(content)})()


def test_live_stub_parses_json_and_sets_source(monkeypatch):
    monkeypatch.delenv("MOCK_LLM", raising=False)
    payload = json.dumps(
        {"country_code": "GB", "registration": "XY99ZZZ", "full_name": "Sam"}
    )
    client = _FakeClient(payload)
    result = extract_document(b"img", "image/png", "x.png", client=client)
    assert result["country_code"] == "GB"
    assert result["fields"]["registration"] == "XY99ZZZ"
    assert result["_source"] == "document"
