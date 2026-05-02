package com.tradeagent.dto;

import java.util.List;

/** Trade advice payload sent to the dashboard. */
public class AdviceDto {
    public String action;
    public String actionText;
    public int score;
    public List<String> reasons;
    public List<String> risks;
    public String generatedAt;

    /** Create an empty advice DTO for JSON binding. */
    public AdviceDto() {
    }

    /** Create a complete advice DTO for API responses. */
    public AdviceDto(String action, String actionText, int score, List<String> reasons, List<String> risks,
            String generatedAt) {
        this.action = action;
        this.actionText = actionText;
        this.score = score;
        this.reasons = reasons;
        this.risks = risks;
        this.generatedAt = generatedAt;
    }
}

