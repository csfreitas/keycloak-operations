# H1 — Observed cluster capabilities and source coverage, 2026-09-19

Status: local corrective slice; validation is recorded below. H1/D1 acceptance remains
open. No real cluster, browser, production-write or release certification is implied.

## Scope and trust boundaries

- `EnvironmentInfo` retains configured infrastructure type separately from observed
  runtime and fixed Route/config v1 advertisement. Complete bounded API-group
  discovery distinguishes SERVED, NOT_SERVED, UNSUPPORTED_VERSION and UNKNOWN.
  Advertisement is not authorization, successful resource collection or complete
  cluster visibility. No arbitrary API/version/endpoint passthrough is introduced.
- Inventory and networking reuse the same observed capabilities. Configured Kubernetes
  with advertised OpenShift APIs can collect them; configured OpenShift does not force
  unsupported queries on observed Kubernetes. An unsupported advertised version is a
  gap, not an empty successful resource list. Unknown Route coverage preserves valid
  Service/Ingress observations while remaining partial.
- Version failure retains independently observed runtime/APIs. Discovery/version gaps
  receive safe inventory warnings. Foreign target/namespace discovery is rejected
  before resource collection. Configured VM is NOT_SUPPORTED, distinct from NONE;
  neither gains a host/container collector or ambient connection fallback.
- `InfrastructureCoverage` centralizes the conservative completeness policy used by
  assessment evidence, snapshots and infrastructure API health. It requires matching
  confirmed discovery, supported/known API coverage, observed versions and the existing
  section/topology prerequisites. Empty warnings alone are insufficient; genuine
  observed zero remains valid and completeness does not require healthy configuration.
- Fresh snapshots retain discovery and `collectionComplete`. Report platform status
  requires explicit true coverage; missing legacy coverage is PARTIAL. Infrastructure
  health exposes false coverage and UNKNOWN when evidence is insufficient. Stored
  historical snapshots/runs are not rewritten; legacy source constructors remain.
- Legacy wrapper type/OpenShift methods are deprecated configuration-only transport
  helpers. Implicit unbounded detection and failure-to-Kubernetes fallback are removed;
  production inventory uses the bounded approved base client instead.

Requirements: FR-DISC-004, FR-INV-001/002/003, FR-ASSESS-005, FR-REPORT-002/004/006,
NFR-ISO-001, NFR-DET-001, NFR-REPORT-001, NFR-TEST-001/002, COMPAT-003/005/006.
Architecture: [portable discovery](../architecture/portable-environment-discovery.md),
[reporting](../architecture/operations-reporting.md),
[assessment](../architecture/assessment-engine.md), [health](../health-check.md).

## Source and versions

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`. Work is in the accumulated uncommitted tree;
pre-existing changes were preserved. No commit, push, rebase, tag or publication.
The [evidence manifest](evidence/h1-capability-coverage-2026-09-19.json) records
**580 source inputs**, aggregate
`7fa2db8765f4a665607b75ce41e53e07420ee824e2c2f182917a2d5cd06828c6`.
Compared with the preceding runner validation, **20 existing backend/source-test
files changed and five were added; none removed**. It retains 117 Maven report hashes,
seven log hashes/excerpts (including the failed attempt), three package hashes and
before/after inventories. Raw logs use `/private/tmp/kcops-h1-capability-*-20260919.log`
and can eventually expire; hashes are unsigned local consistency evidence, not
independent attestations.

Backend/application/MCP/OpenAPI remain `0.8.1-SNAPSHOT`; UI/package-lock root metadata
remain `0.8.1-dev.0`; dynamic report schema remains **1.1**, Flyway **V1–V10**.
Additive discovery/inventory fields and conservative completeness belong to this
unreleased corrective increment. No dependency, UI, grant, deployment or migration
change. AGENTS.md invariants remain applicable and unchanged.

## Validation

Java **21.0.10** selected per command through `JENV_VERSION=21 jenv exec`; global
Java selection unchanged. Host Node **25.6.1** runs the standalone local harness,
not a new UI build/Node support claim. Every Maven test uses local fixtures;
real Kubernetes/OpenShift/RHBK integration remains opt-in and unexecuted here.

| Execution | Result |
|---|---|
| Fresh `mvn clean verify` baseline | **1242 passed**, 9 opt-in ITs skipped; BUILD SUCCESS |
| Initial focused regression | 243 invocations, 2 failures/10 errors; retained failed log |
| Focused rerun | **243 passed**, exit 0 |
| Final clean verification | **1304 passed**, 9 opt-in ITs skipped; BUILD SUCCESS, exit 0 (**62 added invocations**) |
| Final-package installation lab | **83 checks passed**, JWT scan and owned cleanup passed; exit 0 |
| Final-package default identity/report lab | **70 checks passed**, JWT scan and owned cleanup passed; exit 0 |
| Final-package metrics identity/report lab | **75 checks passed**, JWT scan and owned cleanup passed; exit 0 |

The initial focused failures were new fixture errors: Mockito re-stubbing invoked
the default answer with a null matcher argument; VM/NONE used a generic Kubernetes
mock instead of the real no-client discovery path. Corrections use safe re-stubbing
and real unsupported/no-binding discovery without weakening assertions.

Regressions cover configured/observed mismatches, route-only/config-only/both/neither
APIs, non-v1 advertisement, preferred-versus-served version, failed discovery/version,
legacy/foreign/missing-scope observations, preservation of independent facts and
observed zeros, duplicate warnings, shared completeness and missing/non-Boolean report
markers. Mock consumer tests enforce no implicit type/OpenShift/API discovery calls.

The installation lab exercises real OIDC/PostgreSQL binding and audit with a synthetic
cluster, not live OpenShift capability collection. Default/metrics labs validate
authenticated REST/MCP reports and target isolation using Community 26.7.1; the metrics
case uses Prometheus 2.55.1. VM/RHBK/real-cluster capability behavior is not certified
by those runs. UI tests/build, manual browser, CI, image/native builds and comprehensive
dependency/security scanning were not rerun in this slice.

## Local hygiene and remaining gates

Before runtime validation: zero containers/volumes, default Podman network only,
four reusable cached images unchanged, eleven fixture ports bindable, lock absent.
Tests use the explicitly pinned `podman-machine-default-root` connection, named
per-run-owned transient resources and automatic cleanup. No VM restart/global prune
or unrelated cluster access is authorized by this slice.

Independent final inventory at **15:20:10 −03** confirms **zero containers/volumes**,
default network only, four reusable cached image IDs unchanged, all eleven fixture
ports bindable and lock absent. Only this run's disposable test resources were removed;
tmpfs data are not recoverable, while cached images and diagnostic logs were retained.
Run diagnostics: installation `/private/tmp/kcops-installation.npygZI`, default
`/private/tmp/kcops-identity.ZIvgmm`, metrics `/private/tmp/kcops-identity.3jkIHw`.

Final consistency checks: **709 referenced hashes matched**, including all 580 source
inputs and the aggregate. Offline documentation checks passed for **134 Markdown
documents, 796 local links and 15 milestone specifications**, with zero errors/warnings;
the checker also passed its six self-test groups. `git diff --check` passed.
These checks establish reference consistency, not milestone acceptance or external
URL validity. Context, affected architecture, milestone index/specifications,
Unreleased notes and the version decision were reconciled; dated prior ledgers remain
historical.

Broader metadata sanitization, browser negatives/revocation, normalized all-source
authorization/freshness/coverage, dynamic diagnostic reconfiguration, comprehensive
dependency/image scanning and AGT1 remain open. Early binding/budget failures may
omit detailed discovery but remain incomplete. No guarantee of full cluster visibility,
atomic snapshot, universal compatibility, retained replay or hard response-time SLA.

Next local slice: remaining metadata trust boundary and browser/revocation acceptance,
then the read-only reference agent. D2 requires the explicitly approved dedicated
RHBK/OpenShift lab; no cluster is required for the immediate local work.
