package com.tradeagent.strategy.agent;

import java.util.List;

import com.tradeagent.market.dto.KlineRow;

/** Market and stock context supplied to an intelligent trading agent. */
public record AgentContext(
        String symbol,
        String name,
        List<KlineRow> rows) {
}
