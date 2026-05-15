-- Initializes PostgreSQL storage used by the TradeAgent backend.
\set ON_ERROR_STOP on

CREATE TABLE IF NOT EXISTS watchlist (
    symbol TEXT PRIMARY KEY,
    name TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS app_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL DEFAULT '',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE watchlist
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at::timestamptz,
    ALTER COLUMN created_at SET DEFAULT now();

ALTER TABLE app_settings
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at::timestamptz,
    ALTER COLUMN updated_at SET DEFAULT now();
