# Infrastructure screen (conceptual)

Route: `/targets/{targetId}/infrastructure`

## Purpose

Show OpenShift/Kubernetes posture for a Target using **normalized snapshots**, not raw cluster dumps.

Current workload collection requires exact installation binding; the separate `/targets/{targetId}/installation` screen supports existing-target confirmation. Conceptual drift/layout details below do not imply full coverage-aware history or live network/certificate validation. See [inventory](../infrastructure-inventory.md).

## Data

Prefer `EnvironmentSnapshot` / inventory fields:

- Platform / OpenShift or Kubernetes version
- Nodes and availability zones
- Pod distribution (ready/total)
- Requests / limits summaries
- HPA min/max
- PDB
- topologySpreadConstraints
- Configuration hashes (`keycloakConfigurationHash`, `infrastructureConfigurationHash`)

APIs:

- `GET /api/v1/targets/{targetId}/snapshots`
- `GET /api/v1/targets/{targetId}/snapshots/changes`

## UX rules

- Never render Secret contents or managedFields/status noise
- Highlight drift via `EnvironmentChangeService` diffs
- Live cluster refresh is an explicit operator action, not page-load default
