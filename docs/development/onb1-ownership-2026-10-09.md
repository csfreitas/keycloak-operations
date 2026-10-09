# ONB1 configuration ownership — evidence, 2026-10-09

## Scope and provenance

Authorized local roadmap continuation: ownership/revision and safe configuration
reconciliation, not public registration or a completed ONB1 milestone. See
[ADR 0014](../adr/0014-registry-ownership-before-managed-writes.md) and the
[ownership contract](../architecture/registry-ownership.md).

Repository `mcp-server-keycloak`, branch `feature/0.8.1-client-lifecycle`, HEAD
`572cb7ba5aa9a132f3e04280de9378da08cc61e3`. Initial status: **414** dirty/untracked
entries; pre-edit SHA-256 inventory: **915** existing tracked/nonignored files.
Existing work was preserved. No staging, commit, push, rebase, tag or release.

Flyway V11 conservatively classifies historical targets as LEGACY_UNCLASSIFIED with
registry revision 0. Only newly seeded targets explicitly become CONFIGURATION-owned.
JPA row version is separate from the existing installation/binding revision. The
configuration reconciler rejects ownership collisions, skips unchanged effective
state and requires a system audit in the same transaction as every actual change.
Database/composite modes no longer fall back to configuration on failure or emptiness.

There is no new API/CR owner state, registration/transfer endpoint, administrative
write permission, target grant, network validation, credential-value resolution or
UI/MCP surface. Preflight **0.1.0**, global read-only and existing BIND policy remain
unchanged. The mapper does not infer ownership from an ID or confirmed binding.

## Execution, failures and regression evidence

Java was selected per command with `JENV_VERSION=21 jenv exec`: observed OpenJDK
**21.0.10**, without modifying global jenv selection. Every container-backed run used
the explicit local Podman connection `podman-machine-default-root`. The previously
authorized registry-file repair remained intact; no global configuration was edited
in this slice. No ambient Kubernetes/OpenShift context was used.

| Run | Observed result |
|---|---|
| Required pre-code `mvn clean verify` | **2053 passed**, 0 failures/errors/skips; **9 opt-in ITs skipped**; BUILD SUCCESS, 2m03s, finished `2026-10-09T15:57:06-03:00` |
| First focused command invocation | zsh rejected an unquoted wildcard before Maven started; corrected quoting, no test failure or container run attributed to that attempt |
| Focused ownership/preflight/authorization/installation suite | **316 passed**, 0 failures/errors/skips; 31.964s; this preceded the additional binding regression coverage |
| New ambiguous-binding regression before the fix | **2 tests, 2 assertion failures**, 0 errors/skips: both expected rejections were absent; 8.762s, finished `2026-10-09T16:06:11-03:00` |
| Final `mvn clean verify` after correction | **2117 passed**, 0 failures/errors/skips; **9 opt-in ITs skipped**; packaging and verification succeeded, 1m58s, finished `2026-10-09T16:08:41-03:00` |

Baseline and final command, from repository root:

```sh
env -u RUN_KEYCLOAK_IT -u RUN_RHBK_IT -u RUN_PROMETHEUS_IT \
  JENV_VERSION=21 CONTAINER_CONNECTION=podman-machine-default-root \
  jenv exec mvn clean verify
```

The focused command used the same environment with:

```sh
jenv exec mvn '-Dtest=TargetOwnershipBootstrapTest,CompositeTargetRegistryTest,ManagedInstallationBootstrapTest,TargetOwnershipMigrationTest,TargetRegistryRevisionTest,TargetBootstrapTransactionTest,InstallationOnboardingServiceTest,TargetAuthorizationTest,RegistryPreflight*Test' test
```

Task-local logs: `/private/tmp/keycloak-operations-onb1-ownership-20261009.K1BLUq/`
contains `baseline.log`, `focused.log`, `binding-regression-red.log` and `verify.log`.
These diagnostics are not portable release artifacts; review before sharing because
test fixture/SQL details can appear. Existing compiler and Quarkus REST/RESTEasy
Classic client-mixing warnings remain. Negative constraint/concurrency tests do not
turn expected database errors into failed assertions in the successful final run.

## Coverage and actual guarantees

**64 new cases** are included in the final total:

| Suite | Cases | Evidence boundary |
|---|---:|---|
| `TargetOwnershipBootstrapTest` | 31 | Mocked repository boundary: explicit owner, collision-before-mutation, normalized no-ops, binding/context precedence, stable ordering and audit/flush failure propagation |
| `CompositeTargetRegistryTest` | 18 | Mocked mode/lookup boundary: explicit config mode, exclusive database authority, no empty/missing/disabled/error fallback and fixed initialization diagnostics |
| `TargetOwnershipMigrationTest` | 3 | Disposable PostgreSQL: independent populated synthetic V10 schema upgraded to V11; conservative defaults and constraints |
| `TargetRegistryRevisionTest` | 6 | Conservative mapper, real ORM version increments/no-op and distinct binding revision; stale write rejected between two independent EntityManagers |
| `TargetBootstrapTransactionTest` | 6 | Real PostgreSQL/JTA: committed create/no-op/change, binding invalidation, whole-batch rollback on late collision and audit failure after a real flushed audit insert, repeated ambiguous-binding rejection |

Existing managed-bootstrap and installation-onboarding tests were updated without
adding cases; installation confirmation now also checks the actual committed row
version. Existing authorization and preflight regression remain green, without new
permissions or an assertion of live OIDC authentication.

The migration fixture preserves prior rows/values across targets, tags, discovery,
assessment runs/findings, health checks, audit, snapshots, inventory and change
records, including binding/context and enabled state. Matching configured names do
not adopt historical rows. Tests create/drop only a uniquely identified synthetic
schema; they do not clean or migrate any retained platform/customer database.

Independent code review found a gap after the initial focused green run: a changed
connection plus explicit configured binding could first clear the managed binding,
then reapply that old binding on an identical next synchronization. The two-case RED
test demonstrated the missing rejection. The corrected reconciler rejects the
ambiguous combination before mutation; repeated attempts preserve the row, binding,
versions and audit. Removing the configured binding first permits the separately
tested connection-change/invalidation path. Review is not an external operator
reproduction or production security certification.

Mandatory audit uses existing SYSTEM source with `origin=CONFIGURATION_BOOTSTRAP`,
fixed changed-field names and actual flushed revisions. It does not record endpoint,
reference or credential values, free-form tag keys or a fictitious human identity.
Optional operational audit settings cannot suppress these transactional events.
Unit mocks alone are not treated as rollback or ORM-concurrency evidence.

## Upgrade and acceptance limits

**V11 is an unreleased compatibility boundary.** A populated pre-V11 database with
configured-ID collisions will preserve rows but block reconciliation until governed
adoption exists. Do not bulk-set owners, delete/recreate targets or bypass the
conflict with another registry mode. The sanctioned adoption/ownership-transfer,
backup/restore and upgrade procedure remains a next slice, not a delivered command.
No retained database was upgraded by this work.

Older binaries and direct SQL writers do not honor the new ORM ownership/version
contract; mixed-version writers and image-only rollback are unsupported. Row version
is not an exposed expected-revision API, user authorization or approval. Definition
ownership does not replace existing target-scoped binding permissions.

No new UI/browser, real OIDC/provider lab, optional Keycloak/RHBK/Prometheus IT,
AI/model, OpenShift cluster or Operator run was performed. Those nine optional ITs
were **skipped**, not passed. This local result cannot close their acceptance gates.

## Podman cleanup

Before and after validation: **0 containers and 0 volumes**. Final inventory shows
only the default `podman` network and the same four reusable cached images:

| Image | Image ID (short) |
|---|---|
| `quay.io/keycloak/keycloak:26.7.1` | `cc689d358fe6` |
| `docker.io/library/postgres:16` | `02ad0fee02ae` |
| `registry.redhat.io/rhbk/keycloak-rhel9:26.6` | `e7affbc8b409` |
| `docker.io/prom/prometheus:v2.55.1` | `f59c592ea6d9` |

Scoped test lifecycles removed their disposable containers/data. No unrelated
resource or reusable image was removed, no broad prune ran, and no test volume was
retained. Bounded local diagnostic logs remain for review.

## Versions, continuity and remaining work

Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**,
AGT1 **0.2.1**, AGT2 client/answer **0.1.0**, preflight **0.1.0**, observation **1.0**,
report **1.1** and findingDetails **1.0** remain unchanged. Flyway advances **V1–V11**;
historical V1–V10 and dependencies are unchanged. This is not a release version bump.

Architecture, ADRs, schema/audit guides, requirements, roadmap, milestone, AGENTS.md,
project state, documentation index, Unreleased and version bookkeeping now distinguish
this foundation from preflight and future registration. Historical dated ledgers are
preserved; no previous live evidence is relabeled as validation of this change.

Final consistency checks: **23 Markdown files**, **585 relative file destinations**,
**38 balanced fence markers**, **157 unique requirement headings** across the six
requirements documents, with no reported errors; `git diff --check` passed. These
checks cover local file destinations, not every anchor, external URL or rendered
layout. Baseline hashes identify **25 changed existing files** (20 documentation,
3 production Java and 2 test Java) and **10 new files** (3 documentation, 1 production
Java, 1 migration and 5 test Java), zero removals. The other **890 pre-existing files**
remain unchanged. HEAD/branch are unchanged; final status has **425 dirty/untracked
entries** (Git directory aggregation is not a file count).

ONB1 stays **PARTIAL**, OP1 **PLANNED**, AGT2 **PARTIAL**. H1/D1/AGT1 acceptance,
consolidated source review and independent operator/release reproduction remain open.
Next: governed legacy adoption/config-to-API ownership transfer with expected
revisions and mandatory review/audit, then separately authorized transactional
registration, initial-admin/grant bootstrap and approved destination checks.
