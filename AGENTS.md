# Project Instructions

This project uses AkShare for stock-market data.

Primary documentation:
- https://akshare.akfamily.xyz/data/stock/stock.html

When implementing stock data features:
- Prefer `akshare` Python APIs over hand-written scraping.
- Check the AkShare documentation for the exact function name and parameters before using an API.
- Keep data-fetching code small and explicit, because AkShare upstream interfaces can change.
- Return or persist `pandas.DataFrame` values without changing column names unless the caller asks for normalized fields.
- For A-share historical quotes, start with `ak.stock_zh_a_hist`.
- For A-share realtime spot quotes, start with `ak.stock_zh_a_spot_em`.

# Stock Analyst Agent Guide

## Project Overview

This project is a Python + Vue A-share K-line dashboard.

- Backend: FastAPI + AkShare + SQLite
- Frontend: Vue 3 + Vite + ECharts
- Data source: AkShare
- Database: `backend/data/watchlist.db`

Primary AkShare stock documentation:

- https://akshare.akfamily.xyz/data/stock/stock.html

## Run Commands

Backend:

```bash
mkdir -p .logs
python3 -m backend.server 2>&1 | tee .logs/backend.log
```

Backend defaults:

- Host: `0.0.0.0`
- Port: `8001`
- Health: `http://localhost:8001/api/health`

Enable backend reload:

```bash
mkdir -p .logs
BACKEND_RELOAD=1 python3 -m backend.server 2>&1 | tee .logs/backend.log
```

Frontend:

```bash
mkdir -p .logs
npm --prefix frontend run dev 2>&1 | tee .logs/frontend.log
```

Frontend defaults:

- Dev server: `http://localhost:5173`
- API base: `http://localhost:8001`
- WebSocket base: `ws://localhost:8001`

## Verification

Backend compile check:

```bash
python3 -m py_compile $(find backend -name '*.py' -print)
```

Frontend build check:

```bash
npm --prefix frontend run build
```

API smoke checks:

```bash
curl http://localhost:8001/api/health
curl http://localhost:8001/api/watchlist
curl http://localhost:8001/api/stocks/000001/kline
```

## Backend Structure

- `backend/main.py`: FastAPI app factory, CORS, lifespan, router mounting.
- `backend/server.py`: Project-level uvicorn entrypoint with fixed port config.
- `backend/config.py`: Environment variables, paths, host, port, refresh interval.
- `backend/database.py`: SQLite connection setup.
- `backend/api/`: FastAPI routers split by resource.
- `backend/repositories/`: Persistence layer, currently watchlist SQLite operations.
- `backend/services/`: Business logic, AkShare fetching, cache, trade advice.
- `backend/utils.py`: Shared stock-symbol, text, market, and NaN helpers.

## Frontend Structure

- `frontend/src/App.vue`: Dashboard shell that wires composables and presentational panels.
- `frontend/src/main.js`: Vue app bootstrap and global error reporting.
- `frontend/src/components/`: Presentational Vue panels.
  - `AppHeader.vue`: Query form, add-watchlist button, and copy-mode toggle.
  - `WatchlistPanel.vue`: Persisted watchlist display and item actions.
  - `SummaryCards.vue`: Current stock, close, change, update time, and status.
  - `KlineChartPanel.vue`: ECharts container plus copy-range interaction overlay.
  - `BacktestPanel.vue`: Strategy selection, metrics, and executed signal list.
  - `AdvicePanel.vue`: Backend-generated trade advice display.
- `frontend/src/composables/`: Vue state/lifecycle modules.
  - `useStockDashboard.js`: Query state, WebSocket data flow, watchlist API calls, and copy-to-clipboard workflow.
  - `useBacktest.js`: Selected strategy state and automatic result refresh.
  - `useKlineChart.js`: ECharts instance lifecycle and redraw scheduling.
- `frontend/src/services/`: Side-effect and pure-domain services.
  - `stockApi.js`: Thin wrappers around `/api/watchlist` and `/api/stocks/{query}/kline`.
  - `backtest.js`: Pure strategy rules, next-open execution, limit-up/down handling, return and drawdown calculation.
- `frontend/src/charts/klineChartOption.js`: ECharts option builder for candlestick, MA lines, volume, buy/sell markers, and holding bands.
- `frontend/src/utils/formatters.js`: Numeric, percent, and clipboard export formatting helpers.
- `frontend/src/config.js`: Vite environment-derived API/WebSocket bases.
- `frontend/src/style.css`: Global dashboard styling.
- `frontend/vite.config.js`: Vite server/preview fixed port config.

## Frontend Refactor Notes

- Keep `App.vue` thin. Add new dashboard behavior through `components/`, `composables/`, `services/`, or `charts/` according to responsibility.
- Keep API calls inside `frontend/src/services/stockApi.js`; keep pure strategy math inside `frontend/src/services/backtest.js`.
- Keep ECharts option shape in `frontend/src/charts/klineChartOption.js`; components should not inline chart option objects.
- Keep display-only UI in `frontend/src/components/`; components should receive data through props and emit user intents.
- The global stylesheet currently owns dashboard layout and panel styling. Reuse existing class names before adding new visual systems.

## Stock Data Rules

When implementing stock data features:

- Prefer `akshare` Python APIs over hand-written scraping.
- Check the AkShare documentation for exact function names and parameters before using an API.
- Keep data-fetching code small and explicit because AkShare upstream interfaces can change.
- Return or persist `pandas.DataFrame` values without changing column names unless the caller asks for normalized fields.
- For A-share historical quotes, start with `ak.stock_zh_a_hist`.
- For A-share realtime spot quotes, start with `ak.stock_zh_a_spot_em`.

## Current API Surface

- `GET /api/health`
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

The strategy backtest is currently frontend-only and uses the K-line `rows` already returned by the backend. It does not call a separate backtest API.
The backend returns the most recent 5 calendar years of daily K-line rows, so the backtest window follows that returned data range.

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
- Keep `uvicorn backend.main:app` compatibility through the exported `app`, even though `python3 -m backend.server` is the preferred command.
- The frontend Vite dev port uses `strictPort`; if `5173` is occupied, fix the process conflict instead of silently changing ports.
- The backend default port is `8001` because `8000` is unavailable in the current environment.
- Do not commit generated caches, `node_modules`, `dist`, `.venv`, or database files.

## Comment Requirements

- Every source file must include a brief file-level comment describing the file's responsibility.
- Every function must include a short comment or docstring explaining what the function does.
- Keep comments concise and useful; avoid restating obvious implementation details.
- Prefer comments that explain intent, boundaries, assumptions, or non-obvious behavior.
- When changing an existing file, add or update comments for touched functions if they are missing or stale.
