from app.quoting.models import (
    ALLOWED_EXCESS,
    CoverTier,
    DriverInput,
    QuoteInput,
    VehicleInput,
)


def make_quote_input(**overrides) -> QuoteInput:
    vehicle = VehicleInput(
        registration="AB12CDE",
        make="Volkswagen",
        model="Golf",
        year=2019,
        value=14000.0,
        insurance_group=20,
    )
    driver = DriverInput(age=34, ncb_years=5, postcode="SW1A1AA")
    defaults = dict(
        vehicle=vehicle,
        driver=driver,
        cover_tier=CoverTier.COMPREHENSIVE,
        voluntary_excess=250,
    )
    defaults.update(overrides)
    return QuoteInput(**defaults)


def test_models_construct_and_defaults():
    qi = make_quote_input()
    assert qi.cover_tier == CoverTier.COMPREHENSIVE
    assert qi.voluntary_excess in ALLOWED_EXCESS
    assert qi.vehicle.insurance_group == 20


def test_invalid_excess_rejected():
    import pytest
    from pydantic import ValidationError

    with pytest.raises(ValidationError):
        make_quote_input(voluntary_excess=333)


from app.quoting.engine import price
from app.quoting.models import CoverTier


def test_price_is_positive_and_has_breakdown():
    q = price(make_quote_input())
    assert q.annual_premium > 0
    assert q.monthly_premium == round(q.annual_premium / 12, 2)
    assert q.breakdown.base_rate > 0


def test_higher_excess_lowers_premium():
    low = price(make_quote_input(voluntary_excess=0)).annual_premium
    high = price(make_quote_input(voluntary_excess=1000)).annual_premium
    assert high < low


def test_more_ncb_lowers_premium():
    p0 = price(make_quote_input(driver=DriverInput(age=34, ncb_years=0, postcode="SW1A1AA"))).annual_premium
    p9 = price(make_quote_input(driver=DriverInput(age=34, ncb_years=9, postcode="SW1A1AA"))).annual_premium
    assert p9 < p0


def test_cover_tier_ordering_comp_highest():
    comp = price(make_quote_input(cover_tier=CoverTier.COMPREHENSIVE)).annual_premium
    tpft = price(make_quote_input(cover_tier=CoverTier.THIRD_PARTY_FIRE_THEFT)).annual_premium
    tpo = price(make_quote_input(cover_tier=CoverTier.THIRD_PARTY_ONLY)).annual_premium
    assert comp > tpft > tpo


def test_young_driver_pays_more():
    young = price(make_quote_input(driver=DriverInput(age=19, ncb_years=0, postcode="SW1A1AA"))).annual_premium
    mid = price(make_quote_input(driver=DriverInput(age=40, ncb_years=0, postcode="SW1A1AA"))).annual_premium
    assert young > mid
