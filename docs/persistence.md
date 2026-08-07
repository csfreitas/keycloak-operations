# Persistence

PostgreSQL stores **operational state** for the Keycloak/RHBK Operations Platform:
targets (credential refs only), assessment history, health checks, audit events, and
environment snapshots.

PostgreSQL is **not** a time-series database. Metrics belong in Prometheus (or similar);
this schema holds discrete runs, findings, and snapshots.

## Configuration

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=${POSTGRES_JDBC_URL:jdbc:postgresql://localhost:5432/kcops}
quarkus.flyway.migrate-at-start=true
platform.target-registry=composite
platform.audit.enabled=true
platform.audit.mode=SANITIZED
```

Local Postgres: `podman compose -f dev/compose.yaml up -d postgres`

## Migrations

Flyway scripts under `src/main/resources/db/migration/`:

| Version | Content |
|---------|---------|
| V1 | `targets`, `target_tags` |
| V2 | `assessment_runs`, `assessment_findings` |
| V3 | `health_check_runs`, `health_check_results` |
| V4 | `audit_events` |
| V5 | `environment_snapshots`, `inventory_snapshots` |

## Secrets

- Tables store **credential references** (`keycloak_credential_ref`), never client secrets.
- `SensitiveDataFilter` runs before persist and REST responses.
- Secrets remain in `mcp.credentials.*` / vault.

## Target isolation

All history queries filter by `target_id`. Cross-target leakage is a defect.

## Retention

Defaults (days): assessments 90, health checks 30, audit 180, snapshots 60.
Configured under `platform.retention.*`; enforcement job is future work.

See also [database-schema.md](database-schema.md), [audit.md](audit.md), [snapshots.md](snapshots.md).
