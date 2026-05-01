# Stock Analyst

Java + Vue stock K-line dashboard powered by a small Python AkShare adapter.

## Features

- Spring Boot backend serves the dashboard REST API and WebSocket paths.
- A small internal Python adapter fetches A-share data from AkShare.
- Query by A-share stock code or stock name, such as `000001` or `平安银行`.
- Backend calculates MA5, MA20, and MA60 from closing prices.
- Vue frontend renders candlestick, moving-average lines, and volume with ECharts.
- Watchlist and default stock persistence use SQLite at `backend/data/watchlist.db`.
- Strategy backtesting runs in the frontend against the returned K-line rows.

## Structure

- `backend/`: Spring Boot backend, SQLite repositories, WebSocket handler, advice service.
- `akshare_adapter/`: internal FastAPI service that wraps AkShare calls.
- `frontend/`: Vue 3 + Vite + ECharts dashboard.

The frontend still talks to the same public API surface:

- `GET /api/health`
- `GET /api/default-stock`
- `PUT /api/default-stock`
- `GET /api/watchlist`
- `POST /api/watchlist`
- `DELETE /api/watchlist/{symbol}`
- `GET /api/stocks/{query}/kline`
- `WS /ws/stocks/{query}`

## Run AkShare Adapter

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
mkdir -p .logs
python3 -m akshare_adapter.server 2>&1 | tee .logs/akshare-adapter.log
```

Defaults:

- Host: `127.0.0.1`
- Port: `8002`
- Health: `http://localhost:8002/internal/health`

## Run Backend

Requires JDK 17+ and Maven.

```bash
mkdir -p .logs
mvn -f backend/pom.xml spring-boot:run 2>&1 | tee .logs/backend.log
```

Defaults:

- Host: `0.0.0.0`
- Port: `8001`
- Adapter base: `http://localhost:8002`
- SQLite: `backend/data/watchlist.db`

You can also run the packaged jar after building:

```bash
mvn -f backend/pom.xml package
java -jar backend/target/trade-agent-backend-0.1.0.jar
```

## Run Frontend

```bash
npm --prefix frontend install
mkdir -p .logs
npm --prefix frontend run dev 2>&1 | tee .logs/frontend.log
```

Frontend defaults:

- API: `http://localhost:8001`
- WebSocket: `ws://localhost:8001`
- Dev server: `http://localhost:5173`

## Verification

Because this environment may have proxy variables set, local curl checks can use
`--noproxy '*'`.

```bash
python3 -m py_compile $(find akshare_adapter -name '*.py' -print)
mvn -f backend/pom.xml test
npm --prefix frontend run build
curl --noproxy '*' http://localhost:8002/internal/health
curl --noproxy '*' http://localhost:8001/api/health
curl --noproxy '*' http://localhost:8001/api/watchlist
curl --noproxy '*' http://localhost:8001/api/stocks/000001/kline
```

## Backtest Rules

The backend returns the most recent 5 calendar years of daily K-line rows, and
frontend backtests run across that full returned window.

Current strategies:

- `20日线：站上买入，跌破卖出`: if not holding and close is above MA20, signal buy; if holding and close is below MA20, signal sell.
- `放量急跌买入，放量卖出`: if not holding, volume is more than twice the previous 20-day average and close is down at least 4% from the previous close, signal buy; if holding and volume is more than twice the previous 20-day average, signal sell.
- `MA20趋势跟随：有效突破`: if not holding, close is above `MA20 * 1.02`, no higher than `MA20 * 1.05`, and MA60 is at least 99.9% of the previous MA60, signal buy; if holding, close below `MA20 * 0.98` or overheat volume-price stall rules signal sell.
- `BOLL下轨买入，上轨卖出`: if not holding, close crosses below BOLL(20, 2) lower band, signal buy; if holding, intraday high crosses above BOLL(20, 2) upper band, the position has been held for more than 30 trading days, or close falls more than 20% below the actual entry price, signal sell.

Execution model:

- Signals are generated from completed K-line rows after the signal day's close.
- Every strategy uses the shared frontend backtest execution engine.
- Buy and sell executions always happen at the next trading day's open price.
- Limit-up opens cannot be bought; blocked buy orders are skipped.
- Limit-down opens cannot be sold; blocked sell orders remain pending.
- Chart markers represent actual execution dates/prices, not signal dates.
