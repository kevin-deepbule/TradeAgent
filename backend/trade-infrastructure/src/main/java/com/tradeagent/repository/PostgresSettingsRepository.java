package com.tradeagent.repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.tradeagent.config.AppProperties;
import com.tradeagent.domain.repository.SettingsRepository;
import com.tradeagent.dto.StockIdentity;
import com.tradeagent.repository.mapper.SettingsMapper;
import com.tradeagent.util.StockText;

/** PostgreSQL repository for durable dashboard settings. */
@Repository
public class PostgresSettingsRepository implements SettingsRepository {
    private static final String DEFAULT_STOCK_SYMBOL_KEY = "default_stock_symbol";
    private static final String DEFAULT_STOCK_NAME_KEY = "default_stock_name";

    private final SettingsMapper settingsMapper;
    private final AppProperties properties;

    /** Create the repository with MyBatis settings access and app defaults. */
    public PostgresSettingsRepository(SettingsMapper settingsMapper, AppProperties properties) {
        this.settingsMapper = settingsMapper;
        this.properties = properties;
    }

    /** Create settings storage and seed the configured default stock. */
    @Override
    public void init() {
        settingsMapper.createSettingsTable();
        settingsMapper.ensureTimestampDefaults();
        settingsMapper.insertSettingIfAbsent(DEFAULT_STOCK_SYMBOL_KEY, StockText.normalizeSymbol(properties.defaultSymbol()));
        settingsMapper.insertSettingIfAbsent(DEFAULT_STOCK_NAME_KEY, "");
    }

    /** Read the persisted default stock from settings storage. */
    @Override
    public StockIdentity getDefaultStock() {
        List<Map<String, Object>> rows = settingsMapper.selectSettings(
                List.of(DEFAULT_STOCK_SYMBOL_KEY, DEFAULT_STOCK_NAME_KEY));
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
        settingsMapper.upsertSetting(DEFAULT_STOCK_SYMBOL_KEY, normalized);
        settingsMapper.upsertSetting(DEFAULT_STOCK_NAME_KEY, cleanName);
        return new StockIdentity(normalized, cleanName, "");
    }
}
