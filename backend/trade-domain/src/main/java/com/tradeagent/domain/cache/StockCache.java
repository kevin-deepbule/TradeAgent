package com.tradeagent.domain.cache;

import java.util.Set;

import com.tradeagent.dto.KlinePayload;

/** Port for active stock registration and cached K-line payload storage. */
public interface StockCache {
    /** Add or update an active symbol with its display name. */
    void addSymbol(String symbol, String name);

    /** Remove an active symbol and its cached payload. */
    void removeSymbol(String symbol);

    /** Return a snapshot of all active symbols. */
    Set<String> symbols();

    /** Return a cached display name for a symbol. */
    String name(String symbol);

    /** Return a cached K-line payload for a symbol. */
    KlinePayload get(String symbol);

    /** Store the latest K-line payload for a symbol. */
    void save(String symbol, KlinePayload payload);
}
