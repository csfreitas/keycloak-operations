# Security

## Principles

Planned installation boundary: [ADR 0012](../adr/0012-operator-managed-portable-platform.md)
and the [draft Operator contract](operator-installation-contract.md) separate
installer, backend/UI and collector authority. Same-namespace references and
administrator-owned installation intent do not grant target access or disable
existing read-only/BIND checks. This is a design requirement, not delivered RBAC.

1. **Read-only by default** — `mcp.read-only=true`; controlled writes require target write authorization, policy, approval, apply, and verification.
2. **No secrets in tool output** — `ClientDetails` has no secret fields; mappers never copy secrets.
3. **Defense in depth redaction** — `SensitiveDataFilter` recursively redacts maps/lists/beans for keys matching password, secret, token, credential patterns.
4. **No secrets in logs** — audit and log helpers run string redaction before logging.
5. **Least privilege for production** — service accounts should use view/query (or FGAP) roles, not `realm-admin`.

## Authentication to Keycloak

The MCP server authenticates to Keycloak with **OAuth 2.0 client credentials**:

| Property | Env var | Default |
|----------|---------|---------|
| `keycloak.url` | `KEYCLOAK_URL` | `http://localhost:8080` |
| `keycloak.auth-realm` | `KEYCLOAK_AUTH_REALM` | `master` |
| `keycloak.client-id` | `KEYCLOAK_CLIENT_ID` | `keycloak-mcp` |
| `keycloak.client-secret` | `KEYCLOAK_CLIENT_SECRET` | `change-me` |

Store credentials in Kubernetes/OpenShift Secrets (see `deploy/openshift/50-secret.yaml`
template — placeholders only in git).

## Local demo privilege warning

Disposable identity/installation lab automation pins a selected local Podman endpoint and uses per-run UUID/purpose/project labels. Cleanup removes verified full container IDs and the empty owned network, never volumes/images or resources found only by a shared name. Unknown runtime results fail closed and retain the lock; bounded retries are limited to recognized read/cleanup transport failures, not creation. Process-group supervision is POSIX-only and cannot recover from host failure or deliberate session escape. This is developer-owned test lifecycle control, not an infrastructure permission granted to REST/MCP/AI callers; see the [operator contract](../../dev/identity-lab/README.md).

`scripts/setup-dev.sh` assigns broad roles including `realm-admin` for developer
convenience. That is **DEV ONLY**. Production deployments must:

- Prefer Fine-Grained Admin Permissions (FGAP) where available
- Or assign only `view-*` / `query-*` realm-management roles required by read tools
- Never commit real client secrets

## OpenShift RBAC and Secrets

The assessor `ClusterRole` deliberately omits Kubernetes `secrets`. Collectors do not require or return Secret contents. Prefer namespace-scoped Roles when cluster-wide inventory is not required.

## Platform identity and installation confirmation

The [V11 ownership foundation](registry-ownership.md) makes configuration reconciliation
explicit and audited: no legacy takeover, no-op without writes, mandatory atomic
SYSTEM audit and no database/composite fallback. Registry ownership/revision is not
target authorization or the installation binding counter. Changed connections cannot
silently restore an invalidated managed binding from stale explicit configuration.
Populated upgrade/adoption and old-binary/raw-SQL writer limits remain visible gates.

The [administrative preflight](../registry-preflight.md), separate from operational
target grants, is disabled by default and requires an exact verified
issuer/subject/role/REST-client match. It authorizes before application parsing and
local registry checks; candidate URLs remain inert data. No target/provider request,
credential resolution, audit/write, grant or read-only/BIND exception exists. A
successful local draft is not destination approval or registration. See
[ADR 0013](../adr/0013-registry-preflight-before-registration.md).

Platform caller authentication and target grants are distinct from credentials used to read Keycloak or a cluster. The [identity model](../identity-model.md) describes OIDC and exact target permission grants. The UI implements OIDC/PKCE with in-memory tokens; this is not a claim that the complete live browser/IdP/cluster workflow has passed acceptance.

Deep-link restoration is a bounded, one-use navigation hint, not a grant: only known internal paths are accepted, and token/query/fragment material is excluded from storage. Root callbacks and existing OIDC validation remain unchanged. Target children are not mounted until the authorized overview succeeds; backend authorization remains authoritative for every operation.

The [browser-session boundary](../development/d1-browser-session-2026-09-19.md) adds
generation-scoped REST/SSE cancellation and rejection of late token/body/event results.
A current 401, logout or failed identity setup invalidates local transport and removes
protected routes. A target 403 is not authentication loss; an old 401 cannot sign out a
new generation. Cancellation cannot undo an already dispatched server operation.
This does not add token introspection, backchannel logout or cross-tab revocation:
valid copied JWTs may remain usable until expiry, and already admitted SSE uses its
initial target grants for up to five minutes, including beyond token expiry.

The [local browser-negative validation](../development/d1-browser-negatives-2026-09-19.md)
isolates wrong issuer, wrong audience and expiration using genuinely signed local IdP
tokens; each receives a native backend 401, while the valid control receives 200.
The development-only observer verifies signatures against a fixed expected JWKS and
exposes only booleans/status. Expiry is a bounded transport delay, not ordinary
renewal. Two public PKCE lab clients add no platform permission. This supplies local
`/me`/protected-UI evidence, not universal endpoint, RHBK or revocation acceptance.

For existing-target installation operations, reading state requires READ; discovery additionally requires DISCOVER; confirmation additionally requires BIND and is blocked by global read-only. ADMIN or WRITE does not implicitly grant BIND. Disabling global read-only affects other already-granted write operations, so setup requires explicit administrator review, not an automatic UI change.

Only server-retained candidates from an approved target connection/namespace may be confirmed. The service checks actor, target, expiry, revision and connection context, then revalidates the exact resource UID. It does not accept a caller URL, credential, namespace or actor in the confirmation payload. Cluster clients never fall back to ambient kubeconfig. Discovery metadata is untrusted input, not proof that a generic workload is Keycloak or belongs to the target URL.

Binding persistence, run consumption and mandatory success audit share a database transaction under a target row lock; optional operational-audit configuration cannot disable that success record. This is a platform metadata write, not a cluster mutation or an atomic remote snapshot. The UI acknowledgment does not prove human presence or two-person approval. See [persistence](persistence.md), [audit](../audit.md) and [validation/remaining limits](../development/installation-onboarding-2026-09-11.md).

## Optional reference-agent boundary

Planned [AGT2 access-aware assistance](access-aware-operations-assistant.md) requires
fine-grained read policy shared by UI/REST/MCP and all indirect outputs (aggregates,
history, reports, exports and events), not just an agent allowlist. Current target
grants and credential redaction are not realm/resource/PII authorization. Identity A,
the approved client and Identity B access are distinct; source service-account
permissions do not stand in for the human's rights. Restricted context and provider
disclosure need separate acceptance before more tools are exposed. The first opt-in
[configuration-read channel](../configuration-reads.md) now enforces exact
role/client/channel/resource/field grants with pinned identities, no local-lab bypass
and no grants by default. It does not migrate or restrict legacy target roles, nor
complete fine-grained control of all domains or external-model disclosure.

The optional [reference profile](../../dev/reference-agent/README.md) adds a fixed
report-tool/operator-target host boundary, not new server grants or a model runtime.
It validates compact-report facts and bounded report-bound finding details, including
scope/assessment identity, omission counters, source indices and JSON references.
Sanitized finding text, evidence keys/values and URLs remain untrusted data. They must
not be placed in privileged instruction messages or followed as tool instructions.
Prompt instructions do not enforce access: the host must not expose the raw adapter
or other tools, and backend READ/ASSESS remains authoritative. Structural explanation
checks never establish semantic truth, secret/PII safety or approval; no external
disclosure is enabled. Details are sanitized, potentially incomplete report copies,
not raw retained evidence or universal credential/PII detection. Entire oversized or
unsupported findings are omitted explicitly; no hidden truncation becomes a fact.

## Network and pod hardening

Deploy manifests set:

- `runAsNonRoot: true`
- `readOnlyRootFilesystem: true`
- `allowPrivilegeEscalation: false`
- drop all capabilities
- NetworkPolicy limiting ingress/egress

## Sensitive key detection

Structural filtering recognizes normalized secret/password/token/private-key suffixes
and credential-bearing field names. Flags such as `resetPasswordAllowed` are not
secret fields. The existing `redact` map/list contract remains structural: it is also
used by controlled-change state and must not silently rewrite desired configuration.

`redactMetadata` is an explicit **lossy output/persistence projection**, currently
used by reports, snapshots, historical environment comparisons, assessment
findings/results, change output copies and optional audit payloads. It additionally processes JSON-shaped map/list string leaves,
typed DTOs, credential headers/API-key fields and recognizable credential text
(assignments, authentication/cookie headers, compact JWT/JWE, private-key PEM and
URI userinfo/query credentials). Secret-bearing dynamic keys receive opaque unique
labels, preserving entry counts without overwriting existing labels. Numeric/Boolean
facts, nulls, ordinary identifiers and policy prose remain unchanged.

Filtering happens after deterministic assessment evaluation, never in rules or
desired/applied change state. New snapshot hashes use sanitized inventory; old
entities/hashes are not rewritten. Historical read projections do not certify that
old database bytes were secret-free. See the [metadata ledger](../development/h1-metadata-trust-2026-09-19.md).

The [controlled-change slice](../development/h1-change-metadata-2026-09-19.md)
adds admission after target authorization: unsafe caller metadata is rejected before
provider reads/persistence, and unsafe observed/executable historical state requires
replanning. It never replaces applied values with redaction markers. The full client
representation is checked because updates resubmit unselected metadata; known secret
and registration-access-token fields are excluded from inspection and cleared before
sending. Unsafe read-back remains an inconclusive verification failure. REST
`McpException` and controlled-change MCP messages are projected, not arbitrary exception
families. Audit target/trace IDs and change target/actor provenance remain canonical;
trusted identity is not a guarantee that arbitrary issuer/subject text is secret-free.

This is not arbitrary-secret detection, a universal object-graph sanitizer or
prompt-injection prevention. Unlabeled/encoded secrets, unsupported credential
formats, raw arrays/sets/custom Java scalar containers and other legacy output/tool
families require separate review. Do not put secrets in identities, names,
annotations or change descriptions. Architecture remains sensitive after redaction;
sharing or external-model access still needs authorization.

## Assessment / MCP output

### Read-model and tool error projection

The [read-boundary slice](../development/h1-read-metadata-2026-09-19.md) applies
metadata filtering to the six ordinary Keycloak read services and to selected
target/fleet/overview/inventory/metrics REST/MCP output copies. Core inventory and
metrics services remain raw for evidence, rules and associations. Health metadata
is projected after engine evaluation before new persistence, and historical details
are filtered without changing rows, canonical IDs or typed status. SSE event scope
and installation confirmation/mandatory audit are unchanged.

`ReadMetadataProjection` preserves only explicit transport-contract identity paths;
caller data cannot select exclusions. Typed topology and overview count maps retain
their integers while dynamic keys are projected collision-safely. Arbitrary numeric
or Boolean values under credential keys remain masked. Registered target/correlation
identities stay exact, but provider resource identifiers resembling credentials may
be redacted and cease to be usable as follow-up handles. No filtered output is an
authoritative mutation input; do not put secrets in any identifier.

`GET /api/v1/targets/{targetId}/environment` now requires READ before discovery;
global authentication alone was insufficient. This is observation of a registered
target, not installation candidate discovery, and it grants no DISCOVER/BIND capability.

Ten non-change MCP families share `McpToolErrorProjector`: known domain codes retain
recognizable-credential-filtered explanations, prebuilt tool exceptions are copied
without causes/suppressed exceptions, and unknown checked/runtime exceptions expose
fixed text only. Controlled-change MCP retains its preceding equivalent boundary.
Errors in wrapper `finally` instrumentation, generic REST exception types and
third-party/internal logging are not covered by a blanket diagnostic-safety claim.
Browser/revocation and H1/D1 acceptance remain separate gates.

Assessment MCP tools return **compact** DTOs (scores, finding ids, evidence *keys*
only) and always pass through `SensitiveDataFilter`. Full evidence maps are not
dumped to LLM tool responses.

Operations reports enforce `ASSESS` authorization, sanitize both structured and Markdown output, and replace provider failures with safe section statuses instead of returning raw exception messages. AI callers cannot supply provider credentials, endpoints, arbitrary Admin REST, arbitrary PromQL, or policy decisions.

## Bounded provider responses and failure projection

The management-health and Prometheus HTTP clients resolve configured endpoints,
reject URL userinfo/query/fragment and non-HTTP(S) schemes, and do not follow
redirects. Prometheus authentication remains a separately resolved credential;
the change does not broaden target permissions or make endpoints caller inputs.
Management uses default TLS validation. Prometheus retains its existing explicit
`trustInsecure` configuration; this is not a recommended default or a new grant.

`BoundedBodyHandler` limits received bytes before JSON parsing and cancels the
subscription on excess size or a body-completion deadline, including chunked or
stalled responses. The management cap is 64 KiB; the Prometheus cap is 1 MiB.
Both clients reject duplicate JSON fields and trailing documents and impose
parser nesting/string/number limits. These are per-response controls, separate
from connection/request timeouts. Compound report, snapshot, assessment, health,
inventory and metrics collection also inherit a target-bound monotonic deadline:
30 seconds by default, at most 120 seconds, never renewed by a nested call. This
is cooperative collection time, not a hard deadline for persistence, rendering,
DNS/TLS or already-blocked I/O; it is not a fleet-wide budget.
See [health contracts](../health-check.md) and
[observability contracts](observability.md) for projection and cardinality limits.

Management output contains normalized statuses, fixed reason codes and bounded
check counts, not endpoint URLs, raw bodies, exception text, nested check names or
arbitrary check data. Health-engine failures are UNKNOWN with fixed reasons;
infrastructure health projections omit raw collection-warning messages.
Invalid assessment-profile failures use a fixed public message; selected evidence
and metrics collector logs omit exception text and causes. Prometheus parser/transport diagnostics do not copy
raw backend errors or payloads into logs/results; malformed envelope/schema or
backend-reported incomplete responses cannot supply a successful semantic metric.

The subsequent inventory correction normalizes `CollectionWarning` at construction
and deserialization: fixed code-specific text and allowlisted resource identifiers
replace caller/provider details; unknown identifiers become null and fail closed.
The inventory collector no longer logs the caught node-zone exception. Legacy
stored payload bytes are not migrated, while rehydrated warning representations
are normalized. This is not blanket sanitization of workload names, arbitrary
metadata, metric labels or every historical payload.

`InventoryService.toEvidence` suppresses conclusions dependent on failed/missing
sections and marks incomplete collection for the assessment pipeline. Temporal
metrics validation rejects incomplete evaluation grids and invalid samples before
semantic use. The shared collection deadline preserves validated earlier facts
and marks interrupted/unexecuted observations partial or UNKNOWN. Raw scrape
freshness, complete source/instance coverage and capability reconciliation remain
separate work. H1 acceptance remains
open; see the [inventory/temporal evidence](../development/h1-evidence-temporal-2026-09-18.md),
[failure/bounds evidence](../development/h1-failure-bounds-2026-09-18.md) and the
[dated dependency review](../development/h1-dependency-review-2026-09-18.md).

### Cluster response boundary

Integrated environment, inventory, candidate and ServiceMonitor reads now use a
fixed-resource reader over the approved Fabric8 client's existing authentication
and TLS configuration. The client factory disables redirects and request retries;
it does not enable insecure TLS, add permissions or read Secret contents. Explicit
target/cluster namespace equality is checked before namespaced collection; the
underlying client's default namespace is neither authority nor a fallback.

Each response is capped at 1 MiB, with a monotonic deadline of at most five seconds
(or the lower configured request timeout) spanning headers, body and validation.
JSON parsing rejects duplicates/trailing documents and bounds depth, names,
strings, numbers and container size. Inventory lists allow at most 500 objects;
ServiceMonitor lists and combined candidate discovery have 100-object limits.
API-group discovery allows 100 groups and 32 versions per group. Excess, truncated,
paginated, malformed, foreign-namespace or duplicate-identity observations cannot
supply successful complete evidence.

Raw list envelopes, metadata and explicit items are validated before model
conversion; generated empty-list defaults cannot repair missing data. Typed-list
entries may inherit absent API/kind fields only from their validated outer type,
never overwrite explicit null/conflicting fields. Individual GETs require their
own matching type and requested identity. `/apis` failures cannot confirm a
runtime; a separate `/version` failure preserves independently observed runtime
with an unknown version. Public reader/discovery failures use fixed diagnostics
without raw response text or exception causes.

Interruption and late results are rejected, with cancellation attempted for failed
transport/body consumption. The shared target-bound collection budget additionally
caps nested inventory/report reads, without background collection threads or new
permissions. These remain cooperative limits, not a hard real-time
resource-termination guarantee, complete
Kubernetes schema validator or blanket metadata sanitization. Independent known
sections may survive as a partial inventory; failed candidate discovery publishes
no partial candidate set. The
[inventory-envelope ledger](../development/h1-inventory-envelopes-2026-09-18.md)
tracks implementation and validation status, not live Kubernetes/OpenShift
compatibility certification.

### Scoped Admin response and diagnostic boundary

The dedicated scoped-collection Admin client requires an active matching target
scope and retains its own cache, separate from ordinary Admin operations and
controlled writes. Request-local connection, pool-acquisition and socket timeouts
are capped at five seconds or the shorter remaining collection budget. Body reads
retain the captured budget; failed/rejected responses are aborted rather than
drained. No timeout worker continues collection in the background. Apache's pool
maintenance thread is not a collection worker.

The transport caps each body at 1 MiB, including chunked responses, and the JSON
provider limits decoded Admin content to 1 MiB and decoded OAuth token content to
64 KiB. Gzip decoding is explicitly bounded at 1 MiB before model parsing; the
token-specific bound still applies afterward. Duplicate fields, trailing
documents, null/malformed roots and invalid scalar types are rejected before
conversion. Parser limits cover nesting (32), property names (256), string values
(32,768), number text (128) and container entries (1,024). Realm/client lists have
a 500-item cap and reject duplicate identities, missing identifiers and invalid
members without returning a valid prefix or fabricated empty list. Explicitly
empty lists remain valid observations.

Schema checks cover the token, server-info, realm and client representations used
by collection; selected fields are validated, not the entire Admin API schema.
Unknown fields remain compatible within the structural bounds. Optional missing
metadata remains unknown; a returned server feature must explicitly carry its
boolean enabled state. These controls do not establish server-side pagination
completeness, source coverage, requested-versus-returned identity reconciliation
for every resource, or general metadata redaction.

`CollectionDiagnosticPolicy` rejects scoped collection when any selected
RESTEasy/Apache DEBUG or TRACE diagnostic channel is enabled, including inherited
levels. It checks the actual logging APIs before credential resolution/client
construction and again at request/body boundaries. The category set covers the
selected engine's wire, headers, parser, connection, authentication and TLS
diagnostics. It does not change logger levels or suppress global diagnostics.
Native Apache authentication-challenge handling and cookie processing are
disabled on scoped requests; OAuth client credentials and bearer-token filters
remain unchanged. This avoids the unnecessary native challenge/cookie paths that
can log untrusted response headers even at WARN.

Scoped adapter failures retain useful error codes but omit provider bodies,
resource identifiers, nested causes and arbitrary error details. Selected
collection warning logs no longer include provider-supplied realm names. Ordinary
Admin/controlled-write transport and its prior error contracts are unchanged.
Operator logging changes during already-running I/O are not atomic with admission
checks; pool-maintenance/cleanup diagnostics, alternate transport providers,
unlisted channels and arbitrary application metadata are not covered by a blanket
log-safety guarantee. Dependency changes require review of the category set and
RESTEasy engine integration. See the
[Admin-boundary ledger](../development/h1-admin-boundaries-2026-09-18.md) for
recorded validation and remaining H1 limits; this is not live RHBK/OpenShift
compatibility certification.

## Dependency maintenance boundary

The [dependency remediation](../development/h1-dependency-fix-2026-09-18.md)
updates the Quarkus BOM/MCP patch and UI router/build/test chain without changing
identity, grants, trusted endpoints, read-only defaults or schemas. The lockfile
records exact UI resolutions; Node 24 LTS is selected in CI and the image builder.
CI now audits the locked UI dependency graph and fails on moderate-or-higher npm
advisories, including development dependencies. The dated zero-advisory npm result
is not a backend, container-image, operating-system or JDK vulnerability scan.
Backend acceptance here combines exact packaged versions, primary advisory
review and local regression, not a vulnerability-free certification. Recheck
maintenance status and advisories before the presentation freeze or release.
