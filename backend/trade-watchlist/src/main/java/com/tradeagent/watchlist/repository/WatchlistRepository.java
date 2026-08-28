package com.tradeagent.watchlist.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.tradeagent.market.util.StockSymbols;
import com.tradeagent.watchlist.config.WatchlistProperties;
import com.tradeagent.watchlist.dto.WatchlistItem;
import com.tradeagent.watchlist.repository.mapper.WatchlistMapper;

/** PostgreSQL repository for the stock watchlist. */
@Repository
public class WatchlistRepository {
    private final WatchlistMapper watchlistMapper;
    private final WatchlistProperties properties;

    /** Create the repository with MyBatis watchlist access and app defaults. */
    public WatchlistRepository(WatchlistMapper watchlistMapper, WatchlistProperties properties) {
        this.watchlistMapper = watchlistMapper;
        this.properties = properties;
    }

    /** Create watchlist storage and seed the default stock when absent. */
    public void init() {
        watchlistMapper.createWatchlistTable();
        watchlistMapper.ensureTimestampDefaults();
        watchlistMapper.insertWatchlistIfAbsent(StockSymbols.normalizeSymbol(properties.defaultSymbol()), "");
    }

    /** Fetch all watchlist entries in stable display order. */
    public List<WatchlistItem> list() {
        return watchlistMapper.listWatchlist();
    }

    /** Insert or update a watchlist entry and return the saved row shape. */
    public WatchlistItem upsert(String symbol, String name) {
        String normalized = StockSymbols.normalizeSymbol(symbol);
        String cleanName = name == null ? "" : name;
        return watchlistMapper.upsertWatchlist(normalized, cleanName);
    }

    /** Delete a normalized stock symbol from the watchlist. */
    public void delete(String symbol) {
        watchlistMapper.deleteWatchlist(StockSymbols.normalizeSymbol(symbol));
    }
}
