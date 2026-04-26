"""FastAPI application assembly and lifecycle management."""

import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from backend.api.router import api_router
from backend.repositories.watchlist_repository import init_watchlist_db
from backend.services.stock_service import load_watchlist_into_store, refresh_loop


@asynccontextmanager
async def lifespan(_: FastAPI):
    """Initialize persistent state and run the background refresh task."""
    await asyncio.to_thread(init_watchlist_db)
    await load_watchlist_into_store()
    task = asyncio.create_task(refresh_loop())
    yield
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        pass


def create_app() -> FastAPI:
    """Create the FastAPI app, middleware, and API route tree."""
    app = FastAPI(title="Stock K-Line API", lifespan=lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(api_router)
    return app


app = create_app()
