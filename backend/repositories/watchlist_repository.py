"""SQLite repository functions for the stock watchlist."""

from datetime import datetime

from backend.config import DEFAULT_SYMBOL
from backend.database import db_connect
from backend.utils import normalize_symbol


def init_watchlist_db() -> None:
    """Create the watchlist table and seed the default stock if missing."""
    with db_connect() as connection:
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS watchlist (
                symbol TEXT PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL
            )
            """
        )
        connection.execute(
            """
            INSERT OR IGNORE INTO watchlist (symbol, name, created_at)
            VALUES (?, ?, ?)
            """,
            (DEFAULT_SYMBOL, "", datetime.now().isoformat(timespec="seconds")),
        )


def list_watchlist_sync() -> list[dict[str, str]]:
    """Fetch all watchlist entries from SQLite in stable display order."""
    with db_connect() as connection:
        rows = connection.execute(
            """
            SELECT symbol, name, created_at
            FROM watchlist
            ORDER BY created_at ASC, symbol ASC
            """
        ).fetchall()
    return [dict(row) for row in rows]


def upsert_watchlist_sync(symbol: str, name: str) -> dict[str, str]:
    """Insert or update a watchlist entry and return the saved row shape."""
    symbol = normalize_symbol(symbol)
    now = datetime.now().isoformat(timespec="seconds")
    with db_connect() as connection:
        connection.execute(
            """
            INSERT INTO watchlist (symbol, name, created_at)
            VALUES (?, ?, ?)
            ON CONFLICT(symbol) DO UPDATE SET name = excluded.name
            """,
            (symbol, name or "", now),
        )
    return {"symbol": symbol, "name": name or "", "created_at": now}


def delete_watchlist_sync(symbol: str) -> None:
    """Delete a normalized stock symbol from the watchlist table."""
    with db_connect() as connection:
        connection.execute(
            "DELETE FROM watchlist WHERE symbol = ?",
            (normalize_symbol(symbol),),
        )
