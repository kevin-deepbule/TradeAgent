# Backend Agent Guide

This directory contains the DDD-oriented Spring Boot backend.

## Responsibility

- Keep public REST and WebSocket route paths stable because the frontend calls them directly.
- Keep persistence, caching, startup initialization, and trade advice in the backend.
- Call the local Python AkShare adapter through the infrastructure client implementation.
- Do not reimplement AkShare scraping or data-source logic in Java.

## Structure Rules

- `trade-api/`: public REST API contracts and DTO payload classes.
- `trade-app/`: application entrypoint, runtime properties, and MyBatis XML mapper resources.
- `trade-domain/`: domain services and ports; do not depend on infrastructure implementations from here.
- `trade-infrastructure/`: PostgreSQL persistence, MyBatis mapper interfaces, in-memory cache, AkShare adapter HTTP client, datasource, and REST client beans.
- `trade-trigger/`: concrete Spring Web controllers, exception translation, CORS, WebSocket endpoint, startup initialization, and scheduled tasks.
- `trade-types/`: typed config and small shared utilities.
- `docs/`: backend design, module documentation, and PostgreSQL initialization scripts.
- `docker/`: optional Docker Compose definitions for backend development infrastructure.

## API Contract

Current public routes:

- `GET /api/health`
- `GET /api/default-stock`
- `PUT /api/default-stock`
- `GET /api/watchlist`
- `POST /api/watchlist`
- `DELETE /api/watchlist/{symbol}`
- `GET /api/stocks/{query}/kline`
- `WS /ws/stocks/{query}`

The frontend depends on the K-line payload fields:

- `symbol`
- `name`
- `updatedAt`
- `source`
- `rows`
- `advice`
- `error`
- `warnings`

Avoid renaming response fields unless the frontend is updated in the same change.
WebSocket stock streams should send cached K-line payloads immediately when
available, and only block on adapter refresh when no cached payload exists.

## Commands

Run locally:

```bash
mkdir -p .logs
mvn -f backend/pom.xml -pl trade-app -am -DskipTests package
setsid java -jar backend/trade-app/target/trade-app-0.1.0.jar --debug=false > .logs/backend.log 2>&1 < /dev/null &
```

Compile and test:

```bash
mvn -f backend/pom.xml test
```

Docker infrastructure:

```bash
docker compose -f backend/docker/docker-compose-fundament.yml up -d
docker compose -f backend/docker/docker-compose-fundament.yml ps
docker compose -f backend/docker/docker-compose-fundament.yml down
```

Smoke checks:

```bash
curl --noproxy '*' http://localhost:8001/api/health
curl --noproxy '*' http://localhost:8001/api/watchlist
curl --noproxy '*' http://localhost:8001/api/stocks/000001/kline
```

## Runtime Notes

- Default backend port is `8001`.
- Default adapter base URL is `http://localhost:8002`.
- MyBatis XML mapper resources are loaded from `trade-app/src/main/resources/mybatis/`.
- Local PostgreSQL state lives in the `tradeagent-postgres-data` Docker named volume.
- Docker infrastructure runs PostgreSQL, RabbitMQ, and Redis from `docker/docker-compose-fundament.yml`.
- PostgreSQL initialization scripts live in `docs/PostgreSQL/` and are mounted into `/docker-entrypoint-initdb.d` for first-run database initialization.
- Docker service state lives in named volumes; use `docker compose -f backend/docker/docker-compose-fundament.yml down -v` only when intentionally resetting it.
- Update `README.md` and this `AGENTS.md` whenever backend behavior, commands, configuration, APIs, structure, infrastructure, or runtime assumptions change.
- Do not commit Docker service data, database dumps, or `target/`.
- Every source file and public method should have concise comments where the project comment rules require them.
