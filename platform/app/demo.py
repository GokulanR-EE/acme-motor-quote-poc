"""Stable demo GUID + session (brief §9, §17.6, §17.7).

A single, fixed quote id and session id that always resolve to a fully-populated
sample quote for demos / screenshots. The sample is **self-seeded on first
access** and isolated in the same session store as in-progress quotes, but with
a fixed id so it never collides with a real (random-GUID) quote.

The two constants below are the demo's contract — share them with the landing
page / dashboard demo:

    DEMO_QUOTE_ID   = "00000000-0000-4000-8000-000000000001"
    DEMO_SESSION_ID = "demo-session-0000000000000000000000000000000"

Pricing is left for Slice 5: ``pricing`` is seeded as ``None`` (a placeholder).
All sample data is synthetic — no real brand or customer data (brief naming rule).
"""

from __future__ import annotations

from typing import Optional

from app.sessions import QuoteRecord
from app.sessions import store as session_store

# Stable, well-known demo identifiers (brief §9, §17.7).
DEMO_QUOTE_ID = "00000000-0000-4000-8000-000000000001"
DEMO_SESSION_ID = "demo-session-0000000000000000000000000000000"

# A fully-populated sample quote: every mandatory field filled (so it reaches
# ready_to_price). Pricing is a placeholder until Slice 5.
_DEMO_DATA: dict = {
    "vehicle": {
        "registration": "FX19ZTC",
        "make": "Ford",
        "model": "Focus",
        "derivative": "Titanium 1.0 EcoBoost",
        "fuel": "Petrol",
        "transmission": "Manual",
        "datePurchased": {"month": 6, "year": 2020},
        "value": 12000,
        "useOfVehicle": "Social + commuting",
        "security": "Factory-fitted",
        "dashcam": True,
        "modified": False,
        "imported": "No",
        "daytimeLocation": "Street",
        "overnightLocation": "Drive",
        "annualMileage": 8000,
        "registeredKeeper": True,
        "legalOwner": True,
    },
    "customer": {
        "title": "Mr",
        "firstName": "Sam",
        "surname": "Sample",
        "dateOfBirth": "1990-01-01",
        "maritalStatus": "Single",
        "childrenUnder16": "0",
        "employmentStatus": "Employed",
        "partTimeJob": False,
        "yearsLivedInUK": "Since birth",
        "address": {"houseNumberOrName": "1", "postcode": "RG1 1AA"},
        "ownsProperty": True,
        "carKeptOvernightAtAddress": True,
        "email": "sam.sample@example.com",
        "mobile": "07000000000",
    },
    "driver": {
        "licenceType": "Full UK",
        "licenceHeldFor": "10",
        "insuranceCancelledOrVoid": False,
        "ncdYears": 5,
        "ncdOnCompanyCar": False,
    },
    "history": {
        "claimsLast3Years": 0,
        "offencesLast5Years": 0,
        "unspentCriminalConvictions": False,
    },
    "household": {
        "carsInHousehold": "1",
        "anotherCarHasCover": False,
        "regularUseOfOtherVehicles": "None",
    },
    "cover": {
        "paymentMethod": "Monthly instalments",
        "coverLevel": "Comprehensive",
        "coverStartDate": "2026-07-01",
        "voluntaryExcess": 250,
    },
    "namedDrivers": [],
    "marketing": {"email": True, "telephone": False, "sms": False},
    # Pricing is written by the rating engine in Slice 5 — placeholder for now.
    "pricing": None,
}


def ensure_demo_seeded() -> QuoteRecord:
    """Seed the demo quote on first access; return its record.

    Idempotent: re-seeds only if the fixed id is not already present, so it never
    disturbs an in-progress quote (which carries a random GUID).
    """
    if not session_store.exists(DEMO_QUOTE_ID):
        record = QuoteRecord(
            quote_id=DEMO_QUOTE_ID,
            session_id=DEMO_SESSION_ID,
            # Deep-copy so callers mutating their state never touch the seed.
            data={k: v for k, v in _deepcopy(_DEMO_DATA).items()},
        )
        session_store.put(record)
    return session_store._records[DEMO_QUOTE_ID]  # type: ignore[attr-defined]


def _deepcopy(value):
    import copy

    return copy.deepcopy(value)


def get_demo_quote(session_id: str) -> Optional[QuoteRecord]:
    """Self-seed then return the demo record iff the session id matches."""
    ensure_demo_seeded()
    return session_store.get(DEMO_QUOTE_ID, session_id)
