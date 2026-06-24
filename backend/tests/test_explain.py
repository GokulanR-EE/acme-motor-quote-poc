"""explain_quote — explains using API truth (premium / reasons), never invents."""

from app.explain import explain_quote


def _quote_pricing():
    return {
        "annualPremium": 430.0,
        "currency": "GBP",
        "iptIncluded": True,
        "monthly": {"deposit": 43.0, "instalment": 43.0, "instalments": 10},
        "compulsoryExcess": 350,
        "voluntaryExcess": 250,
        "totalExcess": 600,
        "ncdYears": 5,
        "outcome": "quote",
        "reasons": [],
        "breakdown": [
            {"label": "Base premium", "amount": 350.0},
            {"label": "Comprehensive cover", "amount": 80.0},
        ],
    }


def test_quote_explanation_contains_premium_and_monthly():
    text = explain_quote(_quote_pricing())
    assert "430" in text
    assert "43" in text  # monthly instalment
    # one-line breakdown summary references a breakdown label.
    assert "Base premium" in text or "Comprehensive" in text


def test_refer_explanation_contains_reasons_and_adviser_next_step():
    pricing = {
        "outcome": "refer",
        "reasons": ["Vehicle value exceeds £75,000"],
        "annualPremium": 0.0,
        "breakdown": [],
    }
    text = explain_quote(pricing)
    assert "Vehicle value exceeds £75,000" in text
    assert "adviser" in text.lower() or "website" in text.lower()


def test_decline_explanation_contains_reasons_and_website_route():
    pricing = {
        "outcome": "decline",
        "reasons": ["Driver is under 18"],
        "annualPremium": 0.0,
        "breakdown": [],
    }
    text = explain_quote(pricing)
    assert "Driver is under 18" in text
    assert "website" in text.lower()
    assert "cannot offer" in text.lower() or "can't offer" in text.lower()
