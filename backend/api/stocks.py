"""Stock data HTTP and WebSocket routes."""

import asyncio
from datetime import datetime
from typing import Any

from fastapi import APIRouter, HTTPException, WebSocket, WebSocketDisconnect

from backend.config import REFRESH_SECONDS
from backend.services.stock_service import refresh_symbol, resolve_stock
from backend.services.stock_store import store


router = APIRouter()


@router.get("/api/stocks/{query}/kline")
async def get_kline(query: str) -> dict[str, Any]:
    """Resolve a query and return the latest cached or freshly fetched K-line data."""
    try:
        resolved = await resolve_stock(query)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc

    symbol = resolved["symbol"]
    await store.add_symbol(symbol, resolved["name"])
    cached = await store.get(symbol)
    if cached is None:
        cached = await refresh_symbol(symbol)
    else:
        cached = {**cached, "name": resolved["name"] or cached.get("name", "")}
    return cached


@router.websocket("/ws/stocks/{query}")
async def stock_ws(websocket: WebSocket, query: str) -> None:
    """Push refreshed K-line payloads for a resolved stock over WebSocket."""
    await websocket.accept()
    try:
        resolved = await resolve_stock(query)
    except ValueError as exc:
        await websocket.send_json(
            {
                "symbol": "",
                "name": "",
                "updatedAt": datetime.now().isoformat(timespec="seconds"),
                "source": None,
                "rows": [],
                "advice": None,
                "error": str(exc),
            }
        )
        await websocket.close()
        return

    symbol = resolved["symbol"]
    await store.add_symbol(symbol, resolved["name"])
    await refresh_symbol(symbol)

    try:
        while True:
            payload = await store.get(symbol)
            if payload is None:
                payload = await refresh_symbol(symbol)
            else:
                payload = {
                    **payload,
                    "name": resolved["name"] or payload.get("name", ""),
                }
            await websocket.send_json(payload)
            await asyncio.sleep(REFRESH_SECONDS)
    except WebSocketDisconnect:
        return
