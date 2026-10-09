# H1 — Read metadata and environment authorization, 2026-09-19

Status: bounded local corrective slice. H1/D1 acceptance remains open. No live
RHBK/OpenShift, browser/revocation, production-write or agent-safety certification.

## Demonstrated causes and corrections

Pre-fix regressions reproduced recognizable credentials escaping ordinary read DTOs,
health retention/history and non-change MCP errors. Selected transport projections
also needed to distinguish untrusted metadata from canonical identity and typed facts.
Inspection plus a denied-target regression found a separate authorization defect:
REST `/api/v1/targets/{targetId}/environment` could reach discovery without asserting
the caller's target READ grant. The correction checks READ before discovery; this
observes an already registered target, not candidate discovery or installation binding.
No new permission, default grant or ambient infrastructure access is introduced.

- Realm, client, user, group, role and server-info services filter read DTO copies with
  the existing metadata filter. Authorization/provider ordering and raw provider
  representations are unchanged. Ordinary prose and typed facts remain intact.
- `ReadMetadataProjection` handles selected REST target/overview/fleet/inventory/metrics
  and MCP target/inventory/metrics outputs. Canonical target, history correlation and
  snapshot-hash paths are explicit constants. Exact validated installation binding
  remains operational identity, not lossy display metadata. Raw collectors, rules,
  discovery association and executable state are not replaced with these projections.
- Known `Map<String,Integer>` topology/overview fields receive collision-safe key
  filtering while retaining their observed integer counts. A separate pre-fix
  regression reproduced a conversion error when a legitimate node/zone name matched
  a credential-key pattern. The repair is restricted to these typed maps; arbitrary
  details/tags must not regain numeric or Boolean credentials.
- Health summary, component names and details are filtered after engine evaluation
  before new retention, and on historical output copies. Canonical IDs, typed status,
  timestamps/duration, source entities and SSE authorization stay unchanged. Existing
  rows are not rewritten and filtering cannot turn missing evidence into health.
- `McpToolErrorProjector` covers ten non-change families: assessment, client, group,
  inventory, metrics, realm, role, server, target and user. Domain error codes survive
  with filtered messages; null messages use safe fallbacks. Prebuilt tool errors lose
  their original causes/suppressed exceptions. Unexpected checked/runtime failures use
  fixed `INTERNAL_ERROR: tool operation failed`, not arbitrary provider messages.
  Auth ordering, success handling and finally audit/metrics remain in place.

Provider resource names/IDs (realm/client/user/group/role) are untrusted metadata,
unlike registered target/correlation IDs. Credential-shaped values may therefore be
masked and no longer be usable follow-up handles. A dedicated regression documents
this deliberate limitation and verifies that the original lookup/representation is
not changed. Output projection is not admission or configuration remediation.

Requirements: SEC-CRED-002/004/005, SEC-AUTHZ-001, SEC-MULTI-001, NFR-TEST-001/002,
NFR-CHANGE-001. These are scoped evidence inputs, not complete acceptance.
Architecture/contracts: [security](../architecture/security.md),
[multi-target](../architecture/multi-target.md), [persistence](../architecture/persistence.md),
[health checks](../health-check.md), [REST](../rest-api.md), [MCP tools](../tools.md).

## Source and version decision

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`; accumulated dirty/untracked work preserved. No commit,
push, rebase, release, publication or external cluster access. The
[source/test/runtime manifest](evidence/h1-read-metadata-2026-09-19.json) compares with
the preceding change-metadata slice: **593 source inputs**, aggregate
`5671d064a7d8d79001ae633cbfa25ec1b1f40ea6e2634611b6b35ad43d54cf3f`;
**24 existing inputs changed, six added, none removed**. Additions are two production
helpers and four test classes. Existing changes include the identity runner and guide.
Hashes are unsigned local consistency evidence, not signed build attestations.
The manifest also records **127 Maven report hashes**, **12 run logs** (including
all five pre-fix runs), **three final package hashes** and **two runtime inventories**.

Backend/application/MCP/OpenAPI remain **0.8.1-SNAPSHOT**; UI/package-lock root metadata
remain **0.8.1-dev.0**; report schema **1.1**, migrations **V1–V10**. No dependency,
UI, deployment, grant, default read-only or persistent schema change. This unreleased
correction tightens output/error handling and closes an existing target-authorization
gap; exact API values may change when metadata resembles a credential. Historical
rows/backups are not purged. AGENTS' output-versus-authoritative-state invariant is
unchanged. Context, roadmap, affected architecture, H1/D1 milestones, operator guide,
Unreleased notes and explicit version decision are updated; dated ledgers are preserved.

## Backend validation

Java **21.0.10**, selected per command through `JENV_VERSION=21 jenv exec`; no global
version change. Raw logs: `/private/tmp/kcops-h1-read-metadata-*-20260919.log`.
They may expire; manifest hashes/excerpts preserve recorded local evidence.

| Execution | Observed result |
|---|---|
| Fresh `mvn clean verify` baseline | **1469 passed**, 9 opt-in ITs skipped, BUILD SUCCESS; final tool exit code not retained |
| Pre-fix ordinary-service regressions | 55 invocations, **33 failures**, 0 errors; exit 1 |
| Pre-fix generic MCP regressions | 122 invocations, **102 failures**, 0 errors; exit 1 |
| Pre-fix read-surface regressions | 21 invocations, **19 failures**, 0 errors; exit 1 |
| Pre-fix health regressions | 14 invocations, **6 failures**, 0 errors; exit 1 |
| Additional pre-fix surface/count regressions | 23 invocations, **19 failures, 1 error**; exit 1 |
| Core focused green run (service/MCP/health) | **191 passed**; exit 0 |
| Combined four-class focused green run | **214 passed**; exit 0 |
| Final `mvn clean verify`, including review-added provider-ID regression | **1684 passed**, 9 opt-in ITs skipped; exit 0, **215 new invocations** |

The red runs preceded their corresponding fixes and remain failures in the evidence.
The additional surface error was the real typed-count conversion defect, not a fixture
failure. Baseline BUILD SUCCESS completed **16:38:18 −03**; final clean verification
completed **16:51:01 −03**. Final new-test distribution: `ReadServiceMetadataTest`
**56**, `GenericToolsErrorProjectionTest` **122**, `ReadSurfaceMetadataBoundaryTest`
**23**, `HealthCheckMetadataTest` **14**.

Regressions cover canary credential formats and dynamic keys; ordinary safe prose;
authorization before provider access; success/error/null handling; untouched raw
representations/rows; status/count/null fact preservation; canonical correlation;
typed count collisions; historical UNKNOWN; denied/missing/foreign runs; and generic
checked/runtime errors without causes. Independent reviews found no scoped blocker.
Canary tests use mocked providers and controlled Quarkus/PostgreSQL fixtures, not
credential injection into real environments or actual remote administrative writes.

Reproduce with `env JENV_VERSION=21 jenv exec mvn clean verify`; the four classes above
are the focused regression set. `node --check scripts/validate-identity-lab.mjs` passed.

## Final-package local validation

The real local identity runner adds **five** environment checks: own-target success
for A/B with exact target ID, cross-target denial for A/B, and denial for an unmapped
signed principal. This expands default from 70 to **75** checks and metrics from 75
to **80**. Installation remains **83**. Default READ/ASSESS identities are not granted
setup or mutation access. These checks validate the READ guard with actual OIDC.

| Runner | Observed result |
|---|---|
| `bash scripts/validate-installation-lab.sh` | **83 checks**, JWT scan and owned cleanup passed; exit 0 |
| `bash scripts/validate-identity-lab.sh` | **75 checks**, JWT scan and owned cleanup passed; exit 0 |
| `bash scripts/validate-identity-lab.sh --metrics` | **80 checks**, JWT scan and owned cleanup passed; exit 0 |

Installation uses real local OIDC/PostgreSQL and a synthetic cluster API. Identity/report
labs use Community Keycloak **26.7.1**, PostgreSQL **16**, and Prometheus **2.55.1** where
enabled. Cached RHBK **26.6** is not exercised and Community is not RHBK certification.
No UI tests/build, browser negatives/revocation, actual RHBK/OpenShift, opt-in live ITs,
CI, native/image builds or comprehensive dependency scans were run in this slice.

## Local hygiene and handoff

Inventory at **16:42:12 −03**, before runtime execution, found zero containers/volumes,
default network only, four cached reusable images unchanged, eleven fixture ports free
and shared lock absent. Runners pin `podman-machine-default-root`, use named per-run
owned disposable resources and verify full-ID cleanup. Unknown cleanup retains the
lock; no VM restart or global prune is permitted by these runners. No images or retained
volumes are deleted. Removed tmpfs database/TSDB data are disposable and not recoverable;
diagnostic logs and reusable images are retained.

Independent final inventory at **17:00:59 −03** confirms **zero containers/volumes**,
default network only, four cached image IDs unchanged, all eleven fixture ports free
and the shared lock absent. Diagnostic directories: installation
`/private/tmp/kcops-installation.bveQ5l`, default `/private/tmp/kcops-identity.kudYq4`,
metrics `/private/tmp/kcops-identity.XwLsz5`.

Final consistency checks matched **737 referenced hashes**, including all 593 source
inputs and their aggregate. Offline documentation review passed for **137 Markdown
documents, 849 local links and 15 milestone specifications**, with zero errors/warnings;
the checker passed six self-test groups. `git diff --check` passed. These checks do
not validate external URLs or close functional milestone acceptance.

## Remaining work

- Browser negative cases and session revocation, then the AGT1 reference-agent prototype.
  Backend signed-token denial is not browser lifecycle or immediate revocation proof.
- Unknown/encoded/unlabelled secrets and universal log/control-character escaping are
  not solved by recognizable-metadata filtering. Fixed canonical identity/binding
  fields intentionally remain exact; do not place secrets in those fields.
- Installation onboarding/confirmation metadata, mandatory binding audit and operational
  state are not universally filtered by the read helper. They keep separate validation
  and authorization contracts; no broad unsafe substitution is introduced.
- Generic REST exceptions, wrapper-finally instrumentation failures, framework/internal
  logs and third-party diagnostics are outside the new MCP error boundary. Health
  engine diagnostics can still log component names; built-ins use fixed names, while
  arbitrary custom checks require a separate review.
- Normalized all-source authorization/freshness/coverage, comprehensive scanning,
  agent adversarial evaluation, retained-evidence replay and production-write recovery
  remain separate acceptance work. H1/D1 are not closed by this slice.

D2 still requires the explicitly approved dedicated RHBK/OpenShift lab; no cluster is
needed for the immediate next local slice.
