package com.tradeagent.domain.client;

import com.tradeagent.dto.KlinePayload;
import com.tradeagent.dto.StockIdentity;

/** Port for resolving stocks and fetching K-line data from an external market data source. */
public interface StockDataClient {
    /** Resolve a stock code or name into canonical stock identity data. */
    StockIdentity resolveStock(String query);

    /** Fetch K-line rows for one normalized stock symbol. */
    KlinePayload fetchKline(String symbol, String name);
}
