package com.tradeagent.strategy.backtest;

import com.tradeagent.strategy.rule.TradingStrategy;

/** Contract for applying execution rules to strategy signals. */
public interface BacktestEngine {
    /** Execute one scenario with the supplied signal-only strategy. */
    BacktestResult run(BacktestScenario scenario, TradingStrategy strategy);
}
