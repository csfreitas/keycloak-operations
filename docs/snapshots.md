# Snapshots

Environment snapshots capture a **normalized JSON summary** of a target (metadata +
server info when reachable) plus a **SHA-256** hash for change detection.

Inventory snapshots are a companion table with `inventory_type=infrastructure`, storing sanitized inventory sections and collection warnings. `SnapshotService` also stores configuration/runtime hashes. These are operational checkpoints, not immutable retained assessment inputs or independent operations-report artifacts.

## APIs

- `POST /api/v1/targets/{targetId}/snapshots`
- `GET /api/v1/targets/{targetId}/snapshots`
- `GET /api/v1/targets/{targetId}/snapshots/changes?from={id}&to={id}`

`EnvironmentChangeService` diffs summary maps (`ADDED` / `REMOVED` / `CHANGED`).

The [metadata projection](development/h1-metadata-trust-2026-09-19.md) filters
recognizable credential text and secret-bearing dynamic keys before new persistence
and configuration/runtime hashing. Read/detail/diff paths project historical copies;
stored bytes and digests are not rewritten. New sanitized hashes are not equivalent
to old raw/representation-based hashes, so historical derived-hash differences remain
visible. Redaction can suppress secret-only metadata differences; this is intentional
presentation behavior, not proof that underlying confidential configuration is unchanged.

Snapshots are discrete operational checkpoints — not high-frequency metrics.

Current comparison is a recursive summary-map diff, not coverage-aware semantic drift: a missing field can reflect failed collection, not deletion. Current normalization drops volatile fields including `uid`; the hash must not be used as installation identity, signature or proof of unchanged membership. The [D3E milestone](milestones/d3e-evidence-replay.md) defines identity-preserving retained evidence and replay; [P1](milestones/p1-iam-continuous-observability.md) owns full drift semantics.
