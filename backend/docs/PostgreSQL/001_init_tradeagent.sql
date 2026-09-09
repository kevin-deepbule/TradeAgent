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

CREATE TABLE IF NOT EXISTS research_valuation_run (
    id UUID PRIMARY KEY,
    status TEXT NOT NULL,
    progress INTEGER NOT NULL,
    message TEXT NOT NULL DEFAULT '',
    request_payload JSONB NOT NULL,
    result_payload JSONB NOT NULL DEFAULT '[]'::jsonb,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE watchlist
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at::timestamptz,
    ALTER COLUMN created_at SET DEFAULT now();

ALTER TABLE app_settings
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at::timestamptz,
    ALTER COLUMN updated_at SET DEFAULT now();
