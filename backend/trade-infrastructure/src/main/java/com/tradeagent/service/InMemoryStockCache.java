package com.tradeagent.service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.tradeagent.domain.cache.StockCache;
import com.tradeagent.dto.KlinePayload;

/** In-memory stock cache and active symbol registry. */
@Component
public class InMemoryStockCache implements StockCache {
    private final Map<String, String> names = new ConcurrentHashMap<>();
    private final Map<String, KlinePayload> payloads = new ConcurrentHashMap<>();

    /** Add or update an active symbol with its display name. */
    @Override
    public void addSymbol(String symbol, String name) {
        names.put(symbol, name == null ? "" : name);
    }

    /** Remove an active symbol and its cached payload. */
    @Override
    public void removeSymbol(String symbol) {
        names.remove(symbol);
        payloads.remove(symbol);
    }

    /** Return a snapshot of all active symbols. */
    @Override
    public Set<String> symbols() {
        return new LinkedHashSet<>(names.keySet());
    }

    /** Return a cached display name for a symbol. */
    @Override
    public String name(String symbol) {
        return names.getOrDefault(symbol, "");
    }

    /** Return a cached K-line payload for a symbol. */
    @Override
    public KlinePayload get(String symbol) {
        return payloads.get(symbol);
    }

    /** Store the latest K-line payload for a symbol. */
    @Override
    public void save(String symbol, KlinePayload payload) {
        payloads.put(symbol, payload);
    }
}
