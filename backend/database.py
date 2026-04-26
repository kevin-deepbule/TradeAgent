"""SQLite connection helpers used by repository modules."""

import sqlite3

from backend.config import DB_PATH


def db_connect() -> sqlite3.Connection:
    """Open a row-aware SQLite connection and create the data directory."""
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    return connection
