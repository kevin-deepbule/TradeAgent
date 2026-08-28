package com.tradeagent.strategy.backtest;

import java.util.List;

/** Backtest result shaped to support chart overlays and metric panels. */
public record BacktestResult(
        String strategyId,
        String strategyName,
        double totalReturn,
        double maxDrawdown,
        int tradeCount,
        Double winRate,
        int buyCount,
        int sellCount,
        int blockedBuyCount,
        int blockedSellCount,
        int holdingDays,
        boolean openPosition,
        PendingOrder pendingOrder,
        List<BacktestSignal> signals,
        List<BacktestTrade> trades,
        List<HoldingRange> holdingRanges) {
}
