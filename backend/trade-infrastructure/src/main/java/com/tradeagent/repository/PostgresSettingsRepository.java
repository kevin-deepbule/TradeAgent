package com.tradeagent.repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.tradeagent.config.AppProperties;
import com.tradeagent.domain.repository.SettingsRepository;
import com.tradeagent.dto.StockIdentity;
import com.tradeagent.util.StockText;

/** PostgreSQL repository for durable dashboard settings. */
@Repository
public class PostgresSettingsRepository implements SettingsRepository {
    private static final String DEFAULT_STOCK_SYMBOL_KEY = "default_stock_symbol";
    private static final String DEFAULT_STOCK_NAME_KEY = "default_stock_name";

    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    /** Create the repository with JDBC access and app defaults. */
    public PostgresSettingsRepository(JdbcTemplate jdbcTemplate, AppProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /** Create settings storage and seed the configured default stock. */
    @Override
    public void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL DEFAULT '',
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);
        ensureTimestampDefaults();
        jdbcTemplate.update("""
                INSERT INTO app_settings (key, value)
                VALUES (?, ?)
                ON CONFLICT(key) DO NOTHING
                """, DEFAULT_STOCK_SYMBOL_KEY, StockText.normalizeSymbol(properties.defaultSymbol()));
        jdbcTemplate.update("""
                INSERT INTO app_settings (key, value)
                VALUES (?, ?)
                ON CONFLICT(key) DO NOTHING
                """, DEFAULT_STOCK_NAME_KEY, "");
    }

    /** Read the persisted default stock from settings storage. */
    @Override
    public StockIdentity getDefaultStock() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT key, value
                FROM app_settings
                WHERE key IN (?, ?)
                """, DEFAULT_STOCK_SYMBOL_KEY, DEFAULT_STOCK_NAME_KEY);
        Map<String, String> values = rows.stream()
                .collect(Collectors.toMap(row -> String.valueOf(row.get("key")), row -> String.valueOf(row.get("value"))));
        String symbol = values.getOrDefault(DEFAULT_STOCK_SYMBOL_KEY, properties.defaultSymbol());
        String name = values.getOrDefault(DEFAULT_STOCK_NAME_KEY, "");
        return new StockIdentity(StockText.normalizeSymbol(symbol), name, "");
    }

    /** Persist a stock as the dashboard default and return the saved value. */
    @Override
    public StockIdentity setDefaultStock(String symbol, String name) {
        String normalized = StockText.normalizeSymbol(symbol);
        String cleanName = name == null ? "" : name;
        jdbcTemplate.update("""
                INSERT INTO app_settings (key, value)
                VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET
                    value = excluded.value,
                    updated_at = now()
                """, DEFAULT_STOCK_SYMBOL_KEY, normalized);
        jdbcTemplate.update("""
                INSERT INTO app_settings (key, value)
                VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET
                    value = excluded.value,
                    updated_at = now()
                """, DEFAULT_STOCK_NAME_KEY, cleanName);
        return new StockIdentity(normalized, cleanName, "");
    }

    /** Ensure existing PostgreSQL tables use database-managed update time. */
    private void ensureTimestampDefaults() {
        jdbcTemplate.execute("""
                ALTER TABLE app_settings
                    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at::timestamptz,
                    ALTER COLUMN updated_at SET DEFAULT now()
                """);
    }
}
