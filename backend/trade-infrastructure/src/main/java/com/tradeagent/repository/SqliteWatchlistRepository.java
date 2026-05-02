package com.tradeagent.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.tradeagent.config.AppProperties;
import com.tradeagent.domain.repository.WatchlistRepository;
import com.tradeagent.dto.WatchlistItem;
import com.tradeagent.util.StockText;
import com.tradeagent.util.TimeUtil;

/** SQLite repository for the stock watchlist. */
@Repository
public class SqliteWatchlistRepository implements WatchlistRepository {
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    /** Create the repository with JDBC access and app defaults. */
    public SqliteWatchlistRepository(JdbcTemplate jdbcTemplate, AppProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /** Create watchlist storage and seed the default stock when absent. */
    @Override
    public void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS watchlist (
                    symbol TEXT PRIMARY KEY,
                    name TEXT NOT NULL DEFAULT '',
                    created_at TEXT NOT NULL
                )
                """);
        jdbcTemplate.update("""
                INSERT OR IGNORE INTO watchlist (symbol, name, created_at)
                VALUES (?, ?, ?)
                """, StockText.normalizeSymbol(properties.defaultSymbol()), "", TimeUtil.nowIsoSeconds());
    }

    /** Fetch all watchlist entries in stable display order. */
    @Override
    public List<WatchlistItem> list() {
        return jdbcTemplate.query("""
                SELECT symbol, name, created_at
                FROM watchlist
                ORDER BY created_at ASC, symbol ASC
                """, (rs, rowNum) -> new WatchlistItem(
                rs.getString("symbol"),
                rs.getString("name"),
                rs.getString("created_at")));
    }

    /** Insert or update a watchlist entry and return the saved row shape. */
    @Override
    public WatchlistItem upsert(String symbol, String name) {
        String normalized = StockText.normalizeSymbol(symbol);
        String cleanName = name == null ? "" : name;
        String now = TimeUtil.nowIsoSeconds();
        jdbcTemplate.update("""
                INSERT INTO watchlist (symbol, name, created_at)
                VALUES (?, ?, ?)
                ON CONFLICT(symbol) DO UPDATE SET name = excluded.name
                """, normalized, cleanName, now);
        return new WatchlistItem(normalized, cleanName, now);
    }

    /** Delete a normalized stock symbol from the watchlist. */
    @Override
    public void delete(String symbol) {
        jdbcTemplate.update("DELETE FROM watchlist WHERE symbol = ?", StockText.normalizeSymbol(symbol));
    }
}
