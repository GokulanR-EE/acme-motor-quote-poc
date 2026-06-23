"""Tests for the MCP tool functions.

Tools are stateless thin wrappers: they forward to the module-level platform
client and return its parsed dict unchanged. We monkeypatch that client with a
fake and assert each tool delegates correctly — in particular that the session
id flows through get/update.
"""

from __future__ import annotations

import pytest

from app import server


class FakePlatformClient:
    def __init__(self):
        self.calls = []

    def start_quote(self):
        self.calls.append(("start_quote",))
        return {
            "quoteId": "q-1",
            "sessionId": "s-1",
            "journeyState": "quote_started",
            "missingFields": ["vehicle.registration"],
            "currentOutcome": None,
        }

    def get_quote(self, quote_id, session_id):
        self.calls.append(("get_quote", quote_id, session_id))
        return {"quoteId": quote_id, "journeyState": "collecting", "missingFields": []}

    def update_quote(self, quote_id, session_id, patch):
        self.calls.append(("update_quote", quote_id, session_id, patch))
        return {"quoteId": quote_id, "journeyState": "ready_to_price", "missingFields": []}

    def lookup_vehicle(self, registration):
        self.calls.append(("lookup_vehicle", registration))
        return {"registration": registration, "make": "Ford", "model": "Focus"}

    def lookup_address(self, postcode):
        self.calls.append(("lookup_address", postcode))
        return {"postcode": postcode, "candidates": [{"line1": "10 Downing St"}]}


@pytest.fixture
def fake(monkeypatch):
    client = FakePlatformClient()
    monkeypatch.setattr(server, "_platform", client)
    return client


def test_start_motor_quote_returns_creation_state(fake):
    result = server.start_motor_quote()
    assert result["quoteId"] == "q-1"
    assert result["sessionId"] == "s-1"
    assert result["journeyState"] == "quote_started"
    assert fake.calls == [("start_quote",)]


def test_get_motor_quote_forwards_session_id(fake):
    result = server.get_motor_quote("q-1", "s-1")
    assert result["journeyState"] == "collecting"
    assert fake.calls == [("get_quote", "q-1", "s-1")]


def test_update_motor_quote_forwards_session_id_and_patch(fake):
    patch = {"customer": {"firstName": "Sam"}, "vehicle": {"annualMileage": 8000}}
    result = server.update_motor_quote("q-1", "s-1", patch)
    assert result["journeyState"] == "ready_to_price"
    assert fake.calls == [("update_quote", "q-1", "s-1", patch)]


def test_lookup_vehicle_delegates(fake):
    result = server.lookup_vehicle("AB12 CDE")
    assert result["make"] == "Ford"
    assert fake.calls == [("lookup_vehicle", "AB12 CDE")]


def test_lookup_address_delegates(fake):
    result = server.lookup_address("SW1A 1AA")
    assert result["candidates"][0]["line1"] == "10 Downing St"
    assert fake.calls == [("lookup_address", "SW1A 1AA")]


def test_tools_are_registered_with_expected_annotations():
    tools = {t.name: t for t in server.mcp._tool_manager.list_tools()}
    expected = {
        "start_motor_quote",
        "get_motor_quote",
        "update_motor_quote",
        "lookup_vehicle",
        "lookup_address",
    }
    assert expected <= set(tools)

    # read-only hints per the brief.
    assert tools["get_motor_quote"].annotations.readOnlyHint is True
    assert tools["lookup_vehicle"].annotations.readOnlyHint is True
    assert tools["lookup_address"].annotations.readOnlyHint is True
    # state-changing tools are not read-only.
    assert tools["start_motor_quote"].annotations.readOnlyHint is False
    assert tools["update_motor_quote"].annotations.readOnlyHint is False
    # open-world lookups + creation reach external/world state.
    assert tools["lookup_vehicle"].annotations.openWorldHint is True
    assert tools["lookup_address"].annotations.openWorldHint is True
    assert tools["start_motor_quote"].annotations.openWorldHint is True
