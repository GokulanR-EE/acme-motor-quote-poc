"""Conflict detection & resolution (brief §4.6, §17.2, §17.3, §17.4).

A newly-extracted patch is reconciled against the current quote on a **single
shared code path** so it applies uniformly to typed answers and document uploads:

* ``reconcile(current, patch) -> (applicable, conflicts)``
    - Non-conflicting new values (incl. values *loosely equal* to what is held)
      go into ``applicable`` (an equal held value is a no-op — left untouched).
    - A genuine clash with a held, non-empty value becomes a ``conflicts`` entry
      ``{path, current, proposed}`` — queued, NOT applied.
    - Works over **deep dot-paths** into the nested model with **deep-merge**
      semantics; null / empty leaves are dropped (never blank a sibling, §17.4).

* ``resolve_conflict(path, chosen_value) -> value | KEEP_CURRENT``
    - If the reply cannot be parsed as a valid value for the field, returns the
      ``KEEP_CURRENT`` sentinel — **never invents** ``0`` / ``""`` (§17.2:
      ``Number("")`` → 0 → a nonsensical £0).

Loose equality (§17.3): case-insensitive and across string/number forms, so
``"RG1 1AA"`` vs ``"rg1 1aa"`` and ``12000`` vs ``"12000"`` are NOT conflicts.
"""

from __future__ import annotations

from typing import Any

# Sentinel returned when a resolution reply can't be parsed (brief §17.2). The
# caller keeps the current value rather than writing 0 / "".
KEEP_CURRENT = object()


def _loose_key(value: Any) -> Any:
    """Normalise a leaf for loose comparison (brief §17.3).

    - Numbers and numeric strings collapse to the same number, so ``12000`` and
      ``"12000"`` match.
    - Other strings are trimmed, lower-cased and stripped of internal spaces, so
      ``"RG1 1AA"`` and ``"rg1 1aa"`` match.
    - Bools/None pass through (``True`` != ``1`` deliberately: a boolean answer
      and a number are different kinds of fact).
    """
    if isinstance(value, bool) or value is None:
        return value
    if isinstance(value, (int, float)):
        return float(value)
    if isinstance(value, str):
        stripped = value.strip()
        try:
            return float(stripped.replace(",", ""))
        except ValueError:
            return stripped.lower().replace(" ", "")
    return value


def loose_equal(a: Any, b: Any) -> bool:
    return _loose_key(a) == _loose_key(b)


def _is_empty_leaf(value: Any) -> bool:
    if value is None:
        return True
    if isinstance(value, str) and value.strip() == "":
        return True
    return False


def _flatten(patch: dict, prefix: str = "") -> list[tuple[str, Any]]:
    """Flatten a nested patch to (dot-path, leaf) pairs. Lists are leaves."""
    out: list[tuple[str, Any]] = []
    for key, value in patch.items():
        path = f"{prefix}.{key}" if prefix else key
        if isinstance(value, dict):
            out.extend(_flatten(value, path))
        else:
            out.append((path, value))
    return out


def _resolve(data: dict, path: str) -> Any:
    current: Any = data
    for part in path.split("."):
        if not isinstance(current, dict) or part not in current:
            return None
        current = current[part]
    return current


def _set_path(patch: dict, path: str, value: Any) -> None:
    parts = path.split(".")
    node = patch
    for part in parts[:-1]:
        node = node.setdefault(part, {})
    node[parts[-1]] = value


def reconcile(current: dict, patch: dict) -> tuple[dict, list[dict]]:
    """Split ``patch`` against ``current`` into (applicable, conflicts).

    ``applicable`` is a nested patch safe to apply (deep-merge); ``conflicts`` is
    a list of ``{path, current, proposed}`` for the customer to resolve.
    """
    current = current or {}
    applicable: dict = {}
    conflicts: list[dict] = []

    for path, proposed in _flatten(patch or {}):
        if _is_empty_leaf(proposed):
            # Drop null/empty leaves — never blank a sibling (brief §17.4).
            continue
        held = _resolve(current, path)
        if _is_empty_leaf(held):
            # Nothing held — apply.
            _set_path(applicable, path, proposed)
        elif loose_equal(held, proposed):
            # Loosely equal — no-op, leave the held value untouched (§17.3).
            continue
        else:
            # Genuine clash — queue, do not apply (§4.6).
            conflicts.append({"path": path, "current": held, "proposed": proposed})

    return applicable, conflicts


# --- Per-field resolution casters. A resolution reply (the customer picking a
# chip or typing afresh) is cast to the field's type; if it can't be parsed we
# return KEEP_CURRENT and never invent a value (brief §17.2).
def _cast_int(raw: Any):
    return int(round(float(str(raw).replace(",", "").strip())))


def _cast_float(raw: Any):
    return float(str(raw).replace(",", "").replace("£", "").strip())


def _cast_bool(raw: Any):
    if isinstance(raw, bool):
        return raw
    t = str(raw).strip().lower()
    if t in ("yes", "y", "true", "1"):
        return True
    if t in ("no", "n", "false", "0"):
        return False
    raise ValueError(raw)


def _cast_str(raw: Any):
    s = str(raw).strip()
    if s == "":
        raise ValueError("empty")
    return s


_RESOLVE_CASTER = {
    "vehicle.annualMileage": _cast_int,
    "vehicle.value": _cast_float,
    "vehicle.dashcam": _cast_bool,
    "vehicle.modified": _cast_bool,
    "vehicle.registeredKeeper": _cast_bool,
    "vehicle.legalOwner": _cast_bool,
    "customer.partTimeJob": _cast_bool,
    "customer.ownsProperty": _cast_bool,
    "customer.carKeptOvernightAtAddress": _cast_bool,
    "customer.address.postcode": lambda r: _cast_str(r).upper(),
    "driver.insuranceCancelledOrVoid": _cast_bool,
    "driver.ncdYears": _cast_int,
    "driver.ncdOnCompanyCar": _cast_bool,
    "history.claimsLast3Years": _cast_int,
    "history.offencesLast5Years": _cast_int,
    "history.unspentCriminalConvictions": _cast_bool,
    "household.anotherCarHasCover": _cast_bool,
    "cover.voluntaryExcess": _cast_float,
}


def resolve_conflict(path: str, chosen_value: Any):
    """Cast ``chosen_value`` to the field's type, or return KEEP_CURRENT.

    Never falls through to ``0`` / ``""`` for an unparseable reply (brief §17.2);
    the caller keeps the current value in that case.
    """
    caster = _RESOLVE_CASTER.get(path, _cast_str)
    try:
        value = caster(chosen_value)
    except (ValueError, TypeError):
        return KEEP_CURRENT
    if value is None:
        return KEEP_CURRENT
    return value
