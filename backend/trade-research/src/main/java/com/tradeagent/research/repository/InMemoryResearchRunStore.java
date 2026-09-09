package com.tradeagent.research.repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.tradeagent.research.model.ResearchRun;

/** Database-free run storage used when JDBC is intentionally disabled in tests. */
public class InMemoryResearchRunStore implements ResearchRunStore {
    private final ConcurrentMap<String, ResearchRun> runs = new ConcurrentHashMap<>();

    /** No initialization is needed for process-local storage. */
    @Override
    public void initialize() {
    }

    /** Save a task snapshot in the process-local map. */
    @Override
    public void save(ResearchRun run) {
        runs.put(run.id(), run);
    }

    /** Find a task snapshot in the process-local map. */
    @Override
    public Optional<ResearchRun> find(String id) {
        return Optional.ofNullable(runs.get(id));
    }
}
