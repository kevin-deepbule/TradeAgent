package com.tradeagent.market.client;

import java.net.URI;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import com.tradeagent.market.config.MarketProperties;
import com.tradeagent.market.dto.KlinePayload;
import com.tradeagent.market.dto.StockIdentity;

/** HTTP client for the local Python AkShare adapter service. */
@Component
public class AkShareAdapterClient {
    private final RestClient restClient;
    private final String baseUrl;

    /** Create a client using configured adapter base URL. */
    public AkShareAdapterClient(RestClient restClient, MarketProperties properties) {
        this.restClient = restClient;
        this.baseUrl = trimTrailingSlash(properties.akshareBaseUrl());
    }

    /** Resolve a stock code or name through the adapter. */
    public StockIdentity resolveStock(String query) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("internal", "stocks", query, "resolve")
                .build()
                .encode()
                .toUri();
        return restClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new ResponseStatusException(response.getStatusCode(), "股票解析失败");
                })
                .body(StockIdentity.class);
    }

    /** Fetch normalized K-line rows through the adapter. */
    public KlinePayload fetchKline(String symbol, String name) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("internal", "stocks", symbol, "kline")
                .queryParam("name", name == null ? "" : name)
                .build()
                .encode()
                .toUri();
        return restClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new ResponseStatusException(response.getStatusCode(), "AkShare 数据获取失败");
                })
                .body(KlinePayload.class);
    }

    /** Remove a trailing slash so path construction stays predictable. */
    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8002";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
