"""Tests for the whole-model pydantic schema (brief §11)."""

from app.model import Quote


def test_empty_model_validates():
    q = Quote()
    assert q.namedDrivers == []
    # Every section is optional — an empty quote is valid.
    assert q.vehicle is None
    assert q.customer is None


def test_partial_patch_validates():
    # A greedy patch may fill a single leaf; everything else stays absent.
    q = Quote(customer={"firstName": "Sam"})
    assert q.customer is not None
    assert q.customer.firstName == "Sam"
    assert q.customer.surname is None
    assert q.vehicle is None


def test_field_names_match_brief_camelcase():
    q = Quote(
        vehicle={"annualMileage": 8000, "registration": "FX19ZTC"},
        customer={
            "dateOfBirth": "1990-01-01",
            "address": {"houseNumberOrName": "1", "postcode": "RG1 1AA"},
        },
        cover={"coverLevel": "Comprehensive"},
    )
    assert q.vehicle.annualMileage == 8000
    assert q.vehicle.registration == "FX19ZTC"
    assert q.customer.dateOfBirth == "1990-01-01"
    assert q.customer.address.houseNumberOrName == "1"
    assert q.customer.address.postcode == "RG1 1AA"
    assert q.cover.coverLevel == "Comprehensive"


def test_nested_sections_and_named_drivers():
    q = Quote(
        vehicle={"datePurchased": {"month": 6, "year": 2020}},
        namedDrivers=[{"firstName": "Alex", "relationshipToPolicyholder": "Partner"}],
        driver={"ncdYears": 5},
        history={"claimsLast3Years": 0},
        household={"carsInHousehold": "1"},
        marketing={"email": True},
    )
    assert q.vehicle.datePurchased.year == 2020
    assert len(q.namedDrivers) == 1
    assert q.namedDrivers[0].firstName == "Alex"
    assert q.driver.ncdYears == 5
    assert q.history.claimsLast3Years == 0


def test_dump_roundtrips_camelcase_keys():
    q = Quote(customer={"firstName": "Sam"})
    dumped = q.model_dump(exclude_none=True)
    assert dumped["customer"]["firstName"] == "Sam"
