from datetime import date

import pytest
from pydantic import ValidationError

from app.models import (
    ALLOWED_EXCESS, CoverTier, DriverInput, QuoteInput, VehicleInput,
)


def make_quote_input(**overrides) -> QuoteInput:
    vehicle = VehicleInput(
        registration="AB12CDE", make="Volkswagen", model="Golf",
        year=2019, value=14000.0, insurance_group=20,
    )
    driver = DriverInput(
        full_name="Jane Doe", date_of_birth=date(1990, 5, 1),
        postcode="SW1A1AA", ncb_years=5,
    )
    defaults = dict(vehicle=vehicle, driver=driver,
                    cover_tier=CoverTier.COMPREHENSIVE, voluntary_excess=250)
    defaults.update(overrides)
    return QuoteInput(**defaults)


def test_models_construct_and_defaults():
    qi = make_quote_input()
    assert qi.cover_tier == CoverTier.COMPREHENSIVE
    assert qi.voluntary_excess in ALLOWED_EXCESS
    assert qi.vehicle.insurance_group == 20
    assert qi.driver.full_name == "Jane Doe"


def test_invalid_excess_rejected():
    with pytest.raises(ValidationError):
        make_quote_input(voluntary_excess=333)
