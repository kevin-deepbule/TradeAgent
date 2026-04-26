# Stock Analyst

Python + Vue stock K-line dashboard powered by AkShare.

## Features

- FastAPI backend fetches A-share daily K-line data from AkShare every 5 seconds.
- Query by A-share stock code or stock name, such as `000001` or `平安银行`.
- Backend calculates MA5, MA20, and MA60 from closing prices.
- Vue frontend renders candlestick, moving-average lines, and volume with ECharts.
- WebSocket pushes the cached backend payload to the frontend every 5 seconds.
- One-click copy exports date, OHLC, volume, MA5, MA20, and MA60 as tab-separated text.

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
