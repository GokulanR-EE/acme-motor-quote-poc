"""Append-only event store with pub/sub.

This is the spine of the platform's three-layer discipline:
every state change ends with an event appended here, which is then
fanned out to all live subscribers (WebSocket / SSE) for the dashboard.
"""

from __future__ import annotations

import asyncio
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import AsyncGenerator, Literal, Optional

Category = Literal["domain", "api", "tool"]


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass
class Event:
    """A single, immutable record of something that happened."""

    seq: int
    type: str
    category: Category
    payload: dict
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    ts: str = field(default_factory=_now_iso)

    def to_dict(self) -> dict:
        return {
            "id": self.id,
            "seq": self.seq,
            "type": self.type,
            "category": self.category,
            "payload": self.payload,
            "ts": self.ts,
        }


class EventStore:
    """In-memory, append-only event log with live fan-out."""

    def __init__(self) -> None:
        self._events: list[Event] = []
        self._seq: int = 0
        self._subscribers: set[asyncio.Queue[Event]] = set()

    def append(
        self,
        type: str,
        payload: Optional[dict] = None,
        category: Category = "domain",
    ) -> Event:
        self._seq += 1
        event = Event(
            seq=self._seq,
            type=type,
            category=category,
            payload=payload or {},
        )
        self._events.append(event)
        for queue in self._subscribers:
            queue.put_nowait(event)
        return event

    def all(self) -> list[Event]:
        return list(self._events)

    async def subscribe(self) -> AsyncGenerator[Event, None]:
        """Yield events appended after this call. Cleans up on exit."""
        queue: asyncio.Queue[Event] = asyncio.Queue()
        self._subscribers.add(queue)
        try:
            while True:
                event = await queue.get()
                yield event
        finally:
            self._subscribers.discard(queue)

    def _reset(self) -> None:
        """Test helper: clear all state."""
        self._events.clear()
        self._seq = 0
        self._subscribers.clear()


# Module-level singleton shared across the app.
store = EventStore()
