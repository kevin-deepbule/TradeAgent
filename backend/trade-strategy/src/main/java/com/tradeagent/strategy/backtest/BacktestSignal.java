package com.tradeagent.strategy.backtest;

/** Executed buy or sell signal produced by the backtest engine. */
public record BacktestSignal(
        String type,
        int index,
        String date,
        double price,
        String signalDate) {
}
