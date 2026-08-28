package com.tradeagent.watchlist.startup;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.tradeagent.watchlist.service.WatchlistService;

/** Initialize persistent storage and warm active symbols on startup. */
@Component
public class WatchlistStartup implements ApplicationRunner {
    private final WatchlistService watchlistService;

    /** Create the startup hook with the watchlist workflow. */
    public WatchlistStartup(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /** Initialize database tables and load persisted symbols into the live cache. */
    @Override
    public void run(ApplicationArguments args) {
        watchlistService.initialize();
    }
}
