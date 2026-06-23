"""OpenAI function-calling loop with an offline MOCK_LLM fallback."""

import json
import os
import re

from app.llm.tools import TOOL_SCHEMAS, dispatch_tool

SYSTEM_PROMPT = (
    "You are ACME's friendly motor insurance assistant. Help the user get a car "
    "insurance quote by collecting, in natural language: vehicle registration, "
    "driver age, years of no-claims bonus, and postcode. Call get_quote once you "
    "have them. Offer to adjust cover tier or voluntary excess via reprice. Be "
    "concise, warm, and professional. Never claim to be a real or binding quote — "
    "this is an illustrative demo."
)

_QUOTE_TOOLS = {"get_quote", "reprice"}


def _emit_quote_events(tool_name, result):
    events = []
    if tool_name in _QUOTE_TOOLS and "annual_premium" in result:
        events.append({"type": "quote", "data": result})
    return events


def run_agent_turn(user_message: str, session: dict, client=None):
    """Generator of events for one user turn. Mutates session history/state."""
    if os.getenv("MOCK_LLM") == "1" and client is None:
        yield from _run_mock_turn(user_message, session)
        return

    history = session["history"]
    state = session["state"]
    if not history:
        history.append({"role": "system", "content": SYSTEM_PROMPT})
    history.append({"role": "user", "content": user_message})

    while True:
        resp = client.chat.completions.create(
            model=os.getenv("OPENAI_MODEL", "gpt-4o-mini"),
            messages=history,
            tools=TOOL_SCHEMAS,
        )
        msg = resp.choices[0].message

        if msg.tool_calls:
            history.append(
                {
                    "role": "assistant",
                    "content": msg.content or "",
                    "tool_calls": [
                        {
                            "id": tc.id,
                            "type": "function",
                            "function": {
                                "name": tc.function.name,
                                "arguments": tc.function.arguments,
                            },
                        }
                        for tc in msg.tool_calls
                    ],
                }
            )
            for tc in msg.tool_calls:
                args = json.loads(tc.function.arguments or "{}")
                result = dispatch_tool(tc.function.name, args, state)
                yield from _emit_quote_events(tc.function.name, result)
                history.append(
                    {
                        "role": "tool",
                        "tool_call_id": tc.id,
                        "content": json.dumps(result),
                    }
                )
            continue

        history.append({"role": "assistant", "content": msg.content or ""})
        yield {"type": "text", "data": msg.content or ""}
        return


def _run_mock_turn(user_message: str, session: dict):
    """Deterministic offline flow: extract details by regex, quote, explain."""
    state = session["state"]
    reg = (re.search(r"\b([A-Z]{2}\d{2}\s?[A-Z]{3})\b", user_message.upper()) or [None, None])[1]
    age = (re.search(r"\bage\s*(\d{2})\b|\b(\d{2})\s*years old", user_message) or [None])
    age_val = next((g for g in (age.groups() if hasattr(age, "groups") else []) if g), None)
    ncb = re.search(r"(\d{1,2})\s*(?:years?\s*)?(?:ncb|no[- ]?claims)", user_message.lower())
    postcode = re.search(r"\b([A-Z]{1,2}\d[A-Z\d]?\s?\d[A-Z]{2})\b", user_message.upper())

    if reg and age_val and ncb and postcode:
        result = dispatch_tool(
            "get_quote",
            {
                "registration": reg.replace(" ", ""),
                "age": int(age_val),
                "ncb_years": int(ncb.group(1)),
                "postcode": postcode.group(1),
            },
            state,
        )
        yield {"type": "quote", "data": result}
        yield {
            "type": "text",
            "data": (
                f"Thanks! Here's your illustrative ACME quote: "
                f"£{result['annual_premium']:.2f}/year "
                f"(£{result['monthly_premium']:.2f}/month). "
                "Try adjusting the excess or cover tier below."
            ),
        }
    else:
        yield {
            "type": "text",
            "data": (
                "I can quote your car insurance. Please tell me your registration, "
                "age, years of no-claims bonus, and postcode — e.g. "
                "'I drive AB12CDE, age 34, 5 years NCB, SW1A 1AA'."
            ),
        }
