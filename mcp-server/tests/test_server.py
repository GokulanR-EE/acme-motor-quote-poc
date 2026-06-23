from datetime import date

import pytest

from app import server
from app.models import VehicleDetails
from tests.test_models import make_fr_quote_input, make_gb_quote_input


class _FakeAcme:
    def __init__(self):
        self.captured = {}

    def lookup_vehicle(self, identifier: str, country_code: str = "GB"):
        ident = identifier.upper().replace(" ", "")
        if ident == "AB12CDE":
            return VehicleDetails(identifier="AB12CDE", make="Volkswagen",
                model="Golf", year=2019, value=14000.0, insurance_group=20)
        if ident == "AA123BB":
            return VehicleDetails(identifier="AA123BB", make="Renault",
                model="Clio", year=2020, value=16000.0)
        return None

    def get_quote(self, country_code: str, payload: dict):
        self.captured["country_code"] = country_code
        self.captured["payload"] = payload
        return {"quote_ref": "Q-X", "annual_premium": 642.123}


@pytest.fixture(autouse=True)
def _wire_fakes(monkeypatch):
    fake = _FakeAcme()
    monkeypatch.setattr(server, "_acme", fake)
    monkeypatch.setattr(server, "_store", server.QuoteStore())
    return fake


def test_get_quote_schema_dispatches():
    assert server.get_quote_schema("GB")["currency"] == "GBP"
    assert server.get_quote_schema("FR")["currency"] == "EUR"
    assert server.get_quote_schema("DE")["error"] == "unsupported_country"
    assert server.get_quote_schema()["country"] == "GB"


def test_lookup_vehicle_found_and_not_found():
    found = server.lookup_vehicle("AB12CDE", "GB")
    assert found["found"] is True
    assert found["make"] == "Volkswagen"
    assert found["country_code"] == "GB"
    missing = server.lookup_vehicle("ZZ99ZZZ", "GB")
    assert missing == {"found": False, "country_code": "GB", "identifier": "ZZ99ZZZ"}


def test_lookup_vehicle_fr():
    found = server.lookup_vehicle("aa 123 bb", "fr")
    assert found["found"] is True and found["country_code"] == "FR"
    assert found["make"] == "Renault"


def test_submit_quote_request_gb_builds_age_and_quote(_wire_fakes, monkeypatch):
    # Freeze "today" so age derivation is deterministic.
    monkeypatch.setattr(server, "_today", lambda: date(2024, 5, 1))
    out = server.submit_quote_request("GB", make_gb_quote_input().model_dump(mode="json"))
    payload = _wire_fakes.captured["payload"]
    assert payload["age"] == 34
    assert payload["insurance_group"] == 20
    assert payload["cover_tier"] == "comprehensive"
    assert payload["voluntary_excess"] == 250
    assert out["currency"] == "GBP"
    assert out["country_code"] == "GB"
    assert out["annual_premium"] == 642.12
    assert out["monthly_premium"] == round(642.12 / 12, 2)
    assert out["quote_ref"] == "Q-X"


def test_submit_quote_request_fr(_wire_fakes):
    out = server.submit_quote_request("fr", make_fr_quote_input().model_dump(mode="json"))
    payload = _wire_fakes.captured["payload"]
    assert _wire_fakes.captured["country_code"] == "FR"
    assert payload["bonus_malus"] == 0.85
    assert payload["formule"] == "tous_risques"
    assert payload["franchise"] == 300
    assert "value" in payload
    assert out["currency"] == "EUR"
    assert out["country_code"] == "FR"


def test_submit_quote_request_unsupported_country():
    out = server.submit_quote_request("DE", {})
    assert out == {"error": "unsupported_country", "country_code": "DE"}


def test_create_handoff_link_mints_guid_and_stores():
    quote = server.submit_quote_request("GB", make_gb_quote_input().model_dump(mode="json"))
    link = server.create_handoff_link(quote)
    assert link["handoff_url"].endswith(link["guid"])
    assert server._store.get(link["guid"]) is not None


def test_handoff_page_escapes_html():
    from starlette.testclient import TestClient

    qi = make_gb_quote_input()
    qi.vehicle.make = "<script>alert(1)</script>"
    quote = {
        "quote_ref": "Q-<b>x</b>", "currency": "GBP", "annual_premium": 642.12,
        "monthly_premium": 53.51, "country_code": "GB",
        "input": qi.model_dump(mode="json"),
    }
    link = server.create_handoff_link(quote)
    client = TestClient(server.mcp.streamable_http_app())
    page = client.get(f"/handoff/{link['guid']}")
    assert page.status_code == 200
    assert "<script>alert(1)</script>" not in page.text
    assert "&lt;script&gt;" in page.text


def test_handoff_page_renders_known_and_unknown(monkeypatch):
    from starlette.testclient import TestClient

    monkeypatch.setattr(server, "_today", lambda: date(2024, 5, 1))
    quote = server.submit_quote_request("GB", make_gb_quote_input().model_dump(mode="json"))
    link = server.create_handoff_link(quote)
    client = TestClient(server.mcp.streamable_http_app())
    ok = client.get(f"/handoff/{link['guid']}")
    assert ok.status_code == 200 and "642.12" in ok.text and "GBP" in ok.text
    missing = client.get("/handoff/deadbeef")
    assert missing.status_code == 404
