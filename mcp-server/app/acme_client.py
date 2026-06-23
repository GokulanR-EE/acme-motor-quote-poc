"""HTTP client for the (mocked) ACME vehicle + quote APIs.

Computing the driver's age from their date of birth is data normalisation,
not pricing — ACME owns all pricing. Presentation rounding (2dp, monthly)
also lives here, not in the AI.
"""

from __future__ import annotations

from datetime import date

import httpx

from app.models import Quote, QuoteInput, VehicleInput


def _age(dob: date, today: date) -> int:
    return today.year - dob.year - ((today.month, today.day) < (dob.month, dob.day))


class AcmeClient:
    def __init__(self, base_url: str, http: httpx.Client | None = None) -> None:
        self._http = http or httpx.Client(base_url=base_url, timeout=10.0)

    def lookup_vehicle(self, registration: str) -> VehicleInput | None:
        reg = registration.strip().upper().replace(" ", "")
        resp = self._http.get(f"/vehicles/{reg}")
        if resp.status_code == 404:
            return None
        resp.raise_for_status()
        return VehicleInput.model_validate(resp.json())

    def get_quote(self, qi: QuoteInput, today: date | None = None) -> Quote:
        today = today or date.today()
        payload = {
            "registration": qi.vehicle.registration,
            "insurance_group": qi.vehicle.insurance_group,
            "age": _age(qi.driver.date_of_birth, today),
            "ncb_years": qi.driver.ncb_years,
            "cover_tier": qi.cover_tier.value,
            "voluntary_excess": qi.voluntary_excess,
        }
        resp = self._http.post("/quotes", json=payload)
        resp.raise_for_status()
        data = resp.json()
        annual = round(float(data["annual_premium"]), 2)
        return Quote(
            quote_ref=str(data["quote_ref"]),
            annual_premium=annual,
            monthly_premium=round(annual / 12, 2),
            input=qi,
        )
