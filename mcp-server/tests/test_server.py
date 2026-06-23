from datetime import date

import pytest

from app import server
from app.models import Quote, VehicleInput
from tests.test_models import make_quote_input


class _FakeAcme:
    def lookup_vehicle(self, registration: str):
        if registration.upper().replace(" ", "") == "AB12CDE":
            return VehicleInput(registration="AB12CDE", make="Volkswagen",
                model="Golf", year=2019, value=14000.0, insurance_group=20)
        return None

    def get_quote(self, qi, today=None):
        return Quote(quote_ref="Q-AB12CDE", annual_premium=642.12,
                     monthly_premium=53.51, input=qi)


@pytest.fixture(autouse=True)
def _wire_fakes(monkeypatch):
    monkeypatch.setattr(server, "_acme", _FakeAcme())
    monkeypatch.setattr(server, "_store", server.QuoteStore())


def test_lookup_vehicle_found_and_not_found():
    assert server.lookup_vehicle("AB12CDE")["found"] is True
    assert server.lookup_vehicle("AB12CDE")["make"] == "Volkswagen"
    assert server.lookup_vehicle("ZZ99ZZZ") == {"found": False, "registration": "ZZ99ZZZ"}


def test_submit_quote_request_returns_quote():
    out = server.submit_quote_request(make_quote_input().model_dump(mode="json"))
    assert out["annual_premium"] == 642.12
    assert out["quote_ref"] == "Q-AB12CDE"


def test_create_handoff_link_mints_guid_and_stores():
    quote = server.submit_quote_request(make_quote_input().model_dump(mode="json"))
    link = server.create_handoff_link(quote)
    assert link["handoff_url"].endswith(link["guid"])
    assert server._store.get(link["guid"]) is not None


def test_handoff_page_renders_known_and_unknown():
    from starlette.testclient import TestClient
    quote = server.submit_quote_request(make_quote_input().model_dump(mode="json"))
    link = server.create_handoff_link(quote)
    app = server.mcp.streamable_http_app()
    client = TestClient(app)
    ok = client.get(f"/handoff/{link['guid']}")
    assert ok.status_code == 200 and "642.12" in ok.text
    missing = client.get("/handoff/deadbeef")
    assert missing.status_code == 404
