# Stock Analyst

Python + Vue stock K-line dashboard powered by AkShare.

## Features

- FastAPI backend fetches A-share daily K-line data from AkShare every 5 seconds.
- Query by A-share stock code or stock name, such as `000001` or `平安银行`.
- Backend calculates MA5, MA20, and MA60 from closing prices.
- Vue frontend renders candlestick, moving-average lines, and volume with ECharts.
- WebSocket pushes the cached backend payload to the frontend every 5 seconds.
- One-click copy exports date, OHLC, volume, MA5, MA20, and MA60 as tab-separated text.
- Watchlist persistence is backed by SQLite at `backend/data/watchlist.db`.
- Strategy backtesting runs in the frontend against the current K-line rows, with buy/sell markers and yellow holding bands drawn on the chart.

## Frontend Structure

The Vue app is intentionally split by responsibility:

- `frontend/src/App.vue`: page shell and cross-panel wiring.
- `frontend/src/main.js`: Vue app creation and global error reporting.
- `frontend/src/components/`: presentational panels for header, watchlist, summary, chart, backtest, and advice.
- `frontend/src/composables/`: Vue state/lifecycle modules for stock data, backtesting, and ECharts.
- `frontend/src/services/`: API wrappers and pure backtest calculations.
- `frontend/src/charts/`: ECharts option builders.
- `frontend/src/utils/`: shared formatting and numeric helpers.
- `frontend/src/config.js`: Vite environment-derived API/WebSocket bases.
- `frontend/src/style.css`: global dashboard styling.

## Backtest Rules

Current strategies:

- `20日线：站上买入，跌破卖出`: if not holding and close is above MA20, signal buy; if holding and close is below MA20, signal sell.
- `放量急跌买入，放量卖出`: if not holding, volume is more than twice the previous 20-day average and close is down at least 4% from the previous close, signal buy; if holding and volume is more than twice the previous 20-day average, signal sell.
- `MA20趋势跟随：有效突破`: if not holding, close is above `MA20 * 1.02` and MA60 is rising, signal buy; if holding, trend break or overheat volume-price stall rules signal sell.
- `BOLL下轨买入，上轨卖出`: if not holding, close crosses below BOLL(20, 2) lower band, signal buy; if holding, intraday high crosses above BOLL(20, 2) upper band, signal sell.

Execution model:

- Signals are generated from completed K-line rows after the signal day's close, even when a condition uses intraday high/low.
- Every strategy uses the shared frontend backtest execution engine; strategy rules only create `buy`/`sell` signals.
- Buy and sell executions always happen at the next trading day's open price.
- Limit-up opens cannot be bought; blocked buy orders are skipped.
- Limit-down opens cannot be sold; blocked sell orders remain pending until the next tradable open.
- Chart markers represent actual execution dates/prices, not signal dates.

## Run Backend

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
python3 -m backend.server
```

The backend port is fixed in project config:

- Host: `0.0.0.0`
- Port: `8001`

For backend hot reload during development:

```bash
BACKEND_RELOAD=1 python3 -m backend.server
```

If `python3 -m venv .venv` reports that `ensurepip` is unavailable on Ubuntu or
Debian, install the system packages first:

```bash
sudo apt-get install python3-pip python3-venv
```

Backend API:

- `GET http://localhost:8001/api/stocks/000001/kline`
- `WS ws://localhost:8001/ws/stocks/000001`

## Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend defaults to:

- API: `http://localhost:8001`
- WebSocket: `ws://localhost:8001`
- Dev server: `http://localhost:5173`

You can override them with:

```bash
VITE_API_BASE=http://localhost:8001 VITE_WS_BASE=ws://localhost:8001 npm run dev
```

## Verification

```bash
python3 -m py_compile $(find backend -name '*.py' -print)
npm --prefix frontend run build
curl http://localhost:8001/api/health
curl http://localhost:8001/api/watchlist
curl http://localhost:8001/api/stocks/000001/kline
```
