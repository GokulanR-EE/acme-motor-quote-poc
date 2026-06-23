from fastapi.testclient import TestClient

from app.api.main import app, sessions

client = TestClient(app)


def test_health():
    assert client.get("/health").json() == {"status": "ok"}


def test_reprice_requires_existing_quote():
    r = client.post("/reprice", json={"session_id": "missing", "voluntary_excess": 500})
    assert r.status_code == 404


def test_reprice_updates_premium():
    # Seed a session with a quote via the tool dispatch directly.
    from app.llm.tools import dispatch_tool

    sessions["s1"] = {"history": [], "state": {}}
    dispatch_tool(
        "get_quote",
        {"registration": "AB12CDE", "age": 34, "ncb_years": 5, "postcode": "SW1A1AA"},
        sessions["s1"]["state"],
    )
    r = client.post("/reprice", json={"session_id": "s1", "voluntary_excess": 1000})
    assert r.status_code == 200
    assert r.json()["annual_premium"] > 0
