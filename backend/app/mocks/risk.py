# MOCK DATA — synthetic/public only, NOT ACME systems or data.
"""Synthetic risk lookup tables for the POC."""

_BAND_FACTORS = {"low": 0.9, "medium": 1.0, "high": 1.25}

# Mock mapping of postcode area (leading letters) to a risk band.
_AREA_BAND = {
    "SW": "high", "E": "high", "M": "high", "B": "medium",
    "LS": "medium", "G": "medium", "EH": "low", "AB": "low", "CF": "low",
}


def group_base_rate(insurance_group: int) -> float:
    """Higher insurance group -> higher base annual rate."""
    return float(200 + insurance_group * 12)


def postcode_to_band(postcode: str) -> str:
    """Deterministically map a postcode to a risk band."""
    pc = postcode.strip().upper().replace(" ", "")
    for prefix in sorted(_AREA_BAND, key=len, reverse=True):
        if pc.startswith(prefix):
            return _AREA_BAND[prefix]
    # Deterministic fallback from the first character.
    return "medium" if (ord(pc[:1] or "M") % 2 == 0) else "low"


def band_factor(band: str) -> float:
    return _BAND_FACTORS[band]
