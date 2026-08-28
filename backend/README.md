# TradeAgent Backend

Spring Boot modular monolith for TradeAgent. Maven modules are split by business
capability, while one `trade-app` process exposes the REST and WebSocket APIs,
persists local user settings in PostgreSQL, and calls the internal AkShare adapter.

## Role

The backend is the public API boundary for the browser. It owns:

- Health, settings, watchlist, and K-line REST APIs.
- Stock WebSocket updates.
- PostgreSQL persistence.
- Local caching and startup initialization.
- Trade advice generation.
- Communication with `akshare_adapter/`.

## Structure

- `pom.xml`: parent Maven reactor build.
- `trade-market/`: stock identity, K-line DTOs and APIs, AkShare HTTP client, in-memory cache, advice calculation, refresh job, and stock WebSocket updates.
- `trade-watchlist/`: default-stock and watchlist APIs, workflow service, PostgreSQL repositories, MyBatis mappers, and startup initialization.
- `trade-strategy/`: strategy rules, backtest contracts, and explainable decision models. It depends only on market data types from `trade-market`.
- `trade-app/`: Spring Boot entrypoint, runtime properties, health endpoint, exception handling, CORS, and assembly of the other modules.
- `docs/`: backend design, module documentation, and PostgreSQL initialization scripts.
- `docs/PostgreSQL/`: first-run PostgreSQL schema initialization scripts.
- `../depoy/`: Docker Compose services and environment examples for local infrastructure dependencies.

### Module Dependencies

```text
trade-app -> trade-market
trade-app -> trade-watchlist -> trade-market
trade-app -> trade-strategy  -> trade-market
```

Dependencies point from the runnable application and higher-level capabilities
toward the market capability. `trade-market` does not depend on watchlist or
strategy code, so Maven enforces the main module boundary at compile time.

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
- PostgreSQL: `jdbc:postgresql://localhost:5432/tradeagent`

## Package

```bash
mvn -f backend/pom.xml -pl trade-app -am package
java -jar backend/trade-app/target/trade-app-0.1.0.jar
```

## Docker Infrastructure

`../depoy/docker-compose-fundament.yml` starts local dependencies for backend
development:

- PostgreSQL 16 on `localhost:5432`
- PostgreSQL first-run schema initialization from `docs/PostgreSQL/`
- RabbitMQ 3.13 with AMQP on `localhost:5672`
- RabbitMQ management UI on `http://localhost:15672`
- Redis 7 on `localhost:6379`

Start the services:

```bash
cp depoy/.env.example depoy/.env
docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml up -d
```

Check service status:

```bash
docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml ps
```

Stop the services:

```bash
docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml down
```

The compose file uses Docker named volumes for service state. Remove those
volumes only when you intentionally want to reset local infrastructure data:

```bash
docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml down -v
```

Environment variables can override defaults:

- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`
- `RABBITMQ_DEFAULT_USER`, `RABBITMQ_DEFAULT_PASS`, `RABBITMQ_PORT`, `RABBITMQ_MANAGEMENT_PORT`
- `REDIS_PASSWORD`, `REDIS_PORT`

PostgreSQL mounts `backend/docs/PostgreSQL/` into `/docker-entrypoint-initdb.d`,
so the scripts run when the database volume is first initialized.

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

Stock WebSocket connections send cached K-line payloads immediately when a
cache entry exists, and only wait for an adapter refresh when the symbol has not
been cached yet.

## Environment

Runtime settings are configured in `trade-app/src/main/resources/application.properties`:

- `BACKEND_HOST`, default `0.0.0.0`
- `BACKEND_PORT`, default `8001`
- `STOCK_REFRESH_SECONDS`, default `60`
- `DEFAULT_STOCK_SYMBOL`, default `000001`
- `AKSHARE_ADAPTER_BASE_URL`, default `http://localhost:8002`
- `POSTGRES_JDBC_URL`, default `jdbc:postgresql://localhost:5432/tradeagent`
- `POSTGRES_USER`, default `tradeagent`
- `POSTGRES_PASSWORD`, default `tradeagent`
- `mybatis.mapper-locations`, default `classpath*:mybatis/*.xml`

Watchlist-owned MyBatis XML mappers live in
`trade-watchlist/src/main/resources/mybatis/` and are loaded from the classpath.
