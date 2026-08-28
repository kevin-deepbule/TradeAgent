package com.tradeagent.market.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.tradeagent.market.client.AkShareAdapterClient;
import com.tradeagent.market.dto.KlinePayload;
import com.tradeagent.market.dto.StockIdentity;
import com.tradeagent.market.util.StockSymbols;
import com.tradeagent.market.util.TimeUtil;

/** Stock query resolution, adapter fetching, cache refresh, and advice workflow. */
@Service
public class StockService {
    private final AkShareAdapterClient akShareAdapterClient;
    private final AdviceService adviceService;
    private final StockCache stockCache;
    private final Map<String, Object> refreshLocks = new ConcurrentHashMap<>();

    /** Create the stock service with its AkShare client, advice calculator, and local cache. */
    public StockService(AkShareAdapterClient akShareAdapterClient, AdviceService adviceService, StockCache stockCache) {
        this.akShareAdapterClient = akShareAdapterClient;
        this.adviceService = adviceService;
        this.stockCache = stockCache;
    }

    /** Resolve a user-entered stock code or name into symbol metadata. */
    public StockIdentity resolveStock(String query) {
        String cleanQuery = query == null ? "" : query.trim();
        if (StockSymbols.isSymbolQuery(cleanQuery)) {
            String symbol = StockSymbols.normalizeSymbol(cleanQuery);
            try {
                StockIdentity resolved = akShareAdapterClient.resolveStock(cleanQuery);
                return resolved == null ? new StockIdentity(symbol, "", cleanQuery) : resolved;
            } catch (RuntimeException exc) {
                return new StockIdentity(symbol, "", cleanQuery);
            }
        }

        if (cleanQuery.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "请输入股票代码或名称。");
        }
        StockIdentity resolved = akShareAdapterClient.resolveStock(cleanQuery);
        if (resolved == null || resolved.symbol == null || resolved.symbol.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有找到股票：" + cleanQuery);
        }
        return resolved;
    }

    /** Resolve a query and return cached or freshly fetched K-line data. */
    public KlinePayload getKline(String query) {
        StockIdentity resolved = resolveStock(query);
        activate(resolved.symbol, resolved.name);
        KlinePayload cached = cached(resolved.symbol);
        if (cached == null) {
            cached = refreshSymbol(resolved.symbol);
        }
        return cached.withName(resolved.name);
    }

    /** Fetch one symbol, cache the payload, and convert failures into payload errors. */
    public KlinePayload refreshSymbol(String symbol) {
        String normalized = StockSymbols.normalizeSymbol(symbol);
        Object lock = refreshLocks.computeIfAbsent(normalized, key -> new Object());
        synchronized (lock) {
            String name = stockCache.name(normalized);
            try {
                KlinePayload payload = akShareAdapterClient.fetchKline(normalized, name);
                if (payload == null) {
                    payload = emptyPayload(normalized, name, "AkShare adapter returned no payload.");
                }
                payload.symbol = normalized;
                payload.name = name == null || name.isBlank() ? payload.name : name;
                payload.advice = adviceService.buildTradeAdvice(payload.rows);
                if (payload.warnings == null) {
                    payload.warnings = List.of();
                }
                stockCache.save(normalized, payload);
                return payload;
            } catch (RuntimeException exc) {
                KlinePayload payload = emptyPayload(normalized, name, exc.getMessage());
                stockCache.save(normalized, payload);
                return payload;
            }
        }
    }

    /** Refresh every active symbol currently registered in the cache. */
    public void refreshAll() {
        for (String symbol : stockCache.symbols()) {
            refreshSymbol(symbol);
        }
    }

    /** Register a stock for caching and scheduled refresh. */
    public void activate(String symbol, String name) {
        stockCache.addSymbol(StockSymbols.normalizeSymbol(symbol), name);
    }

    /** Remove a stock from the active registry and its cached payload. */
    public void deactivate(String symbol) {
        stockCache.removeSymbol(StockSymbols.normalizeSymbol(symbol));
    }

    /** Return a cached K-line payload without forcing an adapter call. */
    public KlinePayload cached(String symbol) {
        return stockCache.get(StockSymbols.normalizeSymbol(symbol));
    }

    /** Build an error payload that preserves the public K-line response shape. */
    private KlinePayload emptyPayload(String symbol, String name, String error) {
        KlinePayload payload = new KlinePayload();
        payload.symbol = symbol;
        payload.name = name == null ? "" : name;
        payload.updatedAt = TimeUtil.nowIsoSeconds();
        payload.source = null;
        payload.error = error;
        payload.advice = null;
        payload.warnings = List.of();
        return payload;
    }
}
