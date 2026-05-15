package com.tradeagent.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.tradeagent.dto.KlinePayload;

/** Public stock data API contract for K-line queries. */
public interface StockApi {
    /** Resolve a query and return the latest cached or freshly fetched K-line data. */
    @GetMapping("/api/stocks/{query}/kline")
    KlinePayload getKline(@PathVariable String query);
}
