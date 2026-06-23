"""Tests for the mocked vendor SOAP seam."""

from app.vendor import MockVendorClient


def test_seeded_registration_returns_expected_make_model():
    client = MockVendorClient()
    result = client.lookup_vehicle("FX19ZTC")
    assert result["make"] == "Ford"
    assert result["model"] == "Focus"
    assert result["fuel"] == "Petrol"
    assert result["transmission"] == "Manual"
    assert "derivative" in result


def test_registration_lookup_is_space_and_case_insensitive():
    client = MockVendorClient()
    assert client.lookup_vehicle("fx19 ztc")["model"] == "Focus"


def test_performance_car_seeded_for_referral_demo():
    client = MockVendorClient()
    result = client.lookup_vehicle("PF21XYZ")
    assert result["make"] == "Performance Marque"
    assert result["model"] == "GT Coupe"


def test_unknown_registration_returns_deterministic_fallback():
    client = MockVendorClient()
    result = client.lookup_vehicle("ZZ99ZZZ")
    # Documented design: fallback (never None), deterministic.
    assert result is not None
    assert result["make"] == "Sample Motors"
    assert client.lookup_vehicle("ZZ99ZZZ") == result


def test_seeded_postcode_returns_candidate_addresses():
    client = MockVendorClient()
    candidates = client.lookup_address("RG1 1AA")
    assert len(candidates) >= 2
    assert all("houseNumberOrName" in c for c in candidates)
    assert all("postcode" in c for c in candidates)


def test_unknown_postcode_returns_deterministic_fallback():
    client = MockVendorClient()
    candidates = client.lookup_address("ZZ9 9ZZ")
    assert len(candidates) == 1
    assert candidates[0]["postcode"] == "ZZ9 9ZZ"
