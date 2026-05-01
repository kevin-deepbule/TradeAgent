# Project Instructions

This project uses AkShare for stock-market data.

Primary documentation:
- https://akshare.akfamily.xyz/data/stock/stock.html

When implementing stock data features:
- Prefer `akshare` Python APIs in `akshare_adapter/` over hand-written scraping.
- Check the AkShare documentation for exact function names and parameters before using an API.
- Keep data-fetching code small and explicit because AkShare upstream interfaces can change.
- Return or persist `pandas.DataFrame` values without changing column names unless the caller asks for normalized fields.
- For A-share historical quotes, start with `ak.stock_zh_a_hist`.
- For A-share realtime spot quotes, start with `ak.stock_zh_a_spot_em`.

# Stock Analyst Agent Guide

## Project Overview

This project is a Java + Vue A-share K-line dashboard with a small Python AkShare adapter.

- Backend: Spring Boot + SQLite
- Adapter: FastAPI + AkShare + pandas
- Frontend: Vue 3 + Vite + ECharts
- Data source: AkShare
- Database: `backend/data/watchlist.db`

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

Backend defaults:

- Host: `0.0.0.0`
- Port: `8001`
- Health: `http://localhost:8001/api/health`
- AkShare adapter base: `http://localhost:8002`

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

## Backend Structure

- `backend/pom.xml`: Maven build for the Spring Boot backend.
- `backend/src/main/java/com/tradeagent/TradeAgentApplication.java`: backend entrypoint.
- `backend/src/main/java/com/tradeagent/config/`: backend configuration, CORS, datasource, HTTP client.
- `backend/src/main/java/com/tradeagent/controller/`: REST API controllers.
- `backend/src/main/java/com/tradeagent/websocket/`: stock WebSocket endpoint.
- `backend/src/main/java/com/tradeagent/repository/`: SQLite persistence layer.
- `backend/src/main/java/com/tradeagent/service/`: stock workflow, cache, startup initialization, trade advice.
- `backend/src/main/java/com/tradeagent/client/`: local AkShare adapter client.
- `backend/src/main/java/com/tradeagent/dto/`: API payload DTOs.
- `backend/src/main/resources/application.properties`: environment-derived runtime defaults.

## AkShare Adapter Structure

- `akshare_adapter/server.py`: uvicorn entrypoint for the internal adapter.
- `akshare_adapter/main.py`: FastAPI app and internal routes.
- `akshare_adapter/stock_adapter.py`: AkShare calls, symbol resolution, K-line shaping.
- `akshare_adapter/config.py`: adapter host, port, and K-line window settings.
- `akshare_adapter/utils.py`: stock-symbol, text, market, date, and NaN helpers.

## Frontend Structure

- `frontend/src/App.vue`: Dashboard shell that wires composables and presentational panels.
- `frontend/src/main.js`: Vue app bootstrap and global error reporting.
- `frontend/src/components/`: Presentational Vue panels.
- `frontend/src/composables/`: Vue state/lifecycle modules.
- `frontend/src/services/`: API wrappers and pure-domain services.
- `frontend/src/charts/klineChartOption.js`: ECharts option builder.
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

## Current API Surface

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

The strategy backtest is frontend-only and uses the K-line `rows` returned by the backend.
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
