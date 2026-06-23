"""Tests for mandatory-field → missingFields computation (brief §11)."""

from app.required import MANDATORY_FIELDS, missing_fields


def test_empty_quote_misses_all_mandatory():
    missing = missing_fields({})
    assert missing == MANDATORY_FIELDS
    assert len(missing) == len(MANDATORY_FIELDS)


def test_none_quote_misses_all_mandatory():
    assert missing_fields(None) == MANDATORY_FIELDS


def test_known_mandatory_paths_present():
    # Spot-check a representative path from each section.
    for path in [
        "vehicle.registration",
        "customer.dateOfBirth",
        "customer.address.postcode",
        "driver.ncdYears",
        "history.claimsLast3Years",
        "household.carsInHousehold",
        "cover.coverLevel",
    ]:
        assert path in MANDATORY_FIELDS


def test_named_drivers_not_required():
    assert not any(p.startswith("namedDrivers") for p in MANDATORY_FIELDS)


def test_partial_quote_reports_remaining():
    data = {
        "vehicle": {"registration": "FX19ZTC", "make": "Ford", "model": "Focus"},
        "customer": {"firstName": "Sam", "address": {"postcode": "RG1 1AA"}},
    }
    missing = missing_fields(data)
    # Filled paths are gone.
    assert "vehicle.registration" not in missing
    assert "vehicle.make" not in missing
    assert "customer.firstName" not in missing
    assert "customer.address.postcode" not in missing
    # Unfilled remain.
    assert "vehicle.value" in missing
    assert "customer.dateOfBirth" in missing
    assert "customer.address.houseNumberOrName" in missing


def test_false_and_zero_count_as_present():
    data = {
        "vehicle": {"dashcam": False, "modified": False, "annualMileage": 0},
        "history": {"claimsLast3Years": 0, "unspentCriminalConvictions": False},
    }
    missing = missing_fields(data)
    assert "vehicle.dashcam" not in missing
    assert "vehicle.modified" not in missing
    assert "vehicle.annualMileage" not in missing
    assert "history.claimsLast3Years" not in missing
    assert "history.unspentCriminalConvictions" not in missing


def test_empty_string_counts_as_absent():
    data = {"customer": {"email": "", "firstName": "   "}}
    missing = missing_fields(data)
    assert "customer.email" in missing
    assert "customer.firstName" in missing


def test_date_purchased_object_satisfies_path():
    data = {"vehicle": {"datePurchased": {"month": 6, "year": 2020}}}
    assert "vehicle.datePurchased" not in missing_fields(data)
    # Empty object does not satisfy it.
    assert "vehicle.datePurchased" in missing_fields({"vehicle": {"datePurchased": {}}})
