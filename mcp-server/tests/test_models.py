from datetime import date

import pytest
from pydantic import ValidationError

from app.models import (
    ALLOWED_EXCESS,
    ALLOWED_FRANCHISE,
    CoverTier,
    Formule,
    FrDriverInput,
    FrQuoteInput,
    GbDriverInput,
    GbQuoteInput,
    VehicleDetails,
)


def make_gb_quote_input(**overrides) -> GbQuoteInput:
    vehicle = VehicleDetails(
        identifier="AB12CDE", make="Volkswagen", model="Golf",
        year=2019, value=14000.0, insurance_group=20,
    )
    driver = GbDriverInput(
        full_name="Jane Doe", date_of_birth=date(1990, 5, 1),
        postcode="SW1A1AA", ncb_years=5,
    )
    defaults = dict(vehicle=vehicle, driver=driver,
                    cover_tier=CoverTier.COMPREHENSIVE, voluntary_excess=250)
    defaults.update(overrides)
    return GbQuoteInput(**defaults)


def make_fr_quote_input(**overrides) -> FrQuoteInput:
    vehicle = VehicleDetails(
        identifier="AA123BB", make="Renault", model="Clio",
        year=2020, value=16000.0,
    )
    driver = FrDriverInput(
        full_name="Marie Martin", date_of_birth=date(1988, 3, 12),
        code_postal="75001", bonus_malus=0.85,
    )
    defaults = dict(vehicle=vehicle, driver=driver,
                    formule=Formule.TOUS_RISQUES, franchise=300)
    defaults.update(overrides)
    return FrQuoteInput(**defaults)


def test_gb_models_construct_and_defaults():
    qi = make_gb_quote_input()
    assert qi.cover_tier == CoverTier.COMPREHENSIVE
    assert qi.voluntary_excess == 250
    assert qi.vehicle.insurance_group == 20
    assert qi.driver.full_name == "Jane Doe"
    assert qi.vehicle.identifier == "AB12CDE"


def test_fr_models_construct_and_defaults():
    qi = make_fr_quote_input()
    assert qi.formule == Formule.TOUS_RISQUES
    assert qi.franchise == 300
    assert qi.vehicle.insurance_group is None  # FR leaves it None
    assert qi.driver.code_postal == "75001"
    assert qi.driver.bonus_malus == 0.85


def test_invalid_gb_excess_rejected():
    with pytest.raises(ValidationError):
        make_gb_quote_input(voluntary_excess=333)
    assert ALLOWED_EXCESS == [0, 100, 250, 500, 750, 1000]


def test_invalid_fr_franchise_rejected():
    with pytest.raises(ValidationError):
        make_fr_quote_input(franchise=999)
    assert ALLOWED_FRANCHISE == [0, 150, 300, 500, 800]


def test_fr_bonus_malus_out_of_range_rejected():
    with pytest.raises(ValidationError):
        make_fr_quote_input(driver=FrDriverInput(
            full_name="X", date_of_birth=date(1980, 1, 1),
            code_postal="75001", bonus_malus=5.0))
    with pytest.raises(ValidationError):
        make_fr_quote_input(driver=FrDriverInput(
            full_name="X", date_of_birth=date(1980, 1, 1),
            code_postal="75001", bonus_malus=0.1))
