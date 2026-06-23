"""ACME Mock Motor Quote Platform — FastAPI app.

Implements the three-layer discipline foundation:
    (tool fn) -> platform API fn (logs request+response via record_api_call)
             -> mutate state + append domain event.

State (quotes etc.) arrives in a later slice; this is the clean,
front-end-agnostic foundation: HTTP in, events out.
"""

from __future__ import annotations

from typing import Optional

from fastapi import Body, FastAPI

from app.channel import router as channel_router
from app.events import store

app = FastAPI(title="ACME Mock Motor Quote Platform")
app.include_router(channel_router)


def record_api_call(name: str, request, response) -> None:
    """API layer primitive: log an API call's request + response.

    Appends an ``API_CALL`` event (category ``"api"``) so every call
    crossing the platform boundary is observable on the live channel.
    """
    store.append(
        "API_CALL",
        {"api": name, "request": request, "response": response},
        "api",
    )


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}


@app.post("/ping")
async def ping(body: Optional[dict] = Body(default=None)) -> dict:
    request = body or {}
    result = {"pong": True, "echo": request}

    # API layer: log request + response.
    record_api_call("ping", request, result)

    # Domain layer: mutate state (none yet) + append a domain event.
    store.append("PING", {"echo": request}, "domain")

    return result
