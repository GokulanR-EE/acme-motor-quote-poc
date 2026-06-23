"""QuoteService protocol and a deterministic in-process fake.

The QuoteService abstraction lets the form-filling backend talk to either the
real MCP server (see ``mcp_client.MCPQuoteService``) or an offline fake that
mirrors the MCP server's GB+FR behaviour for tests and local development.
The backend never prices anything itself — pricing is the MCP/ACME's job.
"""

from __future__ import annotations

from typing import Protocol, runtime_checkable

_SUPPORTED = ["GB", "FR"]

_GB_SCHEMA = {
    "country": "GB",
    "currency": "GBP",
    "documents": ["driving_licence", "v5c_logbook"],
    "fields": [
        {"name": "registration", "label": "Registration", "type": "string", "required": True},
        {"name": "full_name", "label": "Full name", "type": "string", "required": True},
        {"name": "date_of_birth", "label": "Date of birth", "type": "date", "required": True},
        {"name": "postcode", "label": "Postcode", "type": "string", "required": True},
        {"name": "ncb_years", "label": "No-claims bonus years", "type": "integer", "required": True},
        {
            "name": "cover_tier",
            "label": "Cover tier",
            "type": "enum",
            "required": False,
            "enum": ["third_party", "third_party_fire_theft", "comprehensive"],
            "default": "comprehensive",
        },
        {
            "name": "voluntary_excess",
            "label": "Voluntary excess",
            "type": "integer",
            "required": False,
            "default": 250,
        },
    ],
}

_FR_SCHEMA = {
    "country": "FR",
    "currency": "EUR",
    "documents": ["permis_de_conduire", "carte_grise"],
    "fields": [
        {"name": "immatriculation", "label": "Immatriculation", "type": "string", "required": True},
        {"name": "full_name", "label": "Nom complet", "type": "string", "required": True},
        {"name": "date_of_birth", "label": "Date de naissance", "type": "date", "required": True},
        {"name": "code_postal", "label": "Code postal", "type": "string", "required": True},
        {"name": "bonus_malus", "label": "Bonus-malus", "type": "number", "required": True},
        {
            "name": "formule",
            "label": "Formule",
            "type": "enum",
            "required": False,
            "enum": ["au_tiers", "tiers_plus", "tous_risques"],
            "default": "tous_risques",
        },
        {
            "name": "franchise",
            "label": "Franchise",
            "type": "integer",
            "required": False,
            "default": 300,
        },
    ],
}

# Seeded vehicles keyed by (country_code, normalised identifier).
_VEHICLES = {
    ("GB", "AB12CDE"): {
        "make": "Volkswagen",
        "model": "Golf",
        "year": 2019,
        "value": 14000,
        "insurance_group": 20,
    },
    ("FR", "AB123CD"): {
        "make": "Renault",
        "model": "Clio",
        "year": 2020,
        "value": 16000,
        "insurance_group": None,
    },
}


def _normalise(identifier: str) -> str:
    return identifier.strip().upper().replace(" ", "")


@runtime_checkable
class QuoteService(Protocol):
    """Async interface over the deterministic MCP quoting tools."""

    async def get_quote_schema(self, country_code: str) -> dict: ...

    async def lookup_vehicle(self, identifier: str, country_code: str) -> dict: ...

    async def submit_quote_request(self, country_code: str, data: dict) -> dict: ...

    async def create_handoff_link(self, quote: dict) -> dict: ...


class FakeQuoteService:
    """In-process implementation mirroring the MCP server's GB+FR behaviour."""

    async def get_quote_schema(self, country_code: str) -> dict:
        cc = country_code.upper()
        if cc == "GB":
            return dict(_GB_SCHEMA)
        if cc == "FR":
            return dict(_FR_SCHEMA)
        return {"country": country_code, "supported": list(_SUPPORTED), "error": "unsupported_country"}

    async def lookup_vehicle(self, identifier: str, country_code: str) -> dict:
        cc = country_code.upper()
        ident = _normalise(identifier)
        vehicle = _VEHICLES.get((cc, ident))
        if vehicle is None:
            return {"found": False, "country_code": cc, "identifier": ident}
        return {"found": True, "country_code": cc, "identifier": ident, **vehicle}

    async def submit_quote_request(self, country_code: str, data: dict) -> dict:
        cc = country_code.upper()
        return {
            "quote_ref": "Q-" + data["vehicle"]["identifier"],
            "currency": "GBP" if cc == "GB" else "EUR",
            "annual_premium": 642.12,
            "monthly_premium": 53.51,
            "country_code": cc,
            "input": data,
        }

    async def create_handoff_link(self, quote: dict) -> dict:
        return {
            "guid": "fake-guid-0001",
            "handoff_url": "http://localhost:8090/handoff/fake-guid-0001",
        }
