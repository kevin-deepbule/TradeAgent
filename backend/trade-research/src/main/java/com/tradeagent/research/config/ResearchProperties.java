package com.tradeagent.research.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime settings for financial research data and DeepSeek explanations. */
@ConfigurationProperties(prefix = "tradeagent.research")
public record ResearchProperties(
        String akshareBaseUrl,
        String deepseekApiKey,
        String deepseekBaseUrl,
        String deepseekModel,
        boolean deepseekEnabled,
        int aiBatchSize) {
    /** Return the adapter URL without a trailing slash. */
    public String normalizedAkshareBaseUrl() {
        return normalizeUrl(akshareBaseUrl, "http://localhost:8002");
    }

    /** Return the DeepSeek URL without a trailing slash. */
    public String normalizedDeepSeekBaseUrl() {
        return normalizeUrl(deepseekBaseUrl, "https://api.deepseek.com");
    }

    /** Return a usable model name when none was configured. */
    public String deepseekModelOrDefault() {
        return deepseekModel == null || deepseekModel.isBlank() ? "deepseek-v4-flash" : deepseekModel.trim();
    }

    /** Bound each DeepSeek request while allowing every valuation row to be reviewed. */
    public int aiBatchSizeOrDefault() {
        return aiBatchSize > 0 ? Math.min(aiBatchSize, 20) : 10;
    }

    /** Check whether a DeepSeek call can be attempted safely. */
    public boolean deepseekAvailable() {
        return deepseekEnabled && deepseekApiKey != null && !deepseekApiKey.isBlank();
    }

    /** Normalize one configured HTTP base URL. */
    private String normalizeUrl(String value, String fallback) {
        String resolved = value == null || value.isBlank() ? fallback : value.trim();
        return resolved.endsWith("/") ? resolved.substring(0, resolved.length() - 1) : resolved;
    }
}
