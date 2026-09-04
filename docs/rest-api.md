# REST API

Base path: `/api/v1`.

OpenAPI / Swagger UI are on the **management** interface (default port `9001`) when
`quarkus.management.enabled=true`:

- OpenAPI: `http://localhost:9001/q/openapi`
- Swagger UI: `http://localhost:9001/q/swagger-ui`
- Health: `http://localhost:9001/q/health`

MCP tools and REST share the same application services. Change planning uses semantic, typed contracts rather than raw Keycloak representations.

## Endpoints

| Method | Path | Notes |
|--------|------|-------|
| GET | `/me` | Auth probe (`OPEN_LAB` or OIDC principal) |
| GET | `/targets` | List authorized targets |
| GET | `/targets/{targetId}` | Target details (no secrets) |
| GET | `/targets/{targetId}/status` | Status/overview alias |
| GET | `/targets/{targetId}/environment` | Target-aware runtime discovery |
| GET | `/targets/{targetId}/inventory` | Sanitized infrastructure inventory |
| GET | `/targets/{targetId}/topology` | Pods-by-zone / pods-by-node |
| GET | `/targets/{targetId}/overview` | Overview DTO (persisted signals + snapshot fields) |
| GET | `/fleet` | Fleet dashboard rows (persisted; no live N+1 metrics) |
| POST | `/targets/{targetId}/assessments` | Run + persist assessment |
| GET | `/targets/{targetId}/assessments` | History (paginated; includes completeness/confidence when present) |
| GET | `/assessments/{id}` | Assessment by id |
| GET | `/assessment-profiles` | Built-in assessment profiles |
| GET | `/targets/{targetId}/findings` | Findings (`lifecycleStatus`, `severity`) |
| POST | `/targets/{targetId}/health-checks` | Lightweight health run |
| POST | `/targets/{targetId}/operations-reports?profile=&metricsWindow=` | Generate sanitized health, assessment, platform, and performance report |
| GET | `/targets/{targetId}/health-checks` | Health history |
| GET | `/targets/{targetId}/health-checks/latest` | Latest run + components |
| GET | `/targets/{targetId}/health-checks/{id}` | Health detail + components |
| POST | `/targets/{targetId}/snapshots` | Create environment snapshot |
| GET | `/targets/{targetId}/snapshots` | Snapshot history |
| GET | `/targets/{targetId}/snapshots/latest` | Latest snapshot + summary |
| GET | `/targets/{targetId}/snapshots/{id}` | Snapshot detail + summary |
| GET | `/targets/{targetId}/snapshots/changes?from=&to=` | Snapshot diff |
| GET | `/targets/{targetId}/metrics/status` | Provider status |
| GET | `/targets/{targetId}/metrics/summary?window=` | Performance summary |
| GET | `/targets/{targetId}/metrics/{http\|database\|jvm\|cache\|authentication\|runtime\|cluster}?window=` | Category series |
| GET | `/targets/{targetId}/metrics/{requests\|latency\|jvm\|database-pool\|resources}` | Legacy aliases |
| GET | `/audit` | Audit trail (`targetId`, `source`) |
| GET | `/events` | SSE operational events (JSON) + heartbeat |
| GET | `/changes` | Change lifecycle list (`targetId`, `status`) |
| GET | `/changes/{changeId}` | Change detail (diff, risk, approval, verification) |
| POST | `/changes/plan/client-update` | Plan allowlisted client config update |
| POST | `/changes/plan/client-urls` | Plan typed redirect URI and/or Web Origin set replacement |
| POST | `/changes/{changeId}/approve` | Approve (bound to plan fingerprint) |
| POST | `/changes/{changeId}/reject` | Reject |
| POST | `/changes/{changeId}/apply` | Apply approved plan (`mcp.read-only=false`) |
| POST | `/changes/{changeId}/verify` | Read-back verification |

## Pagination

Query params: `page` (0-based, default 0), `size` (default 20, max 100).
Response shape: `{ items, page, size, total }`.

## Errors

`McpException` → JSON `{ "code", "message" }` with HTTP status mapped from `ErrorCode`.

## Authz

Uses `TargetAuthorizationService` with `READ`, `ASSESS`, `PLAN`, `WRITE`, and `ADMIN`.
Change planning requires `PLAN`; approve/reject/apply require `WRITE` (and global
`mcp.read-only=false` for Keycloak mutations). Responses pass through `SensitiveDataFilter`.

Identity A OIDC is **optional**: enable Quarkus profile `oidc` and set `OIDC_*` env vars. Default lab mode leaves REST open (`OPEN_LAB` via `/me`).

### Operations reports

The report endpoint invokes the same `OperationsReportService` as MCP tool `keycloak_generate_operations_report`. Its JSON includes explicit section completeness, persisted snapshot/health/assessment identifiers, actionable findings, semantic performance data when configured, and deterministic Markdown. `PARTIAL` describes incomplete collection and is not a health verdict. VM and Docker inventory are not currently implemented.

### Client URL planning

`POST /changes/plan/client-urls` accepts `targetId`, `realm`, `clientId`, optional complete desired sets `redirectUris` and `webOrigins`, plus optional `actor` and `idempotencyKey`. At least one set must be present. An omitted set remains unchanged; an empty set removes all of its values. Validation, normalization, diff, risk, policy, fingerprinting, and persistence are performed by the backend.

## CORS

Enabled for local UI origins (`localhost:5173`, `localhost:3000`). Override with `UI_CORS_ORIGINS`.
