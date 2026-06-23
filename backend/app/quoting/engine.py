"""Pure, deterministic motor pricing. No I/O, no LLM, no network."""

import uuid

from app.mocks.risk import band_factor, group_base_rate, postcode_to_band
from app.quoting.models import (
    CoverTier,
    PriceBreakdown,
    Quote,
    QuoteInput,
)

_COVER_FACTOR = {
    CoverTier.COMPREHENSIVE: 1.0,
    CoverTier.THIRD_PARTY_FIRE_THEFT: 0.85,
    CoverTier.THIRD_PARTY_ONLY: 0.70,
}

_EXCESS_FACTOR = {0: 1.10, 100: 1.05, 250: 1.00, 500: 0.92, 750: 0.86, 1000: 0.80}


def _age_factor(age: int) -> float:
    if age < 21:
        return 1.8
    if age < 25:
        return 1.4
    if age < 30:
        return 1.15
    if age < 60:
        return 1.0
    if age < 70:
        return 1.05
    return 1.3


def _ncb_discount(years: int) -> float:
    return min(years * 0.07, 0.65)


def price(qi: QuoteInput) -> Quote:
    base = group_base_rate(qi.vehicle.insurance_group)
    age_f = _age_factor(qi.driver.age)
    cover_f = _COVER_FACTOR[qi.cover_tier]
    band = postcode_to_band(qi.driver.postcode)
    pc_f = band_factor(band)
    ncb_d = _ncb_discount(qi.driver.ncb_years)
    exc_f = _EXCESS_FACTOR[qi.voluntary_excess]

    annual = base * age_f * cover_f * pc_f * (1 - ncb_d) * exc_f
    annual = round(annual, 2)

    return Quote(
        quote_id=str(uuid.uuid4()),
        input=qi,
        breakdown=PriceBreakdown(
            base_rate=base,
            age_factor=age_f,
            cover_factor=cover_f,
            postcode_factor=pc_f,
            ncb_discount=ncb_d,
            excess_factor=exc_f,
        ),
        annual_premium=annual,
        monthly_premium=round(annual / 12, 2),
    )
