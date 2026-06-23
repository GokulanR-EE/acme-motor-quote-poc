"""Country-aware HTTP transport for the (mocked) ACME vehicle + quote APIs.

This client is transport only: it looks up vehicles and forwards a pre-built
quote payload. It contains NO pricing or age logic — ACME owns pricing, and
the server owns input normalisation. The ``http`` client is injectable so
tests can supply an ``httpx.MockTransport``.
"""

from __future__ import annotations

import httpx

from app.models import VehicleDetails


class AcmeClient:
    def __init__(self, base_url: str, http: httpx.Client | None = None) -> None:
        self._http = http or httpx.Client(base_url=base_url, timeout=10.0)

    def lookup_vehicle(
        self, identifier: str, country_code: str = "GB"
    ) -> VehicleDetails | None:
        ident = identifier.strip().upper().replace(" ", "")
        cc = country_code.lower()
        resp = self._http.get(f"/{cc}/vehicles/{ident}")
        if resp.status_code == 404:
            return None
        resp.raise_for_status()
        return VehicleDetails.model_validate(resp.json())

    def get_quote(self, country_code: str, payload: dict) -> dict:
        cc = country_code.lower()
        resp = self._http.post(f"/{cc}/quotes", json=payload)
        resp.raise_for_status()
        return resp.json()
