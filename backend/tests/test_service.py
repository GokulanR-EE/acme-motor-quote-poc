from app.service import FakeQuoteService


def _field(schema, name):
    return next((f for f in schema["fields"] if f["name"] == name), None)


async def test_gb_schema_has_gbp_and_registration():
    svc = FakeQuoteService()
    schema = await svc.get_quote_schema("GB")
    assert schema["currency"] == "GBP"
    reg = _field(schema, "registration")
    assert reg is not None and reg["required"] is True


async def test_fr_schema_has_eur_immatriculation_and_bonus_malus():
    svc = FakeQuoteService()
    schema = await svc.get_quote_schema("FR")
    assert schema["currency"] == "EUR"
    assert _field(schema, "immatriculation") is not None
    assert _field(schema, "bonus_malus") is not None


async def test_unsupported_country_returns_error():
    svc = FakeQuoteService()
    schema = await svc.get_quote_schema("DE")
    assert schema["error"] == "unsupported_country"
    assert schema["country"] == "DE"
    assert set(schema["supported"]) == {"GB", "FR"}


async def test_lookup_vehicle_found_gb():
    svc = FakeQuoteService()
    v = await svc.lookup_vehicle("AB12CDE", "GB")
    assert v["found"] is True
    assert v["make"] == "Volkswagen"
    assert v["model"] == "Golf"
    assert v["year"] == 2019
    assert v["value"] == 14000
    assert v["insurance_group"] == 20
    assert v["country_code"] == "GB"


async def test_lookup_vehicle_found_fr():
    svc = FakeQuoteService()
    v = await svc.lookup_vehicle("AB123CD", "FR")
    assert v["found"] is True
    assert v["make"] == "Renault"
    assert v["model"] == "Clio"
    assert v["insurance_group"] is None
    assert v["country_code"] == "FR"


async def test_lookup_vehicle_not_found_normalises_identifier():
    svc = FakeQuoteService()
    v = await svc.lookup_vehicle("zz99 zzz", "GB")
    assert v["found"] is False
    assert v["identifier"] == "ZZ99ZZZ"
    assert v["country_code"] == "GB"


async def test_submit_returns_currency_by_country():
    svc = FakeQuoteService()
    data = {"vehicle": {"identifier": "AB12CDE"}}
    gb = await svc.submit_quote_request("GB", data)
    fr = await svc.submit_quote_request("FR", data)
    assert gb["currency"] == "GBP"
    assert fr["currency"] == "EUR"
    assert gb["quote_ref"] == "Q-AB12CDE"
    assert gb["annual_premium"] == 642.12
    assert gb["monthly_premium"] == 53.51
    assert gb["country_code"] == "GB"
    assert gb["input"] == data


async def test_create_handoff_link_shape():
    svc = FakeQuoteService()
    link = await svc.create_handoff_link({"quote_ref": "Q-AB12CDE"})
    assert link["guid"] == "fake-guid-0001"
    assert link["handoff_url"].endswith("/handoff/fake-guid-0001")
