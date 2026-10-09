# Target overview (conceptual)

Route: `/targets/{targetId}`

## Purpose

Single-pane summary for one Keycloak/RHBK environment without running a full assessment on every load.

## Data source

`GET /api/v1/targets/{targetId}/status` → `TargetOverviewService`

Prefer persisted summaries (latest health + latest assessment + latest snapshot) over live Admin API chatter.

## Sections

| Tab / section | Content |
|---------------|---------|
| Overview | product, version, runtime, namespace, health, assessment score |
| Health | latest health check status + component results |
| Assessment | overall + category scores; link to findings |
| Performance | request rate, p50/p95/p99 via semantic metrics REST |
| Infrastructure | pods ready/total, zones, HPA/PDB summaries from snapshot |
| Installation | Existing-target candidate review/confirmation with explicit READ/DISCOVER/BIND gates |
| Report | On-demand JSON/Markdown operations report; retained replay planned |
| Findings | critical/high/medium/low counts + drill-down |
| History | assessments, health checks, snapshots, audit |

## UX rules

- Overview count fields are nullable observations: negative/malformed counts, missing installation, failed workload/pod collection and unavailable zone labels must not become `-1` or invented zero capacity. The UI renders these as `Unknown / not collected`; valid observed zeros remain zero.
- The service derives these gaps from the stored inventory and its resource-specific warnings. A pod warning hides pod/zone counts but need not hide observed replicas; unrelated HPA warnings do not hide counts. Cluster-wide zone counts are never substituted for installation topology. Missing or legacy unnamed workload evidence is treated conservatively as unknown.

- Do not call Prometheus or Keycloak Admin API from the browser
- Poll metrics endpoints (15s–60s); use SSE only for discrete events
- Hide credential refs and observability endpoint refs from operators unless needed for troubleshooting (backend-only by default)
