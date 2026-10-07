# Trade Scanner Architecture

`trade-scanner` is the planned Java business module for discovering important
A-share market events. It owns the complete Scanner workflow: collection
coordination, durable state, event processing, deterministic policy, reliable
publication, and Scanner APIs.

This directory currently contains architecture documentation only. It is not
yet included in the Maven reactor and does not change the running application.

## Technology Boundary

Java is the default implementation language for Scanner capabilities:

- Spring scheduling and workflow coordination.
- PostgreSQL transactions and repositories.
- RawMessage and Event domain models.
- Deduplication orchestration and canonical event merging.
- Source validation and importance scoring rules.
- LLM calls that support ordinary HTTP/JSON APIs.
- RabbitMQ outbox publication.
- Public REST APIs and operational status.

Python is used only where the required upstream library or model runtime is
Python-specific:

- AkShare market, announcement, and news access stays in `akshare_adapter/`.
- pandas-based source adaptation stays in `akshare_adapter/`.
- A future local embedding, NLP, or GPU model may use a small internal Python
  model service when no practical Java implementation exists.

Python tools return data or model output. They do not own Scanner workflow
state, write Scanner tables, schedule jobs, or publish business events.

## Runtime Topology

```text
Vue Scanner Dashboard
        |
        v
Spring Boot trade-scanner
  |-- PostgreSQL: messages, events, scores, runs, outbox
  |-- RabbitMQ: important event IDs sent to Research
  |-- Redis: optional locks, short-lived cache, and rate limiting
  |-- HTTP --> akshare_adapter --> AkShare
  |-- HTTP --> DeepSeek or another configured LLM
  +-- HTTP --> optional Python-only model service
```

The browser only calls Spring Boot. Java validates all external and model
responses before changing Scanner business state.

## Maven Dependency Direction

```text
trade-app
   +--> trade-scanner --> trade-watchlist --> trade-market
   +--> trade-research ---------------------> trade-market
   +--> trade-strategy ---------------------> trade-market
```

`trade-scanner` may depend on `trade-watchlist` and `trade-market` to reuse the
authoritative watchlist and stock identity contracts. It must not depend on
Research or Strategy.

If Research consumes accepted Scanner events inside the modular monolith, it
may depend on a small read-only Scanner event query contract:

```text
trade-research --> trade-scanner --> trade-watchlist --> trade-market
```

## Planned Module Layout

```text
trade-agent/trade-scanner/
├── pom.xml
└── src/
    ├── main/java/com/tradeagent/scanner/
    │   ├── api/              public Scanner REST APIs and DTOs
    │   ├── client/           AkShare, LLM, and optional model clients
    │   ├── collector/        source collector contracts and implementations
    │   ├── config/           typed properties and client configuration
    │   ├── domain/           entities, value objects, enums, transitions
    │   ├── extraction/       structured event extraction
    │   ├── deduplication/    fingerprint and semantic matching
    │   ├── mapping/          event-to-stock association
    │   ├── validation/       source trust and corroboration rules
    │   ├── scoring/          versioned importance scoring policy
    │   ├── pipeline/         idempotent stage orchestration
    │   ├── repository/       PostgreSQL repositories and MyBatis mappers
    │   ├── messaging/        outbox and RabbitMQ publisher
    │   ├── job/              collectors, retries, pipeline, outbox jobs
    │   └── service/          application use cases and queries
    ├── main/resources/
    │   └── mybatis/          Scanner-owned mapper XML
    └── test/java/com/tradeagent/scanner/
```

The packages follow one business capability rather than creating separate
Maven modules for each technical layer.

## Collector Contract

Collectors fetch source records and convert transport responses into
RawMessage drafts. They do not extract events, score importance, or publish MQ
messages.

Planned Java contract:

```java
public interface MessageCollector {

    /** Return the stable identifier of this source collector. */
    String sourceId();

    /** Collect messages after the supplied incremental cursor. */
    CollectorResult collect(CollectorCursor cursor);
}
```

Initial collectors:

```text
WatchlistMarketCollector
AnnouncementCollector
NewsCollector
```

Social sources later produce `Clue` records and cannot write directly to the
formal Event pool.

## Pipeline

```text
collect source data
  -> persist RawMessage
  -> extract structured event candidates
  -> match or create canonical Event
  -> attach all supporting and contradicting sources
  -> map related stocks
  -> apply source-verification policy
  -> calculate versioned importance score
  -> write outbox record when eligible
  -> publish eventId to Research through RabbitMQ
```

RawMessage is persisted before extraction so evidence survives LLM or upstream
outages. Every stage is idempotent and independently retryable.

LLM output is treated as a candidate. Java validates its schema and applies
deterministic policy before persisting final verification, score, or
publication status.

## Domain Objects

### RawMessage

Immutable source evidence:

```text
id
source_id
external_id
source_type
title
content
url
author
published_at
collected_at
content_hash
raw_payload
processing_status
retry_count
last_error
```

### Event

Canonical event produced from one or more RawMessages:

```text
id
event_type
title
summary
occurred_at
industry
action
magnitude
verification_status
importance_score
processing_status
publication_status
canonical_fingerprint
extractor_version
policy_version
created_at
updated_at
```

### EventSource

Many-to-many evidence link between Event and RawMessage. It records whether a
source supports or contradicts the event, its weight, and primary-source
selection.

### EventStockRelation

Links an Event to a stock with relation type, relevance, evidence, reason, and
mapper version. Planned relation types:

```text
DIRECT_MENTION
INDUSTRY
SUPPLIER
CUSTOMER
COMPETITOR
WATCHLIST_MATCH
MODEL_INFERRED
```

### EventScore

Stores explainable, versioned dimensions:

```text
credibility
impact
relevance
novelty
persistence
total
reasons
policy_version
```

### Clue

Represents an unverified social-media lead. It becomes an Event only after
corroboration satisfies deterministic promotion policy.

`OpportunityCandidate` is initially a query projection over verified,
high-scoring Events with related stocks. It does not need a separate table in
V1.

## State Model

Processing, fact verification, and publication are separate dimensions:

```text
processing_status:
  COLLECTED -> EXTRACTED -> DEDUPLICATED -> MAPPED -> SCORED -> COMPLETED
  retryable failure -> RETRY_PENDING
  terminal failure  -> FAILED

verification_status:
  UNVERIFIED | MULTI_SOURCE | CONFIRMED | DISPUTED | REJECTED

publication_status:
  NOT_ELIGIBLE | READY | PUBLISHED | PUBLISH_FAILED
```

An Event can complete technical processing while remaining unverified and
ineligible for Research.

## Planned Persistence

| Table | Purpose |
| --- | --- |
| `scanner_source` | Stable source identity and trust tier |
| `scanner_collector_cursor` | Incremental collection checkpoint |
| `scanner_collector_run` | Run status, timing, counts, and errors |
| `scanner_raw_message` | Immutable source evidence and original payload |
| `scanner_event` | Canonical event and lifecycle states |
| `scanner_event_source` | Event-to-evidence relationships |
| `scanner_event_stock` | Stock relationships and explanations |
| `scanner_event_score` | Versioned score dimensions |
| `scanner_clue` | Unverified lead lifecycle |
| `scanner_outbox` | Transactional RabbitMQ publication |

The existing `watchlist` table remains authoritative. Scanner reads it through
the watchlist capability and does not create another watchlist store.

Recommended identity constraints:

```text
UNIQUE (source_id, external_id)
UNIQUE (source_id, content_hash) when no external ID exists
UNIQUE (event_id, raw_message_id)
UNIQUE (event_id, symbol, relation_type)
```

## Python Tool Contracts

### AkShare adapter

Java may call internal endpoints such as:

```text
GET /internal/scanner/market/spot
GET /internal/scanner/announcements
GET /internal/scanner/news
```

Exact endpoints and AkShare APIs must be checked against current AkShare
documentation during implementation. The adapter returns source data and does
not write Scanner tables.

### Optional model service

A Python model service is introduced only for a proven Python-only need, such
as a local sentence-transformers embedding model or GPU inference. Its contract
is small and stateless, for example:

```text
POST /internal/models/embeddings
```

Java retains batching decisions, retries, deduplication decisions, and durable
state. Ordinary cloud LLM APIs should be called directly from Java.

## Reliable Research Delivery

When an Event crosses the configured threshold, the same PostgreSQL transaction
updates the Event and inserts `scanner_outbox`. A publisher sends:

```json
{
  "eventId": "3ee3caa1-0000-0000-0000-000000000000",
  "schemaVersion": 1
}
```

Planned RabbitMQ topology:

```text
exchange:    tradeagent.event
routing key: scanner.important
queue:       research.important-event
dead letter: research.important-event.dlq
```

Research loads authoritative event details through a small Java query contract
or a backend internal API. The queue message does not duplicate the Event.

## Planned Public API

```text
GET  /api/scanner/dashboard
GET  /api/scanner/events
GET  /api/scanner/events/{eventId}
GET  /api/scanner/clues
GET  /api/scanner/collectors/status
POST /api/scanner/events/{eventId}/retry
POST /api/scanner/clues/{clueId}/verify
```

These routes are design targets and are not currently available.

## Delivery Order

1. Define EventType, states, database schema, and Java domain contracts.
2. Add the Maven module, repositories, empty pipeline, and collector health.
3. Deliver a watchlist-market anomaly vertical slice without an LLM dependency.
4. Add announcement and news adapter endpoints and persist RawMessages.
5. Add Java-based structured event extraction and LLM integration.
6. Add deduplication, stock mapping, validation, and scoring policies.
7. Add the transactional outbox and Research queue.
8. Add social Clues, Dashboard APIs, and operational metrics.
