"""Conflict reconcile + resolution (brief §4.6, §17.2, §17.3, §17.4)."""

from app.conflict import KEEP_CURRENT, reconcile, resolve_conflict


def test_loose_equal_string_case_is_noop():
    current = {"customer": {"address": {"postcode": "RG1 1AA"}}}
    patch = {"customer": {"address": {"postcode": "rg1 1aa"}}}
    applicable, conflicts = reconcile(current, patch)
    assert applicable == {}
    assert conflicts == []


def test_loose_equal_number_vs_string_is_noop():
    current = {"vehicle": {"annualMileage": 12000}}
    patch = {"vehicle": {"annualMileage": "12000"}}
    applicable, conflicts = reconcile(current, patch)
    assert applicable == {}
    assert conflicts == []


def test_new_value_into_empty_is_applicable():
    current = {"customer": {"firstName": "Sam"}}
    patch = {"customer": {"surname": "Sample"}, "vehicle": {"annualMileage": 8000}}
    applicable, conflicts = reconcile(current, patch)
    assert applicable == {"customer": {"surname": "Sample"}, "vehicle": {"annualMileage": 8000}}
    assert conflicts == []


def test_genuine_clash_is_queued_not_applied():
    current = {"vehicle": {"annualMileage": 8000}}
    patch = {"vehicle": {"annualMileage": 18000}}
    applicable, conflicts = reconcile(current, patch)
    assert applicable == {}
    assert conflicts == [
        {"path": "vehicle.annualMileage", "current": 8000, "proposed": 18000}
    ]


def test_non_conflicting_applies_while_clash_queued():
    current = {"vehicle": {"annualMileage": 8000}}
    patch = {"vehicle": {"annualMileage": 18000, "value": 12000}}
    applicable, conflicts = reconcile(current, patch)
    assert applicable == {"vehicle": {"value": 12000}}
    assert [c["path"] for c in conflicts] == ["vehicle.annualMileage"]


def test_empty_leaf_dropped_never_blanks():
    current = {"vehicle": {"value": 12000}}
    patch = {"vehicle": {"value": None}, "customer": {"firstName": ""}}
    applicable, conflicts = reconcile(current, patch)
    assert applicable == {}
    assert conflicts == []


def test_resolve_with_valid_value_applies():
    assert resolve_conflict("vehicle.annualMileage", "18000") == 18000
    assert resolve_conflict("vehicle.value", "12000") == 12000.0


def test_resolve_unparseable_keeps_current_never_zero():
    # "that was miles, not value" — must NOT coerce to 0 / "" (brief §17.2).
    assert resolve_conflict("vehicle.value", "that was miles, not value") is KEEP_CURRENT
    assert resolve_conflict("vehicle.annualMileage", "lots") is KEEP_CURRENT
    assert resolve_conflict("customer.firstName", "") is KEEP_CURRENT
