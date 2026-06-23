"""ACME Motor Quote MCP server — the integration layer over the platform.

The Python platform (``PLATFORM_URL``, default ``http://localhost:8070``) is the
**source of truth**: it owns quote state, the journey, validation, pricing and
underwriting. This MCP exposes typed, host-agnostic tools over that contract so
the same server backs both our web app and a ChatGPT App (brief §8).

Design (brief §8):
  * **Stateless** — the platform owns state. The conversation layer holds the
    ``quoteId`` + ``sessionId`` and passes them on every call; we never cache them.
  * **Idempotent** — tools are thin pass-throughs to the platform API.
  * **Session security** — quote state is retrievable only with the matching
    ``sessionId`` (carried as ``X-Session-Id`` by the client); the platform
    returns 404 on cross-session access.

No pricing or underwriting logic lives here — the conversation must never invent
premiums, cover or outcomes.
"""

from __future__ import annotations

from mcp.server.fastmcp import FastMCP
from mcp.types import ToolAnnotations

from app.platform_client import PlatformClient

# Server-level guidance for the host LLM driving the journey (brief §7 / §16).
_INSTRUCTIONS = (
    "ACME motor-insurance new-quote assistant. The platform is the source of "
    "truth: use tool responses as fact and NEVER invent premiums, cover, "
    "underwriting outcomes, or whether a quote can be priced.\n"
    "Flow:\n"
    "1) Call start_motor_quote() once to create a draft. Keep the returned "
    "quoteId AND sessionId for the whole conversation and pass BOTH on every "
    "get/update call — they are the only key to this quote's state.\n"
    "2) Collect details greedily and in ANY order: fill as many fields at once "
    "as the user gives you. Do not enforce a question order. Send everything you "
    "confidently know via update_motor_quote(quote_id, session_id, patch) using a "
    "partial, multi-field patch.\n"
    "3) The platform decides what is still required: drive the conversation from "
    "the returned missingFields and journeyState. Ask only for what is still "
    "missing. When journeyState is ready_to_price, the quote is complete.\n"
    "4) Use lookup_vehicle(registration) to resolve make/model and "
    "lookup_address(postcode) to resolve an address; confirm with the user "
    "before storing.\n"
    "5) For renewals, claims, multi-vehicle, cancellations or any journey other "
    "than a new motor quote, tell the user to visit ACME's website instead."
)

mcp = FastMCP(
    "acme-motor-quote", host="0.0.0.0", port=8090, instructions=_INSTRUCTIONS
)

# Module-level platform client (tests monkeypatch this with a fake).
_platform = PlatformClient()


def start_motor_quote() -> dict:
    """Create a new draft motor quote.

    Returns the quoteId, sessionId, journeyState and missingFields. The caller
    must retain the quoteId and sessionId for the rest of the conversation.
    """
    return _platform.start_quote()


def get_motor_quote(quote_id: str, session_id: str) -> dict:
    """Return the current state (journeyState, missingFields, outcome) of a quote.

    Requires the sessionId issued by start_motor_quote; a mismatch is rejected.
    """
    return _platform.get_quote(quote_id, session_id)


def update_motor_quote(quote_id: str, session_id: str, patch: dict) -> dict:
    """Apply a partial, multi-field patch and return the recomputed state.

    The patch may touch any subset of fields in any order; the platform
    deep-merges it, never blanking untouched data. Requires the matching sessionId.
    """
    return _platform.update_quote(quote_id, session_id, patch)


def lookup_vehicle(registration: str) -> dict:
    """Resolve a vehicle (make/model/derivative/fuel/transmission) from its registration."""
    return _platform.lookup_vehicle(registration)


def lookup_address(postcode: str) -> dict:
    """Resolve candidate addresses from a UK postcode."""
    return _platform.lookup_address(postcode)


# Register tools with Apps-SDK annotations. Reads are read-only; start/update
# change state (non-read-only). start/lookups reach beyond the closed model
# (the platform / vendor seam), so they are open-world.
mcp.tool(
    annotations=ToolAnnotations(
        title="Start motor quote",
        readOnlyHint=False,
        destructiveHint=False,
        idempotentHint=False,
        openWorldHint=True,
    )
)(start_motor_quote)
mcp.tool(
    annotations=ToolAnnotations(title="Get motor quote", readOnlyHint=True)
)(get_motor_quote)
mcp.tool(
    annotations=ToolAnnotations(
        title="Update motor quote",
        readOnlyHint=False,
        destructiveHint=False,
        idempotentHint=True,
    )
)(update_motor_quote)
mcp.tool(
    annotations=ToolAnnotations(
        title="Look up vehicle", readOnlyHint=True, openWorldHint=True
    )
)(lookup_vehicle)
mcp.tool(
    annotations=ToolAnnotations(
        title="Look up address", readOnlyHint=True, openWorldHint=True
    )
)(lookup_address)


def main() -> None:
    mcp.run(transport="streamable-http")


if __name__ == "__main__":
    main()
