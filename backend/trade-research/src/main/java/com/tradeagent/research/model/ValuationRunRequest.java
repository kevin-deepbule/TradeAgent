package com.tradeagent.research.model;

import java.time.LocalDate;
import java.util.List;

/** User-selected industries, report period, and point-in-time analysis cutoff. */
public record ValuationRunRequest(
        List<String> industryCodes,
        LocalDate reportPeriod,
        LocalDate asOfDate,
        String marketScope) {
    /** Return the requested market scope or the first-version main-board default. */
    public String marketScopeOrDefault() {
        return marketScope == null || marketScope.isBlank() ? "MAIN_BOARD" : marketScope.trim().toUpperCase();
    }
}
