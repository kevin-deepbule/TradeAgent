package com.tradeagent.dto;

/** Persisted stock item shown in the watchlist panel. */
public class WatchlistItem {
    public String symbol = "";
    public String name = "";
    public String created_at = "";

    /** Create an empty item for JSON binding. */
    public WatchlistItem() {
    }

    /** Create a watchlist item from stored values. */
    public WatchlistItem(String symbol, String name, String createdAt) {
        this.symbol = symbol;
        this.name = name;
        this.created_at = createdAt;
    }
}

