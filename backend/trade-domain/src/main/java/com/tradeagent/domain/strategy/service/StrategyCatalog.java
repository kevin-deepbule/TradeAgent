package com.tradeagent.domain.strategy.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory catalog for locating strategy-domain implementations by stable identifier. */
public class StrategyCatalog {
    private final Map<String, TradingStrategy> strategies = new LinkedHashMap<>();

    /** Register one strategy implementation for later lookup. */
    public void register(TradingStrategy strategy) {
        if (strategy == null || strategy.id() == null || strategy.id().isBlank()) {
            return;
        }
        strategies.put(strategy.id(), strategy);
    }

    /** Find a strategy implementation by its stable identifier. */
    public Optional<TradingStrategy> find(String strategyId) {
        return Optional.ofNullable(strategies.get(strategyId));
    }

    /** Return all registered strategy implementations in registration order. */
    public Collection<TradingStrategy> all() {
        return strategies.values();
    }
}
