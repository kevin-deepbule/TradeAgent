"""Small AkShare demo for stock data."""

import akshare as ak


def fetch_a_share_history(symbol: str = "000001"):
    """Fetch A-share historical daily data from Eastmoney via AkShare."""
    return ak.stock_zh_a_hist(
        symbol=symbol,
        period="daily",
        start_date="20240101",
        end_date="20240430",
        adjust="qfq",
    )


def fetch_a_share_spot():
    """Fetch current A-share spot quotes from Eastmoney via AkShare."""
    return ak.stock_zh_a_spot_em()


if __name__ == "__main__":
    history_df = fetch_a_share_history("000001")
    print(history_df.head())

    spot_df = fetch_a_share_spot()
    print(spot_df.head())
