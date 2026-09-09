package com.tradeagent.research.repository;

import java.util.Optional;

import com.tradeagent.research.model.ResearchRun;

/** Persistence boundary for replayable valuation-run state. */
public interface ResearchRunStore {
    /** Initialize any runtime storage required by the active implementation. */
    void initialize();

    /** Insert or replace one complete task snapshot. */
    void save(ResearchRun run);

    /** Find one task by its stable UUID string. */
    Optional<ResearchRun> find(String id);
}
