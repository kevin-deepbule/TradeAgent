# Trade Scanner Module Guide

This Java Maven module owns Scanner ingestion, plugin processing, deterministic
scoring, evidence persistence, jobs, APIs and reliable outbox publication.
It is assembled by trade-app and always starts with the backend; no enable flag is used.

## Current Runtime

- Use module-owned scanner-schema.sql for additive initialization on existing volumes.
- Use ScannerRepository and MyBatis-Plus BaseMapper/Wrapper for Scanner CRUD, not JdbcTemplate in business classes.
- Keep PostgreSQL ON CONFLICT and FOR UPDATE SKIP LOCKED in custom Mapper methods.
- Keep caller-owned transactions around event/evidence/revision/outbox writes and row locks.
- Register UuidTypeHandler through mybatis-plus.type-handlers-package; explicitly clear nullable errors in Wrapper updates.
- Embedded PostgreSQL tests must use their own transient database, never the application database.
- Manual HTTP evidence is never trusted as an official collector source.
- Only server-owned collectors may use configured SCANNER_OFFICIAL_SOURCES.
- Preserve event revisions referenced by eventId + eventVersion in queue messages.
- Publication requires score >= 70 and at least one stock; verification status is not a gate.
- Published messages represent research candidates, not confirmed facts; keep source trust in scoring.
- Publisher confirms and mandatory returns must succeed before marking PUBLISHED.
- The market collector reads completed recent rows from the existing market cache.
- CLS, Eastmoney and 10jqka collectors persist ingestion-only RawMessages;
  event extraction, LLM, semantic matching and Research consumption remain future work.
- News responses discard adapter `raw`, deduplicate on the explicit external_id
  column, and retain the stable source_id.
- Retry uses persisted evidence; database failures roll back the whole processing transaction.

## Boundaries

- Implement Scanner orchestration, domain models, persistence, scheduling,
  deterministic policy, MQ publication, and APIs in Java.
- Reuse `trade-watchlist` and `trade-market` rather than duplicating their data.
- Keep AkShare calls and upstream adaptation in `akshare_adapter/`.
- Call cloud LLM HTTP APIs from Java when practical.
- Add a Python model service only for a concrete dependency on Python-specific
  NLP, embedding, or GPU tooling.
- Treat every model result as candidate data and validate it in Java.
- Keep Scanner independent of Research and Strategy.

## Implementation Rules

- Keep controllers, services, clients, collectors, repositories, jobs, DTOs,
  policies, and resources inside this business module.
- Persist RawMessages before extraction and make every stage idempotent.
- Keep processing, verification, and publication states separate.
- Publish accepted events through a transactional outbox.
- Version event taxonomy, extraction contracts, and scoring policies.
- Register event types in scanner_event_type and persist scanner_event.event_type; reject unregistered or disabled types.
- Store evidence and explanations for mapping, verification, and scoring.
- Add concise class-level and public-method comments required by the project.
- Keep this module in the Maven reactor and assembled only by trade-app.
