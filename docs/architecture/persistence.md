# Persistence

PostgreSQL stores **operational state** for the Keycloak/RHBK Operations Platform:
targets (credential refs only), assessment history, health checks, audit events,
environment snapshots, and change-management lifecycle records.

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
| V6 | Assessment depth columns + finding subject + health `duration_ms` |
| V7 | `change_records` (plan / approval / apply / verification lifecycle) |
| V8 | Change-plan policy revision, target-context/integrity fingerprints and untruncated authenticated actor fields |
| V9 | Exact installation API/kind/name/UID on `targets`; complete-or-null binding constraint |
| V10 | Managed installation source/revision; `installation_discovery_runs` with target FK, actor, context hash, revision, expiry, consumption and candidate JSONB |
| V11 | Conservative registry ownership (`LEGACY_UNCLASSIFIED` / `CONFIGURATION`) and nonnegative ORM `@Version` revision; existing definitions/history preserved |

## Registry ownership and configuration transaction

The [ownership foundation](registry-ownership.md) gives configuration bootstrap explicit
ownership of new seeds only. V11 classifies all previous rows as unclassified, never
infers origin from matching IDs/content, and leaves other columns/history intact.
An existing unclassified ID colliding with configuration blocks reconciliation;
reviewed adoption is required before a populated production upgrade. No adoption
API, bulk owner rewrite or image-only rollback is supported.

Configuration-owned changes and mandatory SYSTEM audit share one transaction.
Normalized no-op does not change timestamps, revisions or audit. `registry_revision`
uses `@Version` for ORM concurrency and is distinct from `installation_revision`;
neither is an administrative grant. Database/composite mode uses only the database
after successful bootstrap, without failure/empty-table fallback to configuration.

## Installation confirmation transaction

`InstallationOnboardingService` locks the target row and rechecks caller, discovery expiry, target/connection context, binding revision and live resource UID. Binding update, run consumption and required success audit share one database transaction. Audit failure must not leave a confirmed binding. This uses required repository persistence rather than best-effort `AuditService` logging; optional audit settings do not disable it.

Configuration bootstrap preserves a managed binding while connection fields remain unchanged; changed fields invalidate it and increment its revision. The [ownership foundation](registry-ownership.md) compares the full configured Keycloak connection and infrastructure scope and records accepted changes/invalidation in mandatory transactional audit. The [earlier binding evidence](../development/installation-onboarding-2026-09-11.md) remains historical. Remote reads are not an atomic cluster snapshot, and synthetic populated migration tests do not establish safe upgrade/restore of a customer database.

## Secrets

- Tables store **credential references** (`keycloak_credential_ref`), never client secrets.
- Collection uses allowlisted fields; operational audit params are filtered and REST/MCP responses are redacted. Mandatory binding audit retains trusted actor and selected/previous UIDs, not raw cluster resources or credentials.
- Secrets are supplied through external `mcp.credentials.*` configuration; a direct Vault provider is not implemented.

## Snapshot and assessment metadata projections

New environment/inventory snapshots use the explicit metadata projection before
persistence and before configuration/runtime sub-hashes. Secret-only metadata changes
therefore do not change new-policy sub-hashes; real configuration/runtime changes do.
Historical snapshot reads and environment comparisons sanitize copies without rewriting
stored rows or digests. Old raw/representation-based sub-hashes and new sanitized hashes
are not equivalent-policy comparisons; historical derived-hash differences remain visible.
The digest still identifies retained bytes, not the displayed redacted historical view.

Assessment persistence redacts finding text, subjects, references and evidence after
rule evaluation; historical finding reads and immediate assessment results apply the
same projection. Scores/statuses, rule/target identities and original engine inputs
remain unchanged. No migration or historical purge is performed; this does not certify
all legacy tables or backups as secret-free. Details and exclusions are in the
[metadata ledger](../development/h1-metadata-trust-2026-09-19.md).

## Target isolation

Health result metadata is now filtered after engine status/count calculation and
before new summary/component retention. Historical detail/latest-detail reads filter
summary/name/message/details in copies, preserving stored rows, canonical run/target
IDs, timestamps and durations. This is not a rewrite of old health data or a change
to the health decision algorithm. See [read-boundary evidence](../development/h1-read-metadata-2026-09-19.md).

Controlled-change state uses rejection, not lossy redaction: new unsafe plans cannot
be retained, while executable legacy plans must pass admission before approval/apply
or verification. Historical reads sanitize copies only; rows, fingerprints and the raw
internal change mapper are untouched. Rejection/verification/result descriptions are
filtered before new retention. Canonical change/target/actor provenance is preserved.
Optional audit params/metadata are filtered on write and historical reads, with exact
target/trace IDs. This is not a historical purge, backup certification or rewrite of
the mandatory installation audit transaction. See [change metadata evidence](../development/h1-change-metadata-2026-09-19.md).

All history queries filter by `target_id`. Cross-target leakage is a defect.

## Retention

Shipped `application.properties` values (days): assessments 365, health checks 90, audit 90, snapshots 180. These override the lower-level ConfigMapping defaults (90/30/180/60 respectively).
Configured under `platform.retention.*`; enforcement job is future work, so these values do not currently guarantee automatic deletion.

Discovery has a ten-minute validity window, at most 100 candidates per run and 20 retained runs per target. Rediscovery replaces the same actor's prior run and removes expired runs for that target. Expired data for inactive targets is not automatically purged. Validity, cleanup and historical audit retention are different policies; discovery runs are not retained assessment-evidence replay.

See also [database-schema.md](../database-schema.md), [audit.md](../audit.md), [snapshots.md](../snapshots.md).
