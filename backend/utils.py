"""Small shared helpers for stock symbols, text matching, and values."""

import unicodedata
from math import isnan
from typing import Any

import pandas as pd


def normalize_symbol(symbol: str) -> str:
    """Return the last six digits of a stock symbol with zero padding."""
    return "".join(ch for ch in symbol if ch.isdigit()).zfill(6)[-6:]


def is_symbol_query(query: str) -> bool:
    """Check whether a user query looks like a numeric stock code."""
    text = query.strip()
    return bool(text) and text.isdigit() and len(text) <= 6


def simplify_text(text: str) -> str:
    """Normalize stock names for case-insensitive and width-insensitive search."""
    return "".join(unicodedata.normalize("NFKC", text).upper().split())


def market_symbol(symbol: str) -> str:
    """Convert a six-digit A-share code into the market-prefixed format."""
    symbol = normalize_symbol(symbol)
    if symbol.startswith(("5", "6", "9")):
        return f"sh{symbol}"
    return f"sz{symbol}"


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
