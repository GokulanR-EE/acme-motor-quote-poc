"""Mandatory-field spec → ``missingFields`` computation (brief §11, §6).

The backend — not the conversation layer — owns *what is still required* before
a quote can be priced (architectural invariant, brief §3). This module encodes
the mandatory (``M``) fields from brief §11 as **dot-paths** and computes which
of them are absent from a given quote payload.

Collection is order-free (brief §4.1): we report a flat list of remaining
mandatory paths rather than a per-section phase. ``namedDrivers`` are optional
and are never required here.
"""

from __future__ import annotations

from typing import Any, List

# Mandatory fields from brief §11, as dot-paths into the whole-model payload.
# datePurchased is satisfied either by {month, year} or {notBoughtYet: true};
# we treat the presence of the datePurchased object as the mandatory marker and
# let validation of its contents live in a later slice.
MANDATORY_FIELDS: List[str] = [
    # Vehicle
    "vehicle.registration",
    "vehicle.make",
    "vehicle.model",
    "vehicle.datePurchased",
    "vehicle.value",
    "vehicle.useOfVehicle",
    "vehicle.security",
    "vehicle.dashcam",
    "vehicle.modified",
    "vehicle.imported",
    "vehicle.daytimeLocation",
    "vehicle.overnightLocation",
    "vehicle.annualMileage",
    "vehicle.registeredKeeper",
    "vehicle.legalOwner",
    # Customer
    "customer.title",
    "customer.firstName",
    "customer.surname",
    "customer.dateOfBirth",
    "customer.maritalStatus",
    "customer.childrenUnder16",
    "customer.employmentStatus",
    "customer.partTimeJob",
    "customer.yearsLivedInUK",
    "customer.address.houseNumberOrName",
    "customer.address.postcode",
    "customer.ownsProperty",
    "customer.carKeptOvernightAtAddress",
    "customer.email",
    # Driver
    "driver.licenceType",
    "driver.licenceHeldFor",
    "driver.insuranceCancelledOrVoid",
    "driver.ncdYears",
    "driver.ncdOnCompanyCar",
    # History
    "history.claimsLast3Years",
    "history.offencesLast5Years",
    "history.unspentCriminalConvictions",
    # Household
    "household.carsInHousehold",
    "household.anotherCarHasCover",
    "household.regularUseOfOtherVehicles",
    # Cover
    "cover.paymentMethod",
    "cover.coverLevel",
    "cover.coverStartDate",
    "cover.voluntaryExcess",
]


def _resolve(data: dict, path: str) -> Any:
    """Walk a dot-path through nested dicts. Returns a sentinel-free value or None."""
    current: Any = data
    for part in path.split("."):
        if not isinstance(current, dict) or part not in current:
            return None
        current = current[part]
    return current


def _is_absent(value: Any) -> bool:
    """A value is 'absent' if it is None, an empty string, or an empty container.

    Booleans (including ``False``) and ``0`` are present — they are real answers.
    """
    if value is None:
        return True
    if isinstance(value, str) and value.strip() == "":
        return True
    if isinstance(value, (dict, list)) and len(value) == 0:
        return True
    return False


def missing_fields(quote_data: dict) -> List[str]:
    """Return the mandatory dot-paths whose value is absent/empty in ``quote_data``.

    Order follows ``MANDATORY_FIELDS`` so the conversation layer gets a stable,
    section-grouped list to ask from.
    """
    quote_data = quote_data or {}
    return [
        path
        for path in MANDATORY_FIELDS
        if _is_absent(_resolve(quote_data, path))
    ]
