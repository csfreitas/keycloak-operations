# Snapshots

Environment snapshots capture a **normalized JSON summary** of a target (metadata +
server info when reachable) plus a **SHA-256** hash for change detection.

Inventory snapshots are a simple companion table (`inventory_type=basic` placeholder).

## APIs

- `POST /api/v1/targets/{targetId}/snapshots`
- `GET /api/v1/targets/{targetId}/snapshots`
- `GET /api/v1/targets/{targetId}/snapshots/changes?from={id}&to={id}`

`EnvironmentChangeService` diffs summary maps (`ADDED` / `REMOVED` / `CHANGED`).

Snapshots are discrete operational checkpoints — not high-frequency metrics.
