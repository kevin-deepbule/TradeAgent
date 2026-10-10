# Trade Agent Module Guide

This directory contains Java agent capability modules. Scanner V1 is in the Maven
reactor and assembled by trade-app, and always starts with the backend.

## Boundaries

- Keep one complete business agent in each `trade-*` child module.
- Use Java for workflow orchestration, domain logic, persistence, scheduling,
  messaging, public APIs, and ordinary HTTP model integrations.
- Reuse backend capability contracts instead of duplicating market, watchlist,
  research, or strategy data.
- Keep AkShare calls in `akshare_adapter/`.
- Introduce Python model tooling only for a concrete Python-specific runtime or
  library requirement.
- Keep module dependencies acyclic and let the backend application assemble
  runnable capabilities.
