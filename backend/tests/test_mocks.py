from app.mocks.risk import band_factor, group_base_rate, postcode_to_band


def test_group_base_rate_increases_with_group():
    assert group_base_rate(1) < group_base_rate(50)
    assert group_base_rate(20) == 200 + 20 * 12


def test_postcode_to_band_is_deterministic():
    assert postcode_to_band("SW1A1AA") == postcode_to_band("sw1a 1aa")
    assert postcode_to_band("SW1A1AA") in {"low", "medium", "high"}


def test_band_factor_ordering():
    assert band_factor("low") < band_factor("medium") < band_factor("high")


from app.mocks.vehicles import lookup_vehicle


def test_seeded_registration_returns_known_car():
    v = lookup_vehicle("AB12CDE")
    assert v is not None
    assert v.make and v.model and 1 <= v.insurance_group <= 50


def test_unknown_registration_returns_deterministic_fallback():
    a = lookup_vehicle("ZZ99ZZZ")
    b = lookup_vehicle("zz99 zzz")
    assert a is not None and a.registration == "ZZ99ZZZ"
    assert a.insurance_group == b.insurance_group  # deterministic
