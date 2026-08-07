# ADR 0005 — PostgreSQL is not a TSDB

- **Status:** Accepted
- **Date:** 2026-08 (platform foundation; reinforced in 0.6)

## Context

Operational history (assessments, health, audit, snapshots) needs relational storage. Runtime metrics are high-cardinality time series.

## Decision

PostgreSQL stores operational history and derived evidence/summaries. Continuous metric samples live in Prometheus/Thanos (or equivalent). PostgreSQL **must not** act as a TSDB.

## Consequences

- Metrics providers query external systems.
- Persistence schema focuses on runs, findings, snapshots — not sample streams.
- Capacity planning for the platform DB stays modest.
