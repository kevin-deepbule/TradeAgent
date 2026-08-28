package com.tradeagent.watchlist.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tradeagent.watchlist.dto.WatchlistItem;
import com.tradeagent.watchlist.service.WatchlistService;

/** Watchlist HTTP routes for listing, adding, and removing stocks. */
@RestController
public class WatchlistController {
    private final WatchlistService watchlistService;

    /** Create the controller with the watchlist workflow. */
    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    /** Return all persisted watchlist entries in display order. */
    @GetMapping("/api/watchlist")
    public List<WatchlistItem> listWatchlist() {
        return watchlistService.listWatchlist();
    }

    /** Resolve a query and persist the stock into the watchlist. */
    @PostMapping("/api/watchlist")
    public WatchlistItem addWatchlist(@RequestBody Map<String, String> item) {
        String query = firstNonBlank(item.get("query"), item.get("symbol"));
        return watchlistService.addWatchlist(query);
    }

    /** Remove a stock from both persisted watchlist and live cache. */
    @DeleteMapping("/api/watchlist/{symbol}")
    public Map<String, Boolean> deleteWatchlist(@PathVariable String symbol) {
        watchlistService.deleteWatchlist(symbol);
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
