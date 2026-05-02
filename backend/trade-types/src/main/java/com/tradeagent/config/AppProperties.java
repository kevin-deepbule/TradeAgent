package com.tradeagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed configuration values for the Java backend. */
@ConfigurationProperties(prefix = "tradeagent")
public record AppProperties(
        int refreshSeconds,
        String defaultSymbol,
        String watchlistDbPath,
        String akshareBaseUrl) {
    /** Provide a valid refresh interval when configuration is missing or invalid. */
    public int refreshSecondsOrDefault() {
        return refreshSeconds > 0 ? refreshSeconds : 60;
    }
}

