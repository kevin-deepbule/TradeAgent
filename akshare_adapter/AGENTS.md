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

## Structure Rules

- `main.py`: FastAPI routes and HTTP exceptions.
- `server.py`: uvicorn process entrypoint.
- `stock_adapter.py`: AkShare calls, stock resolution, and K-line payload shaping.
- `config.py`: environment-derived adapter settings.
- `utils.py`: small data, symbol, market, date, and NaN helpers.

## Commands

Run locally:

```bash
mkdir -p .logs
source .venv/bin/activate
python3 -m akshare_adapter.server 2>&1 | tee .logs/akshare-adapter.log
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
- Keep this service internal to the backend; the frontend should not call it directly.
- Do not commit `__pycache__/` or virtual environment files.
- Every source file and public function should have concise comments or docstrings where the project comment rules require them.
