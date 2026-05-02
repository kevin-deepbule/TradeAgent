package com.tradeagent.domain.repository;

import com.tradeagent.dto.StockIdentity;

/** Port for durable dashboard settings. */
public interface SettingsRepository {
    /** Create settings storage and seed configured defaults when needed. */
    void init();

    /** Read the persisted dashboard default stock. */
    StockIdentity getDefaultStock();

    /** Persist a stock as the dashboard default and return the saved value. */
    StockIdentity setDefaultStock(String symbol, String name);
}
