"""Quote service — session-scoped create / get / update (brief §6, §9, §17.4).

The backend owns the journey: it stores quote state keyed to a strong-entropy
session id, deep-merges partial greedy patches (never blanking siblings),
recomputes ``missingFields`` and ``journeyState`` server-side, and emits domain
events so the dashboard and audit trail come for free (three-layer discipline).

State shape returned to callers (brief §6):
    { quoteId, journeyState, missingFields, currentOutcome }
``sessionId`` is returned **only at creation** — never echoed by get/patch.
"""

from __future__ import annotations

from typing import Any, Optional

from app.events import store as event_store
from app.required import missing_fields
from app.sessions import QuoteRecord
from app.sessions import store as session_store


def _journey_state(data: dict, missing: list[str]) -> str:
    """Derive the journey state from the stored data (brief §6).

    - ``quote_started``  — nothing collected yet.
    - ``collecting``     — some data, still missing mandatory fields.
    - ``ready_to_price`` — no mandatory fields remain.
    """
    if not missing:
        return "ready_to_price"
    if not data:
        return "quote_started"
    return "collecting"


def _state(record: QuoteRecord) -> dict:
    """Build the brief §6 state object. Never includes the sessionId."""
    missing = missing_fields(record.data)
    return {
        "quoteId": record.quote_id,
        "journeyState": _journey_state(record.data, missing),
        "missingFields": missing,
        "currentOutcome": None,
    }


def _is_empty_leaf(value: Any) -> bool:
    """Null/empty leaves are dropped before merging (brief §17.4)."""
    if value is None:
        return True
    if isinstance(value, str) and value.strip() == "":
        return True
    return False


def deep_merge(base: dict, patch: dict) -> dict:
    """Deep-merge ``patch`` into ``base`` in place (brief §17.4).

    - Nested dicts merge recursively, so a greedy patch touching one leaf never
      blanks its siblings.
    - Null / empty-string leaves in the patch are dropped (never used to blank
      existing data).
    - Lists (e.g. ``namedDrivers``) replace wholesale — there is no leaf to
      preserve, and named-driver append has its own path in a later slice.
    """
    for key, value in patch.items():
        if isinstance(value, dict):
            existing = base.get(key)
            if not isinstance(existing, dict):
                existing = {}
            merged = deep_merge(existing, value)
            # Only set if the merge produced something (avoid blank sub-objects).
            if merged:
                base[key] = merged
        elif _is_empty_leaf(value):
            # Drop — never blank an existing value with null/empty.
            continue
        else:
            base[key] = value
    return base


def create_quote() -> dict:
    """Create a draft quote bound to a fresh session.

    Emits ``QUOTE_CREATED`` (domain). The payload includes the quoteId but
    **never** the sessionId.
    """
    record = session_store.create()
    event_store.append("QUOTE_CREATED", {"quoteId": record.quote_id}, "domain")
    state = _state(record)
    # sessionId is returned only here, at creation.
    return {**state, "sessionId": record.session_id}


def get_quote(quote_id: str, session_id: str) -> Optional[dict]:
    """Return the state for ``quote_id`` iff ``session_id`` matches; else None."""
    record = session_store.get(quote_id, session_id)
    if record is None:
        return None
    return _state(record)


def apply_patch(quote_id: str, session_id: str, patch: dict) -> Optional[dict]:
    """Deep-merge ``patch`` into the quote; recompute state; emit QUOTE_UPDATED.

    Returns ``None`` on unknown quote or session mismatch (treated as not-found).
    """
    record = session_store.get(quote_id, session_id)
    if record is None:
        return None
    deep_merge(record.data, patch or {})
    event_store.append("QUOTE_UPDATED", {"quoteId": record.quote_id}, "domain")
    return _state(record)
