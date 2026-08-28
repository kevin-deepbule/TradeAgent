package com.tradeagent.watchlist.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tradeagent.market.dto.StockIdentity;
import com.tradeagent.market.service.StockService;
import com.tradeagent.watchlist.dto.WatchlistItem;
import com.tradeagent.watchlist.repository.SettingsRepository;
import com.tradeagent.watchlist.repository.WatchlistRepository;

/** Coordinates dashboard defaults, watchlist persistence, and active market symbols. */
@Service
public class WatchlistService {
    private final SettingsRepository settingsRepository;
    private final WatchlistRepository watchlistRepository;
    private final StockService stockService;

    /** Create the watchlist workflow with its local repositories and market-module service. */
    public WatchlistService(SettingsRepository settingsRepository, WatchlistRepository watchlistRepository,
            StockService stockService) {
        this.settingsRepository = settingsRepository;
        this.watchlistRepository = watchlistRepository;
        this.stockService = stockService;
    }

    /** Initialize watchlist storage and register persisted stocks for market refresh. */
    public void initialize() {
        watchlistRepository.init();
        settingsRepository.init();

        StockIdentity defaultStock = settingsRepository.getDefaultStock();
        stockService.activate(defaultStock.symbol, defaultStock.name);
        for (WatchlistItem item : watchlistRepository.list()) {
            stockService.activate(item.symbol, item.name);
        }
    }

    /** Return the persisted default dashboard stock. */
    public StockIdentity getDefaultStock() {
        return settingsRepository.getDefaultStock();
    }

    /** Resolve and persist the default dashboard stock. */
    public StockIdentity setDefaultStock(String query) {
        StockIdentity resolved = stockService.resolveStock(query);
        StockIdentity saved = settingsRepository.setDefaultStock(resolved.symbol, resolved.name);
        stockService.activate(saved.symbol, saved.name);
        return saved;
    }

    /** Return watchlist entries in their persisted display order. */
    public List<WatchlistItem> listWatchlist() {
        return watchlistRepository.list();
    }

    /** Resolve and add one stock to the persisted watchlist. */
    public WatchlistItem addWatchlist(String query) {
        StockIdentity resolved = stockService.resolveStock(query);
        WatchlistItem saved = watchlistRepository.upsert(resolved.symbol, resolved.name);
        stockService.activate(saved.symbol, saved.name);
        return saved;
    }

    /** Delete one watchlist stock and remove its market cache entry. */
    public void deleteWatchlist(String symbol) {
        watchlistRepository.delete(symbol);
        stockService.deactivate(symbol);
    }
}
