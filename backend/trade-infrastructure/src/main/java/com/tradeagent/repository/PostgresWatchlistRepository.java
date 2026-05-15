package com.tradeagent.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.tradeagent.config.AppProperties;
import com.tradeagent.domain.repository.WatchlistRepository;
import com.tradeagent.dto.WatchlistItem;
import com.tradeagent.repository.mapper.WatchlistMapper;
import com.tradeagent.util.StockText;

/** PostgreSQL repository for the stock watchlist. */
@Repository
public class PostgresWatchlistRepository implements WatchlistRepository {
    private final WatchlistMapper watchlistMapper;
    private final AppProperties properties;

    /** Create the repository with MyBatis watchlist access and app defaults. */
    public PostgresWatchlistRepository(WatchlistMapper watchlistMapper, AppProperties properties) {
        this.watchlistMapper = watchlistMapper;
        this.properties = properties;
    }

    /** Create watchlist storage and seed the default stock when absent. */
    @Override
    public void init() {
        watchlistMapper.createWatchlistTable();
        watchlistMapper.ensureTimestampDefaults();
        watchlistMapper.insertWatchlistIfAbsent(StockText.normalizeSymbol(properties.defaultSymbol()), "");
    }

    /** Fetch all watchlist entries in stable display order. */
    @Override
    public List<WatchlistItem> list() {
        return watchlistMapper.listWatchlist();
    }

    /** Insert or update a watchlist entry and return the saved row shape. */
    @Override
    public WatchlistItem upsert(String symbol, String name) {
        String normalized = StockText.normalizeSymbol(symbol);
        String cleanName = name == null ? "" : name;
        return watchlistMapper.upsertWatchlist(normalized, cleanName);
    }

    /** Delete a normalized stock symbol from the watchlist. */
    @Override
    public void delete(String symbol) {
        watchlistMapper.deleteWatchlist(StockText.normalizeSymbol(symbol));
    }
}
