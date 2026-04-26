"""Central configuration for paths, ports, and stock refresh settings."""

import os
from pathlib import Path


BACKEND_DIR = Path(__file__).resolve().parent

BACKEND_HOST = os.getenv("BACKEND_HOST", "0.0.0.0")
BACKEND_PORT = int(os.getenv("BACKEND_PORT", "8001"))
BACKEND_RELOAD = os.getenv("BACKEND_RELOAD", "").lower() in {"1", "true", "yes"}

DEFAULT_SYMBOL = os.getenv("DEFAULT_STOCK_SYMBOL", "000001")
REFRESH_SECONDS = int(os.getenv("STOCK_REFRESH_SECONDS", "5"))

WATCHLIST_DB_PATH = os.getenv("WATCHLIST_DB_PATH")
DB_PATH = Path(WATCHLIST_DB_PATH) if WATCHLIST_DB_PATH else BACKEND_DIR / "data" / "watchlist.db"
