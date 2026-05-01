"""SQLite repository functions for durable dashboard settings."""

from datetime import datetime

from backend.config import DEFAULT_SYMBOL
from backend.database import db_connect
from backend.utils import normalize_symbol


DEFAULT_STOCK_SYMBOL_KEY = "default_stock_symbol"
DEFAULT_STOCK_NAME_KEY = "default_stock_name"


def init_settings_db() -> None:
    """Create the settings table and seed the configured default stock."""
    now = datetime.now().isoformat(timespec="seconds")
    with db_connect() as connection:
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS app_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL DEFAULT '',
                updated_at TEXT NOT NULL
            )
            """
        )
        connection.execute(
            """
            INSERT OR IGNORE INTO app_settings (key, value, updated_at)
            VALUES (?, ?, ?)
            """,
            (DEFAULT_STOCK_SYMBOL_KEY, normalize_symbol(DEFAULT_SYMBOL), now),
        )
        connection.execute(
            """
            INSERT OR IGNORE INTO app_settings (key, value, updated_at)
            VALUES (?, ?, ?)
            """,
            (DEFAULT_STOCK_NAME_KEY, "", now),
        )


def get_default_stock_sync() -> dict[str, str]:
    """Read the persisted default stock from settings storage."""
    with db_connect() as connection:
        rows = connection.execute(
            """
            SELECT key, value
            FROM app_settings
            WHERE key IN (?, ?)
            """,
            (DEFAULT_STOCK_SYMBOL_KEY, DEFAULT_STOCK_NAME_KEY),
        ).fetchall()
    values = {row["key"]: row["value"] for row in rows}
    symbol = values.get(DEFAULT_STOCK_SYMBOL_KEY) or DEFAULT_SYMBOL
    return {
        "symbol": normalize_symbol(symbol),
        "name": values.get(DEFAULT_STOCK_NAME_KEY) or "",
    }


def set_default_stock_sync(symbol: str, name: str = "") -> dict[str, str]:
    """Persist a stock as the default dashboard symbol."""
    normalized = normalize_symbol(symbol)
    now = datetime.now().isoformat(timespec="seconds")
    with db_connect() as connection:
        connection.executemany(
            """
            INSERT INTO app_settings (key, value, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(key) DO UPDATE SET
                value = excluded.value,
                updated_at = excluded.updated_at
            """,
            [
                (DEFAULT_STOCK_SYMBOL_KEY, normalized, now),
                (DEFAULT_STOCK_NAME_KEY, name or "", now),
            ],
        )
    return {"symbol": normalized, "name": name or ""}
