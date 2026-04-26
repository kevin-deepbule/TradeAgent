"""Watchlist HTTP routes for listing, adding, and removing stocks."""

import asyncio

from fastapi import APIRouter, HTTPException

from backend.repositories.watchlist_repository import (
    delete_watchlist_sync,
    list_watchlist_sync,
    upsert_watchlist_sync,
)
from backend.services.stock_service import resolve_stock
from backend.services.stock_store import store
from backend.utils import normalize_symbol


router = APIRouter()


@router.get("/api/watchlist")
async def list_watchlist() -> list[dict[str, str]]:
    """Return all persisted watchlist entries in display order."""
    return await asyncio.to_thread(list_watchlist_sync)


@router.post("/api/watchlist")
async def add_watchlist(item: dict[str, str]) -> dict[str, str]:
    """Resolve a query and persist the stock into the watchlist."""
    query = (item.get("query") or item.get("symbol") or "").strip()
    try:
        resolved = await resolve_stock(query)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc

    saved = await asyncio.to_thread(
        upsert_watchlist_sync,
        resolved["symbol"],
        resolved["name"],
    )
    await store.add_symbol(saved["symbol"], saved["name"])
    return saved


@router.delete("/api/watchlist/{symbol}")
async def delete_watchlist(symbol: str) -> dict[str, bool]:
    """Remove a stock from both the persisted watchlist and live cache."""
    normalized = normalize_symbol(symbol)
    await asyncio.to_thread(delete_watchlist_sync, normalized)
    await store.remove_symbol(normalized)
    return {"ok": True}
