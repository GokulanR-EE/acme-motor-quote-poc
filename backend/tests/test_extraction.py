"""Greedy, question-anchored extraction (brief §4.1, §4.2, §17.1)."""

import os

import pytest

from app.extraction import extract_patch


@pytest.fixture(autouse=True)
def _mock_mode(monkeypatch):
    monkeypatch.setenv("MOCK_LLM", "1")


def test_anchored_bare_answer_maps_to_asked_field():
    # "8000" replying to the annual-mileage question must land on annualMileage,
    # NOT on vehicle.value (the §17.1 gotcha).
    patch = extract_patch("8000", asked_question="vehicle.annualMileage")
    assert patch == {"vehicle": {"annualMileage": 8000}}


def test_anchored_bare_answer_not_misread_as_value():
    patch = extract_patch("8000", asked_question="vehicle.value")
    # With the value anchor it is a value; the point is the anchor decides.
    assert patch == {"vehicle": {"value": 8000.0}}


def test_greedy_multi_fact_sentence_fills_several_fields():
    msg = "I'm Mr Sam Sample, born 1990-01-01, reg FX19 ZTC worth 12k, 8000 miles, 5 yrs NCD"
    patch = extract_patch(msg, asked_question=None)
    assert patch["customer"]["title"] == "Mr"
    assert patch["customer"]["firstName"] == "Sam"
    assert patch["customer"]["surname"] == "Sample"
    assert patch["customer"]["dateOfBirth"] == "1990-01-01"
    assert patch["vehicle"]["registration"] == "FX19ZTC"
    assert patch["vehicle"]["value"] == 12000.0
    assert patch["vehicle"]["annualMileage"] == 8000
    assert patch["driver"]["ncdYears"] == 5


def test_unrelated_facts_omitted():
    patch = extract_patch("the weather is nice today", asked_question=None)
    assert patch == {}


def test_bare_reply_with_anchor_still_extracts_volunteered_extra():
    # A short reply that also volunteers a postcode: anchor + greedy.
    patch = extract_patch("RG1 1AA", asked_question="customer.address.postcode")
    assert patch["customer"]["address"]["postcode"] == "RG1 1AA"
