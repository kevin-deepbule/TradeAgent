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

## Environment

Optional Vite environment variables:

```bash
VITE_API_BASE=http://localhost:8001
VITE_WS_BASE=ws://localhost:8001
```

When `VITE_WS_BASE` is omitted, the frontend derives it from `VITE_API_BASE`.
