"""Tool schemas exposed to the LLM, and dispatch into the quoting core.

`state` is a per-session mutable dict; get_quote stores the QuoteInput so a
later reprice can reuse everything except the changed field.
"""

from app.mocks.vehicles import lookup_vehicle
from app.quoting.engine import price
from app.quoting.models import CoverTier, DriverInput, QuoteInput

TOOL_SCHEMAS = [
    {
        "type": "function",
        "function": {
            "name": "lookup_vehicle",
            "description": "Look up a vehicle's details from its registration plate.",
            "parameters": {
                "type": "object",
                "properties": {
                    "registration": {"type": "string", "description": "UK-style registration plate"}
                },
                "required": ["registration"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_quote",
            "description": "Produce a motor insurance quote. Looks up the vehicle by registration, then prices it.",
            "parameters": {
                "type": "object",
                "properties": {
                    "registration": {"type": "string"},
                    "age": {"type": "integer"},
                    "ncb_years": {"type": "integer", "description": "Years of no-claims bonus"},
                    "postcode": {"type": "string"},
                    "cover_tier": {
                        "type": "string",
                        "enum": [c.value for c in CoverTier],
                    },
                    "voluntary_excess": {"type": "integer", "enum": [0, 100, 250, 500, 750, 1000]},
                },
                "required": ["registration", "age", "ncb_years", "postcode"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "reprice",
            "description": "Re-price the current quote after changing cover tier and/or voluntary excess.",
            "parameters": {
                "type": "object",
                "properties": {
                    "cover_tier": {"type": "string", "enum": [c.value for c in CoverTier]},
                    "voluntary_excess": {"type": "integer", "enum": [0, 100, 250, 500, 750, 1000]},
                },
            },
        },
    },
]


def _build_quote_input(args: dict) -> QuoteInput:
    vehicle = lookup_vehicle(args["registration"])
    driver = DriverInput(
        age=args["age"], ncb_years=args["ncb_years"], postcode=args["postcode"]
    )
    return QuoteInput(
        vehicle=vehicle,
        driver=driver,
        cover_tier=CoverTier(args.get("cover_tier", "comprehensive")),
        voluntary_excess=int(args.get("voluntary_excess", 250)),
    )


def dispatch_tool(name: str, args: dict, state: dict) -> dict:
    if name == "lookup_vehicle":
        return lookup_vehicle(args["registration"]).model_dump()

    if name == "get_quote":
        qi = _build_quote_input(args)
        state["quote_input"] = qi
        return price(qi).model_dump()

    if name == "reprice":
        qi: QuoteInput = state["quote_input"]
        updated = qi.model_copy(
            update={
                "cover_tier": CoverTier(args.get("cover_tier", qi.cover_tier.value)),
                "voluntary_excess": int(args.get("voluntary_excess", qi.voluntary_excess)),
            }
        )
        state["quote_input"] = updated
        return price(updated).model_dump()

    raise ValueError(f"Unknown tool: {name}")
