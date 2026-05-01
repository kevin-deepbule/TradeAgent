"""FastAPI routes for the internal AkShare adapter service."""

import asyncio
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from akshare_adapter.stock_adapter import fetch_kline_sync, resolve_stock_sync


def create_app() -> FastAPI:
    """Create the internal FastAPI app used by the Java backend."""
    app = FastAPI(title="AkShare Adapter API")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.get("/internal/health")
    async def health() -> dict[str, bool]:
        """Return a lightweight readiness payload for the adapter."""
        return {"ok": True}

    @app.get("/internal/stocks/{query}/resolve")
    async def resolve_stock(query: str) -> dict[str, str]:
        """Resolve a stock code or name through AkShare code/name data."""
        try:
            return await asyncio.to_thread(resolve_stock_sync, query)
        except ValueError as exc:
            raise HTTPException(status_code=404, detail=str(exc)) from exc

    @app.get("/internal/stocks/{symbol}/kline")
    async def get_kline(symbol: str, name: str = "") -> dict[str, Any]:
        """Return normalized K-line rows fetched from AkShare."""
        try:
            return await asyncio.to_thread(fetch_kline_sync, symbol, name)
        except Exception as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc

    return app


app = create_app()

