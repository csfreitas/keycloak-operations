# H1 — Controlled-change/audit metadata, 2026-09-19

Status: bounded local corrective slice. H1/D1 acceptance remains open. No live
controlled-write, real RHBK/OpenShift, browser/revocation or agent-safety certification.

## Demonstrated cause and correction

The previous output-only metadata projection deliberately did not change executable
plans. Three pre-fix regression runs showed recognizable credentials entering plan
descriptions/baselines, audit payloads/history and scoped error messages. Merely
applying lossy redaction to operational state would replace requested values or hide
drift. This slice therefore separates admission from presentation:

- `ChangeMetadataGuard` checks all five planning entry points after target authorization
  and before idempotency lookup/provider access. Recognizable credential content in
  realm, resource ID, idempotency key or desired metadata is rejected with a fixed
  `INVALID_ARGUMENT`; rejected input is not retained as a plan.
- Raw observed client metadata is checked before normalizers and extraction. The full
  representation matters because an update resubmits fields outside the selected
  diff. The known `secret` and `registrationAccessToken` fields are excluded from the
  copied observation and explicitly cleared before outbound create/update. Other
  unsafe metadata fails with `CHANGE_CONFLICT`/`REPLAN_REQUIRED`, never a substituted
  redaction marker. Observed objects are not mutated by admission.
- Executable historical plans undergo the same state check before approval/apply or
  standalone verification, even when their integrity hashes are otherwise valid.
  Historical get/list, idempotent planning and terminal apply returns use safe copies;
  original rows, operations, hashes and fingerprints remain untouched. The internal
  change mapper stays raw for safety, drift and apply logic.
- Unsafe/null read-back cannot normalize into a successful verification. A fixed
  inconclusive `VERIFICATION_FAILED` is retained without raw evidence. Rejection,
  verification and result descriptions are filtered before new retention.
- Optional operational audit applies metadata filtering in SANITIZED/FULL, on mapper
  write and historical output. METADATA omits params; disabling optional persistence
  still leaves log behavior. Target/session authorization remains ahead of DB paging.
  Canonical target/trace IDs stay exact; log projections are separate. Persistence
  warnings contain fixed text, not exceptions or supplied values.
- REST `McpException` messages and controlled-change MCP messages filter recognizable
  credentials, preserving existing status/error codes. Change-MCP errors also discard
  original causes/suppressed exceptions. Other MCP families and generic REST exception
  types are not covered by this correction.

Canonical change/target IDs and trusted actor/approver/rejector provenance remain exact.
Authenticity does not prove arbitrary issuer/subject text secret-free; tests explicitly
preserve that distinction. Mandatory installation binding audit is unchanged and still
shares the binding/run-consumption transaction, independently of optional audit settings.

Requirements: SEC-CRED-002/004/005, SEC-AUTHZ-001, SEC-MULTI-001, NFR-CHANGE-001,
NFR-TEST-001/002. These are scoped evidence inputs, not full acceptance of every surface.
Architecture: [controlled administration](../architecture/controlled-administration.md),
[security](../architecture/security.md), [persistence](../architecture/persistence.md),
[audit](../audit.md).

## Source and version decision

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`; accumulated dirty/untracked work preserved. No commit,
push, rebase, release, publication or external cluster access. The
[source/test/runtime manifest](evidence/h1-change-metadata-2026-09-19.json) compares
with the preceding metadata slice: **587 source inputs**, aggregate
`754fef2a3e6dd81539ba312a6336442dd0724382264d4fd4761c8ef60f9ceb84`;
**six existing implementation files changed, one guard and four test files added,
none removed**. Hashes are unsigned local consistency evidence, not attestations.
The manifest also retains **123 Maven report hashes**, **nine run logs** (including
all three red runs), **three final package hashes** and **two inventories**.

Backend/application/MCP/OpenAPI remain **0.8.1-SNAPSHOT**; UI/package-lock root metadata
remain **0.8.1-dev.0**; report schema **1.1**, migrations **V1–V10**. No dependency,
UI, deployment, grant, default read-only or persistent schema change. This unreleased
correction intentionally tightens admission: existing unsafe metadata may require
operator cleanup and a new plan/idempotency key. Historical records/backups are not
purged. AGENTS' output-versus-authoritative-state invariant remains unchanged.

## Validation

Java **21.0.10**, selected per command through `JENV_VERSION=21 jenv exec`; no global
version change. Existing standalone runtime scripts use host Node **25.6.1**, not a new
UI support claim. Raw logs: `/private/tmp/kcops-h1-change-metadata-*-20260919.log`.
They may expire; manifest hashes/excerpts preserve the recorded local evidence.

| Execution | Observed result |
|---|---|
| Fresh `mvn clean verify` baseline | **1385 passed**, 9 opt-in ITs skipped; exit 0 |
| Pre-fix audit metadata regressions | 16 invocations, **10 failures**, 0 errors; exit 1 |
| Pre-fix REST/change-MCP error regressions | 26 invocations, **14 failures**, 0 errors; exit 1 |
| Pre-fix change metadata regressions | 39 invocations, **33 failures**, 0 errors; exit 1 |
| Focused green regression + existing service/audit/secret tests | **117 passed**; exit 0 |
| Final `mvn clean verify`, including three review-added regressions | **1469 passed**, 9 opt-in ITs skipped; exit 0, **84 new invocations** |
| Final-package installation lab | **83 checks**, JWT scan and owned cleanup passed; exit 0 |
| Final-package default identity/report lab | **70 checks**, JWT scan and owned cleanup passed; exit 0 |
| Final-package metrics identity/report lab | **75 checks**, JWT scan and owned cleanup passed; exit 0 |

The red runs were executed before each corresponding production fix. They are retained,
not erased or relabeled as passing. Final clean verification completed at **16:27:58 −03**.
Regression counts: change metadata **42**, audit **16**, REST error **16**, change-MCP
error **10**. Tests exercise authorization-first admission, all five plan families,
raw PKCE/URL/name/description fields, unsafe dynamic keys, unchanged ordinary policy
prose, credential-clearing, safe historical/idempotent responses, valid-integrity unsafe
legacy plans, standalone verify, provider mutation refusal and read-back uncertainty.
Audit tests cover modes, disabled persistence, historical immutability, cause-free
diagnostics and canonical correlation. Independent review found no scoped blocker;
trusted identity text and detection limits remain explicit rather than waived.

Reproduction: `env JENV_VERSION=21 jenv exec mvn clean verify`; focused classes are
`ChangeMetadataBoundaryTest`, `AuditMetadataBoundaryTest`,
`McpExceptionMapperMetadataTest`, `ChangeToolsErrorProjectionTest`,
`ChangeManagementServiceTest`, `AuditRepositoryTest` and `SecretLeakageTest`.
Local final-package runners: `bash scripts/validate-installation-lab.sh`,
`bash scripts/validate-identity-lab.sh`, and `bash scripts/validate-identity-lab.sh --metrics`.

New controlled-write regressions use Quarkus/PostgreSQL with a mocked Admin adapter,
not actual Keycloak writes. Runtime installation uses real local OIDC/PostgreSQL and
a synthetic cluster. Identity/report labs use Community Keycloak **26.7.1** and
Prometheus **2.55.1** where enabled. This does not establish RHBK support. UI tests/build,
browser negatives/revocation, actual RHBK/OpenShift, opt-in live ITs, CI, image/native
builds and comprehensive dependency scans were not run.

## Local hygiene and handoff

Before final verification/runtime execution, inventory at **16:24:21 −03** found zero
containers/volumes, default network only, four cached images unchanged, eleven fixture
ports free and shared lock absent. Runners pin `podman-machine-default-root`, use named
per-run-owned disposable resources, and verify full-ID cleanup. No global prune,
VM restart, image or volume deletion. Removed tmpfs database/TSDB data are disposable
and not recoverable; diagnostic logs and reusable images are retained.

Independent final inventory at **16:32:37 −03** confirms **zero containers/volumes**,
default network only, all four image IDs unchanged, eleven fixture ports free and
shared lock absent. Diagnostics: installation `/private/tmp/kcops-installation.IuGiap`,
default `/private/tmp/kcops-identity.yiqHkt`, metrics `/private/tmp/kcops-identity.FcFKqX`.

Final consistency checks matched **724 referenced hashes**, including all 587 source
inputs and their aggregate. Offline documentation review passed for **136 Markdown
documents, 829 local links and 15 milestone specifications**, with zero errors/warnings;
the checker passed six self-test groups. `git diff --check` passed. These are static
consistency checks, not external URL validity or milestone acceptance. Context,
roadmap, affected architecture, milestones, Unreleased notes and version decision
were reconciled; historical ledgers and AGENTS invariants were preserved.

## Remaining work

- Generic target/inventory/installation/health/metrics metadata and other MCP error
  families still require review. This is not universal secret detection, log-control
  escaping or prompt-injection prevention; encoded/unlabelled secrets may remain.
- Canonical identities are deliberately not lossy display metadata. Do not embed
  secrets in target/trace IDs or issuer/subject values. Output filtering is not proof
  of secret-free historical bytes/backups or versioned retained-evidence replay.
- Conservative full-client admission can block a harmless selected change if an
  unrelated field resembles a credential. Operator cleanup/replanning is required;
  the platform does not silently remove it or broaden permissions.
- Verification failure after a remote write is not rollback. Durable remote
  reconciliation, production-write governance and cross-plan concurrency remain P2.
- Browser/revocation, normalized all-source authorization/freshness/coverage,
  diagnostic reconfiguration, comprehensive scanning and reviewer acceptance remain
  open. H1/D1 are not completed by this slice.

Next local work: generic metadata/tool error boundaries and browser/revocation, then
AGT1. D2 still requires an explicitly approved dedicated RHBK/OpenShift environment;
no cluster is required for the immediate next slice.
