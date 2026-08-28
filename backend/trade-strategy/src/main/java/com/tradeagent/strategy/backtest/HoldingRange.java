package com.tradeagent.strategy.backtest;

/** Inclusive row-index range where the backtest engine held a position. */
public record HoldingRange(
        int startIndex,
        int endIndex) {
}
