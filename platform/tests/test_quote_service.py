"""Tests for the session-scoped quote service (brief §6, §9, §17.4)."""

import uuid

import pytest

from app.events import store as event_store
from app.quote_service import apply_patch, create_quote, deep_merge, get_quote
from app.required import MANDATORY_FIELDS
from app.sessions import store as session_store


@pytest.fixture(autouse=True)
def reset():
    session_store.reset()
    event_store._reset()
    yield
    session_store.reset()
    event_store._reset()


def test_create_returns_quote_started_with_session():
    state = create_quote()
    uuid.UUID(state["quoteId"])  # valid GUID
    assert len(state["sessionId"]) >= 40
    assert state["journeyState"] == "quote_started"
    assert state["missingFields"] == MANDATORY_FIELDS
    assert state["currentOutcome"] is None


def test_create_emits_quote_created_without_session():
    state = create_quote()
    domain = [e for e in event_store.all() if e.type == "QUOTE_CREATED"]
    assert len(domain) == 1
    assert domain[0].category == "domain"
    assert domain[0].payload == {"quoteId": state["quoteId"]}
    # The session id must never appear in the event.
    assert state["sessionId"] not in str(domain[0].payload)


def test_get_with_correct_session_works_and_hides_session():
    created = create_quote()
    state = get_quote(created["quoteId"], created["sessionId"])
    assert state is not None
    assert state["quoteId"] == created["quoteId"]
    assert "sessionId" not in state


def test_get_with_wrong_or_absent_session_returns_none():
    created = create_quote()
    assert get_quote(created["quoteId"], "wrong") is None
    assert get_quote(created["quoteId"], "") is None
    assert get_quote("unknown", created["sessionId"]) is None


def test_patch_deep_merges_and_preserves_siblings():
    created = create_quote()
    qid, sid = created["quoteId"], created["sessionId"]
    apply_patch(qid, sid, {"customer": {"firstName": "Sam"}})
    apply_patch(qid, sid, {"customer": {"surname": "Sample"}})
    rec = session_store.get(qid, sid)
    # Second patch must not blank the first sibling.
    assert rec is not None
    assert rec.data["customer"]["firstName"] == "Sam"
    assert rec.data["customer"]["surname"] == "Sample"


def test_patch_recomputes_missing_fields_and_state():
    created = create_quote()
    qid, sid = created["quoteId"], created["sessionId"]
    state = apply_patch(qid, sid, {"vehicle": {"registration": "FX19ZTC"}})
    assert state["journeyState"] == "collecting"
    assert "vehicle.registration" not in state["missingFields"]


def test_patch_wrong_session_returns_none():
    created = create_quote()
    assert apply_patch(created["quoteId"], "wrong", {"customer": {"firstName": "X"}}) is None


def test_patch_emits_quote_updated():
    created = create_quote()
    apply_patch(created["quoteId"], created["sessionId"], {"vehicle": {"make": "Ford"}})
    updated = [e for e in event_store.all() if e.type == "QUOTE_UPDATED"]
    assert len(updated) == 1
    assert updated[0].payload == {"quoteId": created["quoteId"]}


def test_reaching_ready_to_price():
    created = create_quote()
    qid, sid = created["quoteId"], created["sessionId"]
    # Fill every mandatory path.
    patch: dict = {}
    for path in MANDATORY_FIELDS:
        node = patch
        parts = path.split(".")
        for part in parts[:-1]:
            node = node.setdefault(part, {})
        node[parts[-1]] = "x"
    state = apply_patch(qid, sid, patch)
    assert state["missingFields"] == []
    assert state["journeyState"] == "ready_to_price"


def test_deep_merge_drops_null_and_empty_leaves():
    base = {"customer": {"firstName": "Sam"}}
    deep_merge(base, {"customer": {"firstName": None, "surname": "", "email": "a@b.c"}})
    # null/empty must not blank or set.
    assert base["customer"]["firstName"] == "Sam"
    assert "surname" not in base["customer"]
    assert base["customer"]["email"] == "a@b.c"
