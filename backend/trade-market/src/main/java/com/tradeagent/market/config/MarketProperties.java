package com.tradeagent.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed refresh and AkShare settings owned by the market module. */
@ConfigurationProperties(prefix = "tradeagent.market")
public record MarketProperties(
        int refreshSeconds,
        String akshareBaseUrl) {
    /** Provide a valid refresh interval when configuration is missing or invalid. */
    public int refreshSecondsOrDefault() {
        return refreshSeconds > 0 ? refreshSeconds : 60;
    }
}
