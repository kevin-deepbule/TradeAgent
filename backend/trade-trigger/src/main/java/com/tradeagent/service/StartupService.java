package com.tradeagent.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.tradeagent.domain.cache.StockCache;
import com.tradeagent.domain.repository.SettingsRepository;
import com.tradeagent.domain.repository.WatchlistRepository;
import com.tradeagent.dto.StockIdentity;
import com.tradeagent.dto.WatchlistItem;

/** Initialize persistent storage and warm active symbols on startup. */
@Component
public class StartupService implements ApplicationRunner {
    private final WatchlistRepository watchlistRepository;
    private final SettingsRepository settingsRepository;
    private final StockCache stockCache;

    /** Create the startup service with persistence and cache dependencies. */
    public StartupService(WatchlistRepository watchlistRepository, SettingsRepository settingsRepository,
            StockCache stockCache) {
        this.watchlistRepository = watchlistRepository;
        this.settingsRepository = settingsRepository;
        this.stockCache = stockCache;
    }

    /** Initialize SQLite tables and load persisted symbols into the live cache. */
    @Override
    public void run(ApplicationArguments args) {
        watchlistRepository.init();
        settingsRepository.init();

        StockIdentity defaultStock = settingsRepository.getDefaultStock();
        stockCache.addSymbol(defaultStock.symbol, defaultStock.name);
        for (WatchlistItem item : watchlistRepository.list()) {
            stockCache.addSymbol(item.symbol, item.name);
        }
    }
}
