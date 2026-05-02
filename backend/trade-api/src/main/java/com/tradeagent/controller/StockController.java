package com.tradeagent.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.tradeagent.dto.KlinePayload;
import com.tradeagent.service.StockService;

/** Stock data HTTP routes. */
@RestController
public class StockController {
    private final StockService stockService;

    /** Create the controller with stock data workflow access. */
    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    /** Resolve a query and return the latest cached or freshly fetched K-line data. */
    @GetMapping("/api/stocks/{query}/kline")
    public KlinePayload getKline(@PathVariable String query) {
        return stockService.getKline(URLDecoder.decode(query, StandardCharsets.UTF_8));
    }
}
