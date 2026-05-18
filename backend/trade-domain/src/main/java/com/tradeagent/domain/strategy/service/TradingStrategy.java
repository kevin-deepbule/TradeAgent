package com.tradeagent.domain.strategy.service;

import java.util.List;

import com.tradeagent.domain.backtest.model.BacktestTrade;
import com.tradeagent.domain.backtest.model.OpenPosition;
import com.tradeagent.domain.strategy.model.StrategyEvaluation;
import com.tradeagent.dto.KlineRow;

/** Domain contract for strategy rules that produce signals without executing trades. */
public interface TradingStrategy {
    /** Return the stable strategy identifier used by API requests and persisted results. */
    String id();

    /** Return the display name that describes the strategy to users. */
    String name();

    /** Evaluate one completed K-line row and return a signal-only decision. */
    StrategyEvaluation evaluate(List<KlineRow> rows, int index, boolean holding, OpenPosition entry,
            List<BacktestTrade> closedTrades);
}
