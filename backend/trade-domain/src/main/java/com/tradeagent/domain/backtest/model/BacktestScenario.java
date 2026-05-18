package com.tradeagent.domain.backtest.model;

import java.util.List;

import com.tradeagent.dto.KlineRow;

/** Input scenario that the backtest domain executes against a selected strategy. */
public record BacktestScenario(
        String symbol,
        String name,
        String strategyId,
        Integer years,
        List<KlineRow> rows) {
}
