package com.tradeagent.domain.agent.model;

import java.util.List;

/** Explainable decision returned by an intelligent trading agent. */
public record AgentDecision(
        AgentAction action,
        String actionText,
        int score,
        List<String> reasons,
        List<String> risks,
        String generatedAt) {
}
