"""QuoteService — the conversation backend's seam onto the platform/MCP.

The conversation layer owns conversation only; **the backend owns the journey**
(brief §6 invariant). It never prices or validates — it asks the platform what is
still ``missing`` and what the ``journeyState`` is, and drives the conversation
from that.

Two implementations behind one ``QuoteService`` Protocol:

* ``PlatformQuoteService`` — an HTTP client straight to the mock platform
  (``PLATFORM_URL``, default ``http://localhost:8070``). This is the path used in
  production wiring's place for determinism; the MCP server is the production
  integration layer and an MCP-backed adapter could implement the same Protocol.
* ``FakeQuoteService`` — an in-process fake that mirrors the platform's
  deep-merge + ``missingFields`` + ``journeyState`` behaviour exactly, so tests
  and offline runs need no network and no running platform.

Front-end-agnostic: nothing here assumes web vs ChatGPT — this is one
conversation adapter onto the same platform contract.
"""

from __future__ import annotations

import os
from typing import Any, Optional, Protocol, runtime_checkable

# --- Whole-model mandatory spec (brief §11), mirrored from the platform so the
# FakeQuoteService computes the same missingFields without importing the platform.
MANDATORY_FIELDS: list[str] = [
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
    "driver.licenceType",
    "driver.licenceHeldFor",
    "driver.insuranceCancelledOrVoid",
    "driver.ncdYears",
    "driver.ncdOnCompanyCar",
    "history.claimsLast3Years",
    "history.offencesLast5Years",
    "history.unspentCriminalConvictions",
    "household.carsInHousehold",
    "household.anotherCarHasCover",
    "household.regularUseOfOtherVehicles",
    "cover.paymentMethod",
    "cover.coverLevel",
    "cover.coverStartDate",
    "cover.voluntaryExcess",
]


def _resolve(data: dict, path: str) -> Any:
    current: Any = data
    for part in path.split("."):
        if not isinstance(current, dict) or part not in current:
            return None
        current = current[part]
    return current


def _is_absent(value: Any) -> bool:
    """Absent = None / empty string / empty container. ``False`` and ``0`` are present."""
    if value is None:
        return True
    if isinstance(value, str) and value.strip() == "":
        return True
    if isinstance(value, (dict, list)) and len(value) == 0:
        return True
    return False


def missing_fields(data: dict) -> list[str]:
    data = data or {}
    return [p for p in MANDATORY_FIELDS if _is_absent(_resolve(data, p))]


def journey_state(data: dict, missing: list[str]) -> str:
    if not missing:
        return "ready_to_price"
    if not data:
        return "quote_started"
    return "collecting"


def _is_empty_leaf(value: Any) -> bool:
    if value is None:
        return True
    if isinstance(value, str) and value.strip() == "":
        return True
    return False


def deep_merge(base: dict, patch: dict) -> dict:
    """Deep-merge ``patch`` into ``base`` in place (brief §17.4).

    Nested dicts merge recursively so a greedy patch never blanks a sibling;
    null / empty-string leaves are dropped (never used to blank existing data).
    Mirrors ``platform.app.quote_service.deep_merge``.
    """
    for key, value in patch.items():
        if isinstance(value, dict):
            existing = base.get(key)
            if not isinstance(existing, dict):
                existing = {}
            merged = deep_merge(existing, value)
            if merged:
                base[key] = merged
        elif _is_empty_leaf(value):
            continue
        else:
            base[key] = value
    return base


@runtime_checkable
class QuoteService(Protocol):
    """Async seam onto the platform's quote tools (brief §6, §8)."""

    async def start(self) -> dict: ...

    async def get(self, quote_id: str, session_id: str) -> Optional[dict]: ...

    async def update(self, quote_id: str, session_id: str, patch: dict) -> Optional[dict]: ...

    async def lookup_vehicle(self, registration: str) -> dict: ...

    async def lookup_address(self, postcode: str) -> dict: ...


def _platform_url() -> str:
    return os.getenv("PLATFORM_URL", "http://localhost:8070").rstrip("/")


class PlatformQuoteService:
    """QuoteService over the mock platform's HTTP contract (brief §10).

    Stateless: carries the sessionId as ``X-Session-Id`` on get/update, mirroring
    the MCP server's session-security discipline.
    """

    def __init__(self, base_url: Optional[str] = None) -> None:
        self._base = (base_url or _platform_url()).rstrip("/")

    async def _client(self):
        import httpx

        return httpx.AsyncClient(base_url=self._base, timeout=30.0)

    async def start(self) -> dict:
        async with await self._client() as client:
            resp = await client.post("/quotes")
            resp.raise_for_status()
            return resp.json()

    async def get(self, quote_id: str, session_id: str) -> Optional[dict]:
        async with await self._client() as client:
            resp = await client.get(
                f"/quotes/{quote_id}", headers={"X-Session-Id": session_id}
            )
            if resp.status_code == 404:
                return None
            resp.raise_for_status()
            return resp.json()

    async def update(self, quote_id: str, session_id: str, patch: dict) -> Optional[dict]:
        async with await self._client() as client:
            resp = await client.patch(
                f"/quotes/{quote_id}",
                json={"patch": patch},
                headers={"X-Session-Id": session_id},
            )
            if resp.status_code == 404:
                return None
            resp.raise_for_status()
            return resp.json()

    async def lookup_vehicle(self, registration: str) -> dict:
        async with await self._client() as client:
            resp = await client.get(f"/vehicles/{registration}")
            if resp.status_code == 404:
                return {"found": False, "registration": registration}
            resp.raise_for_status()
            return {"found": True, **resp.json()}

    async def lookup_address(self, postcode: str) -> dict:
        async with await self._client() as client:
            resp = await client.get("/addresses", params={"postcode": postcode})
            resp.raise_for_status()
            return resp.json()


# --- Seeded synthetic lookups, mirrored from platform.app.vendor (no real data).
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
    "PF21XYZ": {
        "make": "Performance Marque",
        "model": "GT Coupe",
        "derivative": "Twin-Turbo 600",
        "fuel": "Petrol",
        "transmission": "Automatic",
    },
}

_SEEDED_ADDRESSES: dict[str, list[dict]] = {
    "RG11AA": [
        {"houseNumberOrName": "1", "line1": "1 Sample Street", "postcode": "RG1 1AA"},
        {"houseNumberOrName": "2", "line1": "2 Sample Street", "postcode": "RG1 1AA"},
    ],
    "M12AB": [
        {"houseNumberOrName": "10", "line1": "10 Example Road", "postcode": "M1 2AB"},
    ],
}


class FakeQuoteService:
    """In-process platform mirror (deep-merge + missingFields) — no network.

    Holds quote data keyed by (quoteId, sessionId) so it faithfully mirrors the
    platform's session-scoped store and order-free collection for tests.
    """

    def __init__(self) -> None:
        self._records: dict[str, dict] = {}  # quote_id -> {"session": str, "data": dict}
        self._counter = 0

    def _state(self, quote_id: str, data: dict) -> dict:
        missing = missing_fields(data)
        return {
            "quoteId": quote_id,
            "journeyState": journey_state(data, missing),
            "missingFields": missing,
            "currentOutcome": None,
        }

    async def start(self) -> dict:
        self._counter += 1
        quote_id = f"fake-quote-{self._counter:04d}"
        session_id = f"fake-session-{self._counter:04d}"
        self._records[quote_id] = {"session": session_id, "data": {}}
        return {**self._state(quote_id, {}), "sessionId": session_id}

    async def get(self, quote_id: str, session_id: str) -> Optional[dict]:
        record = self._records.get(quote_id)
        if record is None or record["session"] != session_id:
            return None
        return self._state(quote_id, record["data"])

    async def update(self, quote_id: str, session_id: str, patch: dict) -> Optional[dict]:
        record = self._records.get(quote_id)
        if record is None or record["session"] != session_id:
            return None
        deep_merge(record["data"], patch or {})
        return self._state(quote_id, record["data"])

    async def lookup_vehicle(self, registration: str) -> dict:
        key = (registration or "").upper().replace(" ", "")
        if key in _SEEDED_VEHICLES:
            return {"found": True, "registration": registration, **_SEEDED_VEHICLES[key]}
        return {
            "found": True,
            "registration": registration,
            "make": "Sample Motors",
            "model": "Saloon",
            "derivative": "Standard",
            "fuel": "Petrol",
            "transmission": "Manual",
        }

    async def lookup_address(self, postcode: str) -> dict:
        key = (postcode or "").upper().replace(" ", "")
        candidates = _SEEDED_ADDRESSES.get(
            key,
            [{"houseNumberOrName": "1", "line1": "1 Synthetic Avenue", "postcode": (postcode or "").strip().upper()}],
        )
        return {"postcode": postcode, "candidates": list(candidates)}
