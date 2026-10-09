# History screen (conceptual)

Route: `/targets/{targetId}/history`

## Purpose

Timeline of operational change for a Target.

## Streams

| Stream | API |
|--------|-----|
| Assessment history | `GET /api/v1/targets/{targetId}/assessments` |
| Health check history | `GET /api/v1/targets/{targetId}/health-checks` |
| Configuration / infrastructure changes | `GET /api/v1/targets/{targetId}/snapshots/changes` |
| Audit events | `GET /api/v1/audit?targetId=…` |

All lists are paginated and filtered by `targetId` (multi-target isolation).

## Planned interactions (not current acceptance)

- Pick two assessments → score / finding comparison (`ComparisonService` is currently an unsupported stub)
- Pick two snapshots → environment change list (`EnvironmentChangeService`)
- Filter audit by source (`MCP` / `WEB` / `REST` / `SCHEDULED` / `SYSTEM`), tool, status, time range

The implemented screen lists assessments, health, snapshots and audit by target. Snapshot summary diff exists in `EnvironmentChangeService`, but full coverage-aware drift and report replay are separate [D3E](../milestones/d3e-evidence-replay.md)/[P1](../milestones/p1-iam-continuous-observability.md) deliveries. Planned filters/interactions above are not all current UI controls.
