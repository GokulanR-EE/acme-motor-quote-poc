"""FakeQuoteService price / purchase / issue — shapes + non-quote error results.

No network, no key: the fake mirrors the platform's §15 pricing/underwriting and
the §10 purchase-link / issue-policy contract (422/409 surfaced as structured
result dicts, never exceptions).
"""

from app.quote_session_client import MANDATORY_FIELDS, FakeQuoteService


def _complete_patch(**overrides):
    """A whole-model patch filling every mandatory field with realistic values,
    mirroring the platform test's completePatch() so the clean profile prices to
    a £430.00 'quote' (base 350 + comprehensive 80)."""
    patch: dict = {}
    for path in MANDATORY_FIELDS:
        parts = path.split(".")
        node = patch
        for part in parts[:-1]:
            node = node.setdefault(part, {})
        node[parts[-1]] = "filled"
    # Realistic rating/underwriting inputs for a clean quote.
    patch["customer"]["dateOfBirth"] = "1990-01-01"
    patch["customer"].setdefault("address", {})["postcode"] = "RG1 1AA"
    patch["vehicle"]["value"] = 12000
    patch["vehicle"]["annualMileage"] = 8000
    patch["history"]["claimsLast3Years"] = 0
    patch["history"]["offencesLast5Years"] = 0
    patch["cover"]["coverLevel"] = "Comprehensive"
    patch["cover"]["voluntaryExcess"] = 250
    patch["cover"]["coverStartDate"] = "2026-07-01"
    patch["driver"]["ncdYears"] = 5
    for path, value in overrides.items():
        parts = path.split(".")
        node = patch
        for part in parts[:-1]:
            node = node.setdefault(part, {})
        node[parts[-1]] = value
    return patch


async def _started_complete(service, **overrides):
    created = await service.start()
    qid, sid = created["quoteId"], created["sessionId"]
    state = await service.update(qid, sid, _complete_patch(**overrides))
    assert state["journeyState"] == "ready_to_price"
    return qid, sid


async def test_price_clean_profile_returns_quote_object():
    service = FakeQuoteService()
    qid, sid = await _started_complete(service)

    pricing = await service.price(qid, sid)

    assert pricing["outcome"] == "quote"
    assert pricing["currency"] == "GBP"
    assert pricing["iptIncluded"] is True
    assert pricing["annualPremium"] == 430.0
    assert pricing["monthly"]["instalments"] == 10
    assert pricing["totalExcess"] == 350 + 250
    assert pricing["compulsoryExcess"] == 350
    assert pricing["voluntaryExcess"] == 250
    assert pricing["ncdYears"] == 5
    assert isinstance(pricing["breakdown"], list) and pricing["breakdown"]
    assert pricing["reasons"] == []


async def test_price_incomplete_returns_not_ready_with_missing_fields():
    service = FakeQuoteService()
    created = await service.start()
    result = await service.price(created["quoteId"], created["sessionId"])
    assert result["error"] == "not_ready_to_price"
    assert result["missingFields"]


async def test_price_high_value_vehicle_refers_with_reasons():
    service = FakeQuoteService()
    qid, sid = await _started_complete(service, **{"vehicle.value": 90000})
    pricing = await service.price(qid, sid)
    assert pricing["outcome"] == "refer"
    assert pricing["reasons"]


async def test_price_under_18_declines_with_reasons():
    service = FakeQuoteService()
    qid, sid = await _started_complete(
        service, **{"customer.dateOfBirth": "2012-01-01"}
    )
    pricing = await service.price(qid, sid)
    assert pricing["outcome"] == "decline"
    assert pricing["reasons"]


async def test_price_unknown_session_returns_not_found():
    service = FakeQuoteService()
    qid, _ = await _started_complete(service)
    result = await service.price(qid, "wrong-session")
    assert result["error"] == "not_found"


async def test_purchase_link_on_clean_quote_returns_url():
    service = FakeQuoteService()
    qid, sid = await _started_complete(service)
    await service.price(qid, sid)
    result = await service.generate_purchase_link(qid, sid)
    assert result["purchaseToken"]
    assert result["purchaseUrl"].endswith(result["purchaseToken"])


async def test_purchase_link_on_refer_is_not_purchasable():
    service = FakeQuoteService()
    qid, sid = await _started_complete(service, **{"vehicle.value": 90000})
    await service.price(qid, sid)
    result = await service.generate_purchase_link(qid, sid)
    assert result["error"] == "not_purchasable"


async def test_purchase_link_before_pricing_is_not_purchasable():
    service = FakeQuoteService()
    qid, sid = await _started_complete(service)
    result = await service.generate_purchase_link(qid, sid)
    assert result["error"] == "not_purchasable"


async def test_issue_policy_on_clean_quote_returns_policy_number():
    service = FakeQuoteService()
    qid, sid = await _started_complete(service)
    await service.price(qid, sid)
    result = await service.issue_policy(qid, sid)
    assert result["policyNumber"] == "ACME-POL-TEST"
    assert result["status"] == "ISSUED"
    assert result["effectiveDate"] == "2026-07-01"


async def test_issue_policy_on_decline_is_not_issuable():
    service = FakeQuoteService()
    qid, sid = await _started_complete(
        service, **{"customer.dateOfBirth": "2012-01-01"}
    )
    await service.price(qid, sid)
    result = await service.issue_policy(qid, sid)
    assert result["error"] == "not_issuable"


async def test_issue_policy_unknown_session_not_found():
    service = FakeQuoteService()
    qid, sid = await _started_complete(service)
    await service.price(qid, sid)
    result = await service.issue_policy(qid, "wrong")
    assert result["error"] == "not_found"
