# TradeAgent AkShare Adapter

Internal FastAPI service that wraps AkShare calls for the Spring Boot backend.

## Role

The adapter isolates Python, pandas, and AkShare from the Java backend and Vue
frontend. It should stay small and explicit because AkShare upstream interfaces
can change.

## Structure

- `server.py`: uvicorn entrypoint.
- `main.py`: FastAPI app and internal routes.
- `stock_adapter.py`: AkShare calls, symbol resolution, and K-line shaping.
- `config.py`: adapter host, port, and K-line window settings.
- `utils.py`: stock-symbol, text, market, date, and NaN helpers.
- `__init__.py`: package marker.

## Setup

From the repository root:

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
```

## Run

```bash
source .venv/bin/activate
mkdir -p .logs && setsid python3 -m akshare_adapter.server > .logs/akshare-adapter.log 2>&1 < /dev/null &
```

Defaults:

- Host: `127.0.0.1`
- Port: `8002`
- Health: `http://localhost:8002/internal/health`

Logs are written to `.logs/akshare-adapter.log`.

## Check

```bash
python3 -m py_compile $(find akshare_adapter -name '*.py' -print)
curl --noproxy '*' http://localhost:8002/internal/health
```

## Internal Routes

- `GET /internal/health`
- `GET /internal/stocks/{query}/resolve`
- `GET /internal/stocks/{symbol}/kline?name=...`

These routes are for the Java backend, not for direct browser use.

## Environment

- `AKSHARE_ADAPTER_HOST`, default `127.0.0.1`
- `AKSHARE_ADAPTER_PORT`, default `8002`
- `KLINE_DISPLAY_YEARS`, default `5`
- `KLINE_MA_WARMUP_DAYS`, default `120`
- `REALTIME_SPOT_CACHE_SECONDS`, default `20`

## K-line Data Behavior

Daily K-line history uses AkShare historical APIs. During the A-share trading
session, if historical data does not yet include today's row, the adapter tries
to append a temporary intraday row from realtime spot quotes. It tries
`ak.stock_zh_a_spot_em()` first and falls back to `ak.stock_zh_a_spot()` when
Eastmoney realtime quotes are unavailable. Sina realtime volume is converted
from shares to hands so it matches the historical K-line volume unit.
