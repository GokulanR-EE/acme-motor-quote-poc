from app.schemas import get_schema


def _field(schema, name):
    return next((f for f in schema["fields"] if f["name"] == name), None)


def test_gb_schema():
    s = get_schema("GB")
    assert s["country"] == "GB"
    assert s["currency"] == "GBP"
    assert "driving_licence" in s["documents"]
    reg = _field(s, "registration")
    assert reg is not None and reg["type"] == "string" and reg["required"] is True
    cover = _field(s, "cover_tier")
    assert cover["type"] == "enum" and cover["default"] == "comprehensive"
    excess = _field(s, "voluntary_excess")
    assert excess["enum"] == [0, 100, 250, 500, 750, 1000]
    assert excess["default"] == 250


def test_fr_schema():
    s = get_schema("FR")
    assert s["country"] == "FR"
    assert s["currency"] == "EUR"
    assert "carte_grise" in s["documents"]
    immat = _field(s, "immatriculation")
    assert immat is not None and immat["required"] is True
    bm = _field(s, "bonus_malus")
    assert bm is not None and bm["type"] == "number" and bm["required"] is True
    formule = _field(s, "formule")
    assert formule["enum"] == ["tous_risques", "tiers_plus", "au_tiers"]
    assert formule["default"] == "tous_risques"
    franchise = _field(s, "franchise")
    assert franchise["enum"] == [0, 150, 300, 500, 800]
    assert franchise["default"] == 300


def test_unsupported_country():
    s = get_schema("DE")
    assert s["error"] == "unsupported_country"
    assert s["country"] == "DE"
    assert s["supported"] == ["GB", "FR"]


def test_lowercase_normalised():
    assert get_schema("fr")["country"] == "FR"
    assert get_schema("gb")["currency"] == "GBP"


def test_empty_or_none_defaults_to_gb():
    assert get_schema("")["country"] == "GB"
    assert get_schema(None)["country"] == "GB"
