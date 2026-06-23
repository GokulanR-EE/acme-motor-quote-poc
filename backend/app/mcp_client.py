"""Real QuoteService over MCP streamable-HTTP.

This client connects to the deterministic MCP server (which prices via a mocked
ACME). Only ``parse_tool_result`` is unit-tested — the live transport needs a
running server, so end-to-end behaviour is covered separately by integration
tests against the running MCP server, not here.
"""

from __future__ import annotations

import json
import os

from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client


def parse_tool_result(result) -> dict:
    """Extract a plain dict from an MCP ``CallToolResult``.

    Prefers ``structuredContent`` (unwrapping a ``{"result": ...}`` envelope
    when present), then falls back to JSON-parsing the first text content
    block, then to an empty dict.
    """
    structured = getattr(result, "structuredContent", None)
    if isinstance(structured, dict):
        return structured.get("result", structured)

    for block in getattr(result, "content", None) or []:
        text = getattr(block, "text", None)
        if text:
            try:
                return json.loads(text)
            except (ValueError, TypeError):
                continue
    return {}


def _url() -> str:
    return os.getenv("MCP_URL", "http://localhost:8090/mcp")


class MCPQuoteService:
    """QuoteService backed by the live MCP server over streamable-HTTP."""

    async def _call(self, name: str, args: dict) -> dict:
        async with streamablehttp_client(_url()) as (read, write, _):
            async with ClientSession(read, write) as session:
                await session.initialize()
                res = await session.call_tool(name, args)
                return parse_tool_result(res)

    async def get_quote_schema(self, country_code: str) -> dict:
        return await self._call("get_quote_schema", {"country_code": country_code})

    async def lookup_vehicle(self, identifier: str, country_code: str) -> dict:
        return await self._call(
            "lookup_vehicle", {"identifier": identifier, "country_code": country_code}
        )

    async def submit_quote_request(self, country_code: str, data: dict) -> dict:
        return await self._call(
            "submit_quote_request", {"country_code": country_code, "data": data}
        )

    async def create_handoff_link(self, quote: dict) -> dict:
        return await self._call("create_handoff_link", {"quote": quote})
