# MOCK DATA — synthetic/public only, NOT ACME systems or data.
"""Synthetic vehicle registration lookup for the POC."""

from app.quoting.models import VehicleInput

_SEED = {
    "AB12CDE": ("Volkswagen", "Golf", 2019, 14000.0, 20),
    "LR68XYZ": ("Land Rover", "Discovery", 2018, 32000.0, 40),
    "FT19ABC": ("Ford", "Fiesta", 2020, 11000.0, 10),
    "TS21EVS": ("Tesla", "Model 3", 2021, 38000.0, 48),
}


def _normalise(reg: str) -> str:
    return reg.strip().upper().replace(" ", "")


def lookup_vehicle(registration: str) -> VehicleInput | None:
    """Return a VehicleInput for a registration, or a deterministic fallback."""
    reg = _normalise(registration)
    if reg in _SEED:
        make, model, year, value, group = _SEED[reg]
        return VehicleInput(
            registration=reg, make=make, model=model,
            year=year, value=value, insurance_group=group,
        )
    # Deterministic synthetic fallback derived from the plate characters.
    seed = sum(ord(c) for c in reg) if reg else 100
    group = (seed % 45) + 1
    return VehicleInput(
        registration=reg,
        make="Generic",
        model="Hatchback",
        year=2015 + (seed % 10),
        value=float(8000 + (seed % 20) * 1000),
        insurance_group=group,
    )
