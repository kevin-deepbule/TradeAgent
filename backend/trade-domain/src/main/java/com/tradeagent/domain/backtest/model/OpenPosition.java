package com.tradeagent.domain.backtest.model;

/** Current open position tracked by the backtest engine during strategy evaluation. */
public record OpenPosition(
        int index,
        String date,
        double price,
        String signalDate) {
}
