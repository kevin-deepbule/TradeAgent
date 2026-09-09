package com.tradeagent.research.model;

import java.time.Instant;
import java.util.List;

/** Persistable state and ranked output for one valuation analysis task. */
public record ResearchRun(
        String id,
        String status,
        int progress,
        String message,
        ValuationRunRequest request,
        List<ValuationResult> results,
        String error,
        Instant createdAt,
        Instant updatedAt) {
    /** Create a queued task before background data collection starts. */
    public static ResearchRun queued(String id, ValuationRunRequest request) {
        Instant now = Instant.now();
        return new ResearchRun(id, "QUEUED", 0, "等待执行", request, List.of(), null, now, now);
    }

    /** Return a copy with updated progress while retaining any prior results. */
    public ResearchRun running(int newProgress, String newMessage) {
        return new ResearchRun(id, "RUNNING", newProgress, newMessage, request, results, null, createdAt,
                Instant.now());
    }

    /** Return a completed task containing stable ranked results. */
    public ResearchRun completed(List<ValuationResult> newResults) {
        return new ResearchRun(id, "COMPLETED", 100, "分析完成", request, List.copyOf(newResults), null, createdAt,
                Instant.now());
    }

    /** Return a failed task with a user-visible reason. */
    public ResearchRun failed(String failure) {
        return new ResearchRun(id, "FAILED", progress, "分析失败", request, results, failure, createdAt, Instant.now());
    }
}
