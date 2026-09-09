package com.tradeagent.research.valuation;

/** Versioned reasonable-PE and valuation-band rules selected for an industry. */
public record ValuationPolicy(
        String version,
        double basePe,
        double minPe,
        double maxPe,
        double deepValueRatio,
        double valueRatio,
        double fairRatio,
        double expensiveRatio) {
}
