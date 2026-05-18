package com.tradeagent.domain.strategy.model;

/** Result of evaluating a strategy at one row before the backtest engine applies execution rules. */
public record StrategyEvaluation(
        TradeAction action,
        String reason) {
}
