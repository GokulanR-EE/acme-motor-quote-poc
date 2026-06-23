"""Deterministic MCP server: three tools + a GUID-protected handoff page.

No LLM runs here. Tool inputs are validated by pydantic at the boundary, and
any text that originated from a document is treated as data, never instructions.
"""

from __future__ import annotations

import os

from mcp.server.fastmcp import FastMCP
from starlette.requests import Request
from starlette.responses import HTMLResponse

from app.acme_client import AcmeClient
from app.models import Quote, QuoteInput
from app.store import QuoteStore

mcp = FastMCP("acme-motor-quote", host="0.0.0.0", port=8090)

_acme = AcmeClient(base_url=os.getenv("ACME_BASE_URL", "http://localhost:8080"))
_store = QuoteStore()


def lookup_vehicle(registration: str) -> dict:
    """Look up a vehicle's details from its registration plate."""
    vehicle = _acme.lookup_vehicle(registration)
    if vehicle is None:
        return {"found": False, "registration": registration.strip().upper().replace(" ", "")}
    return {"found": True, **vehicle.model_dump()}


def submit_quote_request(quote_input: dict) -> dict:
    """Validate the collected form and get a priced quote from ACME."""
    qi = QuoteInput.model_validate(quote_input)
    return _acme.get_quote(qi).model_dump(mode="json")


def create_handoff_link(quote: dict) -> dict:
    """Store a quote and mint a non-enumerable GUID handoff link."""
    q = Quote.model_validate(quote)
    guid = _store.save(q)
    base = os.getenv("PUBLIC_BASE_URL", "http://localhost:8090").rstrip("/")
    return {"guid": guid, "handoff_url": f"{base}/handoff/{guid}"}


mcp.tool()(lookup_vehicle)
mcp.tool()(submit_quote_request)
mcp.tool()(create_handoff_link)


def _quote_html(q: Quote) -> str:
    v = q.input.vehicle
    return f"""<!doctype html><html><head><meta charset="utf-8">
<title>ACME Motor Quote</title></head>
<body style="font-family:sans-serif;background:#f7f7fb;padding:40px">
  <div style="max-width:480px;margin:auto;background:#fff;border-radius:12px;
       border-left:6px solid #00008f;padding:24px">
    <div style="color:#00008f;font-weight:700">ACME Motor Quote</div>
    <div style="opacity:.7">{v.make} {v.model} ({v.year}) &middot; {v.registration}</div>
    <div style="font-size:34px;font-weight:800;margin:12px 0">
      &pound;{q.annual_premium:.2f}<span style="font-size:14px;font-weight:400"> /year</span></div>
    <div style="color:#ff1721">&pound;{q.monthly_premium:.2f} /month</div>
    <div style="font-size:11px;opacity:.6;margin-top:12px">
      Quote ref {q.quote_ref}. Illustrative demo &mdash; mock data only, not a binding ACME quote.</div>
  </div></body></html>"""


@mcp.custom_route("/handoff/{guid}", methods=["GET"])
async def handoff(request: Request) -> HTMLResponse:
    guid = request.path_params["guid"]
    quote = _store.get(guid)
    if quote is None:
        return HTMLResponse("<h1>Quote not found or expired</h1>", status_code=404)
    return HTMLResponse(_quote_html(quote))


def main() -> None:
    mcp.run(transport="streamable-http")


if __name__ == "__main__":
    main()
