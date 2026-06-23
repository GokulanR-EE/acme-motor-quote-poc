"""Tests for the stable demo GUID self-seeding (brief §9, §17.7)."""

import pytest

from app.demo import (
    DEMO_QUOTE_ID,
    DEMO_SESSION_ID,
    ensure_demo_seeded,
    get_demo_quote,
)
from app.required import missing_fields
from app.sessions import store as session_store


@pytest.fixture(autouse=True)
def reset():
    session_store.reset()
    yield
    session_store.reset()


def test_demo_self_seeds_fully_populated():
    rec = get_demo_quote(DEMO_SESSION_ID)
    assert rec is not None
    assert rec.quote_id == DEMO_QUOTE_ID
    # Every mandatory field filled → reaches ready_to_price.
    assert missing_fields(rec.data) == []


def test_demo_wrong_session_returns_none():
    assert get_demo_quote("wrong-session") is None


def test_demo_seed_is_idempotent_and_isolated():
    ensure_demo_seeded()
    other = session_store.create({"customer": {"firstName": "Real"}})
    ensure_demo_seeded()  # re-seed must not disturb the in-progress quote
    assert session_store.get(other.quote_id, other.session_id).data == {
        "customer": {"firstName": "Real"}
    }


def test_demo_pricing_is_placeholder_until_slice5():
    rec = get_demo_quote(DEMO_SESSION_ID)
    assert rec.data["pricing"] is None
