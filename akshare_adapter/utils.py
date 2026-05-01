"""Small helpers for symbol normalization, text matching, and JSON values."""

import unicodedata
from datetime import date
from math import isnan
from typing import Any

import pandas as pd


def normalize_symbol(symbol: str) -> str:
    """Return the last six digits of a stock symbol with zero padding."""
    return "".join(ch for ch in symbol if ch.isdigit()).zfill(6)[-6:]


def is_symbol_query(query: str) -> bool:
    """Check whether a query looks like a numeric A-share stock code."""
    text = query.strip()
    return bool(text) and text.isdigit() and len(text) <= 6


def simplify_text(text: str) -> str:
    """Normalize stock names for width-insensitive and case-insensitive lookup."""
    return "".join(unicodedata.normalize("NFKC", text).upper().split())


def market_symbol(symbol: str) -> str:
    """Convert a six-digit A-share code into the market-prefixed format."""
    normalized = normalize_symbol(symbol)
    if normalized.startswith(("5", "6", "9")):
        return f"sh{normalized}"
    return f"sz{normalized}"


def none_if_nan(value: Any) -> float | int | str | None:
    """Convert pandas/float NaN values to None and round floats for JSON."""
    if value is None:
        return None
    if isinstance(value, float) and isnan(value):
        return None
    if pd.isna(value):
        return None
    if isinstance(value, float):
        return round(value, 4)
    return value


def years_ago(value: date, years: int) -> date:
    """Return the same calendar day a number of years earlier."""
    try:
        return value.replace(year=value.year - years)
    except ValueError:
        return value.replace(year=value.year - years, month=2, day=28)

