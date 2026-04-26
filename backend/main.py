import asyncio
import os
import sqlite3
import unicodedata
from contextlib import asynccontextmanager
from datetime import date, datetime, timedelta
from functools import lru_cache
from math import isnan
from pathlib import Path
from typing import Any

import pandas as pd
from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware


DEFAULT_SYMBOL = os.getenv("DEFAULT_STOCK_SYMBOL", "000001")
REFRESH_SECONDS = int(os.getenv("STOCK_REFRESH_SECONDS", "5"))
DB_PATH = Path(os.getenv("WATCHLIST_DB_PATH", "data/watchlist.db"))


class StockStore:
    def __init__(self) -> None:
        self._lock = asyncio.Lock()
        self.active_symbols: set[str] = {DEFAULT_SYMBOL}
        self.symbol_names: dict[str, str] = {}
        self.payloads: dict[str, dict[str, Any]] = {}

    async def add_symbol(self, symbol: str, name: str | None = None) -> None:
        symbol = normalize_symbol(symbol)
        async with self._lock:
            self.active_symbols.add(symbol)
            if name:
                self.symbol_names[symbol] = name

    async def remove_symbol(self, symbol: str) -> None:
        symbol = normalize_symbol(symbol)
        async with self._lock:
            self.active_symbols.discard(symbol)
            self.symbol_names.pop(symbol, None)
            self.payloads.pop(symbol, None)

    async def symbols(self) -> list[str]:
        async with self._lock:
            return sorted(self.active_symbols)

    async def name(self, symbol: str) -> str | None:
        async with self._lock:
            return self.symbol_names.get(normalize_symbol(symbol))

    async def save(self, symbol: str, payload: dict[str, Any]) -> None:
        async with self._lock:
            self.payloads[normalize_symbol(symbol)] = payload

    async def get(self, symbol: str) -> dict[str, Any] | None:
        async with self._lock:
            return self.payloads.get(normalize_symbol(symbol))


store = StockStore()


def db_connect() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    return connection


def init_watchlist_db() -> None:
    with db_connect() as connection:
        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS watchlist (
                symbol TEXT PRIMARY KEY,
                name TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL
            )
            """
        )
        connection.execute(
            """
            INSERT OR IGNORE INTO watchlist (symbol, name, created_at)
            VALUES (?, ?, ?)
            """,
            (DEFAULT_SYMBOL, "", datetime.now().isoformat(timespec="seconds")),
        )


def list_watchlist_sync() -> list[dict[str, str]]:
    with db_connect() as connection:
        rows = connection.execute(
            """
            SELECT symbol, name, created_at
            FROM watchlist
            ORDER BY created_at ASC, symbol ASC
            """
        ).fetchall()
    return [dict(row) for row in rows]


def upsert_watchlist_sync(symbol: str, name: str) -> dict[str, str]:
    symbol = normalize_symbol(symbol)
    now = datetime.now().isoformat(timespec="seconds")
    with db_connect() as connection:
        connection.execute(
            """
            INSERT INTO watchlist (symbol, name, created_at)
            VALUES (?, ?, ?)
            ON CONFLICT(symbol) DO UPDATE SET name = excluded.name
            """,
            (symbol, name or "", now),
        )
    return {"symbol": symbol, "name": name or "", "created_at": now}


def delete_watchlist_sync(symbol: str) -> None:
    with db_connect() as connection:
        connection.execute(
            "DELETE FROM watchlist WHERE symbol = ?",
            (normalize_symbol(symbol),),
        )


async def load_watchlist_into_store() -> None:
    items = await asyncio.to_thread(list_watchlist_sync)
    for item in items:
        await store.add_symbol(item["symbol"], item.get("name") or None)


def normalize_symbol(symbol: str) -> str:
    return "".join(ch for ch in symbol if ch.isdigit()).zfill(6)[-6:]


def is_symbol_query(query: str) -> bool:
    text = query.strip()
    return bool(text) and text.isdigit() and len(text) <= 6


def simplify_text(text: str) -> str:
    return "".join(unicodedata.normalize("NFKC", text).upper().split())


@lru_cache(maxsize=1)
def stock_code_name_df() -> pd.DataFrame:
    import akshare as ak

    df = ak.stock_info_a_code_name()
    df = df.copy()
    df["code"] = df["code"].astype(str).str.zfill(6)
    df["name"] = df["name"].astype(str)
    df["simple_name"] = df["name"].map(simplify_text)
    return df


def resolve_stock_sync(query: str) -> dict[str, str]:
    query = query.strip()
    if is_symbol_query(query):
        symbol = normalize_symbol(query)
        try:
            df = stock_code_name_df()
            matched = df[df["code"] == symbol]
            name = matched.iloc[0]["name"] if not matched.empty else ""
        except Exception:
            name = ""
        return {"symbol": symbol, "name": name, "query": query}

    simple_query = simplify_text(query)
    if not simple_query:
        raise ValueError("请输入股票代码或名称。")

    df = stock_code_name_df()
    exact = df[df["simple_name"] == simple_query]
    if exact.empty:
        exact = df[df["simple_name"].str.startswith(simple_query)]
    if exact.empty:
        exact = df[df["simple_name"].str.contains(simple_query, regex=False)]
    if exact.empty:
        raise ValueError(f"没有找到股票：{query}")

    row = exact.iloc[0]
    return {"symbol": row["code"], "name": row["name"], "query": query}


async def resolve_stock(query: str) -> dict[str, str]:
    return await asyncio.to_thread(resolve_stock_sync, query)


def market_symbol(symbol: str) -> str:
    symbol = normalize_symbol(symbol)
    if symbol.startswith(("5", "6", "9")):
        return f"sh{symbol}"
    return f"sz{symbol}"


def none_if_nan(value: Any) -> float | int | str | None:
    if value is None:
        return None
    if isinstance(value, float) and isnan(value):
        return None
    if pd.isna(value):
        return None
    if isinstance(value, float):
        return round(value, 4)
    return value


def fetch_kline_sync(symbol: str, name: str | None = None) -> dict[str, Any]:
    import akshare as ak

    end = date.today()
    start = end - timedelta(days=520)
    start_text = start.strftime("%Y%m%d")
    end_text = end.strftime("%Y%m%d")
    source = "eastmoney"
    errors: list[str] = []

    try:
        df = ak.stock_zh_a_hist(
            symbol=symbol,
            period="daily",
            start_date=start_text,
            end_date=end_text,
            adjust="qfq",
        )
        df = df.rename(
            columns={
                "日期": "date",
                "开盘": "open",
                "收盘": "close",
                "最高": "high",
                "最低": "low",
                "成交量": "volume",
            }
        )
    except Exception as exc:
        errors.append(f"eastmoney: {exc}")
        try:
            source = "sina"
            df = ak.stock_zh_a_daily(
                symbol=market_symbol(symbol),
                start_date=start_text,
                end_date=end_text,
                adjust="qfq",
            )
        except Exception as sina_exc:
            errors.append(f"sina: {sina_exc}")
            source = "tencent"
            df = ak.stock_zh_a_hist_tx(
                symbol=market_symbol(symbol),
                start_date=start_text,
                end_date=end_text,
                adjust="qfq",
            )

    if df.empty:
        return {
            "symbol": symbol,
            "name": name or "",
            "updatedAt": datetime.now().isoformat(timespec="seconds"),
            "rows": [],
            "source": source,
            "error": "AkShare returned no rows.",
        }

    df = df.copy()
    df["date"] = pd.to_datetime(df["date"])
    df = df.sort_values("date")

    for column in ["open", "close", "high", "low", "volume"]:
        if column not in df:
            df[column] = None
        df[column] = pd.to_numeric(df[column], errors="coerce")

    df["ma5"] = df["close"].rolling(window=5).mean()
    df["ma20"] = df["close"].rolling(window=20).mean()
    df["ma60"] = df["close"].rolling(window=60).mean()

    rows = []
    for _, row in df.tail(240).iterrows():
        rows.append(
            {
                "date": row["date"].strftime("%Y-%m-%d"),
                "open": none_if_nan(row["open"]),
                "close": none_if_nan(row["close"]),
                "high": none_if_nan(row["high"]),
                "low": none_if_nan(row["low"]),
                "volume": none_if_nan(row["volume"]),
                "ma5": none_if_nan(row["ma5"]),
                "ma20": none_if_nan(row["ma20"]),
                "ma60": none_if_nan(row["ma60"]),
            }
        )

    return {
        "symbol": symbol,
        "name": name or "",
        "updatedAt": datetime.now().isoformat(timespec="seconds"),
        "source": source,
        "rows": rows,
        "error": None,
        "warnings": errors,
    }


async def refresh_symbol(symbol: str) -> dict[str, Any]:
    symbol = normalize_symbol(symbol)
    name = await store.name(symbol)
    try:
        payload = await asyncio.to_thread(fetch_kline_sync, symbol, name)
    except Exception as exc:
        payload = {
            "symbol": symbol,
            "name": name or "",
            "updatedAt": datetime.now().isoformat(timespec="seconds"),
            "source": None,
            "rows": [],
            "error": str(exc),
        }
    await store.save(symbol, payload)
    return payload


async def refresh_loop() -> None:
    while True:
        symbols = await store.symbols()
        for symbol in symbols:
            await refresh_symbol(symbol)
        await asyncio.sleep(REFRESH_SECONDS)


@asynccontextmanager
async def lifespan(_: FastAPI):
    await asyncio.to_thread(init_watchlist_db)
    await load_watchlist_into_store()
    task = asyncio.create_task(refresh_loop())
    yield
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        pass


app = FastAPI(title="Stock K-Line API", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/health")
async def health() -> dict[str, Any]:
    return {"ok": True, "refreshSeconds": REFRESH_SECONDS}


@app.get("/api/watchlist")
async def list_watchlist() -> list[dict[str, str]]:
    return await asyncio.to_thread(list_watchlist_sync)


@app.post("/api/watchlist")
async def add_watchlist(item: dict[str, str]) -> dict[str, str]:
    query = (item.get("query") or item.get("symbol") or "").strip()
    try:
        resolved = await resolve_stock(query)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc

    saved = await asyncio.to_thread(
        upsert_watchlist_sync,
        resolved["symbol"],
        resolved["name"],
    )
    await store.add_symbol(saved["symbol"], saved["name"])
    return saved


@app.delete("/api/watchlist/{symbol}")
async def delete_watchlist(symbol: str) -> dict[str, bool]:
    normalized = normalize_symbol(symbol)
    await asyncio.to_thread(delete_watchlist_sync, normalized)
    await store.remove_symbol(normalized)
    return {"ok": True}


@app.get("/api/stocks/{query}/kline")
async def get_kline(query: str) -> dict[str, Any]:
    try:
        resolved = await resolve_stock(query)
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc

    symbol = resolved["symbol"]
    await store.add_symbol(symbol, resolved["name"])
    cached = await store.get(symbol)
    if cached is None:
        cached = await refresh_symbol(symbol)
    else:
        cached = {**cached, "name": resolved["name"] or cached.get("name", "")}
    return cached


@app.websocket("/ws/stocks/{query}")
async def stock_ws(websocket: WebSocket, query: str) -> None:
    await websocket.accept()
    try:
        resolved = await resolve_stock(query)
    except ValueError as exc:
        await websocket.send_json(
            {
                "symbol": "",
                "name": "",
                "updatedAt": datetime.now().isoformat(timespec="seconds"),
                "source": None,
                "rows": [],
                "error": str(exc),
            }
        )
        await websocket.close()
        return

    symbol = resolved["symbol"]
    await store.add_symbol(symbol, resolved["name"])
    await refresh_symbol(symbol)

    try:
        while True:
            payload = await store.get(symbol)
            if payload is None:
                payload = await refresh_symbol(symbol)
            else:
                payload = {
                    **payload,
                    "name": resolved["name"] or payload.get("name", ""),
                }
            await websocket.send_json(payload)
            await asyncio.sleep(REFRESH_SECONDS)
    except WebSocketDisconnect:
        return
