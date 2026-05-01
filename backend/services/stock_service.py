"""Stock lookup, AkShare fetching, K-line shaping, and refresh workflow."""

import asyncio
from datetime import date, datetime, timedelta
from functools import lru_cache
from typing import Any

import pandas as pd

from backend.config import REFRESH_SECONDS
from backend.repositories.settings_repository import get_default_stock_sync
from backend.repositories.watchlist_repository import list_watchlist_sync
from backend.services.advice_service import build_trade_advice
from backend.services.stock_store import store
from backend.utils import (
    is_symbol_query,
    market_symbol,
    none_if_nan,
    normalize_symbol,
    simplify_text,
)


KLINE_DISPLAY_YEARS = 5
KLINE_MA_WARMUP_DAYS = 120
_refresh_locks: dict[str, asyncio.Lock] = {}


def years_ago(value: date, years: int) -> date:
    """Return the same calendar day a number of years earlier."""
    try:
        return value.replace(year=value.year - years)
    except ValueError:
        return value.replace(year=value.year - years, month=2, day=28)


async def load_watchlist_into_store() -> None:
    """Load persisted watchlist symbols into the live refresh store."""
    items = await asyncio.to_thread(list_watchlist_sync)
    for item in items:
        await store.add_symbol(item["symbol"], item.get("name") or None)


async def load_default_stock_into_store() -> None:
    """Load the persisted default stock into the live refresh store."""
    item = await asyncio.to_thread(get_default_stock_sync)
    await store.add_symbol(item["symbol"], item.get("name") or None)


@lru_cache(maxsize=1)
def stock_code_name_df() -> pd.DataFrame:
    """Fetch and cache A-share code/name data for query resolution."""
    import akshare as ak

    df = ak.stock_info_a_code_name()
    df = df.copy()
    df["code"] = df["code"].astype(str).str.zfill(6)
    df["name"] = df["name"].astype(str)
    df["simple_name"] = df["name"].map(simplify_text)
    return df


def resolve_stock_sync(query: str) -> dict[str, str]:
    """Resolve a user-entered stock code or name into symbol metadata."""
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
    """Resolve stock metadata without blocking the event loop."""
    return await asyncio.to_thread(resolve_stock_sync, query)


def fetch_kline_sync(symbol: str, name: str | None = None) -> dict[str, Any]:
    """Fetch K-line rows from AkShare and return the frontend payload shape."""
    import akshare as ak

    end = date.today()
    display_start = years_ago(end, KLINE_DISPLAY_YEARS)
    start = display_start - timedelta(days=KLINE_MA_WARMUP_DAYS)
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
            "advice": None,
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

    display_df = df[df["date"] >= pd.Timestamp(display_start)]

    rows = []
    for _, row in display_df.iterrows():
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
        "advice": build_trade_advice(rows),
        "error": None,
        "warnings": errors,
    }


async def refresh_symbol(symbol: str) -> dict[str, Any]:
    """Fetch one symbol, cache the payload, and convert failures into payload errors."""
    symbol = normalize_symbol(symbol)
    lock = _refresh_locks.setdefault(symbol, asyncio.Lock())
    async with lock:
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
                "advice": None,
                "error": str(exc),
            }
        await store.save(symbol, payload)
        return payload


async def refresh_loop() -> None:
    """Continuously refresh every active symbol at the configured interval."""
    while True:
        symbols = await store.symbols()
        for symbol in symbols:
            await refresh_symbol(symbol)
        await asyncio.sleep(REFRESH_SECONDS)
