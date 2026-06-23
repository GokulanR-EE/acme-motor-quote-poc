import asyncio

import pytest

from app.events import Event, EventStore


def test_seq_increments_and_ids_unique():
    s = EventStore()
    e1 = s.append("A")
    e2 = s.append("B")
    e3 = s.append("C")

    assert [e1.seq, e2.seq, e3.seq] == [1, 2, 3]

    ids = {e1.id, e2.id, e3.id}
    assert len(ids) == 3
    for e in (e1, e2, e3):
        assert isinstance(e.id, str)
        assert len(e.id) == 36


def test_category_defaults_to_domain():
    s = EventStore()
    e = s.append("PING", {"k": "v"})
    assert e.category == "domain"


def test_all_returns_appended_events_in_order():
    s = EventStore()
    s.append("A")
    s.append("B")
    s.append("C")

    events = s.all()
    assert [e.type for e in events] == ["A", "B", "C"]
    assert all(isinstance(e, Event) for e in events)


def test_to_dict_shape():
    s = EventStore()
    e = s.append("PING", {"echo": 1}, "api")
    d = e.to_dict()
    assert d["type"] == "PING"
    assert d["category"] == "api"
    assert d["payload"] == {"echo": 1}
    assert d["seq"] == 1
    assert d["id"] == e.id
    assert "ts" in d


async def test_subscriber_receives_event_appended_after_subscribe():
    s = EventStore()
    s.append("BEFORE")

    received = []

    async def consume():
        async for event in s.subscribe():
            received.append(event)
            break

    task = asyncio.create_task(consume())
    await asyncio.sleep(0.01)  # let the subscriber register

    s.append("AFTER", {"x": 1})

    await asyncio.wait_for(task, timeout=1.0)

    assert len(received) == 1
    assert received[0].type == "AFTER"
