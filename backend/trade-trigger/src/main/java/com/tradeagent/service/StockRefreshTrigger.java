package com.tradeagent.service;

import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled trigger that refreshes active stock K-line payloads. */
@Component
public class StockRefreshTrigger {
    private final StockService stockService;

    /** Create the trigger with the domain stock workflow service. */
    public StockRefreshTrigger(StockService stockService) {
        this.stockService = stockService;
    }

    /** Refresh every active stock on the configured interval. */
    @Scheduled(fixedDelayString = "${tradeagent.refresh-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public void refreshAll() {
        stockService.refreshAll();
    }
}
