# TradeAgent Frontend

Vue 3 + Vite + ECharts dashboard for viewing A-share K-line data, watchlists,
trade advice, and frontend-only strategy backtests.

## Role

The frontend owns browser-facing interaction and presentation. It calls the
Spring Boot backend only:

- REST API base: `http://localhost:8001`
- WebSocket base: `ws://localhost:8001`

It should not call the Python AkShare adapter directly.

## Structure

- `src/App.vue`: dashboard shell that wires composables and panels.
- `src/main.js`: Vue bootstrap and global error reporting.
- `src/views/`: full-screen frontend views such as watchlist batch backtests.
- `src/components/`: presentational dashboard panels.
- `src/composables/`: state, lifecycle, and UI workflow modules.
- `src/services/stockApi.js`: backend API wrapper.
- `src/services/backtest.js`: frontend-only strategy backtest engine.
- `src/charts/klineChartOption.js`: ECharts option builder.
- `src/utils/formatters.js`: display and export formatting helpers.
- `src/config.js`: Vite environment-derived API and WebSocket bases.
- `src/style.css`: global dashboard layout and visual styling.
- `vite.config.js`: dev and preview server configuration.

## Setup

From the repository root:

```bash
npm --prefix frontend install
```

## Run

```bash
mkdir -p .logs
npm --prefix frontend run dev 2>&1 | tee .logs/frontend.log
```

Default dev server:

- `http://localhost:5173`

## Build

```bash
npm --prefix frontend run build
```

## Backtests

Strategy backtests run in `src/services/backtest.js` from the K-line `rows`
returned by the backend. `MA20趋势跟随：有效突破` buys only after the close is
above MA20, no higher than `MA20 * 1.05`, with non-weak MA60. Same-day volume must be greater than
`volume MA20 * 1.5` only when MA60 is below `previous MA60 * 1.002`; execution
still happens at the next trading day's open.

The backtest panel can open a dedicated watchlist batch-backtest page. That
page selects one or more watchlist stocks, runs the same strategy against each
selected stock, and displays the batch results. Those runs still fetch K-line
rows through the backend stock API and execute through `calculateBacktest`.

## Chart Tooltip

The K-line hover tooltip shows OHLC, volume, MA5/MA20/MA60 values, each MA's
day-over-day slope, and volume versus its 20-day average.

## Environment

Optional Vite environment variables:

```bash
VITE_API_BASE=http://localhost:8001
VITE_WS_BASE=ws://localhost:8001
```

When `VITE_WS_BASE` is omitted, the frontend derives it from `VITE_API_BASE`.
