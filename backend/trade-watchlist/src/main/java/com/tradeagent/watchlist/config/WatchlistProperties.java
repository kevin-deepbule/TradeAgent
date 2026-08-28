package com.tradeagent.watchlist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed default-stock settings owned by the watchlist module. */
@ConfigurationProperties(prefix = "tradeagent.watchlist")
public record WatchlistProperties(String defaultSymbol) {
}
