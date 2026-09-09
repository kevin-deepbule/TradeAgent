package com.tradeagent.research.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Dedicated background execution for long-running research scans. */
@Configuration
public class ResearchAsyncConfig {
    /** Create the single-run executor that keeps upstream request pressure bounded. */
    @Bean(name = "researchTaskExecutor")
    public Executor researchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("research-run-");
        executor.initialize();
        return executor;
    }

    /** Create bounded parallel workers for slow per-company AkShare statement calls. */
    @Bean(name = "researchFetchExecutor")
    public Executor researchFetchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("research-fetch-");
        executor.initialize();
        return executor;
    }
}
