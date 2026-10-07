# Trade Scanner Module Guide

This planned Java module owns the complete Scanner business workflow. It
currently contains architecture documentation and is not part of the Maven
reactor.

## Boundaries

- Implement Scanner orchestration, domain models, persistence, scheduling,
  deterministic policy, MQ publication, and APIs in Java.
- Reuse `trade-watchlist` and `trade-market` rather than duplicating their data.
- Keep AkShare calls and upstream adaptation in `akshare_adapter/`.
- Call cloud LLM HTTP APIs from Java when practical.
- Add a Python model service only for a concrete dependency on Python-specific
  NLP, embedding, or GPU tooling.
- Treat every model result as candidate data and validate it in Java.
- Keep Scanner independent of Research and Strategy.

## Implementation Rules

- Keep controllers, services, clients, collectors, repositories, jobs, DTOs,
  policies, and resources inside this business module.
- Persist RawMessages before extraction and make every stage idempotent.
- Keep processing, verification, and publication states separate.
- Publish accepted events through a transactional outbox.
- Version event taxonomy, extraction contracts, and scoring policies.
- Store evidence and explanations for mapping, verification, and scoring.
- Add concise class-level and public-method comments required by the project.
- Add this module to the Maven reactor only when implementation begins.

