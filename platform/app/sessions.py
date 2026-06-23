"""Session-scoped quote store (in-memory) — Phil's production-quality access rule.

Quotes are keyed by ``quoteId`` and bound to a **strong-entropy session id**
(``secrets.token_urlsafe(32)``). A quote is retrievable / updatable **only** by
presenting its session id; cross-session or missing-session access is rejected
and treated as not-found — we never reveal that a quote exists (plan
cross-cutting "MCP session security"; brief §17.6 strict retrieval).

There are no user accounts or sign-in: the high-entropy session id is the sole
access control. The store is in-memory for the PoC; ``reset()`` clears it for
tests.
"""

from __future__ import annotations

import secrets
import uuid
from dataclasses import dataclass, field
from typing import Dict, Optional


@dataclass
class QuoteRecord:
    """A stored quote: its id, the bound session id, and the whole-model data."""

    quote_id: str
    session_id: str
    data: dict = field(default_factory=dict)


def new_quote_id() -> str:
    """A GUID quote id (brief §9 — crypto.randomUUID style)."""
    return str(uuid.uuid4())


def new_session_id() -> str:
    """A strong-entropy session id (~43 url-safe chars from 32 bytes)."""
    return secrets.token_urlsafe(32)


class SessionStore:
    """In-memory quote store enforcing session-scoped access."""

    def __init__(self) -> None:
        self._records: Dict[str, QuoteRecord] = {}

    def create(self, data: Optional[dict] = None) -> QuoteRecord:
        record = QuoteRecord(
            quote_id=new_quote_id(),
            session_id=new_session_id(),
            data=data or {},
        )
        self._records[record.quote_id] = record
        return record

    def put(self, record: QuoteRecord) -> QuoteRecord:
        """Insert/replace a record verbatim (used to self-seed the demo quote)."""
        self._records[record.quote_id] = record
        return record

    def get(self, quote_id: str, session_id: str) -> Optional[QuoteRecord]:
        """Return the record only if the session id matches; else None.

        A missing/empty session id, an unknown quote id, or a mismatch all yield
        ``None`` — indistinguishable, so existence is never revealed.
        """
        record = self._records.get(quote_id)
        if record is None or not session_id:
            return None
        # Constant-time comparison to avoid leaking the session id by timing.
        if not secrets.compare_digest(record.session_id, session_id):
            return None
        return record

    def exists(self, quote_id: str) -> bool:
        return quote_id in self._records

    def reset(self) -> None:
        """Test helper: clear all stored quotes."""
        self._records.clear()


# Module-level singleton shared across the app.
store = SessionStore()
