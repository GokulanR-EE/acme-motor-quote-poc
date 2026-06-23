"""HTTP client over the Python platform — the MCP's integration seam.

The platform (``PLATFORM_URL``, default ``http://localhost:8070``) is the
source of truth: it owns quote state, the journey, validation, pricing and
underwriting. This client speaks its REST contract and returns parsed JSON.

Session security (brief §16 / plan): quote state is retrievable **only** with
the matching ``sessionId``. We carry it in the ``X-Session-Id`` header on get
and update; the open lookups (vehicle/address) need no session. The
conversation layer holds the ``quoteId`` + ``sessionId`` and passes them in.

The transport is injectable so tests can drive an ``httpx.MockTransport``
without a live platform.
"""

from __future__ import annotations

import os
from typing import Optional

import httpx

DEFAULT_PLATFORM_URL = "http://localhost:8070"


class PlatformClient:
    """Thin, stateless wrapper over the platform's REST API."""

    def __init__(
        self,
        base_url: Optional[str] = None,
        transport: Optional[httpx.BaseTransport] = None,
        timeout: float = 10.0,
    ) -> None:
        self._base_url = (base_url or os.getenv("PLATFORM_URL", DEFAULT_PLATFORM_URL)).rstrip("/")
        self._client = httpx.Client(
            base_url=self._base_url, transport=transport, timeout=timeout
        )

    def _json(self, response: httpx.Response) -> dict:
        response.raise_for_status()
        return response.json()

    def start_quote(self) -> dict:
        """Create a draft quote. Returns quoteId, sessionId, journeyState, missingFields."""
        return self._json(self._client.post("/quotes"))

    def get_quote(self, quote_id: str, session_id: str) -> dict:
        """Retrieve a quote's state. Requires the matching session id (404 otherwise)."""
        return self._json(
            self._client.get(
                f"/quotes/{quote_id}", headers={"X-Session-Id": session_id}
            )
        )

    def update_quote(self, quote_id: str, session_id: str, patch: dict) -> dict:
        """Apply a partial, multi-field patch. Requires the matching session id."""
        return self._json(
            self._client.patch(
                f"/quotes/{quote_id}",
                headers={"X-Session-Id": session_id},
                json={"patch": patch},
            )
        )

    def lookup_vehicle(self, registration: str) -> dict:
        """Resolve a vehicle from its registration (open lookup, no session)."""
        return self._json(self._client.get(f"/vehicles/{registration}"))

    def lookup_address(self, postcode: str) -> dict:
        """Resolve candidate addresses from a postcode (open lookup, no session)."""
        return self._json(
            self._client.get("/addresses", params={"postcode": postcode})
        )
