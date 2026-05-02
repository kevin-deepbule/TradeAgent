# Project Instructions

This project uses AkShare for A-share stock-market data.

Primary documentation:
- https://akshare.akfamily.xyz/data/stock/stock.html

When implementing stock data features:
- Prefer `akshare` Python APIs in `akshare_adapter/` over hand-written scraping.
- Check the AkShare documentation for exact function names and parameters before using an API.
- Keep data-fetching code small and explicit because AkShare upstream interfaces can change.
- Return or persist `pandas.DataFrame` values without changing column names unless the caller asks for normalized fields.
- For A-share historical quotes, start with `ak.stock_zh_a_hist`.
- For A-share realtime spot quotes, start with `ak.stock_zh_a_spot_em`.

# TradeAgent Agent Guide

## Project Overview

This is a Java + Vue A-share K-line dashboard with a small Python AkShare
adapter.

- Frontend: Vue 3 + Vite + ECharts in `frontend/`
- Backend: Spring Boot + SQLite in `backend/`
- Adapter: FastAPI + AkShare + pandas in `akshare_adapter/`
- Data source: AkShare
- Database: `backend/data/watchlist.db`

## Directory Boundaries

- `frontend/` owns browser UI, ECharts configuration, API wrappers, and frontend-only strategy backtests.
- `backend/` owns public REST APIs, WebSocket APIs, persistence, caching, startup initialization, and trade advice.
- `akshare_adapter/` owns AkShare calls and internal data-source adaptation.
- Root-level `README.md` and `AGENTS.md` describe the whole workspace.
- Subdirectory `README.md` and `AGENTS.md` files describe local module rules.

Do not make the frontend call `akshare_adapter/` directly. Keep the adapter as
an internal backend dependency.

## Run Commands

AkShare adapter:

```bash
mkdir -p .logs
source .venv/bin/activate
python3 -m akshare_adapter.server 2>&1 | tee .logs/akshare-adapter.log
```

Backend:

```bash
mkdir -p .logs
mvn -f backend/pom.xml spring-boot:run 2>&1 | tee .logs/backend.log
```

Frontend:

```bash
mkdir -p .logs
npm --prefix frontend run dev 2>&1 | tee .logs/frontend.log
```

## Verification

Adapter compile check:

```bash
python3 -m py_compile $(find akshare_adapter -name '*.py' -print)
```

Backend compile check:

```bash
mvn -f backend/pom.xml test
```

Frontend build check:

```bash
npm --prefix frontend run build
```

API smoke checks:

```bash
curl --noproxy '*' http://localhost:8002/internal/health
curl --noproxy '*' http://localhost:8001/api/health
curl --noproxy '*' http://localhost:8001/api/watchlist
curl --noproxy '*' http://localhost:8001/api/stocks/000001/kline
```

## Current Public API Surface

- `GET /api/health`
- `GET /api/default-stock`
- `PUT /api/default-stock`
- `GET /api/watchlist`
- `POST /api/watchlist`
- `DELETE /api/watchlist/{symbol}`
- `GET /api/stocks/{query}/kline`
- `WS /ws/stocks/{query}`

The K-line response includes:

- `symbol`
- `name`
- `updatedAt`
- `source`
- `rows`
- `advice`
- `error`
- `warnings`

`advice` may be `null`. When present, it contains:

- `action`: `buy`, `sell`, or `hold`
- `actionText`: Chinese display text
- `score`: 0-100
- `reasons`
- `risks`
- `generatedAt`

## Strategy Backtest Rules

The strategy backtest is frontend-only and uses the K-line `rows` returned by
the backend. The backend returns the most recent 5 calendar years of daily
K-line rows, so the backtest window follows that returned data range.

Available strategies:

- `ma20-cross` / `20日线：站上买入，跌破卖出`: when not holding, close above MA20 signals buy; when holding, close below MA20 signals sell.
- `volume-drop` / `放量急跌买入，放量卖出`: when not holding, volume above 2x the previous 20-day average and close down at least 4% from the previous close signals buy; when holding, volume above 2x the previous 20-day average signals sell.
- `ma20-breakout` / `MA20趋势跟随：有效突破`: when not holding, close above `MA20 * 1.02`, no higher than `MA20 * 1.05`, and MA60 is at least 99.9% of the previous MA60 signal buy; when holding, close below `MA20 * 0.98` or overheat volume-price stall signals sell.
- `boll-break-buy` / `BOLL下轨买入，上轨卖出`: when not holding, close crossing below BOLL(20, 2) lower band signals buy; when holding, intraday high crossing above BOLL(20, 2) upper band, holding more than 30 trading days, or close falling more than 20% below the actual entry price signals sell.

Execution assumptions:

- Signals are generated from completed K-line rows after the signal day's close, even when a condition uses intraday high/low.
- All strategies must execute through the shared `calculateBacktest` engine in `frontend/src/services/backtest.js`; individual strategy helpers should only return `buy`, `sell`, or `null`.
- Actual buy/sell execution always happens at the next trading day's open price.
- Limit-up opens cannot be bought; blocked buy orders are skipped.
- Limit-down opens cannot be sold; blocked sell orders remain pending until the next tradable open.
- Chart buy/sell markers represent actual execution dates/prices.
- Yellow chart `markArea` bands represent actual holding periods.

## Development Notes

- Keep backend route paths stable because the frontend calls them directly.
- Keep `backend/data/watchlist.db` out of git; it is local runtime state.
- Keep AkShare access isolated in `akshare_adapter/`; Java should call the adapter instead of reimplementing AkShare scraping.
- The frontend Vite dev port uses `strictPort`; if `5173` is occupied, fix the process conflict instead of silently changing ports.
- The backend default port is `8001` because `8000` is unavailable in the current environment.
- Do not commit generated caches, `node_modules`, `dist`, `.venv`, Maven `target`, or database files.

## Comment Requirements

- Every source file must include a brief file-level or class-level comment describing the file's responsibility.
- Every function or public method must include a short comment or docstring explaining what it does.
- Keep comments concise and useful; avoid restating obvious implementation details.
- Prefer comments that explain intent, boundaries, assumptions, or non-obvious behavior.
- When changing an existing file, add or update comments for touched functions if they are missing or stale.
