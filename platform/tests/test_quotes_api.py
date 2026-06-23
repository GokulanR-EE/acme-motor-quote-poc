"""API tests for Slice 2 quote routes, vendor lookups, and the demo GUID."""

import pytest
from fastapi.testclient import TestClient

from app.api import app
from app.demo import DEMO_QUOTE_ID, DEMO_SESSION_ID
from app.events import store as event_store
from app.sessions import store as session_store


@pytest.fixture(autouse=True)
def reset():
    session_store.reset()
    event_store._reset()
    yield
    session_store.reset()
    event_store._reset()


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


def test_post_quote_returns_201_with_session(client: TestClient):
    resp = client.post("/quotes")
    assert resp.status_code == 201
    body = resp.json()
    assert "quoteId" in body
    assert "sessionId" in body
    assert body["journeyState"] == "quote_started"
    assert isinstance(body["missingFields"], list) and body["missingFields"]


def test_post_quote_emits_created_event_without_session(client: TestClient):
    body = client.post("/quotes").json()
    domain = [e for e in event_store.all() if e.type == "QUOTE_CREATED"]
    assert len(domain) == 1
    # session id never appears in any event payload.
    assert all(body["sessionId"] not in str(e.payload) for e in event_store.all())


def test_get_quote_with_session_works(client: TestClient):
    created = client.post("/quotes").json()
    resp = client.get(
        f"/quotes/{created['quoteId']}",
        headers={"X-Session-Id": created["sessionId"]},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["quoteId"] == created["quoteId"]
    assert "sessionId" not in body


def test_get_quote_without_session_is_404(client: TestClient):
    created = client.post("/quotes").json()
    resp = client.get(f"/quotes/{created['quoteId']}")
    assert resp.status_code == 404


def test_get_quote_with_wrong_session_is_404(client: TestClient):
    created = client.post("/quotes").json()
    resp = client.get(
        f"/quotes/{created['quoteId']}",
        headers={"X-Session-Id": "wrong-session"},
    )
    assert resp.status_code == 404


def test_patch_quote_deep_merges(client: TestClient):
    created = client.post("/quotes").json()
    qid, sid = created["quoteId"], created["sessionId"]
    client.patch(
        f"/quotes/{qid}",
        json={"patch": {"customer": {"firstName": "Sam"}}},
        headers={"X-Session-Id": sid},
    )
    resp = client.patch(
        f"/quotes/{qid}",
        json={"patch": {"customer": {"surname": "Sample"}}},
        headers={"X-Session-Id": sid},
    )
    assert resp.status_code == 200
    # Verify the sibling was preserved server-side.
    rec = session_store.get(qid, sid)
    assert rec.data["customer"] == {"firstName": "Sam", "surname": "Sample"}
    assert resp.json()["journeyState"] == "collecting"


def test_patch_quote_wrong_session_is_404(client: TestClient):
    created = client.post("/quotes").json()
    resp = client.patch(
        f"/quotes/{created['quoteId']}",
        json={"patch": {"customer": {"firstName": "X"}}},
        headers={"X-Session-Id": "wrong"},
    )
    assert resp.status_code == 404


def test_vehicle_lookup_route(client: TestClient):
    resp = client.get("/vehicles/FX19ZTC")
    assert resp.status_code == 200
    assert resp.json()["model"] == "Focus"


def test_address_lookup_route(client: TestClient):
    resp = client.get("/addresses", params={"postcode": "RG1 1AA"})
    assert resp.status_code == 200
    body = resp.json()
    assert body["postcode"] == "RG1 1AA"
    assert len(body["candidates"]) >= 2


def test_demo_quote_resolves_with_demo_session(client: TestClient):
    resp = client.get(
        f"/quotes/{DEMO_QUOTE_ID}",
        headers={"X-Session-Id": DEMO_SESSION_ID},
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["quoteId"] == DEMO_QUOTE_ID
    # Fully populated → no mandatory fields missing → ready_to_price.
    assert body["missingFields"] == []
    assert body["journeyState"] == "ready_to_price"


def test_demo_quote_wrong_session_is_404(client: TestClient):
    resp = client.get(
        f"/quotes/{DEMO_QUOTE_ID}",
        headers={"X-Session-Id": "not-the-demo-session"},
    )
    assert resp.status_code == 404


def test_demo_isolated_from_in_progress(client: TestClient):
    """Seeding the demo must not disturb a fresh in-progress quote."""
    created = client.post("/quotes").json()
    client.get(f"/quotes/{DEMO_QUOTE_ID}", headers={"X-Session-Id": DEMO_SESSION_ID})
    resp = client.get(
        f"/quotes/{created['quoteId']}",
        headers={"X-Session-Id": created["sessionId"]},
    )
    assert resp.status_code == 200
    assert resp.json()["journeyState"] == "quote_started"


def test_api_call_logged_for_quote_routes(client: TestClient):
    client.post("/quotes")
    api_calls = [e for e in event_store.all() if e.type == "API_CALL"]
    assert any(e.payload["api"] == "create_quote" for e in api_calls)
    # The create_quote API log must not contain the session id.
    create_logs = [e for e in api_calls if e.payload["api"] == "create_quote"]
    assert "sessionId" not in create_logs[0].payload["response"]
