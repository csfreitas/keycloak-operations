# AGT2 — First restricted configuration reads, 2026-09-19

Status: **IMPLEMENTED / LOCALLY VERIFIED SUBSET; AGT2 PARTIAL**.

## Delivered and bounded

[Contract, question matrix and onboarding](../configuration-reads.md) describe the
first additive read channel: exact configured realm/client Boolean fields, pinned
internal resource identities, authenticated role + client + channel grants, one
shared service behind REST/MCP and a standalone operator screen. No target-wide READ
is required or granted. Empty configuration denies access. The console is usable
without a model; no external client/provider/model or default scope is enabled.

Main implementation: `ConfigurationReadConfig`, `ConfigurationReadPolicy`,
`ConfigurationReadService`, `ConfigurationReadResource`, `ConfigurationReadTools`,
contract-1.0 records, attribution-only `AuditService.recordConfigurationRead`, REST
cache filter and `ui/src/pages/ConfigurationPage.tsx`. The fixture is
[synthetic and loopback-only](../../dev/configuration-browser/README.md).

Legacy roles remain broad/additive, not silently migrated. Product version stays
UNKNOWN without a separately authorized source; null is not false; COMPLETE refers
only to requested fields. No full policy evaluation, health verdict, score, PII,
new report history, mutation, agent orchestration or delegation implementation.
Audit remains optional and best-effort, with pseudonymous fingerprints rather than
raw claims or source facts. Token revocation is not instantaneous.

## Source provenance and versions

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`; 368 pre-existing dirty/untracked status entries
were present before this slice. No commit, push, rebase, release or tag. Existing
changes were preserved. The [manifest](evidence/agt2-configuration-reads-2026-09-19.json)
records **655 implementation/test/deploy inputs**, 24 additions, 6 changed inputs and
zero removals relative to the prior 631-input catalogue checkpoint. Aggregate SHA-256:

```text
d368b33ef4a39d4d5dd2acf59ac68955debfcb71731af87d1fd0f9a604d2cdae
```

All 29 retained historical model-trial artifacts still match their original bytes
and hashes. No raw response was repaired or relabeled. Documentation/AGENTS changes
are outside that implementation-input aggregate and are checked separately.

Independent configuration observation schema **1.0** is new. Backend/application/
MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/lockfile **0.8.1-dev.0**, reference profile **0.2.1**,
report **1.1**, findingDetails **1.0**, Flyway **V1–V10**, fixture catalogue **1** and
dependencies are unchanged. Unreleased additive development, not a product release.

## Fresh validation

| Check | Result / boundary |
|---|---|
| Pre-code Java baseline | 1705 passed; 9 opt-in integrations skipped; clean verify success |
| Pre-code UI baseline | 195 passed / 22 files |
| New focused policy/service/audit | 93 passed (35 + 20 + 38) |
| Cache reproduction | 8 cases, 2 failing before correction; leading-slash path mismatch |
| Cache + HTTP/MCP focused rerun | 21 passed (8 cache + 13 boundary); before final mixed-role addition |
| Final Java 21 clean verify | **1820 passed**, zero failures/errors; 9 opt-in integrations skipped; build success |
| Final new boundary cases | 14 HTTP/MCP cases, including two operators on one target and mixed broad/restricted roles |
| UI regression and build | **232 passed / 23 files**, TypeScript and production Vite build passed |
| Offline profile + SecOps catalogue | **167 passed** (115 + 52); no model invoked |
| Browser | Real in-app browser, synthetic HTTP/provider data only; cases below |
| Offline documentation | 155 Markdown files, 1077 local links, 152 requirement definitions and 17 milestone specifications checked; zero errors/warnings; six parser self-tests pass |

Java **21.0.10** was selected per command with jenv; no global Java selection changed.
The backend command explicitly unset `RUN_KEYCLOAK_IT`, `RUN_RHBK_IT`,
`RUN_PROMETHEUS_IT` and selected `podman-machine-default-root` before `mvn clean verify`.
Node was the existing bundled runtime, selected only for these commands.

HTTP/MCP tests inject a **synthetic trusted JWT principal**, not signed tokens. Only
the source adapter is mocked at that boundary; resource routing, MCP serialization,
shared policy, field projection and response headers execute locally. They test:
separate roles/resources on one target; exact fields; REST/MCP client segregation;
spoofed headers/clientInfo; unknown-versus-forbidden equality; no provider call before
denial; no access upgrade to legacy overview/report/history/tools; partial/null parity;
and the explicitly additive mixed-role behavior. Unit cases also cover invalid policy,
local-lab/identity rejection, recreated identities, bounded collection, changed target,
reauthorization and sanitized provider failure. Not every indirect domain is accepted.

The first full run exposed five missing-cache-header assertions. `UriInfo.getPath()`
included a leading slash; normalization fixed matching for both representations.
The minimal red/green test and subsequent real REST/MCP checks establish this cause.
Earlier authored-test compilation/Mockito setup failures were corrected separately;
their logs remain, rather than being presented as passing runs. Existing REST/RESTEasy
mixing and OpenTelemetry warnings were not changed by this slice.

An independent read-only review found no security blocker in the new channel and
identified one documentation precision issue, corrected here: its 10-second shared
collection budget is cooperative, not a hard DNS/TLS/I/O wall-clock limit or SLA.

Key local logs (hashes and per-class totals are in the manifest):

- `/private/tmp/kcops-agt2-read-baseline-20260919.log`
- `/private/tmp/kcops-agt2-restricted-unit-final-20260919.log`
- `/private/tmp/kcops-agt2-cache-red-20260919.log`
- `/private/tmp/kcops-agt2-http-green-20260919.log`
- `/private/tmp/kcops-agt2-verify-final-20260919.log`
- `/private/tmp/kcops-agt2-ui-final-20260919.log`
- `/private/tmp/kcops-agt2-ui-build-20260919.log`
- `/private/tmp/kcops-agt2-node-20260919.log`
- `/private/tmp/kcops-agt2-configuration-docs-20260919.log`

## Browser observations and cleanup

At `127.0.0.1:18312`, observed: descriptor load without automatic provider inspection;
switch from a pending realm read to client; client COMPLETE with true/false; realm
PARTIAL with Unknown; 403 removes previous results; wrong-scope and inconsistent
COMPLETE responses rejected; empty and failed catalogue; logout during a pending
read stays cleared after the synthetic response completes; Tab reaches the inspection
button and Return activates it; the 390×844 layout has no page horizontal overflow
(384px content width, 334px table). No browser warnings/errors were returned.

Native-select arrow-only automation was inconclusive; semantic selection plus
Tab/Return button activation was verified. No screen-reader or full WCAG claim.
The synthetic fixture does not authenticate, query a provider or prove live deployment.
Its foreground server was stopped; port 18312 is free, the temporary tab closed and
viewport override reset. Only ordinary reusable UI dependency/build caches remain.

Independent Podman inventory before and after: **0 containers, 0 volumes**, same four
reusable images (Keycloak 26.7.1, RHBK 26.6, PostgreSQL 16, Prometheus v2.55.1).
Existing named/labelled PostgreSQL tests use disposable tmpfs and remove owned
containers. No global prune, image deletion, persistent test volume, VM restart,
unrelated cluster or system runtime reconfiguration.

## Remaining gate and next step

No live Keycloak/RHBK/OpenShift provider integration or signed-token OIDC run was
performed for this new channel; no external model or GitHub CI execution. All whole
AGT2 exit criteria remain open, as do H1/D1/AGT1 acceptance and independent operator
reproduction. Architecture, identity/security/UI docs, tool index, roadmap, milestone,
project state, AGENTS and Unreleased/version bookkeeping reflect partial delivery.

Next: run the approved **local authenticated lab** with two dedicated restricted
principals, pinned actual realm/client IDs, independently minimal provider credentials
and distinct console/MCP client approvals. Reproduce deny/parity/recreated-resource
cases there before adding a separately versioned multi-tool host/profile or broader
users/policies/PII/infra coverage. Do not enable a model or broaden legacy roles merely
to make that lab pass.
