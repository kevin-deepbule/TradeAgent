"""Aggregate resource routers into one API router for the app."""

from fastapi import APIRouter

from backend.api import health, stocks, watchlist


api_router = APIRouter()
api_router.include_router(health.router)
api_router.include_router(watchlist.router)
api_router.include_router(stocks.router)
