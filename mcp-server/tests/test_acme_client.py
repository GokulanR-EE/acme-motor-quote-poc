import json

import httpx

from app.acme_client import AcmeClient


def _client_with(handler) -> AcmeClient:
    transport = httpx.MockTransport(handler)
    http = httpx.Client(base_url="http://acme.test", transport=transport)
    return AcmeClient(base_url="http://acme.test", http=http)


def test_lookup_vehicle_gb_found():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/gb/vehicles/AB12CDE"
        return httpx.Response(200, json={
            "identifier": "AB12CDE", "make": "Volkswagen", "model": "Golf",
            "year": 2019, "value": 14000, "insurance_group": 20})
    v = _client_with(handler).lookup_vehicle("ab12 cde", "GB")
    assert v is not None and v.make == "Volkswagen" and v.insurance_group == 20


def test_lookup_vehicle_fr_found():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/fr/vehicles/AA123BB"
        return httpx.Response(200, json={
            "identifier": "AA123BB", "make": "Renault", "model": "Clio",
            "year": 2020, "value": 16000})
    v = _client_with(handler).lookup_vehicle("aa 123 bb", "FR")
    assert v is not None and v.make == "Renault" and v.insurance_group is None


def test_lookup_vehicle_not_found_returns_none():
    def handler(request): return httpx.Response(404)
    assert _client_with(handler).lookup_vehicle("ZZ99ZZZ", "GB") is None


def test_get_quote_posts_to_country_path_and_returns_json():
    captured = {}

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/gb/quotes"
        captured["body"] = json.loads(request.content)
        return httpx.Response(200, json={"quote_ref": "Q-AB12CDE", "annual_premium": 642.123})

    payload = {"identifier": "AB12CDE", "insurance_group": 20, "age": 34,
               "ncb_years": 5, "cover_tier": "comprehensive", "voluntary_excess": 250}
    out = _client_with(handler).get_quote("GB", payload)
    assert captured["body"] == payload
    assert out == {"quote_ref": "Q-AB12CDE", "annual_premium": 642.123}


def test_get_quote_fr_path():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/fr/quotes"
        return httpx.Response(200, json={"quote_ref": "Q-FR", "annual_premium": 500.0})

    out = _client_with(handler).get_quote("FR", {"identifier": "AA123BB"})
    assert out["quote_ref"] == "Q-FR"
