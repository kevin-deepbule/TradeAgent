# Backend Agent Guide

This directory contains the Spring Boot backend.

## Responsibility

- Keep public REST and WebSocket route paths stable because the frontend calls them directly.
- Keep persistence, caching, startup initialization, and trade advice in the backend.
- Call the local Python AkShare adapter through `src/main/java/com/tradeagent/client/`.
- Do not reimplement AkShare scraping or data-source logic in Java.

## Structure Rules

- `controller/`: REST API controllers and exception translation.
- `websocket/`: stock WebSocket endpoint and configuration.
- `service/`: orchestration, cache, startup initialization, and advice logic.
- `repository/`: SQLite persistence.
- `client/`: internal HTTP client for `akshare_adapter/`.
- `dto/`: public API payloads.
- `config/`: Spring configuration, app properties, CORS, datasource, and HTTP client beans.
- `util/`: small backend-only helpers.
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

## Commands

Run locally:

```bash
mkdir -p .logs
mvn -f backend/pom.xml spring-boot:run 2>&1 | tee .logs/backend.log
```

Compile and test:

```bash
mvn -f backend/pom.xml test
```

Optional Docker infrastructure:

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
- Local SQLite state lives at `backend/data/watchlist.db`.
- Optional Docker infrastructure runs PostgreSQL, RabbitMQ, and Redis from `docker/docker-compose-fundament.yml`.
- Docker service state lives in named volumes; use `docker compose -f backend/docker/docker-compose-fundament.yml down -v` only when intentionally resetting it.
- Update `README.md` and this `AGENTS.md` whenever backend behavior, commands, configuration, APIs, structure, infrastructure, or runtime assumptions change.
- Do not commit `data/watchlist.db`, Docker service data, or `target/`.
- Every source file and public method should have concise comments where the project comment rules require them.
