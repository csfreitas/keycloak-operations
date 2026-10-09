# ONB1 administrative preflight — evidence, 2026-10-09

## Scope and provenance

Authorized continuation: first local ONB1 contract/policy slice, not full registration.
The user separately authorized correction of the local Podman registry configuration
with a backup after the pre-change baseline exposed a syntax error.

Repository `mcp-server-keycloak`, branch `feature/0.8.1-client-lifecycle`, HEAD
`572cb7ba5aa9a132f3e04280de9378da08cc61e3`. Initial status: **401** dirty/untracked
entries; pre-edit SHA-256 inventory: **898** existing tracked/nonignored files.
Existing changes were preserved. No staging, commit, push, rebase, tag or release.

Delivered [REST preflight 0.1.0](../registry-preflight.md): explicit administrator/client
policy, bounded closed draft parser, local reference/ID checks and safe inconclusive
results. [ADR 0013](../adr/0013-registry-preflight-before-registration.md) distinguishes
inert administrative drafts from operational endpoint selection. No new target,
connection persistence, audit write, grants, credential resolution, remote validation,
UI/MCP surface, migration or read-only/BIND change.

## Execution and failures retained

Java was selected per command with `JENV_VERSION=21 jenv exec` (observed OpenJDK
**21.0.10**); global jenv configuration was not changed. Podman **5.7.1**, explicit
local connection `podman-machine-default-root`. No ambient Kubernetes context was used.

| Run | Actual result |
|---|---|
| Required pre-code `mvn clean verify` | Failed before Quarkus resource startup: 1820 discovered, 0 assertions failed, 4 startup errors, 283 skipped; Podman could not parse its global registry file |
| Same baseline with task-local registry override | Same startup failure/counts; no hidden retry success claim |
| Fully qualified cached-image diagnostic before repair | Same registry parser failure; no container created |
| Policy-only development run | Initial test-mock setup errors corrected; final 73 tests passed |
| First focused run | 232 cases, one test-fixture failure: direct encoding replaced an unpaired Java surrogate before parsing; changed fixture to send the actual JSON escape |
| Final focused unit/regression run | **232 passed**, 0 failures/errors/skips: 222 new preflight cases + 10 existing target-authorization cases |
| Named cached-image probe after authorized repair | Passed with `--rm --pull=never` and tmpfs; no retained container/volume |
| Final `mvn clean verify` after repair | **2053 passed**, 0 failures/errors/skips in Surefire; **9 opt-in ITs skipped** in Failsafe; packaging/Quarkus augmentation and verification succeeded |

Final command (from repository root):

```sh
env -u RUN_KEYCLOAK_IT -u RUN_RHBK_IT -u RUN_PROMETHEUS_IT \
  JENV_VERSION=21 CONTAINER_CONNECTION=podman-machine-default-root \
  jenv exec mvn clean verify
```

Final run took **2m05s**, completed `2026-10-09T15:45:18-03:00`. Local session logs:
`/private/tmp/keycloak-operations-onb1-20261009.wRqQzt/` contains `baseline.log`,
`baseline-isolated.log`, `focused.log`, `focused-final.log`, `verify.log`.
Policy development logs are separately under `/private/tmp/kcops-registry-preflight-policy-tests*20261009.log`.
These are local diagnostics, not portable release artifacts, and may contain test
fixture information; do not upload them as customer evidence without review.
Existing Quarkus REST/RESTEasy Classic client-mixing and compiler warnings remain;
this slice does not resolve or suppress them.

## New coverage and what it proves

**233 new cases** are included in the final 2053 total:

- Policy: **73** — SmallRye disabled/empty defaults, explicit configuration bounds,
  exact same-entry issuer/subject/role/client, denied local-lab/non-JWT/legacy cases,
  safe malformed identity/config diagnostics and fresh per-call identity checks.
- Validator: **106** — closed shape, types, duplicates/trailing input, finite byte/
  structural/field bounds, endpoint subset, no normalization/echo, Unicode edge cases.
- Service: **17** — authorization before reads/parsing, collision/ref membership,
  registry outage versus absence, no credential-value access or repository mutation,
  no registration even with global read-only false.
- Resource: **5**, cache filter: **21** — HTTP outcome mapping/delegation, exact path
  subtree and no-store/Vary, including mapped failure responses.
- Quarkus HTTP boundary: **11** — real local HTTP routing/serialization and actual
  service/parser/policy with **synthetic trusted identities and mocked registry reads**;
  denied subject/client/role/non-JWT/legacy, success, collision, secret-field rejection,
  sanitized outage and cache headers. Not JWT signature, real OIDC or preflight live
  database query acceptance. The broader suite uses disposable platform PostgreSQL.

Independent security review found no demonstrated blocker in these new classes.
It explicitly retained the limitations: platform DB reads are I/O; size limits are
not whole-request deadlines or concurrency caps; pre-REST authentication/proxy cache
behavior and real OIDC still need deployment evidence. Code review is not independent
operator reproduction or a production security certification.

## Authorized Podman repair and cleanup

`~/.config/containers/registries.conf` contained a truncated registry fragment and
invalid TOML, preventing even a cached image from starting. No image download,
credential change, runtime switch or global prune was needed. An exact permission-
preserving backup was created at:

`~/.config/containers/registries.conf.onb1-20261009.bak`

Its SHA-256 matches the original:
`088131b0e532450d574b563cc915efd1afcad19570054f4ff23fefea94d787a1`.
The valid `quay.io` entry and `insecure=false` were preserved. The truncated tail is
retained as comments, not silently guessed/reconstructed. The incomplete private
registry therefore still needs its intended full configuration from the operator
before it can be enabled. Its fragment is deliberately omitted from this repository.
Restoring the exact backup would also restore the syntax failure; review before use.

Post-run explicit inventory: **0 containers, 0 volumes**, only the default `podman`
network and the same four reusable cached images as before:

| Image | Image ID (short) |
|---|---|
| `quay.io/keycloak/keycloak:26.7.1` | `cc689d358fe6` |
| `docker.io/library/postgres:16` | `02ad0fee02ae` |
| `registry.redhat.io/rhbk/keycloak-rhel9:26.6` | `e7affbc8b409` |
| `docker.io/prom/prometheus:v2.55.1` | `f59c592ea6d9` |

Temporary test containers were automatically removed by their scoped lifecycle;
their disposable data was not retained. No unrelated container, image or reusable
volume was removed. Backup and bounded local diagnostic logs are intentionally kept.

## Versions, documentation and remaining gates

Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**,
AGT1 **0.2.1**, AGT2 client/answer **0.1.0**, observation **1.0**, report **1.1**,
findingDetails **1.0**, Flyway **V1–V10** and dependencies are unchanged. New preflight
response **0.1.0** is independent and unreleased. No configured administrator is
enabled by default; no historical grant or operational permission is changed.

Architecture, requirements, ADRs, milestone/spec, roadmap, AGENTS.md, project state,
REST guide, documentation map, Unreleased and version bookkeeping were reconciled.
Historical ledgers remain unchanged. No new UI/browser, AI/model, real IdP/Keycloak,
RHBK/OpenShift, Operator/CRD or release acceptance is claimed.

Final consistency checks: **22 Markdown files**, **548 relative file destinations**,
**24 balanced fence markers**, **156 unique requirement headings**, no reported errors;
`git diff --check` passed. This verifies local file destinations, not every anchor,
external link or rendered layout. Baseline hash comparison identifies **19 changed
existing documentation files** and **17 new files** (3 documents, 7 production Java,
7 test Java), zero removals. All **879 other pre-existing files** are unchanged,
including existing product/tests, UI, deployment, build/dependencies and prior
authorization/read-only code. HEAD/branch remained unchanged; final status has 414
dirty/untracked entries (Git directory aggregation is not a file count).

ONB1 stays **PARTIAL**, OP1 **PLANNED**, AGT2 **PARTIAL**; H1/D1/AGT1 acceptance and
independent release-source/reproduction gates remain open. Next: ownership/revision
and config precedence contract, then separately reviewed transactional registration,
audit, first-admin/grant bootstrap and destination/credential-use policy. Do not turn
preflight success into write authorization or expand its network surface implicitly.
