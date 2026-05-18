package com.tradeagent.domain.backtest.service;

import com.tradeagent.domain.backtest.model.BacktestResult;
import com.tradeagent.domain.backtest.model.BacktestScenario;
import com.tradeagent.domain.strategy.service.TradingStrategy;

/** Domain service contract for applying execution rules to strategy signals. */
public interface BacktestEngine {
    /** Execute one scenario with the supplied signal-only strategy. */
    BacktestResult run(BacktestScenario scenario, TradingStrategy strategy);
}
