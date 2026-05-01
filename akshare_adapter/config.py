"""Configuration values for the internal AkShare adapter service."""

import os


ADAPTER_HOST = os.getenv("AKSHARE_ADAPTER_HOST", "127.0.0.1")
ADAPTER_PORT = int(os.getenv("AKSHARE_ADAPTER_PORT", "8002"))
KLINE_DISPLAY_YEARS = int(os.getenv("KLINE_DISPLAY_YEARS", "5"))
KLINE_MA_WARMUP_DAYS = int(os.getenv("KLINE_MA_WARMUP_DAYS", "120"))

