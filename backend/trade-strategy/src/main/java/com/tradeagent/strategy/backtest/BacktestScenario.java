package com.tradeagent.strategy.backtest;

import java.util.List;

import com.tradeagent.market.dto.KlineRow;

/** Input scenario executed against a selected strategy. */
public record BacktestScenario(
        String symbol,
        String name,
        String strategyId,
        Integer years,
        List<KlineRow> rows) {
}
