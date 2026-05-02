# Backend Modules

The backend uses a Maven reactor with DDD-oriented module boundaries.

## Module Responsibilities

- `trade-api`: external REST and WebSocket adapters used by the frontend.
- `trade-app`: runnable Spring Boot application and runtime properties.
- `trade-domain`: stock workflow services plus ports for market data, cache, settings, and watchlist persistence.
- `trade-infrastructure`: concrete adapters for SQLite, in-memory cache, AkShare HTTP access, datasource, and REST client beans.
- `trade-trigger`: startup initialization and scheduled refresh triggers.
- `trade-types`: shared DTOs, typed config, and small utility types.

## Dependency Direction

`trade-app` assembles all modules. API and trigger modules depend on domain
ports and services. Infrastructure depends on domain ports and provides the
runtime implementations. Shared DTO/config/util types live in `trade-types`.

## Run And Verify

```bash
mvn -f backend/pom.xml test
mvn -f backend/pom.xml -pl trade-app -am spring-boot:run
curl --noproxy '*' http://localhost:8001/api/health
```
