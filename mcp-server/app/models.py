"""Pydantic models for the motor-quote form and resulting quote."""

from __future__ import annotations

from datetime import date
from enum import Enum

from pydantic import BaseModel, Field, field_validator

ALLOWED_EXCESS = [0, 100, 250, 500, 750, 1000]


class CoverTier(str, Enum):
    COMPREHENSIVE = "comprehensive"
    THIRD_PARTY_FIRE_THEFT = "third_party_fire_theft"
    THIRD_PARTY_ONLY = "third_party_only"


class VehicleInput(BaseModel):
    registration: str
    make: str
    model: str
    year: int = Field(ge=1980, le=2027)
    value: float = Field(gt=0)
    insurance_group: int = Field(ge=1, le=50)


class DriverInput(BaseModel):
    full_name: str
    date_of_birth: date
    postcode: str
    ncb_years: int = Field(ge=0, le=20)


class QuoteInput(BaseModel):
    vehicle: VehicleInput
    driver: DriverInput
    cover_tier: CoverTier = CoverTier.COMPREHENSIVE
    voluntary_excess: int = 250

    @field_validator("voluntary_excess")
    @classmethod
    def _excess_allowed(cls, v: int) -> int:
        if v not in ALLOWED_EXCESS:
            raise ValueError(f"voluntary_excess must be one of {ALLOWED_EXCESS}")
        return v


class Quote(BaseModel):
    quote_ref: str
    annual_premium: float
    monthly_premium: float
    input: QuoteInput
