package com.tradeagent.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tradeagent.dto.StockIdentity;
import com.tradeagent.dto.WatchlistItem;
import com.tradeagent.repository.WatchlistRepository;
import com.tradeagent.service.StockService;
import com.tradeagent.util.StockText;

/** Watchlist HTTP routes for listing, adding, and removing stocks. */
@RestController
public class WatchlistController {
    private final WatchlistRepository watchlistRepository;
    private final StockService stockService;

    /** Create the controller with persistence and stock lookup services. */
    public WatchlistController(WatchlistRepository watchlistRepository, StockService stockService) {
        this.watchlistRepository = watchlistRepository;
        this.stockService = stockService;
    }

    /** Return all persisted watchlist entries in display order. */
    @GetMapping("/api/watchlist")
    public List<WatchlistItem> listWatchlist() {
        return watchlistRepository.list();
    }

    /** Resolve a query and persist the stock into the watchlist. */
    @PostMapping("/api/watchlist")
    public WatchlistItem addWatchlist(@RequestBody Map<String, String> item) {
        String query = firstNonBlank(item.get("query"), item.get("symbol"));
        StockIdentity resolved = stockService.resolveStock(query);
        WatchlistItem saved = watchlistRepository.upsert(resolved.symbol, resolved.name);
        stockService.cache().addSymbol(saved.symbol, saved.name);
        return saved;
    }

    /** Remove a stock from both persisted watchlist and live cache. */
    @DeleteMapping("/api/watchlist/{symbol}")
    public Map<String, Boolean> deleteWatchlist(@PathVariable String symbol) {
        String normalized = StockText.normalizeSymbol(symbol);
        watchlistRepository.delete(normalized);
        stockService.cache().removeSymbol(normalized);
        return Map.of("ok", true);
    }

    /** Return the first non-blank request value. */
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }
}

