# Backend Agent Guide

This directory contains the business-oriented Spring Boot modular monolith.

## Responsibility

- Keep public REST and WebSocket route paths stable because the frontend calls them directly.
- Keep persistence, caching, startup initialization, and trade advice in the backend.
- Call the local Python AkShare adapter through the client owned by the relevant capability module.
- Do not reimplement AkShare scraping or data-source logic in Java.

## Structure Rules

- `trade-market/`: stock identity, K-line DTOs and APIs, advice, caching, market-data AkShare access, refresh scheduling, and WebSocket updates.
- `trade-watchlist/`: default-stock and watchlist workflows, APIs, PostgreSQL repositories, MyBatis mappers, and startup initialization.
- `trade-strategy/`: strategy rules, backtest contracts, and explainable-decision models.
- `trade-research/`: finance-specific AkShare adapter access, financial
  disclosures, deterministic forecasts and rule-PE baselines, research
  persistence, full-table bounded DeepSeek PE review, and ranking.
- `trade-app/`: runnable entrypoint, runtime properties, health API, error translation, CORS, and module assembly.
- `docs/`: backend design, module documentation, and PostgreSQL initialization scripts.
- `../depoy/`: Docker Compose services and environment examples for local infrastructure dependencies.

Dependencies must remain acyclic: `trade-watchlist` and `trade-strategy` may
depend on `trade-market`; `trade-market` must not depend on either of them; and
only `trade-app` may assemble every capability module. Keep controllers,
services, clients, repositories, scheduled jobs, DTOs, and resources inside the
business module that owns the behavior.

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
- `GET /api/research/industries`
- `POST /api/research/valuation-runs`
- `GET /api/research/valuation-runs/{runId}`

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

## Capability Packages

- `com.tradeagent.market`: market data APIs, DTOs, AkShare client, cache, refresh, and advice workflow.
- `com.tradeagent.watchlist`: persisted default-stock and watchlist behavior.
- `com.tradeagent.strategy`: strategy rules, backtest contracts, and agent decision types.
- `com.tradeagent.research`: financial-report data, valuation tasks, policies,
  persistence, and explanatory research output.
- `com.tradeagent.app`: process-level health, error handling, configuration, and startup assembly.

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
cp depoy/.env.example depoy/.env
docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml up -d
docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml ps
docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml down
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
- MyBatis XML mapper resources are owned by and loaded from `trade-watchlist/src/main/resources/mybatis/`.
- Local PostgreSQL state lives in the `tradeagent-postgres-data` Docker named volume.
- `trade-research` reads `DeepSeek_API_KEY` from the optional `depoy/.env`
  import; AI failures must not invalidate deterministic valuation results.
- Docker infrastructure runs PostgreSQL, RabbitMQ, and Redis from `depoy/docker-compose-fundament.yml`.
- PostgreSQL initialization scripts live in `docs/PostgreSQL/` and are mounted into `/docker-entrypoint-initdb.d` for first-run database initialization.
- Docker service state lives in named volumes; use `docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml down -v` only when intentionally resetting it.
- Update `README.md` and this `AGENTS.md` whenever backend behavior, commands, configuration, APIs, structure, infrastructure, or runtime assumptions change.
- Do not commit Docker service data, database dumps, or `target/`.
- Every source file and public method should have concise comments where the project comment rules require them.
