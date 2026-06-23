import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.channel import router
from app.events import store


@pytest.fixture(autouse=True)
def reset_store():
    store._reset()
    yield
    store._reset()


def _make_client() -> TestClient:
    app = FastAPI()
    app.include_router(router)
    return app


def test_ws_streams_new_event_after_connect():
    app = _make_client()
    client = TestClient(app)
    with client.websocket_connect("/ws") as ws:
        store.append("X", {"hello": "world"}, "domain")
        frame = ws.receive_json()
        assert frame["type"] == "X"
        assert frame["category"] == "domain"
        assert frame["payload"] == {"hello": "world"}


def test_ws_replays_existing_events_on_connect():
    app = _make_client()
    store.append("OLD", {"n": 1}, "api")
    client = TestClient(app)
    with client.websocket_connect("/ws") as ws:
        frame = ws.receive_json()
        assert frame["type"] == "OLD"
        assert frame["category"] == "api"
