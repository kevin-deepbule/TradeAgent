"""AkShare industry, disclosure, financial-statement, and valuation inputs."""

from __future__ import annotations

from datetime import date, datetime
from typing import Any, Iterable

import pandas as pd

from akshare_adapter.stock_adapter import cached_spot_frame
from akshare_adapter.utils import normalize_symbol


def _json_value(value: Any) -> Any:
    """Convert pandas and numpy scalar values into JSON-safe Python values."""
    if value is None or pd.isna(value):
        return None
    if isinstance(value, (pd.Timestamp, datetime, date)):
        return value.isoformat()
    if hasattr(value, "item"):
        return value.item()
    return value


def _first(row: pd.Series, names: Iterable[str]) -> Any:
    """Return the first non-empty value from a list of upstream column names."""
    for name in names:
        if name not in row:
            continue
        value = row.get(name)
        if value is not None and not pd.isna(value) and str(value).strip():
            return value
    return None


def _number(value: Any) -> float | None:
    """Parse an upstream numeric value without raising on blanks or text."""
    parsed = pd.to_numeric(value, errors="coerce")
    if pd.isna(parsed):
        return None
    return float(parsed)


def _text(value: Any) -> str | None:
    """Return trimmed text or None for an empty upstream value."""
    normalized = _json_value(value)
    if normalized is None:
        return None
    text = str(normalized).strip()
    return text or None


def _date_text(value: Any) -> str | None:
    """Normalize an upstream date-like value to an ISO calendar date."""
    if value is None or pd.isna(value):
        return None
    parsed = pd.to_datetime(value, errors="coerce")
    if pd.isna(parsed):
        return None
    return parsed.date().isoformat()


def _validate_report_period(report_period: str) -> str:
    """Validate and normalize a quarter-end report period in YYYYMMDD form."""
    try:
        parsed = datetime.strptime(report_period, "%Y%m%d").date()
    except ValueError as exc:
        raise ValueError("报告期必须使用 YYYYMMDD 格式") from exc
    if parsed.strftime("%m%d") not in {"0331", "0630", "0930", "1231"}:
        raise ValueError("报告期必须是一季报、中报、三季报或年报期末")
    return parsed.strftime("%Y%m%d")


def _eastmoney_symbol(symbol: str) -> str:
    """Convert a six-digit code into the uppercase Eastmoney statement symbol."""
    normalized = normalize_symbol(symbol)
    prefix = "SH" if normalized.startswith(("5", "6", "9")) else "SZ"
    return f"{prefix}{normalized}"


def _is_main_board(symbol: str) -> bool:
    """Classify the current Shanghai and Shenzhen main-board code families."""
    return symbol.startswith(("600", "601", "603", "605", "000", "001", "002", "003"))


def fetch_industries_sync(level: int = 3) -> dict[str, Any]:
    """Fetch selectable Shenwan industries at level two or three."""
    import akshare as ak

    if level == 2:
        frame = ak.sw_index_second_info()
    elif level == 3:
        frame = ak.sw_index_third_info()
    else:
        raise ValueError("仅支持申万二级或三级行业")

    rows = []
    for _, row in frame.iterrows():
        rows.append(
            {
                "code": _text(row.get("行业代码")),
                "name": _text(row.get("行业名称")),
                "parentName": _text(row.get("上级行业")),
                "level": level,
                "memberCount": int(_number(row.get("成份个数")) or 0),
                "staticPe": _number(row.get("静态市盈率")),
                "ttmPe": _number(row.get("TTM(滚动)市盈率")),
                "pb": _number(row.get("市净率")),
                "dividendYield": _number(row.get("静态股息率")),
            }
        )
    return {"updatedAt": datetime.now().astimezone().isoformat(), "rows": rows}


def fetch_industry_constituents_sync(industry_code: str) -> dict[str, Any]:
    """Fetch the current constituents of one Shenwan level-three industry."""
    import akshare as ak

    code = industry_code.strip().upper()
    if code.isdigit():
        code = f"{code}.SI"
    frame = ak.sw_index_third_cons(symbol=code)
    rows = []
    for _, row in frame.iterrows():
        symbol = normalize_symbol(str(row.get("股票代码", "")))
        if not symbol.strip("0"):
            continue
        rows.append(
            {
                "symbol": symbol,
                "name": _text(row.get("股票简称")) or "",
                "industryCode": code,
                "industryName": _text(row.get("申万3级")) or "",
                "includedAt": _date_text(row.get("纳入时间")),
                "price": _number(row.get("价格")),
                "pe": _number(row.get("市盈率")),
                "ttmPe": _number(row.get("市盈率ttm")),
                "pb": _number(row.get("市净率")),
                "roe": _number(row.get("ROE(%)")),
                "marketCapYi": _number(row.get("市值")),
                "profitGrowth": _number(row.get("净利润增速(%)")),
                "revenueGrowth": _number(row.get("营收增速(%)")),
                "marketBoard": "MAIN_BOARD" if _is_main_board(symbol) else "OTHER",
            }
        )
    return {
        "industryCode": code,
        "updatedAt": datetime.now().astimezone().isoformat(),
        "rows": rows,
    }


def _report_rows(frame: pd.DataFrame, report_period: str, source_type: str) -> list[dict[str, Any]]:
    """Normalize formal-report or quick-report summary rows."""
    rows = []
    for _, row in frame.iterrows():
        is_quick = source_type == "QUICK_REPORT"
        rows.append(
            {
                "sourceType": source_type,
                "symbol": normalize_symbol(str(row.get("股票代码", ""))),
                "name": _text(row.get("股票简称")) or "",
                "reportPeriod": report_period,
                "announcementDate": _date_text(
                    row.get("公告日期") if is_quick else row.get("最新公告日期")
                ),
                "revenue": _number(
                    row.get("营业收入-营业收入")
                    if is_quick
                    else row.get("营业总收入-营业总收入")
                ),
                "revenueYoY": _number(
                    row.get("营业收入-同比增长")
                    if is_quick
                    else row.get("营业总收入-同比增长")
                ),
                "parentNetProfit": _number(row.get("净利润-净利润")),
                "parentNetProfitYoY": _number(row.get("净利润-同比增长")),
                "forecastMetric": None,
                "forecastValue": None,
                "forecastChange": None,
                "changeReason": None,
                "forecastType": None,
            }
        )
    return rows


def _forecast_rows(frame: pd.DataFrame, report_period: str) -> list[dict[str, Any]]:
    """Normalize earnings-forecast rows while retaining their metric semantics."""
    rows = []
    for _, row in frame.iterrows():
        rows.append(
            {
                "sourceType": "FORECAST",
                "symbol": normalize_symbol(str(row.get("股票代码", ""))),
                "name": _text(row.get("股票简称")) or "",
                "reportPeriod": report_period,
                "announcementDate": _date_text(row.get("公告日期")),
                "revenue": None,
                "revenueYoY": None,
                "parentNetProfit": None,
                "parentNetProfitYoY": None,
                "forecastMetric": _text(row.get("预测指标")),
                "forecastValue": _number(row.get("预测数值")),
                "forecastChange": _number(row.get("业绩变动幅度")),
                "changeReason": _text(row.get("业绩变动原因")),
                "forecastType": _text(row.get("预告类型")),
            }
        )
    return rows


def fetch_disclosures_sync(report_period: str) -> dict[str, Any]:
    """Fetch bulk forecasts, quick reports, and report summaries for one period."""
    import akshare as ak

    normalized_period = _validate_report_period(report_period)
    rows: list[dict[str, Any]] = []
    warnings: list[str] = []
    sources = (
        ("REPORT", ak.stock_yjbb_em, _report_rows),
        ("QUICK_REPORT", ak.stock_yjkb_em, _report_rows),
        ("FORECAST", ak.stock_yjyg_em, _forecast_rows),
    )
    for source_type, fetcher, normalizer in sources:
        try:
            frame = fetcher(date=normalized_period)
            if source_type == "FORECAST":
                rows.extend(normalizer(frame, normalized_period))
            else:
                rows.extend(normalizer(frame, normalized_period, source_type))
        except Exception as exc:
            warnings.append(f"{source_type}: {type(exc).__name__}: {exc}")
    return {
        "reportPeriod": normalized_period,
        "updatedAt": datetime.now().astimezone().isoformat(),
        "rows": rows,
        "warnings": warnings,
    }


def _cash_by_period(
    frame: pd.DataFrame,
    as_of_date: date,
) -> dict[str, float | None]:
    """Index the latest point-visible quarterly operating cash flow by period."""
    selected: dict[str, tuple[str, float | None]] = {}
    for _, row in frame.iterrows():
        report_date = _date_text(_first(row, ("REPORT_DATE", "REPORTDATE")))
        if not report_date:
            continue
        notice_date = _date_text(_first(row, ("NOTICE_DATE", "UPDATE_DATE")))
        update_date = _date_text(row.get("UPDATE_DATE"))
        visible_date = update_date or notice_date or ""
        if notice_date and datetime.strptime(notice_date, "%Y-%m-%d").date() > as_of_date:
            continue
        if update_date and datetime.strptime(update_date, "%Y-%m-%d").date() > as_of_date:
            continue
        value = _number(
            _first(
                row,
                (
                    "NETCASH_OPERATE",
                    "NET_CASH_FLOW_OPERATE",
                    "NETCASH_OPERATE_ACTIVITY",
                ),
            )
        )
        current = selected.get(report_date)
        if current is None or visible_date > current[0]:
            selected[report_date] = (visible_date, value)
    return {report_date: item[1] for report_date, item in selected.items()}


def fetch_financial_history_sync(
    symbol: str,
    report_period: str,
    as_of: str | None = None,
) -> dict[str, Any]:
    """Fetch up to five years of point-filtered single-quarter financial facts."""
    import akshare as ak

    normalized = normalize_symbol(symbol)
    normalized_period = _validate_report_period(report_period)
    cutoff = datetime.strptime(normalized_period, "%Y%m%d").date()
    earliest = cutoff.replace(year=cutoff.year - 5)
    as_of_date = datetime.strptime(as_of, "%Y-%m-%d").date() if as_of else date.today()
    warnings: list[str] = []

    try:
        profit_frame = ak.stock_profit_sheet_by_quarterly_em(
            symbol=_eastmoney_symbol(normalized)
        )
    except Exception as exc:
        return {
            "symbol": normalized,
            "rows": [],
            "warnings": [f"PROFIT_SHEET: {type(exc).__name__}: {exc}"],
        }

    try:
        cash_frame = ak.stock_cash_flow_sheet_by_quarterly_em(
            symbol=_eastmoney_symbol(normalized)
        )
        cash_values = _cash_by_period(cash_frame, as_of_date)
    except Exception as exc:
        cash_values = {}
        warnings.append(f"CASH_FLOW: {type(exc).__name__}: {exc}")

    selected: dict[str, dict[str, Any]] = {}
    for _, row in profit_frame.iterrows():
        report_date = _date_text(_first(row, ("REPORT_DATE", "REPORTDATE")))
        if not report_date:
            continue
        period_date = datetime.strptime(report_date, "%Y-%m-%d").date()
        notice_date = _date_text(_first(row, ("NOTICE_DATE", "UPDATE_DATE")))
        update_date = _date_text(row.get("UPDATE_DATE"))
        if period_date > cutoff or period_date < earliest:
            continue
        if notice_date and datetime.strptime(notice_date, "%Y-%m-%d").date() > as_of_date:
            continue
        if update_date and datetime.strptime(update_date, "%Y-%m-%d").date() > as_of_date:
            continue
        candidate = {
            "reportDate": report_date,
            "noticeDate": notice_date,
            "updateDate": update_date,
            "revenue": _number(
                _first(row, ("TOTAL_OPERATE_INCOME", "OPERATE_INCOME"))
            ),
            "parentNetProfit": _number(
                _first(row, ("PARENT_NETPROFIT", "PARENT_NET_PROFIT"))
            ),
            "deductParentNetProfit": _number(
                _first(
                    row,
                    ("DEDUCT_PARENT_NETPROFIT", "DEDUCT_PARENT_NET_PROFIT"),
                )
            ),
            "operatingCashFlow": cash_values.get(report_date),
            "grossProfit": _number(_first(row, ("GROSS_PROFIT",))),
            "sourceType": "FORMAL_REPORT",
        }
        current = selected.get(report_date)
        if current is None or (candidate.get("updateDate") or "") > (
            current.get("updateDate") or ""
        ):
            selected[report_date] = candidate

    rows = sorted(selected.values(), key=lambda item: item["reportDate"], reverse=True)
    return {
        "symbol": normalized,
        "reportPeriod": normalized_period,
        "asOf": as_of_date.isoformat(),
        "rows": rows,
        "warnings": warnings,
    }


def fetch_market_snapshot_sync(symbols: list[str]) -> dict[str, Any]:
    """Fetch current all-market spot values and retain requested stock codes."""
    import akshare as ak

    normalized_symbols = {normalize_symbol(symbol) for symbol in symbols}
    frame = cached_spot_frame("eastmoney", ak.stock_zh_a_spot_em)
    rows = []
    for _, row in frame.iterrows():
        symbol = normalize_symbol(str(row.get("代码", "")))
        if symbol not in normalized_symbols:
            continue
        rows.append(
            {
                "symbol": symbol,
                "name": _text(row.get("名称")) or "",
                "price": _number(row.get("最新价")),
                "dynamicPe": _number(row.get("市盈率-动态")),
                "totalMarketCap": _number(row.get("总市值")),
                "floatMarketCap": _number(row.get("流通市值")),
            }
        )
    return {"asOf": datetime.now().astimezone().isoformat(), "rows": rows}


def fetch_profit_forecasts_sync() -> dict[str, Any]:
    """Fetch current analyst EPS forecasts and preserve year-labelled values."""
    import akshare as ak

    frame = ak.stock_profit_forecast_em()
    forecast_columns = [column for column in frame.columns if "预测每股收益" in str(column)]
    rows = []
    for _, row in frame.iterrows():
        eps_forecasts: dict[str, float | None] = {}
        for column in forecast_columns:
            year = "".join(character for character in str(column) if character.isdigit())[:4]
            if year:
                eps_forecasts[year] = _number(row.get(column))
        rows.append(
            {
                "symbol": normalize_symbol(str(row.get("代码", ""))),
                "name": _text(row.get("名称")) or "",
                "reportCount": int(_number(row.get("研报数")) or 0),
                "epsForecasts": eps_forecasts,
            }
        )
    return {"asOf": datetime.now().astimezone().isoformat(), "rows": rows}
