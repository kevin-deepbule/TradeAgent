# TradeAgent

TradeAgent is an A-share K-line dashboard with a separated frontend, Java
backend, and Python AkShare adapter.

## Architecture

```text
frontend/          Vue 3 + Vite + ECharts dashboard
backend/           DDD-oriented Spring Boot multi-module backend
backend/docker/    Optional Docker Compose services for backend development
akshare_adapter/   FastAPI internal service wrapping AkShare
```

Runtime flow:

```text
Browser -> frontend -> backend -> akshare_adapter -> AkShare
```

The browser only talks to the Spring Boot backend. The AkShare adapter is an
internal service used by the backend so data-source details stay out of the
frontend.

## Documentation

- [frontend/README.md](frontend/README.md): frontend setup, structure, and build commands.
- [backend/README.md](backend/README.md): backend setup, API surface, database, and runtime settings.
- [akshare_adapter/README.md](akshare_adapter/README.md): adapter setup, internal routes, and AkShare notes.
- [AGENTS.md](AGENTS.md): global project rules for AI and automation agents.

Each subproject also has its own `AGENTS.md` with local development rules.
When changing behavior, commands, configuration, APIs, directory
responsibilities, or runtime assumptions, update the nearest matching
`README.md` and `AGENTS.md` in the same change.

## Quick Start

Install Python dependencies once:

```bash
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install -r requirements.txt
```

Run the AkShare adapter:

```bash
mkdir -p .logs
source .venv/bin/activate
python3 -m akshare_adapter.server 2>&1 | tee .logs/akshare-adapter.log
```

Start optional backend infrastructure:

```bash
docker compose -f backend/docker/docker-compose-fundament.yml up -d
```

Run the backend:

```bash
mkdir -p .logs
mvn -f backend/pom.xml -pl trade-app -am -DskipTests package
setsid java -jar backend/trade-app/target/trade-app-0.1.0.jar --debug=false > .logs/backend.log 2>&1 < /dev/null &
```

Run the frontend:

```bash
npm --prefix frontend install
mkdir -p .logs
npm --prefix frontend run dev 2>&1 | tee .logs/frontend.log
```

Default local endpoints:

- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8001/api/health`
- AkShare adapter health: `http://localhost:8002/internal/health`
- RabbitMQ management, when Docker infrastructure is running: `http://localhost:15672`

## Verification

```bash
python3 -m py_compile $(find akshare_adapter -name '*.py' -print)
mvn -f backend/pom.xml test
npm --prefix frontend run build
curl --noproxy '*' http://localhost:8002/internal/health
curl --noproxy '*' http://localhost:8001/api/health
curl --noproxy '*' http://localhost:8001/api/watchlist
curl --noproxy '*' http://localhost:8001/api/stocks/000001/kline
```

## Runtime State

- SQLite database: `backend/data/watchlist.db`
- Optional Docker service state: Docker named volumes declared in `backend/docker/`
- Local logs: `.logs/`
- Frontend build output: `frontend/dist/`
- Maven build output: `backend/*/target/`

These generated files should stay out of git.
