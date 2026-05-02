# Frontend Agent Guide

This directory contains the Vue 3 + Vite + ECharts dashboard.

## Responsibility

- Keep browser UI, interaction state, chart configuration, and frontend-only backtests here.
- Call the Spring Boot backend through `src/services/stockApi.js`.
- Do not call the Python AkShare adapter directly from frontend code.
- Keep `App.vue` thin; add behavior through `components/`, `composables/`, `services/`, or `charts/` according to responsibility.

## Structure Rules

- `src/components/`: display-focused Vue panels that receive props and emit user intents.
- `src/composables/`: state, lifecycle, and dashboard workflow logic.
- `src/services/stockApi.js`: all HTTP API calls.
- `src/services/backtest.js`: pure strategy math and shared execution engine.
- `src/charts/klineChartOption.js`: ECharts option shape.
- `src/utils/formatters.js`: numeric, percent, and clipboard export formatting.
- `src/style.css`: shared dashboard styling; reuse existing class names before adding new visual systems.

## Backtest Rules

- Backtests use the K-line `rows` returned by the backend.
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
- Do not commit `node_modules/` or `dist/`.
- Every source file and touched function should have concise comments where the project comment rules require them.
