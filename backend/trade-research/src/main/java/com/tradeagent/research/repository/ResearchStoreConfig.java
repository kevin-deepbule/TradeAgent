package com.tradeagent.research.repository;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Select PostgreSQL storage in production and an in-memory fallback without JDBC. */
@Configuration
public class ResearchStoreConfig {
    /** Create PostgreSQL research storage when a datasource is available. */
    @Bean
    @ConditionalOnBean(JdbcTemplate.class)
    public ResearchRunStore jdbcResearchRunStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcResearchRunStore(jdbcTemplate, objectMapper);
    }

    /** Create process-local storage for isolated module and application tests. */
    @Bean
    @ConditionalOnMissingBean(ResearchRunStore.class)
    public ResearchRunStore inMemoryResearchRunStore() {
        return new InMemoryResearchRunStore();
    }
}
