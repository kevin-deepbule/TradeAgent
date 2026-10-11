"""Tests for deterministic news shaping without calling upstream services."""

import sys
import unittest
from datetime import date, time
from types import SimpleNamespace
from unittest.mock import patch

import pandas as pd

from akshare_adapter.news_adapter import (
    fetch_cls_news_sync,
    fetch_eastmoney_news_sync,
    fetch_ths_news_sync,
)


class NewsAdapterTest(unittest.TestCase):
    """Verify CLS validation, ordering, raw fields, and stable identifiers."""

    def test_fetch_cls_news_shapes_and_orders_rows(self) -> None:
        """Normalize CLS messages while retaining the original Chinese fields."""
        frame = pd.DataFrame(
            [
                {"标题": "较早消息", "内容": "正文一", "发布日期": date(2026, 10, 11), "发布时间": time(9, 0)},
                {"标题": "较新消息", "内容": "正文二", "发布日期": date(2026, 10, 11), "发布时间": time(10, 30)},
            ]
        )
        fake_akshare = SimpleNamespace(stock_info_global_cls=lambda symbol: frame)

        with patch.dict(sys.modules, {"akshare": fake_akshare}):
            first = fetch_cls_news_sync("全部", 1)
            second = fetch_cls_news_sync("全部", 2)

        self.assertEqual("cls-news", first["sourceId"])
        self.assertEqual("较新消息", first["rows"][0]["title"])
        self.assertEqual("2026-10-11T10:30:00+08:00", first["rows"][0]["publishedAt"])
        self.assertEqual("较新消息", first["rows"][0]["raw"]["标题"])
        self.assertEqual(first["rows"][0]["externalId"], second["rows"][0]["externalId"])

    def test_rejects_unsupported_category(self) -> None:
        """Reject categories that AkShare's CLS function does not support."""
        with self.assertRaisesRegex(ValueError, "全部、重点"):
            fetch_cls_news_sync("公司", 20)

    def test_rejects_invalid_limit(self) -> None:
        """Bound response sizes before invoking the upstream API."""
        with self.assertRaisesRegex(ValueError, "1 到 500"):
            fetch_cls_news_sync("全部", 0)

    def test_fetch_eastmoney_news_preserves_summary_and_link(self) -> None:
        """Map Eastmoney summaries and links into the shared news contract."""
        frame = pd.DataFrame(
            [{"标题": "东财标题", "摘要": "东财摘要", "发布时间": "2026-10-11 12:30:00", "链接": "https://example.com/em"}]
        )
        fake_akshare = SimpleNamespace(stock_info_global_em=lambda: frame)

        with patch.dict(sys.modules, {"akshare": fake_akshare}):
            result = fetch_eastmoney_news_sync(10)

        self.assertEqual("eastmoney-news", result["sourceId"])
        self.assertEqual("东财摘要", result["rows"][0]["content"])
        self.assertEqual("https://example.com/em", result["rows"][0]["url"])
        self.assertEqual("2026-10-11T12:30:00+08:00", result["rows"][0]["publishedAt"])

    def test_fetch_ths_news_preserves_content_and_link(self) -> None:
        """Map 10jqka content and links into the shared news contract."""
        frame = pd.DataFrame(
            [{"标题": "同花顺标题", "内容": "同花顺正文", "发布时间": "2026-10-11 12:40:00", "链接": "https://example.com/ths"}]
        )
        fake_akshare = SimpleNamespace(stock_info_global_ths=lambda: frame)

        with patch.dict(sys.modules, {"akshare": fake_akshare}):
            result = fetch_ths_news_sync(10)

        self.assertEqual("ths-news", result["sourceId"])
        self.assertEqual("同花顺正文", result["rows"][0]["content"])
        self.assertEqual("https://example.com/ths", result["rows"][0]["url"])
        self.assertEqual("同花顺正文", result["rows"][0]["raw"]["内容"])


if __name__ == "__main__":
    unittest.main()
