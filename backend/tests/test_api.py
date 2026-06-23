"""API: /start, /chat (SSE), /resolve — with FakeQuoteService + stubbed extraction."""

import json

import pytest
from fastapi.testclient import TestClient

from app import main
from app.quote_session_client import FakeQuoteService


def _events(resp):
    out = []
    for line in resp.text.splitlines():
        if line.startswith("data: "):
            out.append(json.loads(line[len("data: "):]))
    return out


@pytest.fixture
def client(monkeypatch):
    main.sessions.clear()
    main.app.state.service = FakeQuoteService()
    monkeypatch.setenv("MOCK_LLM", "1")
    return TestClient(main.app)


def test_start_returns_session_and_missing(client):
    resp = client.post("/start")
    assert resp.status_code == 200
    body = resp.json()
    assert body["session_id"]
    assert body["journeyState"] in ("quote_started", "collecting")
    assert body["missingFields"]


def test_chat_greedy_turn_advances(client, monkeypatch):
    sid = client.post("/start").json()["session_id"]
    patch = {"customer": {"firstName": "Sam", "surname": "Sample"}, "vehicle": {"annualMileage": 8000}}
    monkeypatch.setattr("app.agent.extract_patch", lambda *a, **k: patch)

    resp = client.post("/chat", json={"session_id": sid, "message": "..."})
    events = _events(resp)
    types = [e["type"] for e in events]
    assert "echo" in types
    assert types[-1] == "done"


def test_chat_conflict_then_resolve(client, monkeypatch):
    sid = client.post("/start").json()["session_id"]
    # First, set mileage 8000.
    monkeypatch.setattr("app.agent.extract_patch", lambda *a, **k: {"vehicle": {"annualMileage": 8000}})
    client.post("/chat", json={"session_id": sid, "message": "8000 miles"})

    # Now a conflicting mileage → conflict event.
    monkeypatch.setattr("app.agent.extract_patch", lambda *a, **k: {"vehicle": {"annualMileage": 18000}})
    resp = client.post("/chat", json={"session_id": sid, "message": "actually 18000"})
    events = _events(resp)
    conflict = next(e for e in events if e["type"] == "conflict")
    assert conflict["data"]["path"] == "vehicle.annualMileage"

    # Resolve picks 18000.
    resp = client.post(
        "/resolve",
        json={"session_id": sid, "path": "vehicle.annualMileage", "value": "18000"},
    )
    events = _events(resp)
    assert any(e["type"] == "echo" for e in events)
    assert main.sessions[sid]["current"]["vehicle"]["annualMileage"] == 18000


def test_chat_unknown_session_404(client):
    resp = client.post("/chat", json={"session_id": "nope", "message": "hi"})
    assert resp.status_code == 404
