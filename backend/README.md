# TradeAgent Backend

Spring Boot backend for TradeAgent. It is split into DDD-oriented Maven modules,
exposes the public REST and WebSocket API used by the Vue frontend, persists
local user settings in SQLite, and calls the internal AkShare adapter for stock
data.

## Role

The backend is the public API boundary for the browser. It owns:

- Health, settings, watchlist, and K-line REST APIs.
- Stock WebSocket updates.
- SQLite persistence.
- Local caching and startup initialization.
- Trade advice generation.
- Communication with `akshare_adapter/`.

## Structure

- `pom.xml`: parent Maven reactor build.
- `trade-app/`: Spring Boot entrypoint and `application.properties`.
- `trade-api/`: REST controllers, exception handling, CORS, and WebSocket endpoints.
- `trade-domain/`: stock workflow services and ports for data source, cache, and persistence.
- `trade-infrastructure/`: SQLite repositories, in-memory cache, HTTP client, datasource, and REST client beans.
- `trade-trigger/`: startup initialization and scheduled refresh triggers.
- `trade-types/`: shared DTOs, typed config, and backend utility types.
- `docs/`: backend design and module documentation.
- `docker/`: optional Docker Compose definitions for local backend infrastructure.
- `data/watchlist.db`: local SQLite runtime database.

## Run

Requires JDK 17+ and Maven.

```bash
mkdir -p .logs
mvn -f backend/pom.xml -pl trade-app -am -DskipTests package
setsid java -jar backend/trade-app/target/trade-app-0.1.0.jar --debug=false > .logs/backend.log 2>&1 < /dev/null &
```

Defaults:

- Host: `0.0.0.0`
- Port: `8001`
- Health: `http://localhost:8001/api/health`
- AkShare adapter base: `http://localhost:8002`
- SQLite: `backend/data/watchlist.db`

## Package

```bash
mvn -f backend/pom.xml -pl trade-app -am package
java -jar backend/trade-app/target/trade-app-0.1.0.jar
```

## Docker Infrastructure

`docker/docker-compose-fundament.yml` starts optional local dependencies for
backend development:

- PostgreSQL 16 on `localhost:5432`
- RabbitMQ 3.13 with AMQP on `localhost:5672`
- RabbitMQ management UI on `http://localhost:15672`
- Redis 7 on `localhost:6379`

Start the services:

```bash
docker compose -f backend/docker/docker-compose-fundament.yml up -d
```

Check service status:

```bash
docker compose -f backend/docker/docker-compose-fundament.yml ps
```

Stop the services:

```bash
docker compose -f backend/docker/docker-compose-fundament.yml down
```

The compose file uses Docker named volumes for service state. Remove those
volumes only when you intentionally want to reset local infrastructure data:

```bash
docker compose -f backend/docker/docker-compose-fundament.yml down -v
```

Environment variables can override defaults:

- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`
- `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS`, `RABBITMQ_PORT`, `RABBITMQ_MANAGEMENT_PORT`
- `REDIS_PORT`

## Test

```bash
mvn -f backend/pom.xml test
```

## Public API

- `GET /api/health`
- `GET /api/default-stock`
- `PUT /api/default-stock`
- `GET /api/watchlist`
- `POST /api/watchlist`
- `DELETE /api/watchlist/{symbol}`
- `GET /api/stocks/{query}/kline`
- `WS /ws/stocks/{query}`

The K-line response includes:

- `symbol`
- `name`
- `updatedAt`
- `source`
- `rows`
- `advice`
- `error`
- `warnings`

`advice` may be `null`. When present, it contains:

- `action`: `buy`, `sell`, or `hold`
- `actionText`: Chinese display text
- `score`: 0-100
- `reasons`
- `risks`
- `generatedAt`

## Environment

Runtime settings are configured in `trade-app/src/main/resources/application.properties`:

- `BACKEND_HOST`, default `0.0.0.0`
- `BACKEND_PORT`, default `8001`
- `STOCK_REFRESH_SECONDS`, default `60`
- `DEFAULT_STOCK_SYMBOL`, default `000001`
- `WATCHLIST_DB_PATH`, default empty, resolved by the app
- `AKSHARE_ADAPTER_BASE_URL`, default `http://localhost:8002`
