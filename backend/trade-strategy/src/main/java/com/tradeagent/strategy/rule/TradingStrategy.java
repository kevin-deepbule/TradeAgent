package com.tradeagent.strategy.rule;

import java.util.List;

import com.tradeagent.market.dto.KlineRow;
import com.tradeagent.strategy.backtest.BacktestTrade;
import com.tradeagent.strategy.backtest.OpenPosition;

/** Contract for strategy rules that produce signals without executing trades. */
public interface TradingStrategy {
    /** Return the stable strategy identifier used by API requests and persisted results. */
    String id();

    /** Return the display name that describes the strategy to users. */
    String name();

    /** Evaluate one completed K-line row and return a signal-only decision. */
    StrategyEvaluation evaluate(List<KlineRow> rows, int index, boolean holding, OpenPosition entry,
            List<BacktestTrade> closedTrades);
}
