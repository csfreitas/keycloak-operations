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
| POST | `/registry/targets/preflight` | [Administrative preflight 0.1.0](registry-preflight.md), disabled by default; local draft checks only, no registration/provider calls |
| GET | `/targets` | List authorized targets |
| GET | `/targets/{targetId}` | Target details (no secrets) |
| GET | `/targets/{targetId}/status` | Status/overview alias |
| GET | `/targets/{targetId}/environment` | READ authorization before target-aware runtime discovery |
| GET | `/targets/{targetId}/inventory` | Sanitized infrastructure inventory |
| GET | `/targets/{targetId}/topology` | Pods-by-zone / pods-by-node |
| GET | `/targets/{targetId}/installation` | Current binding/revision and caller capabilities; no live verification |
| POST | `/targets/{targetId}/installation/discover` | Retained expiring candidates from the approved connection/namespace |
| POST | `/targets/{targetId}/installation/confirm` | Confirm server-retained `runId` / `candidateId`; mandatory audit |
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
| POST | `/changes/plan/client-security` | Plan typed client security and OAuth/OIDC flow settings |
| POST | `/changes/plan/client-create` | Plan typed, secret-free OpenID Connect client creation |
| POST | `/changes/plan/client-enabled` | Plan enabling or disabling an existing client |
| POST | `/changes/{changeId}/approve` | Approve (bound to plan fingerprint) |
| POST | `/changes/{changeId}/reject` | Reject |
| POST | `/changes/{changeId}/apply` | Apply approved plan (`mcp.read-only=false`) |
| POST | `/changes/{changeId}/verify` | Read-back verification |

## Pagination

Query params: `page` (0-based, default 0), `size` (default 20, max 100).
Response shape: `{ items, page, size, total }`.

## Errors

`McpException` → JSON `{ "code", "message" }` with HTTP status mapped from `ErrorCode`.
Its existing mapper filters recognizable credentials in the message, retains the
code/status contract and does not include error details or cause chains. This is
not a guarantee for every framework or arbitrary exception family. The new shared
MCP error projector is a separate transport boundary; see [MCP errors](tools.md#error-shape).

## Read-output trust boundary

The [read-metadata slice](development/h1-read-metadata-2026-09-19.md) adds explicit
lossy output copies for ordinary Admin read DTOs and selected target, environment,
inventory/topology, overview and semantic-metrics responses. Recognizable
credential text and sensitive metadata keys are filtered after collection;
registered-target and declared provenance identifiers remain canonical. Typed
counts remain counts. Raw collector observations and deterministic rule inputs
are not replaced with these presentation copies, and historical reads do not
rewrite stored evidence or its hashes.

Filtering is not arbitrary-secret detection, and preserved identity text is not
certified secret-free. It does not add permissions, change controlled mutation
semantics, or generically mask installation identities. Installation confirmation
and mandatory audit keep their separate exact-identity/transactional contracts.
See the ledger for executed validation and residual limits; no H1 closure or live
RHBK/OpenShift acceptance is implied.

## Authz

Uses `TargetAuthorizationService` with explicit `READ`, `DISCOVER`, `BIND`, `ASSESS`, `PLAN`, `APPROVE`, `WRITE` and `ADMIN` permissions. Planning requires PLAN; approve/reject require APPROVE; apply requires WRITE; read-back verification requires READ. Global read-only also blocks BIND, APPROVE, WRITE and ADMIN. Change lists require an authorized `targetId`. Output filtering follows the boundaries above and is not an authorization decision.

`GET /targets/{targetId}/environment` resolves the registered target and explicitly
requires READ **before** runtime discovery, matching the MCP discovery boundary.
It does not grant candidate discovery, installation confirmation or cluster writes.

Packaged applications fail closed by default. Enable `oidc` with the intended issuer/audience and exact role/target grants. Only explicit `local-lab` and dev/test profiles allow unauthenticated local access; never expose them publicly. Caller-supplied actor/approver fields are compatibility metadata, not identity. See [identity model](identity-model.md).

### Installation confirmation

Existing persisted targets only: READ for state; READ + DISCOVER for candidates; READ + DISCOVER + BIND and global read-only disabled for confirmation. The confirmation body contains only `runId` and `candidateId`, never URLs, namespace, credentials or actor. Actor/target/expiry/revision/context/UID checks precede transactional binding, run consumption and mandatory audit. This is not a cluster write or a new target registration API. See [contract and limits](development/installation-onboarding-2026-09-11.md).

### Operations reports

The report endpoint invokes the same `OperationsReportService` as MCP tool `keycloak_generate_operations_report`. Its JSON includes explicit section completeness, persisted snapshot/health/assessment identifiers, actionable findings, semantic performance data when configured, and deterministic Markdown. `PARTIAL` describes incomplete collection and is not a health verdict. VM and Docker inventory are not currently implemented.

### Client URL planning

`POST /changes/plan/client-urls` accepts `targetId`, `realm`, `clientId`, optional complete desired sets `redirectUris` and `webOrigins`, plus optional `actor` and `idempotencyKey`. At least one set must be present. An omitted set remains unchanged; an empty set removes all of its values. Validation, normalization, diff, risk, policy, fingerprinting, and persistence are performed by the backend.

### Client security and flow planning

`POST /changes/plan/client-security` accepts `targetId`, `realm`, `clientId`, optional `pkceCodeChallengeMethod`, `standardFlowEnabled`, `implicitFlowEnabled`, `directAccessGrantsEnabled`, `serviceAccountsEnabled`, and `publicClient`, plus optional `actor` and `idempotencyKey`. At least one setting must be present. Omitted settings remain unchanged. PKCE accepts `S256` or `NONE`; service accounts require an effective confidential client. Risk and production policy are derived from the effective transition by the backend.

## CORS

Enabled for local UI origins (`localhost:5173`, `localhost:3000`). Override with `UI_CORS_ORIGINS`.
