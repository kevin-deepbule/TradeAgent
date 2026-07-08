# Deployment Agent Guide

This directory owns Docker Compose files and environment examples for local
infrastructure dependencies.

## Services

`docker-compose-fundament.yml` starts the local services used by the backend:

- PostgreSQL 16 on `localhost:5432`
- RabbitMQ 3.13 on `localhost:5672`
- RabbitMQ management UI on `http://localhost:15672`
- Redis 7 on `localhost:6379`

## Commands

Create a local environment file from the example:

```bash
cp depoy/.env.example depoy/.env
```

Start infrastructure services:

```bash
docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml up -d
```

Check service status:

```bash
docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml ps
```

Follow service logs:

```bash
docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml logs -f
```

Stop services while keeping named volumes:

```bash
docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml down
```

Reset services and delete named volumes:

```bash
docker compose --env-file depoy/.env -f depoy/docker-compose-fundament.yml down -v
```

## Runtime Notes

- `.env` is local machine configuration and should not be committed.
- `.env.example` keeps safe defaults for local development.
- Redis requires `REDIS_PASSWORD`; use `redis-cli -a "$REDIS_PASSWORD"` for
  local manual checks.
- PostgreSQL initializes from `backend/docs/PostgreSQL/` only when the database
  volume is first created.
- Changing `POSTGRES_PASSWORD` after the PostgreSQL volume exists does not
  update the existing database password; reset the volume or change the
  password inside PostgreSQL intentionally.
