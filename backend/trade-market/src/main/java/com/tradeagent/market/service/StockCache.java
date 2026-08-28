package com.tradeagent.market.service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.tradeagent.market.dto.KlinePayload;

/** In-memory K-line cache and registry of stocks that need periodic refresh. */
@Component
public class StockCache {
    private final Map<String, String> names = new ConcurrentHashMap<>();
    private final Map<String, KlinePayload> payloads = new ConcurrentHashMap<>();

    /** Add or update an active symbol with its display name. */
    public void addSymbol(String symbol, String name) {
        names.put(symbol, name == null ? "" : name);
    }

    /** Remove an active symbol and its cached payload. */
    public void removeSymbol(String symbol) {
        names.remove(symbol);
        payloads.remove(symbol);
    }

    /** Return a snapshot of all active symbols. */
    public Set<String> symbols() {
        return new LinkedHashSet<>(names.keySet());
    }

    /** Return a cached display name for a symbol. */
    public String name(String symbol) {
        return names.getOrDefault(symbol, "");
    }

    /** Return a cached K-line payload for a symbol. */
    public KlinePayload get(String symbol) {
        return payloads.get(symbol);
    }

    /** Store the latest K-line payload for a symbol. */
    public void save(String symbol, KlinePayload payload) {
        payloads.put(symbol, payload);
    }
}
