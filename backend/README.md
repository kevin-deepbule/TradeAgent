# TradeAgent Backend

Spring Boot backend for TradeAgent. It exposes the public REST and WebSocket
API used by the Vue frontend, persists local user settings in SQLite, and calls
the internal AkShare adapter for stock data.

## Role

The backend is the public API boundary for the browser. It owns:

- Health, settings, watchlist, and K-line REST APIs.
- Stock WebSocket updates.
- SQLite persistence.
- Local caching and startup initialization.
- Trade advice generation.
- Communication with `akshare_adapter/`.

## Structure

- `pom.xml`: Maven build definition.
- `src/main/java/com/tradeagent/TradeAgentApplication.java`: application entrypoint.
- `src/main/java/com/tradeagent/config/`: runtime configuration, CORS, datasource, and HTTP client beans.
- `src/main/java/com/tradeagent/controller/`: REST controllers and exception handling.
- `src/main/java/com/tradeagent/client/`: client for the internal AkShare adapter.
- `src/main/java/com/tradeagent/dto/`: API payload DTOs.
- `src/main/java/com/tradeagent/repository/`: SQLite repositories.
- `src/main/java/com/tradeagent/service/`: stock workflow, cache, startup, and advice services.
- `src/main/java/com/tradeagent/websocket/`: WebSocket configuration and handler.
- `src/main/resources/application.properties`: environment-derived runtime defaults.
- `docker/`: optional Docker Compose definitions for local backend infrastructure.
- `data/watchlist.db`: local SQLite runtime database.

## Run

Requires JDK 17+ and Maven.

```bash
mkdir -p .logs
mvn -f backend/pom.xml spring-boot:run 2>&1 | tee .logs/backend.log
```

Defaults:

- Host: `0.0.0.0`
- Port: `8001`
- Health: `http://localhost:8001/api/health`
- AkShare adapter base: `http://localhost:8002`
- SQLite: `backend/data/watchlist.db`

## Package

```bash
mvn -f backend/pom.xml package
java -jar backend/target/trade-agent-backend-0.1.0.jar
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

Runtime settings are configured in `src/main/resources/application.properties`:

- `BACKEND_HOST`, default `0.0.0.0`
- `BACKEND_PORT`, default `8001`
- `STOCK_REFRESH_SECONDS`, default `60`
- `DEFAULT_STOCK_SYMBOL`, default `000001`
- `WATCHLIST_DB_PATH`, default empty, resolved by the app
- `AKSHARE_ADAPTER_BASE_URL`, default `http://localhost:8002`
