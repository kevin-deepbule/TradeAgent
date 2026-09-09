# AkShare Adapter Agent Guide

This directory contains the internal FastAPI adapter around AkShare.

## Responsibility

- Keep AkShare access isolated here.
- Prefer official `akshare` Python APIs over hand-written scraping.
- Keep data-fetching code small and explicit because AkShare upstream interfaces can change.
- Return or persist `pandas.DataFrame` values without changing column names unless the caller asks for normalized fields.
- Expose only internal routes consumed by the Spring Boot backend.

## AkShare Rules

Primary documentation:
- https://akshare.akfamily.xyz/data/stock/stock.html

Before adding or changing stock data features, check the AkShare documentation
for exact function names and parameters.

Starting points:

- A-share historical quotes: `ak.stock_zh_a_hist`
- A-share realtime spot quotes: `ak.stock_zh_a_spot_em`
- If historical daily data does not include the current trading day, append a
  temporary intraday row from realtime spot quotes. Prefer
  `ak.stock_zh_a_spot_em`; fall back to `ak.stock_zh_a_spot` when Eastmoney is
  unavailable, and keep volume units aligned with historical rows.

## Structure Rules

- `main.py`: FastAPI routes and HTTP exceptions.
- `server.py`: uvicorn process entrypoint.
- `stock_adapter.py`: AkShare calls, stock resolution, and K-line payload shaping.
- `financial_adapter.py`: Shenwan membership, disclosures, formal quarterly
  statements, spot valuation inputs, and analyst EPS forecast adaptation.
- `config.py`: environment-derived adapter settings.
- `utils.py`: small data, symbol, market, date, and NaN helpers.

## Commands

Run locally:

```bash
source .venv/bin/activate
mkdir -p .logs && setsid python3 -m akshare_adapter.server > .logs/akshare-adapter.log 2>&1 < /dev/null &
```

Compile check:

```bash
python3 -m py_compile $(find akshare_adapter -name '*.py' -print)
```

Smoke check:

```bash
curl --noproxy '*' http://localhost:8002/internal/health
```

## Notes

- Default adapter port is `8002`.
- `REALTIME_SPOT_CACHE_SECONDS` controls the short all-market realtime spot
  cache used while appending temporary intraday rows.
- Keep this service internal to the backend; the frontend should not call it directly.
- Financial research endpoints should keep upstream interface calls explicit
  and return warnings when optional sources fail instead of hiding missing data.
- Update `README.md` and this `AGENTS.md` whenever adapter behavior, commands, configuration, routes, AkShare usage, structure, or runtime assumptions change.
- Do not commit `__pycache__/` or virtual environment files.
- Every source file and public function should have concise comments or docstrings where the project comment rules require them.
