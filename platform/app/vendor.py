"""Vendor SOAP seam (mocked) — brief §15 lookups, plan cross-cutting note.

A real UK motor insurer reaches an **external vendor over SOAP** for values it
does not own — vehicle data from a registration, and address candidates from a
postcode. We model that boundary as a ``VendorClient`` interface so the services
that depend on it never see the transport.

- Today: ``MockVendorClient`` returns deterministic **synthetic** data (no real
  brand or vehicle data anywhere — brief naming rule).
- Later: a ``SoapVendorClient`` generated from the vendor WSDL will implement the
  same ``VendorClient`` interface, with **zero change** to the callers.

Design decision (documented): for an **unknown registration** the mock returns a
deterministic synthetic fallback vehicle (never ``None``) so demos always have a
make/model to show; address lookup likewise returns a deterministic candidate
list for unseeded postcodes.
"""

from __future__ import annotations

from typing import List, Optional, Protocol


class VendorClient(Protocol):
    """The external-vendor seam a real insurer calls over SOAP.

    The real implementation will be a ``SoapVendorClient`` generated from the
    vendor WSDL. Services depend only on this interface — never on the transport.
    """

    def lookup_vehicle(self, registration: str) -> Optional[dict]:
        """Resolve a registration to make/model/derivative/fuel/transmission."""
        ...

    def lookup_address(self, postcode: str) -> List[dict]:
        """Resolve a postcode to a list of candidate addresses."""
        ...


def _normalise_reg(registration: str) -> str:
    return (registration or "").upper().replace(" ", "")


def _normalise_postcode(postcode: str) -> str:
    return (postcode or "").upper().replace(" ", "")


# Seeded synthetic registrations. One ordinary car, plus a performance /
# high-value car used later for the referral demo (brief §15: value > £75k or
# performance vehicle). All synthetic — no real plates.
_SEEDED_VEHICLES: dict[str, dict] = {
    "FX19ZTC": {
        "make": "Ford",
        "model": "Focus",
        "derivative": "Titanium 1.0 EcoBoost",
        "fuel": "Petrol",
        "transmission": "Manual",
    },
    "VW68ABC": {
        "make": "Volkswagen",
        "model": "Golf",
        "derivative": "Life 1.5 TSI",
        "fuel": "Petrol",
        "transmission": "Automatic",
    },
    # Performance / high-value car for referral demos.
    "PF21XYZ": {
        "make": "Performance Marque",
        "model": "GT Coupe",
        "derivative": "Twin-Turbo 600",
        "fuel": "Petrol",
        "transmission": "Automatic",
    },
}

# Seeded synthetic postcodes → candidate addresses.
_SEEDED_ADDRESSES: dict[str, List[dict]] = {
    "RG11AA": [
        {"houseNumberOrName": "1", "line1": "1 Sample Street", "postcode": "RG1 1AA"},
        {"houseNumberOrName": "2", "line1": "2 Sample Street", "postcode": "RG1 1AA"},
        {"houseNumberOrName": "3", "line1": "3 Sample Street", "postcode": "RG1 1AA"},
    ],
    "M12AB": [
        {"houseNumberOrName": "10", "line1": "10 Example Road", "postcode": "M1 2AB"},
        {"houseNumberOrName": "12", "line1": "12 Example Road", "postcode": "M1 2AB"},
    ],
}


class MockVendorClient:
    """Deterministic, synthetic ``VendorClient`` for the PoC (no real data)."""

    def lookup_vehicle(self, registration: str) -> Optional[dict]:
        key = _normalise_reg(registration)
        if key in _SEEDED_VEHICLES:
            return {"registration": registration, **_SEEDED_VEHICLES[key]}
        # Deterministic synthetic fallback so a demo always has a make/model.
        return {
            "registration": registration,
            "make": "Sample Motors",
            "model": "Saloon",
            "derivative": "Standard",
            "fuel": "Petrol",
            "transmission": "Manual",
        }

    def lookup_address(self, postcode: str) -> List[dict]:
        key = _normalise_postcode(postcode)
        if key in _SEEDED_ADDRESSES:
            return list(_SEEDED_ADDRESSES[key])
        # Deterministic synthetic fallback candidate.
        normalised = (postcode or "").strip().upper()
        return [
            {
                "houseNumberOrName": "1",
                "line1": "1 Synthetic Avenue",
                "postcode": normalised,
            }
        ]


# Module-level default the API depends on (swap for SoapVendorClient later).
vendor: VendorClient = MockVendorClient()
