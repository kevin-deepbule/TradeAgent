"""AkShare calls and normalized JSON shaping for the Java backend."""

from datetime import date, datetime, timedelta
from functools import lru_cache
from typing import Any

import pandas as pd

from akshare_adapter.config import KLINE_DISPLAY_YEARS, KLINE_MA_WARMUP_DAYS
from akshare_adapter.utils import (
    is_symbol_query,
    market_symbol,
    none_if_nan,
    normalize_symbol,
    simplify_text,
    years_ago,
)


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
    """Resolve a stock code or name into symbol metadata."""
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


def fetch_kline_sync(symbol: str, name: str | None = None) -> dict[str, Any]:
    """Fetch A-share daily K-line rows from AkShare for Java consumption."""
    import akshare as ak

    normalized = normalize_symbol(symbol)
    end = date.today()
    display_start = years_ago(end, KLINE_DISPLAY_YEARS)
    start = display_start - timedelta(days=KLINE_MA_WARMUP_DAYS)
    start_text = start.strftime("%Y%m%d")
    end_text = end.strftime("%Y%m%d")
    source = "eastmoney"
    warnings: list[str] = []

    try:
        df = ak.stock_zh_a_hist(
            symbol=normalized,
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
        warnings.append(f"eastmoney: {exc}")
        try:
            source = "sina"
            df = ak.stock_zh_a_daily(
                symbol=market_symbol(normalized),
                start_date=start_text,
                end_date=end_text,
                adjust="qfq",
            )
        except Exception as sina_exc:
            warnings.append(f"sina: {sina_exc}")
            source = "tencent"
            df = ak.stock_zh_a_hist_tx(
                symbol=market_symbol(normalized),
                start_date=start_text,
                end_date=end_text,
                adjust="qfq",
            )

    if df.empty:
        return {
            "symbol": normalized,
            "name": name or "",
            "updatedAt": datetime.now().isoformat(timespec="seconds"),
            "source": source,
            "rows": [],
            "error": "AkShare returned no rows.",
            "warnings": warnings,
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
    display_df = df[df["date"] >= pd.Timestamp(display_start)]
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
        "symbol": normalized,
        "name": name or "",
        "updatedAt": datetime.now().isoformat(timespec="seconds"),
        "source": source,
        "rows": rows,
        "error": None,
        "warnings": warnings,
    }

