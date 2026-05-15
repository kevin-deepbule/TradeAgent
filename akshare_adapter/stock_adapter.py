"""AkShare calls and normalized JSON shaping for the Java backend."""

from datetime import date, datetime, time, timedelta
from functools import lru_cache
from threading import Lock
from typing import Any

import pandas as pd

from akshare_adapter.config import (
    KLINE_DISPLAY_YEARS,
    KLINE_MA_WARMUP_DAYS,
    REALTIME_SPOT_CACHE_SECONDS,
)
from akshare_adapter.utils import (
    is_symbol_query,
    market_symbol,
    none_if_nan,
    normalize_symbol,
    simplify_text,
    years_ago,
)


_spot_cache_lock = Lock()
_spot_cache: dict[str, tuple[datetime, pd.DataFrame | None, str | None]] = {}


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


def cached_spot_frame(source: str, fetcher: Any) -> pd.DataFrame:
    """Fetch all-market realtime spot data with a short process-local cache."""
    now = datetime.now()
    with _spot_cache_lock:
        cached = _spot_cache.get(source)
        if cached and now - cached[0] <= timedelta(seconds=REALTIME_SPOT_CACHE_SECONDS):
            _, cached_df, cached_error = cached
            if cached_error:
                raise RuntimeError(cached_error)
            if cached_df is not None:
                return cached_df.copy()

    try:
        df = fetcher()
        error = None
    except Exception as exc:
        df = None
        error = f"{type(exc).__name__}: {exc}"

    with _spot_cache_lock:
        _spot_cache[source] = (now, df, error)

    if error:
        raise RuntimeError(error)
    return df.copy()


def numeric_value(value: Any) -> float | None:
    """Convert AkShare scalar values to floats while tolerating blanks."""
    parsed = pd.to_numeric(value, errors="coerce")
    if pd.isna(parsed):
        return None
    return float(parsed)


def valid_price(value: float | None) -> bool:
    """Check that a realtime price is usable for a temporary K-line row."""
    return value is not None and value > 0


def parse_spot_time(value: Any) -> time | None:
    """Parse realtime quote time strings such as HH:MM:SS."""
    text = "" if value is None else str(value).strip()
    if not text:
        return None
    for pattern in ("%H:%M:%S", "%H:%M"):
        try:
            return datetime.strptime(text, pattern).time()
        except ValueError:
            continue
    return None


def should_append_intraday_row(today: date, spot_time: time | None = None) -> bool:
    """Allow temporary daily rows only during a plausible A-share trading day."""
    now = datetime.now()
    if today != now.date() or today.weekday() >= 5:
        return False
    if not time(9, 15) <= now.time() <= time(16, 0):
        return False
    if spot_time is None:
        return True
    if not time(9, 15) <= spot_time <= time(15, 35):
        return False
    if now.time() < time(15, 35):
        latest_allowed = (now + timedelta(minutes=10)).time()
        if spot_time > latest_allowed:
            return False
    return True


def row_from_eastmoney_spot(df: pd.DataFrame, normalized: str, today: date) -> dict[str, Any] | None:
    """Build a temporary K-line row from Eastmoney realtime spot data."""
    matched = df[df["代码"].astype(str).str.zfill(6) == normalized] if "代码" in df else pd.DataFrame()
    if matched.empty or not should_append_intraday_row(today):
        return None
    row = matched.iloc[0]
    open_price = numeric_value(row.get("今开"))
    close_price = numeric_value(row.get("最新价"))
    high_price = numeric_value(row.get("最高"))
    low_price = numeric_value(row.get("最低"))
    volume = numeric_value(row.get("成交量"))
    if not all(valid_price(value) for value in [open_price, close_price, high_price, low_price]):
        return None
    return {
        "date": pd.Timestamp(today),
        "open": open_price,
        "close": close_price,
        "high": high_price,
        "low": low_price,
        "volume": volume,
    }


def row_from_sina_spot(df: pd.DataFrame, normalized: str, today: date) -> dict[str, Any] | None:
    """Build a temporary K-line row from Sina realtime spot data."""
    code = market_symbol(normalized)
    matched = df[df["代码"].astype(str) == code] if "代码" in df else pd.DataFrame()
    if matched.empty:
        matched = df[df["代码"].astype(str).str.endswith(normalized)] if "代码" in df else pd.DataFrame()
    if matched.empty:
        return None
    row = matched.iloc[0]
    spot_time = parse_spot_time(row.get("时间戳"))
    if not should_append_intraday_row(today, spot_time):
        return None
    open_price = numeric_value(row.get("今开"))
    close_price = numeric_value(row.get("最新价"))
    high_price = numeric_value(row.get("最高"))
    low_price = numeric_value(row.get("最低"))
    volume_shares = numeric_value(row.get("成交量"))
    if not all(valid_price(value) for value in [open_price, close_price, high_price, low_price]):
        return None
    return {
        "date": pd.Timestamp(today),
        "open": open_price,
        "close": close_price,
        "high": high_price,
        "low": low_price,
        "volume": None if volume_shares is None else volume_shares / 100,
    }


def append_intraday_row(df: pd.DataFrame, ak: Any, normalized: str, warnings: list[str]) -> tuple[pd.DataFrame, str | None]:
    """Append today's temporary realtime K-line row when history lacks it."""
    today = date.today()
    if not df.empty and df["date"].max().date() >= today:
        return df, None

    try:
        spot_df = cached_spot_frame("eastmoney", ak.stock_zh_a_spot_em)
        realtime_row = row_from_eastmoney_spot(spot_df, normalized, today)
        if realtime_row is not None:
            return pd.concat([df, pd.DataFrame([realtime_row])], ignore_index=True), "realtime-eastmoney"
    except Exception as exc:
        warnings.append(f"realtime-eastmoney: {exc}")

    try:
        spot_df = cached_spot_frame("sina", ak.stock_zh_a_spot)
        realtime_row = row_from_sina_spot(spot_df, normalized, today)
        if realtime_row is not None:
            return pd.concat([df, pd.DataFrame([realtime_row])], ignore_index=True), "realtime-sina"
    except Exception as exc:
        warnings.append(f"realtime-sina: {exc}")

    return df, None


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

    df, realtime_source = append_intraday_row(df, ak, normalized, warnings)
    if realtime_source:
        source = f"{source}+{realtime_source}"
    df = df.sort_values("date")

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
