package com.tradeagent.domain.backtest.model;

/** Inclusive row-index range where the backtest engine held a position. */
public record HoldingRange(
        int startIndex,
        int endIndex) {
}
