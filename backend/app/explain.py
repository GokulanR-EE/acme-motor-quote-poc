"""Explanation helpers — the conversation layer explains using **API truth**.

The backend never prices or underwrites; ``explain_quote`` turns the platform's
pricing object (brief §11) into a customer-facing sentence using only values the
API returned — the premium, the monthly split, the breakdown labels, and the
underwriting reasons. It never invents a number or a reason.

* ``quote``   → premium + monthly + a one-line breakdown summary.
* ``refer``   → the reasons + "an adviser will be in touch / try the website".
* ``decline`` → the reasons + "we cannot offer cover" + route to the ACME website.
"""

from __future__ import annotations


def _money(value) -> str:
    """Format a GBP amount: drop a trailing ``.0`` but keep real pence."""
    try:
        amount = float(value)
    except (TypeError, ValueError):
        return str(value)
    if amount == int(amount):
        return f"£{int(amount)}"
    return f"£{amount:,.2f}"


def _breakdown_summary(breakdown) -> str:
    """A compact one-line summary of the rating breakdown labels (API truth)."""
    labels = [str(line.get("label")) for line in (breakdown or []) if line.get("label")]
    if not labels:
        return ""
    return ", ".join(labels)


def explain_quote(pricing: dict) -> str:
    """Explain a pricing object in one short paragraph, using only its own values."""
    pricing = pricing or {}
    outcome = pricing.get("outcome")
    reasons = pricing.get("reasons") or []

    if outcome == "quote":
        annual = _money(pricing.get("annualPremium"))
        monthly = pricing.get("monthly") or {}
        parts = [f"Your annual premium is {annual}"]
        instalment = monthly.get("instalment")
        instalments = monthly.get("instalments")
        if instalment is not None and instalments:
            parts.append(
                f"or {_money(instalment)} a month over {instalments} instalments"
            )
        line = ", ".join(parts) + "."
        summary = _breakdown_summary(pricing.get("breakdown"))
        if summary:
            line += f" That's made up of: {summary}."
        return line

    reason_text = "; ".join(str(r) for r in reasons) if reasons else "your circumstances"

    if outcome == "refer":
        return (
            f"I can't finalise a price online right now because: {reason_text}. "
            "One of our advisers will be in touch, or you can continue on the ACME website."
        )

    if outcome == "decline":
        return (
            f"Unfortunately we cannot offer cover for this quote because: {reason_text}. "
            "Please visit the ACME website for other options."
        )

    return "I wasn't able to produce a price for this quote."
