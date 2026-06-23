"""End-to-end integration: the MCP server's tools against a LIVE WireMock ACME.

Unlike the other tests (which stub ACME), this boots the real WireMock mock from
`mock-acme/mappings` and exercises the actual seam: MCP tool -> acme_client HTTP
-> WireMock response templating -> premium -> MCP rounding/currency. Skipped
automatically if Java or the WireMock jar is unavailable, so CI stays green.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import time
from datetime import date
from pathlib import Path

import httpx
import pytest

from starlette.testclient import TestClient

from app import server
from app.acme_client import AcmeClient

_ROOT = Path(__file__).resolve().parents[2]
_JAR = Path(os.getenv("WIREMOCK_JAR", _ROOT / "mock-acme" / "wiremock-standalone-3.9.1.jar"))
_PORT = 8080

pytestmark = pytest.mark.skipif(
    shutil.which("java") is None or not _JAR.exists(),
    reason="Java or the WireMock standalone jar is not available",
)


@pytest.fixture(scope="module")
def acme_base() -> str:
    proc = subprocess.Popen(
        [
            "java", "-jar", str(_JAR),
            "--root-dir", str(_ROOT / "mock-acme"),
            "--global-response-templating",
            "--port", str(_PORT),
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    base = f"http://localhost:{_PORT}"
    try:
        deadline = time.time() + 40
        while time.time() < deadline:
            try:
                httpx.get(f"{base}/__admin/mappings", timeout=1.0)
                break
            except Exception:
                time.sleep(0.5)
        else:
            raise RuntimeError("WireMock did not become ready in time")
        yield base
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except Exception:
            proc.kill()


def _gb_data() -> dict:
    return {
        "vehicle": {
            "identifier": "AB12CDE", "make": "Volkswagen", "model": "Golf",
            "year": 2019, "value": 14000, "insurance_group": 20,
        },
        "driver": {
            "full_name": "Jane Doe", "date_of_birth": "1990-05-01",
            "postcode": "SW1A1AA", "ncb_years": 5,
        },
        "cover_tier": "comprehensive", "voluntary_excess": 250,
    }


def _fr_data() -> dict:
    return {
        "vehicle": {
            "identifier": "AB123CD", "make": "Renault", "model": "Clio",
            "year": 2020, "value": 16000, "insurance_group": None,
        },
        "driver": {
            "full_name": "Jean Dupont", "date_of_birth": "1985-03-10",
            "code_postal": "75001", "bonus_malus": 0.90,
        },
        "formule": "tous_risques", "franchise": 300,
    }


def test_gb_vehicle_lookup_real(acme_base):
    v = AcmeClient(base_url=acme_base).lookup_vehicle("ab12 cde", "GB")
    assert v is not None
    assert v.make == "Volkswagen" and v.model == "Golf" and v.insurance_group == 20


def test_unknown_vehicle_returns_none_real(acme_base):
    assert AcmeClient(base_url=acme_base).lookup_vehicle("ZZ99ZZZ", "GB") is None


def test_fr_vehicle_lookup_real(acme_base):
    v = AcmeClient(base_url=acme_base).lookup_vehicle("AB123CD", "FR")
    assert v is not None
    assert v.make == "Renault" and v.insurance_group is None


def test_gb_quote_end_to_end(acme_base, monkeypatch):
    monkeypatch.setattr(server, "_acme", AcmeClient(base_url=acme_base))
    monkeypatch.setattr(server, "_today", lambda: date(2024, 5, 1))  # age 34
    out = server.submit_quote_request("GB", _gb_data())
    assert out["currency"] == "GBP"
    assert out["quote_ref"] == "Q-AB12CDE"
    assert out["annual_premium"] == 413.82  # deterministic WireMock pricing
    assert out["monthly_premium"] == round(out["annual_premium"] / 12, 2)
    assert out["country_code"] == "GB"


def test_fr_quote_end_to_end(acme_base, monkeypatch):
    monkeypatch.setattr(server, "_acme", AcmeClient(base_url=acme_base))
    out = server.submit_quote_request("FR", _fr_data())
    assert out["currency"] == "EUR"
    assert out["quote_ref"] == "Q-AB123CD"
    assert out["annual_premium"] == 340.47
    assert out["monthly_premium"] == round(out["annual_premium"] / 12, 2)


def test_full_chain_schema_to_handoff(acme_base, monkeypatch):
    monkeypatch.setattr(server, "_acme", AcmeClient(base_url=acme_base))
    monkeypatch.setattr(server, "_today", lambda: date(2024, 5, 1))

    schema = server.get_quote_schema("GB")
    assert schema["currency"] == "GBP"

    found = server.lookup_vehicle("AB12CDE", "GB")
    assert found["found"] is True and found["insurance_group"] == 20

    quote = server.submit_quote_request("GB", _gb_data())
    link = server.create_handoff_link(quote)

    page = TestClient(server.mcp.streamable_http_app()).get(f"/handoff/{link['guid']}")
    assert page.status_code == 200
    assert "413.82" in page.text and "GBP" in page.text and "Volkswagen" in page.text
