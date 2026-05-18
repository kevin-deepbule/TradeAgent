package com.tradeagent.domain.agent.service;

import com.tradeagent.domain.agent.model.AgentContext;
import com.tradeagent.domain.agent.model.AgentDecision;

/** Domain contract for intelligent agents that generate explainable trade decisions. */
public interface TradingAgent {
    /** Build one explainable decision from the supplied market context. */
    AgentDecision decide(AgentContext context);
}

