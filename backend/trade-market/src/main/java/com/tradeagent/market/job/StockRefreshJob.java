package com.tradeagent.market.job;

import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tradeagent.market.service.StockService;

/** Scheduled job that refreshes active stock K-line payloads. */
@Component
public class StockRefreshJob {
    private final StockService stockService;

    /** Create the job with the market workflow service. */
    public StockRefreshJob(StockService stockService) {
        this.stockService = stockService;
    }

    /** Refresh every active stock on the configured interval. */
    @Scheduled(fixedDelayString = "${tradeagent.market.refresh-seconds:60}", timeUnit = TimeUnit.SECONDS)
    public void refreshAll() {
        stockService.refreshAll();
    }
}
