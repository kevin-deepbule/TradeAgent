package com.tradeagent.domain.backtest.model;

/** Order delayed by execution constraints such as a limit-down open. */
public record PendingOrder(
        String type,
        int signalIndex,
        String signalDate) {
}
