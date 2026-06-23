"""Country-aware pydantic models for the motor-quote form and resulting quote.

The MCP server holds NO pricing logic. These models describe the inputs the
host collects (per country) and the shape of the quote ACME returns.
"""

from __future__ import annotations

from datetime import date
from enum import Enum

from pydantic import BaseModel, Field, field_validator

# GB voluntary excess options (£) and FR franchise options (€).
ALLOWED_EXCESS = [0, 100, 250, 500, 750, 1000]
ALLOWED_FRANCHISE = [0, 150, 300, 500, 800]


class CoverTier(str, Enum):
    COMPREHENSIVE = "comprehensive"
    THIRD_PARTY_FIRE_THEFT = "third_party_fire_theft"
    THIRD_PARTY_ONLY = "third_party_only"


class Formule(str, Enum):
    TOUS_RISQUES = "tous_risques"
    TIERS_PLUS = "tiers_plus"
    AU_TIERS = "au_tiers"


class VehicleDetails(BaseModel):
    """A vehicle as returned by ACME's lookup. ``identifier`` is the GB
    registration or FR immatriculation. ``insurance_group`` is GB-only."""

    identifier: str
    make: str
    model: str
    year: int = Field(ge=1980, le=2027)
    value: float = Field(gt=0)
    insurance_group: int | None = Field(default=None, ge=1, le=50)


class GbDriverInput(BaseModel):
    full_name: str
    date_of_birth: date
    postcode: str
    ncb_years: int = Field(ge=0, le=20)


class GbQuoteInput(BaseModel):
    vehicle: VehicleDetails
    driver: GbDriverInput
    cover_tier: CoverTier = CoverTier.COMPREHENSIVE
    voluntary_excess: int = 250

    @field_validator("voluntary_excess")
    @classmethod
    def _excess_allowed(cls, v: int) -> int:
        if v not in ALLOWED_EXCESS:
            raise ValueError(f"voluntary_excess must be one of {ALLOWED_EXCESS}")
        return v


class FrDriverInput(BaseModel):
    full_name: str
    date_of_birth: date
    code_postal: str
    bonus_malus: float = Field(ge=0.50, le=3.50)


class FrQuoteInput(BaseModel):
    vehicle: VehicleDetails
    driver: FrDriverInput
    formule: Formule = Formule.TOUS_RISQUES
    franchise: int = 300

    @field_validator("franchise")
    @classmethod
    def _franchise_allowed(cls, v: int) -> int:
        if v not in ALLOWED_FRANCHISE:
            raise ValueError(f"franchise must be one of {ALLOWED_FRANCHISE}")
        return v


class Quote(BaseModel):
    quote_ref: str
    currency: str
    annual_premium: float
    monthly_premium: float
    country_code: str
    input: dict
