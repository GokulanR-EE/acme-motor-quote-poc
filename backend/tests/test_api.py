"""API: /start, /chat (SSE), /resolve, /price, /purchase, /issue-policy —
with FakeQuoteService + stubbed extraction."""

import json

import pytest
from fastapi.testclient import TestClient

from app import main
from app.quote_session_client import MANDATORY_FIELDS, FakeQuoteService


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


# --- price / purchase / issue-policy ---------------------------------------


def _complete_patch(**overrides):
    patch: dict = {}
    for path in MANDATORY_FIELDS:
        parts = path.split(".")
        node = patch
        for part in parts[:-1]:
            node = node.setdefault(part, {})
        node[parts[-1]] = "filled"
    patch["customer"]["dateOfBirth"] = "1990-01-01"
    patch["customer"].setdefault("address", {})["postcode"] = "RG1 1AA"
    patch["vehicle"]["value"] = 12000
    patch["vehicle"]["annualMileage"] = 8000
    patch["history"]["claimsLast3Years"] = 0
    patch["history"]["offencesLast5Years"] = 0
    patch["cover"]["coverLevel"] = "Comprehensive"
    patch["cover"]["voluntaryExcess"] = 250
    patch["cover"]["coverStartDate"] = "2026-07-01"
    patch["driver"]["ncdYears"] = 5
    for path, value in overrides.items():
        parts = path.split(".")
        node = patch
        for part in parts[:-1]:
            node = node.setdefault(part, {})
        node[parts[-1]] = value
    return patch


def _complete_session(client, monkeypatch, **overrides):
    """Start a quote and drive collection to ready_to_price via one greedy turn."""
    sid = client.post("/start").json()["session_id"]
    monkeypatch.setattr(
        "app.agent.extract_patch", lambda *a, **k: _complete_patch(**overrides)
    )
    client.post("/chat", json={"session_id": sid, "message": "everything"})
    return sid


def test_price_complete_quote_returns_quote_and_explanation(client, monkeypatch):
    sid = _complete_session(client, monkeypatch)
    resp = client.post("/price", json={"session_id": sid})
    assert resp.status_code == 200
    body = resp.json()
    assert body["pricing"]["outcome"] == "quote"
    assert body["pricing"]["annualPremium"] == 430.0
    assert "430" in body["explanation"]


def test_chat_ready_to_price_announces(client, monkeypatch):
    sid = client.post("/start").json()["session_id"]
    monkeypatch.setattr("app.agent.extract_patch", lambda *a, **k: _complete_patch())
    resp = client.post("/chat", json={"session_id": sid, "message": "everything"})
    texts = [e["data"] for e in _events(resp) if e["type"] == "text"]
    assert any("price" in t.lower() for t in texts)


def test_price_incomplete_returns_422_with_missing_fields(client):
    sid = client.post("/start").json()["session_id"]
    resp = client.post("/price", json={"session_id": sid})
    assert resp.status_code == 422
    body = resp.json()
    assert body["error"] == "not_ready_to_price"
    assert body["missingFields"]


def test_purchase_returns_url(client, monkeypatch):
    sid = _complete_session(client, monkeypatch)
    client.post("/price", json={"session_id": sid})
    resp = client.post("/purchase", json={"session_id": sid})
    assert resp.status_code == 200
    assert resp.json()["purchaseUrl"]


def test_purchase_before_clean_quote_is_error(client, monkeypatch):
    sid = _complete_session(client, monkeypatch)
    # No /price call → not a clean quote yet.
    resp = client.post("/purchase", json={"session_id": sid})
    assert resp.status_code == 409
    assert resp.json()["error"] == "not_purchasable"


def test_issue_policy_returns_policy_number(client, monkeypatch):
    sid = _complete_session(client, monkeypatch)
    client.post("/price", json={"session_id": sid})
    resp = client.post("/issue-policy", json={"session_id": sid})
    assert resp.status_code == 200
    body = resp.json()
    assert body["policyNumber"]
    assert body["status"] == "ISSUED"
    assert body["effectiveDate"]


def test_price_unknown_session_404(client):
    resp = client.post("/price", json={"session_id": "nope"})
    assert resp.status_code == 404


def test_purchase_unknown_session_404(client):
    resp = client.post("/purchase", json={"session_id": "nope"})
    assert resp.status_code == 404


def test_issue_policy_unknown_session_404(client):
    resp = client.post("/issue-policy", json={"session_id": "nope"})
    assert resp.status_code == 404
