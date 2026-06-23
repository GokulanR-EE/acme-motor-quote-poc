from app.llm.tools import TOOL_SCHEMAS, dispatch_tool


def test_tool_schemas_present():
    names = {t["function"]["name"] for t in TOOL_SCHEMAS}
    assert names == {"lookup_vehicle", "get_quote", "reprice"}


def test_dispatch_lookup_vehicle():
    out = dispatch_tool("lookup_vehicle", {"registration": "AB12CDE"}, state={})
    assert out["make"] == "Volkswagen"


def test_dispatch_get_quote_stores_state():
    state = {}
    out = dispatch_tool(
        "get_quote",
        {
            "registration": "AB12CDE",
            "age": 34,
            "ncb_years": 5,
            "postcode": "SW1A1AA",
            "cover_tier": "comprehensive",
            "voluntary_excess": 250,
        },
        state=state,
    )
    assert out["annual_premium"] > 0
    assert "quote_input" in state  # remembered for later reprice


def test_dispatch_reprice_uses_state():
    state = {}
    dispatch_tool(
        "get_quote",
        {
            "registration": "AB12CDE", "age": 34, "ncb_years": 5,
            "postcode": "SW1A1AA", "cover_tier": "comprehensive",
            "voluntary_excess": 250,
        },
        state=state,
    )
    cheaper = dispatch_tool("reprice", {"voluntary_excess": 1000}, state=state)
    assert cheaper["annual_premium"] > 0
