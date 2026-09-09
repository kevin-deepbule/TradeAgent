package com.tradeagent.research.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeagent.research.model.ResearchRun;
import com.tradeagent.research.model.ValuationResult;
import com.tradeagent.research.model.ValuationRunRequest;

/** PostgreSQL JSONB storage for immutable research task snapshots. */
public class JdbcResearchRunStore implements ResearchRunStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** Create PostgreSQL-backed storage with Spring JSON configuration. */
    public JdbcResearchRunStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /** Create the run table for existing database volumes as well as fresh installs. */
    @Override
    public void initialize() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS research_valuation_run (
                    id UUID PRIMARY KEY,
                    status TEXT NOT NULL,
                    progress INTEGER NOT NULL,
                    message TEXT NOT NULL DEFAULT '',
                    request_payload JSONB NOT NULL,
                    result_payload JSONB NOT NULL DEFAULT '[]'::jsonb,
                    error TEXT,
                    created_at TIMESTAMPTZ NOT NULL,
                    updated_at TIMESTAMPTZ NOT NULL
                )
                """);
    }

    /** Upsert a complete run snapshot so readers never observe partial JSON. */
    @Override
    public void save(ResearchRun run) {
        jdbcTemplate.update("""
                INSERT INTO research_valuation_run (
                    id, status, progress, message, request_payload, result_payload,
                    error, created_at, updated_at
                ) VALUES (
                    CAST(? AS UUID), ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?, ?, ?
                )
                ON CONFLICT(id) DO UPDATE SET
                    status = excluded.status,
                    progress = excluded.progress,
                    message = excluded.message,
                    request_payload = excluded.request_payload,
                    result_payload = excluded.result_payload,
                    error = excluded.error,
                    updated_at = excluded.updated_at
                """,
                run.id(), run.status(), run.progress(), run.message(), json(run.request()), json(run.results()),
                run.error(), run.createdAt(), run.updatedAt());
    }

    /** Load and deserialize one task snapshot by UUID. */
    @Override
    public Optional<ResearchRun> find(String id) {
        List<ResearchRun> rows = jdbcTemplate.query("""
                SELECT id::text, status, progress, message, request_payload::text,
                       result_payload::text, error, created_at, updated_at
                FROM research_valuation_run
                WHERE id = CAST(? AS UUID)
                """, this::mapRun, id);
        return rows.stream().findFirst();
    }

    /** Map one PostgreSQL row back into the public run contract. */
    private ResearchRun mapRun(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            ValuationRunRequest request = objectMapper.readValue(
                    resultSet.getString("request_payload"), ValuationRunRequest.class);
            ValuationResult[] resultArray = objectMapper.readValue(
                    resultSet.getString("result_payload"), ValuationResult[].class);
            return new ResearchRun(
                    resultSet.getString("id"),
                    resultSet.getString("status"),
                    resultSet.getInt("progress"),
                    resultSet.getString("message"),
                    request,
                    Arrays.asList(resultArray),
                    resultSet.getString("error"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant());
        } catch (JsonProcessingException exc) {
            throw new SQLException("无法解析已保存的财报估值任务", exc);
        }
    }

    /** Serialize one run payload or fail before writing inconsistent state. */
    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exc) {
            throw new IllegalStateException("无法序列化财报估值任务", exc);
        }
    }
}
