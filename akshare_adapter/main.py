"""FastAPI routes for the internal AkShare adapter service."""

import asyncio
from typing import Any

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from akshare_adapter.financial_adapter import (
    fetch_disclosures_sync,
    fetch_financial_history_sync,
    fetch_industries_sync,
    fetch_industry_constituents_sync,
    fetch_market_snapshot_sync,
    fetch_profit_forecasts_sync,
)
from akshare_adapter.stock_adapter import fetch_kline_sync, resolve_stock_sync


class SymbolSelection(BaseModel):
    """Stock symbols requested for one current-market snapshot."""

    symbols: list[str]


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

    @app.get("/internal/research/industries")
    async def get_research_industries(level: int = 3) -> dict[str, Any]:
        """Return selectable Shenwan industries for financial research."""
        try:
            return await asyncio.to_thread(fetch_industries_sync, level)
        except Exception as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc

    @app.get("/internal/research/industries/{industry_code}/constituents")
    async def get_industry_constituents(industry_code: str) -> dict[str, Any]:
        """Return current constituents for one Shenwan level-three industry."""
        try:
            return await asyncio.to_thread(
                fetch_industry_constituents_sync, industry_code
            )
        except Exception as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc

    @app.get("/internal/research/disclosures")
    async def get_disclosures(report_period: str) -> dict[str, Any]:
        """Return report, quick-report, and forecast rows for one report period."""
        try:
            return await asyncio.to_thread(fetch_disclosures_sync, report_period)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        except Exception as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc

    @app.get("/internal/research/stocks/{symbol}/financials")
    async def get_financial_history(
        symbol: str,
        report_period: str,
        as_of: str | None = None,
    ) -> dict[str, Any]:
        """Return normalized single-quarter financial history for one stock."""
        try:
            return await asyncio.to_thread(
                fetch_financial_history_sync, symbol, report_period, as_of
            )
        except ValueError as exc:
            raise HTTPException(status_code=400, detail=str(exc)) from exc
        except Exception as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc

    @app.post("/internal/research/market-snapshot")
    async def get_market_snapshot(selection: SymbolSelection) -> dict[str, Any]:
        """Return current market values for a bounded set of stock symbols."""
        try:
            return await asyncio.to_thread(
                fetch_market_snapshot_sync, selection.symbols
            )
        except Exception as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc

    @app.get("/internal/research/profit-forecasts")
    async def get_profit_forecasts() -> dict[str, Any]:
        """Return current analyst EPS forecast coverage for A-share stocks."""
        try:
            return await asyncio.to_thread(fetch_profit_forecasts_sync)
        except Exception as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc

    return app


app = create_app()
