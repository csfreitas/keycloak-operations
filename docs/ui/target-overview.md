# Target overview (conceptual)

Route: `/targets/{targetId}`

## Purpose

Single-pane summary for one Keycloak/RHBK environment without running a full assessment on every load.

## Data source

`GET /api/v1/targets/{targetId}/overview` → `TargetOverviewService`

Prefer persisted summaries (latest health + latest assessment + latest snapshot) over live Admin API chatter.

## Sections

| Tab / section | Content |
|---------------|---------|
| Overview | product, version, runtime, namespace, health, assessment score |
| Health | latest health check status + component results |
| Assessment | overall + category scores; link to findings |
| Performance | request rate, p50/p95/p99 via semantic metrics REST |
| Infrastructure | pods ready/total, zones, HPA/PDB summaries from snapshot |
| Findings | critical/high/medium/low counts + drill-down |
| History | assessments, health checks, snapshots, audit |

## UX rules

- Do not call Prometheus or Keycloak Admin API from the browser
- Poll metrics endpoints (15s–60s); use SSE only for discrete events
- Hide credential refs and observability endpoint refs from operators unless needed for troubleshooting (backend-only by default)
