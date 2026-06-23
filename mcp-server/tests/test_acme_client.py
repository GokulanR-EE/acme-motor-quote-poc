import json
from datetime import date

import httpx

from app.acme_client import AcmeClient
from app.models import CoverTier
from tests.test_models import make_quote_input


def _client_with(handler) -> AcmeClient:
    transport = httpx.MockTransport(handler)
    http = httpx.Client(base_url="http://acme.test", transport=transport)
    return AcmeClient(base_url="http://acme.test", http=http)


def test_lookup_vehicle_found():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/vehicles/AB12CDE"
        return httpx.Response(200, json={
            "registration": "AB12CDE", "make": "Volkswagen", "model": "Golf",
            "year": 2019, "value": 14000, "insurance_group": 20})
    v = _client_with(handler).lookup_vehicle("ab12 cde")
    assert v is not None and v.make == "Volkswagen" and v.insurance_group == 20


def test_lookup_vehicle_not_found_returns_none():
    def handler(request): return httpx.Response(404)
    assert _client_with(handler).lookup_vehicle("ZZ99ZZZ") is None


def test_get_quote_sends_numeric_inputs_and_parses_quote():
    captured = {}
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/quotes"
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"quote_ref": "Q-AB12CDE", "annual_premium": 642.123})
    qi = make_quote_input(cover_tier=CoverTier.COMPREHENSIVE, voluntary_excess=250)
    quote = _client_with(handler).get_quote(qi, today=date(2024, 5, 1))
    assert captured["body"]["age"] == 34
    assert captured["body"]["insurance_group"] == 20
    assert captured["body"]["cover_tier"] == "comprehensive"
    assert captured["body"]["voluntary_excess"] == 250
    assert quote.quote_ref == "Q-AB12CDE"
    assert quote.annual_premium == 642.12
    assert quote.monthly_premium == round(642.12 / 12, 2)
    assert quote.input.vehicle.registration == "AB12CDE"


def test_get_quote_age_before_birthday_is_one_less():
    captured = {}

    def handler(request):
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"quote_ref": "Q-X", "annual_premium": 500.0})

    # DOB 1990-05-01; as of 2024-04-30 (day before birthday) age is 33, not 34.
    qi = make_quote_input()
    _client_with(handler).get_quote(qi, today=date(2024, 4, 30))
    assert captured["body"]["age"] == 33
