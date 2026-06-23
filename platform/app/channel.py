"""Live event channel: WebSocket + Server-Sent Events.

Both endpoints first replay the full history (so a freshly connected
dashboard is fully caught up) and then stream new events as they are
appended to the store. Front-end-agnostic: pure HTTP + events.
"""

from __future__ import annotations

import json

from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from fastapi.responses import StreamingResponse

from app.events import store

router = APIRouter()


@router.get("/events")
async def events_sse() -> StreamingResponse:
    """Stream events as SSE: replay history, then live tail."""

    async def event_stream():
        for event in store.all():
            yield f"data: {json.dumps(event.to_dict())}\n\n"
        async for event in store.subscribe():
            yield f"data: {json.dumps(event.to_dict())}\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")


@router.websocket("/ws")
async def events_ws(websocket: WebSocket) -> None:
    """Stream events over a WebSocket: replay history, then live tail."""
    await websocket.accept()
    try:
        for event in store.all():
            await websocket.send_json(event.to_dict())
        async for event in store.subscribe():
            await websocket.send_json(event.to_dict())
    except WebSocketDisconnect:
        return
