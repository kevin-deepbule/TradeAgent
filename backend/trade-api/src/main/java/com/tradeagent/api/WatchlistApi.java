package com.tradeagent.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.tradeagent.dto.WatchlistItem;

/** Public watchlist API contract for persisted stock selections. */
public interface WatchlistApi {
    /** Return all persisted watchlist entries in display order. */
    @GetMapping("/api/watchlist")
    List<WatchlistItem> listWatchlist();

    /** Resolve a query and persist the stock into the watchlist. */
    @PostMapping("/api/watchlist")
    WatchlistItem addWatchlist(@RequestBody Map<String, String> item);

    /** Remove a stock from both persisted watchlist and live cache. */
    @DeleteMapping("/api/watchlist/{symbol}")
    Map<String, Boolean> deleteWatchlist(@PathVariable String symbol);
}
