"""Dashboard settings HTTP routes."""

import asyncio

from fastapi import APIRouter, HTTPException

from backend.repositories.settings_repository import (
    get_default_stock_sync,
    set_default_stock_sync,
)
from backend.services.stock_service import resolve_stock
from backend.services.stock_store import store


router = APIRouter()


@router.get("/api/default-stock")
async def get_default_stock() -> dict[str, str]:
    """Return the stock configured as the dashboard default."""
    return await asyncio.to_thread(get_default_stock_sync)


@router.put("/api/default-stock")
async def set_default_stock(item: dict[str, str]) -> dict[str, str]:
    """Resolve and persist the requested stock as the dashboard default."""
    query = (item.get("query") or item.get("symbol") or "").strip()
    try:
        resolved = await resolve_stock(query)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc

    saved = await asyncio.to_thread(
        set_default_stock_sync,
        resolved["symbol"],
        resolved["name"],
    )
    await store.add_symbol(saved["symbol"], saved["name"])
    return saved
