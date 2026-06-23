import pytest
from fastapi.testclient import TestClient

from app.api import app
from app.events import store


@pytest.fixture(autouse=True)
def reset_store():
    store._reset()
    yield
    store._reset()


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


def test_health_ok(client: TestClient):
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json() == {"status": "ok"}


def test_ping_returns_pong_and_echo(client: TestClient):
    resp = client.post("/ping", json={"msg": "hi"})
    assert resp.status_code == 200
    assert resp.json() == {"pong": True, "echo": {"msg": "hi"}}


def test_ping_with_no_body_echoes_empty(client: TestClient):
    resp = client.post("/ping")
    assert resp.status_code == 200
    assert resp.json() == {"pong": True, "echo": {}}


def test_ping_records_api_and_domain_events(client: TestClient):
    client.post("/ping", json={"msg": "hi"})

    events = store.all()

    api_events = [e for e in events if e.category == "api"]
    assert len(api_events) == 1
    assert api_events[0].type == "API_CALL"
    assert api_events[0].payload["api"] == "ping"
    assert api_events[0].payload["request"] == {"msg": "hi"}
    assert api_events[0].payload["response"] == {"pong": True, "echo": {"msg": "hi"}}

    domain_events = [e for e in events if e.category == "domain"]
    assert len(domain_events) == 1
    assert domain_events[0].type == "PING"
    assert domain_events[0].payload == {"echo": {"msg": "hi"}}
