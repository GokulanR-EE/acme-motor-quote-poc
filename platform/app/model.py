"""Whole quote data model — brief §11.

Mirrors a representative UK motor insurer's "Your details" capture form.
Every leaf field is OPTIONAL so partial / greedy patches validate (brief §8,
§4.1): the LLM extractor returns only the fields it can confidently determine,
and the backend recomputes what is still missing (see ``required.py``).

Field names use the exact camelCase from brief §11 (e.g. ``dateOfBirth``,
``annualMileage``, ``houseNumberOrName``). The top-level *journey* / quote-state
object (``quoteId``, ``journeyState``, ``missingFields``, ``currentOutcome``) is
separate — see ``quote_service.py``.

All data flowing through this model is synthetic — no real brand or customer
data anywhere (brief naming rule).
"""

from __future__ import annotations

from typing import List, Optional

from pydantic import BaseModel, ConfigDict


class _Base(BaseModel):
    # Accept and round-trip the camelCase field names verbatim; allow unknown
    # keys to pass validation rather than reject a greedy patch.
    model_config = ConfigDict(extra="allow")


class DatePurchased(_Base):
    month: Optional[int] = None
    year: Optional[int] = None
    notBoughtYet: Optional[bool] = None


class Vehicle(_Base):
    registration: Optional[str] = None
    make: Optional[str] = None
    model: Optional[str] = None
    derivative: Optional[str] = None
    fuel: Optional[str] = None
    transmission: Optional[str] = None
    datePurchased: Optional[DatePurchased] = None
    value: Optional[float] = None
    useOfVehicle: Optional[str] = None
    security: Optional[str] = None
    dashcam: Optional[bool] = None
    modified: Optional[bool] = None
    imported: Optional[str] = None
    daytimeLocation: Optional[str] = None
    overnightLocation: Optional[str] = None
    annualMileage: Optional[int] = None
    registeredKeeper: Optional[bool] = None
    legalOwner: Optional[bool] = None


class Address(_Base):
    houseNumberOrName: Optional[str] = None
    postcode: Optional[str] = None


class Customer(_Base):
    title: Optional[str] = None
    firstName: Optional[str] = None
    surname: Optional[str] = None
    dateOfBirth: Optional[str] = None
    maritalStatus: Optional[str] = None
    childrenUnder16: Optional[str] = None
    employmentStatus: Optional[str] = None
    partTimeJob: Optional[bool] = None
    yearsLivedInUK: Optional[str] = None
    address: Optional[Address] = None
    ownsProperty: Optional[bool] = None
    carKeptOvernightAtAddress: Optional[bool] = None
    email: Optional[str] = None
    mobile: Optional[str] = None


class Driver(_Base):
    licenceType: Optional[str] = None
    licenceHeldFor: Optional[str] = None
    insuranceCancelledOrVoid: Optional[bool] = None
    ncdYears: Optional[int] = None
    ncdOnCompanyCar: Optional[bool] = None


class History(_Base):
    claimsLast3Years: Optional[int] = None
    offencesLast5Years: Optional[int] = None
    unspentCriminalConvictions: Optional[bool] = None


class Household(_Base):
    carsInHousehold: Optional[str] = None
    anotherCarHasCover: Optional[bool] = None
    regularUseOfOtherVehicles: Optional[str] = None


class Cover(_Base):
    paymentMethod: Optional[str] = None
    coverLevel: Optional[str] = None
    coverStartDate: Optional[str] = None
    voluntaryExcess: Optional[float] = None
    promoCode: Optional[str] = None


class NamedDriver(_Base):
    title: Optional[str] = None
    firstName: Optional[str] = None
    surname: Optional[str] = None
    dateOfBirth: Optional[str] = None
    relationshipToPolicyholder: Optional[str] = None
    maritalStatus: Optional[str] = None
    licenceType: Optional[str] = None
    licenceHeldFor: Optional[str] = None
    claimsLast3Years: Optional[int] = None
    offencesLast5Years: Optional[int] = None


class Marketing(_Base):
    email: Optional[bool] = None
    telephone: Optional[bool] = None
    sms: Optional[bool] = None


class Monthly(_Base):
    deposit: Optional[float] = None
    instalment: Optional[float] = None
    instalments: Optional[int] = None


class Pricing(_Base):
    """Output section, written by the rating engine (Slice 5)."""

    annualPremium: Optional[float] = None
    currency: Optional[str] = None
    iptIncluded: Optional[bool] = None
    monthly: Optional[Monthly] = None
    compulsoryExcess: Optional[float] = None
    voluntaryExcess: Optional[float] = None
    totalExcess: Optional[float] = None
    ncdYears: Optional[int] = None
    outcome: Optional[str] = None
    reasons: Optional[List[str]] = None
    breakdown: Optional[List[dict]] = None


class Quote(_Base):
    """The whole quote data model (brief §11). Every section is optional."""

    vehicle: Optional[Vehicle] = None
    customer: Optional[Customer] = None
    driver: Optional[Driver] = None
    history: Optional[History] = None
    household: Optional[Household] = None
    cover: Optional[Cover] = None
    namedDrivers: List[NamedDriver] = []
    marketing: Optional[Marketing] = None
    pricing: Optional[Pricing] = None
