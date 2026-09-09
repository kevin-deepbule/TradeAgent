# Backend Modules

The backend is one deployable Spring Boot process built from Maven modules that
follow business capabilities instead of technical layers.

## Module Responsibilities

- `trade-market`: resolves stocks, fetches K-lines from the internal AkShare
  adapter, calculates advice, caches active symbols, refreshes quotes, and owns
  the stock REST and WebSocket endpoints.
- `trade-watchlist`: owns the default stock and watchlist, including REST
  endpoints, workflow coordination, PostgreSQL repositories, MyBatis mappers,
  and startup warming of market symbols.
- `trade-strategy`: owns signal-rule, backtest, and explainable-decision types.
  The frontend remains the runtime owner of the currently implemented strategy
  backtests.
- `trade-research`: owns Shenwan industry selection, financial-report runs,
  deterministic profit forecasts and rule-PE baselines, PostgreSQL run
  snapshots, bounded full-table DeepSeek PE review, and final ranking.
- `trade-app`: owns the Spring Boot entrypoint, runtime properties, health
  endpoint, exception translation, CORS, and final executable packaging.
- `docs/PostgreSQL`: contains idempotent first-run PostgreSQL schema scripts.

## Dependency Direction

```text
trade-app -> trade-market
trade-app -> trade-watchlist -> trade-market
trade-app -> trade-strategy  -> trade-market
trade-app -> trade-research  -> trade-market
```

Maven module dependencies enforce an acyclic graph. A capability keeps its own
controllers, services, clients, repositories, jobs, DTOs, and resources; it
must not place each technical layer in a separate Maven module. Cross-module
calls should use a small service or data type exposed by the owning capability.

`trade-research` calls finance-specific endpoints on the same internal Python
adapter. Versioned rules produce the profit forecast, rule-PE baseline, and PE
bounds. Every row is sent to DeepSeek in bounded batches; an in-range AI PE is
adopted and dependent valuation fields are recalculated, while missing,
out-of-range, or failed responses retain the rule PE.

## Run And Verify

```bash
mvn -f backend/pom.xml test
mvn -f backend/pom.xml -pl trade-app -am spring-boot:run
curl --noproxy '*' http://localhost:8001/api/health
```
