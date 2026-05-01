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
- The dashboard default stock can be changed from the current stock and is persisted in SQLite.
- Strategy backtesting runs in the frontend against the current K-line rows, with buy/sell markers, per-trade return labels, and yellow holding bands drawn on the chart.

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

The backend returns the most recent 5 calendar years of daily K-line rows, and
frontend backtests run across that full returned window.

Current strategies:

- `20日线：站上买入，跌破卖出`: if not holding and close is above MA20, signal buy; if holding and close is below MA20, signal sell.
- `放量急跌买入，放量卖出`: if not holding, volume is more than twice the previous 20-day average and close is down at least 4% from the previous close, signal buy; if holding and volume is more than twice the previous 20-day average, signal sell.
- `MA20趋势跟随：有效突破`: if not holding, close is above `MA20 * 1.02`, no higher than `MA20 * 1.05`, and MA60 is at least 99.9% of the previous MA60, signal buy; if holding, close below `MA20 * 0.98` or overheat volume-price stall rules signal sell.
- `BOLL下轨买入，上轨卖出`: if not holding, close crosses below BOLL(20, 2) lower band, signal buy; if holding, intraday high crosses above BOLL(20, 2) upper band, the position has been held for more than 30 trading days, or close falls more than 20% below the actual entry price, signal sell.

Execution model:

- Signals are generated from completed K-line rows after the signal day's close, even when a condition uses intraday high/low.
- Every strategy uses the shared frontend backtest execution engine; strategy rules only create `buy`/`sell` signals.
- Buy and sell executions always happen at the next trading day's open price.
- Limit-up opens cannot be bought; blocked buy orders are skipped.
- Limit-down opens cannot be sold; blocked sell orders remain pending until the next tradable open.
- Chart markers represent actual execution dates/prices, not signal dates.
- K-line return labels show each completed trade's gain/loss rate, and show floating gain/loss for an open position at the latest K-line.

## Run Backend

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
mkdir -p .logs
python3 -m backend.server 2>&1 | tee .logs/backend.log
```

The backend port is fixed in project config:

- Host: `0.0.0.0`
- Port: `8001`

For backend hot reload during development:

```bash
mkdir -p .logs
BACKEND_RELOAD=1 python3 -m backend.server 2>&1 | tee .logs/backend.log
```

If `python3 -m venv .venv` reports that `ensurepip` is unavailable on Ubuntu or
Debian, install the system packages first:

```bash
sudo apt-get install python3-pip python3-venv
```

Backend API:

- `GET http://localhost:8001/api/default-stock`
- `PUT http://localhost:8001/api/default-stock`
- `GET http://localhost:8001/api/stocks/000001/kline`
- `WS ws://localhost:8001/ws/stocks/000001`

## Run Frontend

```bash
npm --prefix frontend install
mkdir -p .logs
npm --prefix frontend run dev 2>&1 | tee .logs/frontend.log
```

Frontend defaults to:

- API: `http://localhost:8001`
- WebSocket: `ws://localhost:8001`
- Dev server: `http://localhost:5173`

You can override them with:

```bash
mkdir -p .logs
VITE_API_BASE=http://localhost:8001 VITE_WS_BASE=ws://localhost:8001 npm --prefix frontend run dev 2>&1 | tee .logs/frontend.log
```

## Verification

```bash
python3 -m py_compile $(find backend -name '*.py' -print)
npm --prefix frontend run build
curl http://localhost:8001/api/health
curl http://localhost:8001/api/watchlist
curl http://localhost:8001/api/stocks/000001/kline
```
