package com.tradeagent.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.tradeagent.config.AppProperties;
import com.tradeagent.domain.repository.WatchlistRepository;
import com.tradeagent.dto.WatchlistItem;
import com.tradeagent.util.StockText;

/** PostgreSQL repository for the stock watchlist. */
@Repository
public class PostgresWatchlistRepository implements WatchlistRepository {
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    /** Create the repository with JDBC access and app defaults. */
    public PostgresWatchlistRepository(JdbcTemplate jdbcTemplate, AppProperties properties) {
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
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);
        ensureTimestampDefaults();
        jdbcTemplate.update("""
                INSERT INTO watchlist (symbol, name)
                VALUES (?, ?)
                ON CONFLICT(symbol) DO NOTHING
                """, StockText.normalizeSymbol(properties.defaultSymbol()), "");
    }

    /** Fetch all watchlist entries in stable display order. */
    @Override
    public List<WatchlistItem> list() {
        return jdbcTemplate.query("""
                SELECT symbol, name, created_at
                FROM watchlist
                ORDER BY created_at ASC, symbol ASC
                """, (rs, rowNum) -> mapWatchlistItem(rs));
    }

    /** Insert or update a watchlist entry and return the saved row shape. */
    @Override
    public WatchlistItem upsert(String symbol, String name) {
        String normalized = StockText.normalizeSymbol(symbol);
        String cleanName = name == null ? "" : name;
        return jdbcTemplate.queryForObject("""
                INSERT INTO watchlist (symbol, name)
                VALUES (?, ?)
                ON CONFLICT(symbol) DO UPDATE SET name = excluded.name
                RETURNING symbol, name, created_at
                """, (rs, rowNum) -> mapWatchlistItem(rs), normalized, cleanName);
    }

    /** Delete a normalized stock symbol from the watchlist. */
    @Override
    public void delete(String symbol) {
        jdbcTemplate.update("DELETE FROM watchlist WHERE symbol = ?", StockText.normalizeSymbol(symbol));
    }

    /** Ensure existing PostgreSQL tables use database-managed creation time. */
    private void ensureTimestampDefaults() {
        jdbcTemplate.execute("""
                ALTER TABLE watchlist
                    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at::timestamptz,
                    ALTER COLUMN created_at SET DEFAULT now()
                """);
    }

    /** Convert database timestamp values into the API's existing string field. */
    private WatchlistItem mapWatchlistItem(ResultSet rs) throws SQLException {
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        return new WatchlistItem(
                rs.getString("symbol"),
                rs.getString("name"),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(createdAt));
    }
}
