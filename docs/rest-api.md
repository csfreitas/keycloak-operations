# REST API

Base path: `/api/v1`.

OpenAPI / Swagger UI are on the **management** interface (default port `9001`) when
`quarkus.management.enabled=true`:

- OpenAPI: `http://localhost:9001/q/openapi`
- Swagger UI: `http://localhost:9001/q/swagger-ui`
- Health: `http://localhost:9001/q/health`

MCP tools and REST share the same application services. Tool signatures are unchanged.

## Endpoints

| Method | Path | Notes |
|--------|------|-------|
| GET | `/targets` | List authorized targets |
| GET | `/targets/{targetId}` | Target details (no secrets) |
| GET | `/targets/{targetId}/status` | Status/overview alias |
| GET | `/targets/{targetId}/environment` | Target-aware runtime discovery |
| GET | `/targets/{targetId}/inventory` | Sanitized infrastructure inventory |
| GET | `/targets/{targetId}/topology` | Pods-by-zone / pods-by-node |
| GET | `/targets/{targetId}/overview` | Overview DTO |
| GET | `/fleet` | Fleet dashboard rows |
| POST | `/targets/{targetId}/assessments` | Run + persist assessment |
| GET | `/targets/{targetId}/assessments` | History (paginated) |
| GET | `/assessments/{id}` | Assessment by id |
| GET | `/targets/{targetId}/findings` | Findings (`lifecycleStatus`, `severity`) |
| POST | `/targets/{targetId}/health-checks` | Lightweight health run |
| GET | `/targets/{targetId}/health-checks` | Health history |
| POST | `/targets/{targetId}/snapshots` | Create environment snapshot |
| GET | `/targets/{targetId}/snapshots` | Snapshot history |
| GET | `/targets/{targetId}/snapshots/changes?from=&to=` | Snapshot diff |
| GET | `/targets/{targetId}/metrics/{requests\|latency\|jvm\|database-pool\|resources}` | Semantic metrics (may be empty) |
| GET | `/audit` | Audit trail (`targetId`, `source`) |
| GET | `/events` | SSE hello + heartbeat |

## Pagination

Query params: `page` (0-based, default 0), `size` (default 20, max 100).
Response shape: `{ items, page, size, total }`.

## Errors

`McpException` → JSON `{ "code", "message" }` with HTTP status mapped from `ErrorCode`.

## Authz

Uses `TargetAuthorizationService` with `READ` (and `ASSESS` for assessment POST).
Responses pass through `SensitiveDataFilter`.
