"""In-memory stock cache shared by HTTP routes and background refresh."""

import asyncio
from typing import Any

from backend.config import DEFAULT_SYMBOL
from backend.utils import normalize_symbol


class StockStore:
    """Protect active symbols, names, and payloads behind an async lock."""

    def __init__(self) -> None:
        """Initialize cache state with the configured default symbol."""
        self._lock = asyncio.Lock()
        self.active_symbols: set[str] = {DEFAULT_SYMBOL}
        self.symbol_names: dict[str, str] = {}
        self.payloads: dict[str, dict[str, Any]] = {}

    async def add_symbol(self, symbol: str, name: str | None = None) -> None:
        """Track a symbol for refresh and optionally remember its display name."""
        symbol = normalize_symbol(symbol)
        async with self._lock:
            self.active_symbols.add(symbol)
            if name:
                self.symbol_names[symbol] = name

    async def remove_symbol(self, symbol: str) -> None:
        """Remove a symbol and any cached payload from live state."""
        symbol = normalize_symbol(symbol)
        async with self._lock:
            self.active_symbols.discard(symbol)
            self.symbol_names.pop(symbol, None)
            self.payloads.pop(symbol, None)

    async def symbols(self) -> list[str]:
        """Return the active symbols in deterministic order."""
        async with self._lock:
            return sorted(self.active_symbols)

    async def name(self, symbol: str) -> str | None:
        """Return the cached display name for a symbol when available."""
        async with self._lock:
            return self.symbol_names.get(normalize_symbol(symbol))

    async def save(self, symbol: str, payload: dict[str, Any]) -> None:
        """Store the latest fetched payload for a symbol."""
        async with self._lock:
            self.payloads[normalize_symbol(symbol)] = payload

    async def get(self, symbol: str) -> dict[str, Any] | None:
        """Read the cached payload for a symbol if one exists."""
        async with self._lock:
            return self.payloads.get(normalize_symbol(symbol))


store = StockStore()
