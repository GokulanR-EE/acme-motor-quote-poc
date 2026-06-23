"""Tests for the session-scoped quote store."""

import uuid

from app.sessions import SessionStore, new_session_id


def test_create_returns_guid_and_strong_session():
    store = SessionStore()
    rec = store.create()
    # quote id is a uuid4 GUID.
    parsed = uuid.UUID(rec.quote_id)
    assert parsed.version == 4
    # session id is strong-entropy, ~43 url-safe chars.
    assert len(rec.session_id) >= 40
    assert rec.data == {}


def test_session_ids_are_unique():
    assert new_session_id() != new_session_id()


def test_get_with_correct_session_returns_record():
    store = SessionStore()
    rec = store.create({"customer": {"firstName": "Sam"}})
    got = store.get(rec.quote_id, rec.session_id)
    assert got is rec


def test_get_with_wrong_session_returns_none():
    store = SessionStore()
    rec = store.create()
    assert store.get(rec.quote_id, "wrong-session") is None


def test_get_with_empty_session_returns_none():
    store = SessionStore()
    rec = store.create()
    assert store.get(rec.quote_id, "") is None
    assert store.get(rec.quote_id, None) is None


def test_get_unknown_quote_returns_none():
    store = SessionStore()
    rec = store.create()
    assert store.get("nonexistent", rec.session_id) is None


def test_cross_session_access_rejected():
    store = SessionStore()
    a = store.create()
    b = store.create()
    # b's session cannot read a's quote.
    assert store.get(a.quote_id, b.session_id) is None


def test_reset_clears():
    store = SessionStore()
    rec = store.create()
    store.reset()
    assert store.get(rec.quote_id, rec.session_id) is None
