# Frontend Agent Guide

This directory contains the Vue 3 + Vite + ECharts dashboard.

## Responsibility

- Keep browser UI, interaction state, chart configuration, and frontend-only backtests here.
- Call the Spring Boot backend through `src/services/stockApi.js`.
- Do not call the Python AkShare adapter directly from frontend code.
- Keep `App.vue` thin; add behavior through `components/`, `composables/`, `services/`, or `charts/` according to responsibility.

## Structure Rules

- `src/views/`: full-screen frontend views that compose controls and result panels.
- `src/components/`: display-focused Vue panels that receive props and emit user intents.
- `src/composables/`: state, lifecycle, and dashboard workflow logic.
- `src/services/stockApi.js`: all HTTP API calls.
- `src/services/backtest.js`: pure strategy math and shared execution engine.
- `src/charts/klineChartOption.js`: ECharts option shape.
- `src/utils/formatters.js`: numeric, percent, and clipboard export formatting.
- `src/style.css`: shared dashboard styling; reuse existing class names before adding new visual systems.

## Backtest Rules

- Backtests use the K-line `rows` returned by the backend.
- Watchlist batch backtests run on a dedicated frontend page; that page fetches each selected stock's K-line rows through the backend stock API and then executes through `calculateBacktest`.
- `MA20趋势跟随：有效突破` buys only when the close is above MA20, no higher than `MA20 * 1.05`, and MA60 is at least 99.9% of the previous MA60; if MA60 is below `previous MA60 * 1.002`, same-day volume must also be greater than `volume MA20 * 1.5`.
- All strategies must execute through `calculateBacktest` in `src/services/backtest.js`.
- Individual strategy helpers should return only `buy`, `sell`, or `null`.
- Actual executions happen at the next trading day's open price.
- Limit-up opens cannot be bought; blocked buy orders are skipped.
- Limit-down opens cannot be sold; blocked sell orders remain pending until the next tradable open.
- Chart buy/sell markers represent actual execution dates/prices.
- Yellow chart `markArea` bands represent actual holding periods.

## Commands

Run locally:

```bash
mkdir -p .logs
npm --prefix frontend run dev 2>&1 | tee .logs/frontend.log
```

Build check:

```bash
npm --prefix frontend run build
```

## Notes

- The Vite dev server uses `strictPort`; if `5173` is occupied, fix the process conflict instead of silently changing ports.
- The K-line hover tooltip shows OHLC, volume, MA5/MA20/MA60 values, their day-over-day slopes, and volume/MA20 context.
- Update `README.md` and this `AGENTS.md` whenever frontend behavior, commands, configuration, API usage, structure, backtest rules, or runtime assumptions change.
- Do not commit `node_modules/` or `dist/`.
- Every source file and touched function should have concise comments where the project comment rules require them.
