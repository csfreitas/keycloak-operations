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

## Interactions

- Pick two assessments → score / finding comparison (`ComparisonService`)
- Pick two snapshots → environment change list (`EnvironmentChangeService`)
- Filter audit by source (`MCP` / `WEB` / `REST` / `SCHEDULED` / `SYSTEM`), tool, status, time range
