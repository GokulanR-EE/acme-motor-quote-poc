"""Static validation of the mock-acme WireMock mappings.

Runs WITHOUT a running WireMock server: it only parses the JSON mapping
files and asserts the expected stubs exist. This guards the contract the
MCP ``acme_client`` relies on (vehicle lookups + per-tier/formule quote
stubs) so a malformed mapping fails CI rather than the demo.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

# tests/ -> mcp-server/ -> repo root -> mock-acme/mappings
MAPPINGS_DIR = (
    Path(__file__).resolve().parents[2] / "mock-acme" / "mappings"
)

MAPPING_FILES = [
    "gb-vehicles.json",
    "gb-quotes.json",
    "fr-vehicles.json",
    "fr-quotes.json",
]


def _load(filename: str) -> list[dict]:
    path = MAPPINGS_DIR / filename
    data = json.loads(path.read_text())
    assert isinstance(data, dict), f"{filename}: top level must be an object"
    mappings = data.get("mappings")
    assert isinstance(mappings, list), f"{filename}: 'mappings' must be a list"
    assert mappings, f"{filename}: 'mappings' must be non-empty"
    return mappings


def _all_mappings() -> list[dict]:
    out: list[dict] = []
    for f in MAPPING_FILES:
        out.extend(_load(f))
    return out


def _jsonpath_equalto(mapping: dict) -> dict[str, str]:
    """Return {expression: equalTo} for every matchesJsonPath body pattern."""
    result: dict[str, str] = {}
    for pat in mapping.get("request", {}).get("bodyPatterns", []) or []:
        mj = pat.get("matchesJsonPath")
        if isinstance(mj, dict) and "expression" in mj and "equalTo" in mj:
            result[mj["expression"]] = mj["equalTo"]
    return result


@pytest.mark.parametrize("filename", MAPPING_FILES)
def test_mapping_file_is_well_formed(filename: str):
    assert (MAPPINGS_DIR / filename).exists(), f"missing {filename}"
    _load(filename)  # raises on malformed / empty


def test_gb_cover_tier_quote_stubs_exist():
    tiers = {
        m["request"]["urlPath"]: _jsonpath_equalto(m).get("$.cover_tier")
        for m in _load("gb-quotes.json")
    }
    matched = {
        _jsonpath_equalto(m).get("$.cover_tier")
        for m in _load("gb-quotes.json")
        if m.get("request", {}).get("urlPath") == "/gb/quotes"
        and m.get("request", {}).get("method") == "POST"
    }
    assert {"comprehensive", "third_party_fire_theft", "third_party_only"} <= matched
    # sanity: the urlPath dict above proves all stubs target /gb/quotes
    assert set(tiers.keys()) == {"/gb/quotes"}


def test_fr_formule_quote_stubs_exist():
    matched = {
        _jsonpath_equalto(m).get("$.formule")
        for m in _load("fr-quotes.json")
        if m.get("request", {}).get("urlPath") == "/fr/quotes"
        and m.get("request", {}).get("method") == "POST"
    }
    assert {"tous_risques", "tiers_plus", "au_tiers"} <= matched


def _vehicle_paths(filename: str) -> set[str]:
    paths: set[str] = set()
    for m in _load(filename):
        req = m.get("request", {})
        if req.get("method") != "GET":
            continue
        for key in ("urlPath", "urlPathPattern"):
            if key in req:
                paths.add(req[key])
    return paths


def test_seeded_gb_vehicle_stubs_exist():
    paths = _vehicle_paths("gb-vehicles.json")
    assert "/gb/vehicles/AB12CDE" in paths
    assert "/gb/vehicles/TS21EVS" in paths
    assert "/gb/vehicles/.*" in paths  # catch-all 404


def test_seeded_fr_vehicle_stubs_exist():
    paths = _vehicle_paths("fr-vehicles.json")
    assert "/fr/vehicles/AB123CD" in paths
    assert "/fr/vehicles/.*" in paths  # catch-all 404


def test_gb_catch_all_is_lower_priority_404():
    catch_all = next(
        m for m in _load("gb-vehicles.json")
        if m.get("request", {}).get("urlPathPattern") == "/gb/vehicles/.*"
    )
    assert catch_all["response"]["status"] == 404
    assert catch_all.get("priority", 1) > 1, "catch-all must be lower priority"


def test_quote_stubs_use_response_template_transformer():
    for filename in ("gb-quotes.json", "fr-quotes.json"):
        for m in _load(filename):
            transformers = m["response"].get("transformers", [])
            assert "response-template" in transformers, (
                f"{filename}: {m.get('name')} missing response-template transformer"
            )
            body = m["response"]["jsonBody"]
            assert body["quote_ref"] == "Q-{{jsonPath request.body '$.identifier'}}"
            assert "{{math" in body["annual_premium"]
