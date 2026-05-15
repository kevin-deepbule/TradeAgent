# Backend Modules

The backend uses a Maven reactor with DDD-oriented module boundaries.

## Module Responsibilities

- `trade-api`: public REST API contracts and DTO payload classes.
- `trade-app`: runnable Spring Boot application, runtime properties, and MyBatis XML mapper resources.
- `trade-domain`: stock workflow services plus ports for market data, cache, settings, and watchlist persistence.
- `trade-infrastructure`: concrete adapters for PostgreSQL, MyBatis mapper interfaces, in-memory cache, AkShare HTTP access, datasource, and REST client beans.
- `trade-trigger`: concrete Spring Web controllers, WebSocket endpoint, startup initialization, and scheduled refresh triggers.
- `trade-types`: typed config and small utility types.
- `docs/PostgreSQL`: idempotent SQL scripts for local PostgreSQL schema initialization.

## Dependency Direction

`trade-app` assembles all modules and carries runtime MyBatis XML mapper
resources under `src/main/resources/mybatis/`. `trade-trigger` depends on API
contracts and domain services for concrete HTTP/WebSocket implementations.
Infrastructure depends on domain ports and API payload DTOs while providing the
runtime PostgreSQL, cache, and adapter implementations.

## Run And Verify

```bash
mvn -f backend/pom.xml test
mvn -f backend/pom.xml -pl trade-app -am spring-boot:run
curl --noproxy '*' http://localhost:8001/api/health
```
