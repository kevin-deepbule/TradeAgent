# Project Instructions

This project uses AkShare for stock-market data.

Primary documentation:
- https://akshare.akfamily.xyz/data/stock/stock.html

When implementing stock data features:
- Prefer `akshare` Python APIs over hand-written scraping.
- Check the AkShare documentation for the exact function name and parameters before using an API.
- Keep data-fetching code small and explicit, because AkShare upstream interfaces can change.
- Return or persist `pandas.DataFrame` values without changing column names unless the caller asks for normalized fields.
- For A-share historical quotes, start with `ak.stock_zh_a_hist`.
- For A-share realtime spot quotes, start with `ak.stock_zh_a_spot_em`.

