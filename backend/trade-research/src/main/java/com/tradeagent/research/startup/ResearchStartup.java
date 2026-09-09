package com.tradeagent.research.startup;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.tradeagent.research.repository.ResearchRunStore;

/** Initializes persistent research-run storage for old and new database volumes. */
@Component
public class ResearchStartup implements ApplicationRunner {
    private final ResearchRunStore runStore;

    /** Create the startup hook with the active persistence implementation. */
    public ResearchStartup(ResearchRunStore runStore) {
        this.runStore = runStore;
    }

    /** Initialize research storage before valuation tasks are accepted. */
    @Override
    public void run(ApplicationArguments args) {
        runStore.initialize();
    }
}
