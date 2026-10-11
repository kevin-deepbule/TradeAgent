"""通过 AkShare 获取财经资讯，并转换为后端采集器使用的统一消息结构。"""

from datetime import date, datetime, time
from hashlib import sha256
from typing import Any
from zoneinfo import ZoneInfo

import pandas as pd


CLS_SYMBOLS = {"全部", "重点"}
SHANGHAI_ZONE = ZoneInfo("Asia/Shanghai")


def _json_value(value: Any) -> Any:
    """把 pandas 标量或日期时间转换为 JSON 可序列化的值。

    输入任意 DataFrame 单元格值；输出字符串、数字、布尔值或 ``None``。
    """
    if value is None or pd.isna(value):
        return None
    if isinstance(value, (datetime, date, time)):
        return value.isoformat()
    if hasattr(value, "item"):
        return value.item()
    return value


def _published_at(date_value: Any, time_value: Any) -> str | None:
    """合并财联社分开的发布日期和发布时间。

    输入日期值和时间值；输出带上海时区的 ISO 时间字符串，无法解析时返回 ``None``。
    """
    if date_value is None or time_value is None:
        return None
    parsed = pd.to_datetime(
        f"{_json_value(date_value)} {_json_value(time_value)}", errors="coerce"
    )
    if pd.isna(parsed):
        return None
    timestamp = parsed.to_pydatetime()
    if timestamp.tzinfo is None:
        timestamp = timestamp.replace(tzinfo=SHANGHAI_ZONE)
    else:
        timestamp = timestamp.astimezone(SHANGHAI_ZONE)
    return timestamp.isoformat()


def _timestamp_at(value: Any) -> str | None:
    """规范化东方财富或同花顺的发布时间。

    输入上游时间值；输出带上海时区的 ISO 时间字符串，无法解析时返回 ``None``。
    """
    if value is None:
        return None
    parsed = pd.to_datetime(value, errors="coerce")
    if pd.isna(parsed):
        return None
    timestamp = parsed.to_pydatetime()
    if timestamp.tzinfo is None:
        timestamp = timestamp.replace(tzinfo=SHANGHAI_ZONE)
    else:
        timestamp = timestamp.astimezone(SHANGHAI_ZONE)
    return timestamp.isoformat()


def _external_id(title: str, content: str, published_at: str | None) -> str:
    """为没有上游消息 ID 的财联社消息生成稳定标识。

    输入标题、正文和发布时间；输出一个 SHA-256 十六进制字符串。
    """
    identity = f"cls-news\n{published_at or ''}\n{title}\n{content}"
    return sha256(identity.encode("utf-8")).hexdigest()


def _cls_row(row: pd.Series) -> dict[str, Any]:
    """把一行财联社数据转换为统一消息。

    输入 AkShare 返回的一行 ``Series``；输出包含标题、正文、发布时间、稳定 ID
    和原始字段的字典。
    """
    raw = {str(column): _json_value(value) for column, value in row.items()}
    title = str(raw.get("标题") or "").strip()
    content = str(raw.get("内容") or "").strip()
    published_at = _published_at(row.get("发布日期"), row.get("发布时间"))
    return {
        "externalId": _external_id(title, content, published_at),
        "title": title,
        "content": content,
        "url": None,
        "publishedAt": published_at,
        "raw": raw,
    }


def _linked_news_row(
    row: pd.Series, source_id: str, content_column: str
) -> dict[str, Any]:
    """把一行带原文链接的资讯转换为统一消息。

    输入上游行、稳定来源 ID 和正文字段名；输出包含标题、正文、链接、发布时间、
    稳定 ID 和原始字段的字典。
    """
    raw = {str(column): _json_value(value) for column, value in row.items()}
    title = str(raw.get("标题") or "").strip()
    content = str(raw.get(content_column) or "").strip()
    url = str(raw.get("链接") or "").strip() or None
    published_at = _timestamp_at(row.get("发布时间"))
    identity = f"{source_id}\n{url or ''}\n{published_at or ''}\n{title}\n{content}"
    return {
        "externalId": sha256(identity.encode("utf-8")).hexdigest(),
        "title": title,
        "content": content,
        "url": url,
        "publishedAt": published_at,
        "raw": raw,
    }


def _news_payload(
    frame: pd.DataFrame,
    source_id: str,
    source_name: str,
    content_column: str,
    limit: int,
) -> dict[str, Any]:
    """校验并组装东方财富或同花顺的资讯响应。

    输入上游 DataFrame、来源信息、正文字段名和返回上限；输出按发布时间倒序排列的
    统一资讯响应。字段缺失或 limit 越界时抛出异常。
    """
    if limit < 1 or limit > 500:
        raise ValueError("limit 必须在 1 到 500 之间。")
    required = {"标题", content_column, "发布时间", "链接"}
    missing = required.difference(frame.columns)
    if missing:
        raise RuntimeError(
            f"AkShare {source_name}接口缺少字段：{', '.join(sorted(missing))}"
        )
    rows = [
        _linked_news_row(row, source_id, content_column)
        for _, row in frame.iterrows()
    ]
    rows.sort(key=lambda item: item["publishedAt"] or "", reverse=True)
    return {
        "sourceId": source_id,
        "sourceName": source_name,
        "fetchedAt": datetime.now(SHANGHAI_ZONE).isoformat(timespec="seconds"),
        "rows": rows[:limit],
    }


def fetch_cls_news_sync(symbol: str = "全部", limit: int = 100) -> dict[str, Any]:
    """同步获取财联社最新电报，不执行事件分类。

    输入 ``symbol``（``全部`` 或 ``重点``）和返回条数 ``limit``；输出包含来源信息、
    抓取时间和统一消息列表的字典。
    """
    category = symbol.strip()
    if category not in CLS_SYMBOLS:
        raise ValueError("财联社资讯类型仅支持：全部、重点。")
    if limit < 1 or limit > 500:
        raise ValueError("limit 必须在 1 到 500 之间。")

    import akshare as ak

    frame = ak.stock_info_global_cls(symbol=category)
    required = {"标题", "内容", "发布日期", "发布时间"}
    missing = required.difference(frame.columns)
    if missing:
        raise RuntimeError(f"AkShare 财联社接口缺少字段：{', '.join(sorted(missing))}")

    rows = [_cls_row(row) for _, row in frame.iterrows()]
    rows.sort(key=lambda item: item["publishedAt"] or "", reverse=True)
    return {
        "sourceId": "cls-news",
        "sourceName": "财联社电报",
        "category": category,
        "fetchedAt": datetime.now(SHANGHAI_ZONE).isoformat(timespec="seconds"),
        "rows": rows[:limit],
    }


def fetch_eastmoney_news_sync(limit: int = 100) -> dict[str, Any]:
    """同步获取东方财富全球财经快讯，不执行事件分类。

    输入返回条数 ``limit``；输出包含来源信息、抓取时间和统一消息列表的字典。
    """
    import akshare as ak

    frame = ak.stock_info_global_em()
    return _news_payload(
        frame, "eastmoney-news", "东方财富全球财经快讯", "摘要", limit
    )


def fetch_ths_news_sync(limit: int = 100) -> dict[str, Any]:
    """同步获取同花顺全球财经直播，不执行事件分类。

    输入返回条数 ``limit``；输出包含来源信息、抓取时间和统一消息列表的字典。
    """
    import akshare as ak

    frame = ak.stock_info_global_ths()
    return _news_payload(frame, "ths-news", "同花顺全球财经直播", "内容", limit)
