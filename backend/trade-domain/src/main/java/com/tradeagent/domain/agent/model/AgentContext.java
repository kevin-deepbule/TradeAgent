package com.tradeagent.domain.agent.model;

import java.util.List;

import com.tradeagent.dto.KlineRow;

/** Market and stock context supplied to an intelligent trading agent. */
public record AgentContext(
        String symbol,
        String name,
        List<KlineRow> rows) {
}
