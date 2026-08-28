package com.tradeagent.strategy.agent;

/** Contract for intelligent agents that generate explainable trade decisions. */
public interface TradingAgent {
    /** Build one explainable decision from the supplied market context. */
    AgentDecision decide(AgentContext context);
}
