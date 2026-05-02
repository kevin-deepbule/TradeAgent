package com.tradeagent.domain.repository;

import java.util.List;

import com.tradeagent.dto.WatchlistItem;

/** Port for durable watchlist storage. */
public interface WatchlistRepository {
    /** Create watchlist storage and seed configured defaults when needed. */
    void init();

    /** Fetch all watchlist entries in stable display order. */
    List<WatchlistItem> list();

    /** Insert or update a watchlist entry and return the saved row shape. */
    WatchlistItem upsert(String symbol, String name);

    /** Delete a normalized stock symbol from the watchlist. */
    void delete(String symbol);
}
