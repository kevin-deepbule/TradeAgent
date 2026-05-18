package com.tradeagent.domain.backtest.model;

/** Executed buy or sell signal produced by the backtest engine. */
public record BacktestSignal(
        String type,
        int index,
        String date,
        double price,
        String signalDate) {
}
