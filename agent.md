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
python3 -m backend.server
```

Backend defaults:

- Host: `0.0.0.0`
- Port: `8001`
- Health: `http://localhost:8001/api/health`

Enable backend reload:

```bash
BACKEND_RELOAD=1 python3 -m backend.server
```

Frontend:

```bash
cd frontend
npm run dev
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

- `frontend/src/App.vue`: Main dashboard UI and WebSocket/API state handling.
- `frontend/src/style.css`: App styling.
- `frontend/src/main.js`: Vue app bootstrap.
- `frontend/vite.config.js`: Vite server/preview fixed port config.

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
