# Development versions and release bookkeeping

## Current checkpoint — 2026-10-09

The operator explicitly authorizes source commit/push and `main` integration via PR.
[Fresh evidence](repository-integration-2026-10-09.md) records the consolidated
development checkpoint, not a tagged release. Product/contract versions listed below
stay unchanged. The only new dependency change in this publication slice is locked
transitive development package `source-map-js` **1.2.1 → 1.2.2**, addressing the newly
observed npm advisory; repeated UI tests/build and npm audit passed. No forced upgrade,
new direct dependency, runtime permission, image publication or deployment.

Release readiness and V11 populated-upgrade/adoption remain separate open gates.
Git/PR state is authoritative for actual commit/merge identity; earlier no-commit
entries below describe their dated historical slices.

## Previous checkpoint — ownership, 2026-10-09

The [ONB1 ownership foundation](onb1-ownership-2026-10-09.md) advances database migrations
to **V1–V11** with conservative ownership and an ORM row revision. This is an unreleased
compatibility change: existing configured-ID collisions require reviewed adoption,
and older mixed-version writers/image-only rollback are unsupported. No populated
customer or retained local database is migrated by this validation.

Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**,
AGT1 **0.2.1**, AGT2 client/answer **0.1.0**, preflight **0.1.0**, observation **1.0**,
report **1.1**, findingDetails **1.0**, dependencies and grants remain unchanged.
Audit adds fixed SYSTEM metadata/operations, not a new public source enum or permission.
Product release versions await accepted scope/reproduction/upgrade gates. No staging,
commit, push, rebase, tag, release or image publication.

## Previous checkpoint — preflight, 2026-10-09

The [ONB1 preflight slice](onb1-preflight-2026-10-09.md) introduces independent REST
response contract **0.1.0**, not a product release. Backend/application/MCP/OpenAPI
**0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**, AGT1 **0.2.1**, AGT2 client/answer
**0.1.0**, observation **1.0**, report **1.1**, findingDetails **1.0**, Flyway **V1–V10**
and dependencies are unchanged. New administration defaults to disabled with no
shipped identity grants. No UI/MCP expansion, registration or migration is delivered.
There is no tag/commit/push/release; conditional publication authority is not exercised
while consolidated readiness/review and independent reproduction gates remain open.

## Previous checkpoint — Operator design, 2026-10-09

The [Operator design slice](operator-architecture-2026-10-09.md) changes only
documentation and a non-deployable YAML example. Installation contract draft **0.1**
and proposed **v1alpha1** are not released product/API versions. No Operator artifact,
CRD, bundle or image is built/published. Backend/application/MCP/OpenAPI
**0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**, AGT1 **0.2.1**, AGT2 client/
answer **0.1.0**, observation **1.0**, report **1.1**, findingDetails **1.0**, Flyway
**V1–V10**, dependencies and grants remain unchanged. No release/tag/commit/push or
deployment. Existing acceptance/release gates remain open; prior tests are historical.

## Previous checkpoint — 2026-09-22

The [authenticated local client exercise](agt2-client-live-2026-09-22.md) adds only
lab host/checker code, regressions, CI wiring and documentation. Core client/profile/
answer **0.1.0**, observation **1.0**, AGT1 **0.2.1**, backend/application/MCP/OpenAPI
**0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**, report **1.1**, findingDetails
**1.0**, Flyway **V1–V10**, catalogue **1**, dependencies and grants stay unchanged.
The portable profile's prototype status is unchanged; the separate lab adapter is not
a published transport or general product compatibility claim.

The bounded local integration gate now has actual evidence; do not retain it as an
unmet blocker. Conditional release authority is still not exercised: consolidated
H1/source review, independent operator reproduction and exact release-artifact/version
checks remain open. No commit, push, rebase, tag, release, container publication or
external model disclosure. Do not tag the existing SNAPSHOT as a release.

## Previous checkpoint — 2026-09-21

The [offline access-aware client](agt2-client-2026-09-21.md) introduces independent
profile and answer contracts **0.1.0**, requiring existing observation **1.0**.
AGT1 remains **0.2.1**. Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root
lockfile **0.8.1-dev.0**, report **1.1**, findingDetails **1.0**, Flyway **V1–V10**,
catalogue **1**, dependencies and grants are unchanged. No product release bump is
warranted for an offline prototype whose real host transport remains unimplemented.

The operator now authorizes commit/release conditionally upon readiness for the
declared use. It is **not executed**: the new client's authenticated host/MCP gate and
review of the accumulated dirty tree remain open, along with product readiness gates.
Do not tag SNAPSHOT or treat the 0.1.0 profile identifier as a published artifact.
No commit, push, rebase, tag, external model call or image publication occurred.

## Previous checkpoint — 2026-09-19

The [two-operator authenticated lab](agt2-authenticated-operators-2026-09-19.md)
changes only disposable fixtures, validation scripts/tests and documentation. All
versions listed below remain unchanged; product Java/UI, reference profile,
dependencies and persisted schemas are hash-identical to the previous checkpoint.
No release/tag/commit/push, default grant or model-disclosure authorization.

## Previous configuration implementation checkpoint — 2026-09-19

The [restricted configuration-read slice](agt2-configuration-reads-2026-09-19.md)
adds independent observation contract **1.0**, two opt-in server MCP capabilities and
a console page. Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile
**0.8.1-dev.0**, reference profile **0.2.1**, report **1.1**, findingDetails **1.0**,
Flyway **V1–V10**, catalogue **1** and dependencies remain unchanged. These are
unreleased additive changes under existing development versions, not a product release.
No default grants, AGT1 tool expansion, persisted schema, tag, commit or push.

## Previous direction checkpoint — 2026-09-19

The [AGT2 product-direction addition](agt2-direction-2026-09-19.md) changes only
requirements/architecture/backlog/context. It preserves the operator console and
plans access-aware multi-tool reads as a separate milestone, not a larger AGT1
allowlist. All artifact versions remain unchanged: backend/application/MCP/OpenAPI
**0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**, reference profile **0.2.1**,
report **1.1**, findingDetails **1.0**, Flyway **V1–V10** and fixture catalogue **1**.
No dependencies, grants, runtime source, release/tag/commit/push or global settings
changed. Future profile/policy/evidence contracts need their own version decision
and migration acceptance; the new milestone number is not an artifact version.

## Previous scenario-catalogue checkpoint — 2026-09-19

The [SecOps/IAM catalogue slice](secops-catalogue-2026-09-19.md) adds planned
requirements, synthetic fixtures and offline integrity checks only. Private fixture
`catalogVersion: 1` is not a product/report/API version. Backend/application/MCP/
OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**, reference profile
**0.2.1**, report **1.1**, findingDetails **1.0**, Flyway **V1–V10**, dependencies
and permissions remain unchanged. No new MCP tool or release/tag/commit/push.
The profile remains READ/ASSESS; model trials and production mutations are not
authorized by adding acceptance data. AGENTS.md workflow/invariants are unchanged.

## Previous alignment checkpoint — 2026-09-19

The [AGT1 local alignment](agt1-contract-alignment-2026-09-19.md) advances only the
optional reference profile **0.2.0 → 0.2.1**: shared unchanged explanation bounds,
complete synchronized instructions/descriptor/guide, offline event inspection and
regression/CI checks. The exact profile identity changes; hosts must use matching
instructions and packets, never relabel old 0.2.0 responses. Historical failures stay
unchanged and no new model evaluation is implied. Backend/application/MCP/OpenAPI
**0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**, report **1.1**, findingDetails
**1.0**, Flyway **V1–V10**, dependencies and permissions are unchanged. No release/tag,
commit/push, real cluster or external model call. Fresh backend baseline and offline
profile checks are recorded separately from historical UI/MCP/provider results.

## Previous model-trial checkpoint — 2026-09-19

The [authorized AGT1 model trial](agt1-synthetic-model-trial-2026-09-19.md) adds
documented evidence of three responses with **0/3 structural acceptance**, not a
validated client integration or product implementation. The instruction/validator
reference-limit mismatch is recorded, not fixed in this slice. All product, report,
MCP-extension, reference-profile and migration versions below remain unchanged.
The prior [preflight](agt1-synthetic-preflight-2026-09-19.md) and its local tests remain
historical; no new Java/UI/runtime tests, release/tag, commit/push or global configuration edit.

## Previous SCORE1 planning checkpoint — 2026-09-19

The [SCORE1 planning addition](../milestones/score1-explainable-rating.md) changes only
documentation/backlog and corrects the scoring guide's completeness description.
The operator-confirmed 1–100 proposed scale is aligned across the rubric/formula,
example, requirements and indexes; the implemented legacy 0–100 range is unchanged.
No new rating, feed adapter, schema, migration or runtime behavior is implemented.
Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**,
canonical report **1.1**, findingDetails **1.0**, reference profile **0.2.0**, Flyway
**V1–V10** and dependencies remain unchanged. There is no version bump, release/tag,
commit or push; earlier validation ledgers remain historical, not new test runs.

## Previous structured-grounding checkpoint — 2026-09-19

The [AGT1 structured-grounding slice](agt1-grounding-2026-09-19.md) adds the MCP-only
**findingDetails1.0** extension and updates the independent reference profile to
**0.2.0**, which requires that extension. Whole-finding bounds and explicit omission
counts preserve sanitized source values and report-local identity. Canonical report
**1.1**, REST, backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile
**0.8.1-dev.0**, Flyway **V1–V10** and dependencies remain unchanged. This is an
unreleased additive MCP capability, not a schema migration/product publication.
Legacy MCP consumers can ignore the new field; profile 0.1.0 remains historical and
profile 0.2.0 rejects missing/unsupported details. No new grant, provider or Git write.

## Previous profile-foundation checkpoint — 2026-09-19

The [AGT1 profile foundation](agt1-profile-2026-09-19.md) introduces an independent
**reference-profile contract 0.1.0** while retaining product **0.8.1-SNAPSHOT /
0.8.1-dev.0**, report **1.1**, Flyway **V1–V10**. New files are optional local profile,
instructions, projection/evaluation/CLI, fixtures and tests; the lab has an opt-in
rehearsal. Backend/UI source, dependencies, identity/grants and schemas are unchanged.
No model/provider installation, external disclosure, release/tag or Git write occurs.

## Previous browser-negative checkpoint — 2026-09-19

The [real-browser identity validation](d1-browser-negatives-2026-09-19.md) retains
**0.8.1-SNAPSHOT / 0.8.1-dev.0**, report **1.1** and Flyway **V1–V10**. Only disposable
lab clients, browser instrumentation, bounded runner mode, tests and documentation
change. Product source, backend/application/MCP/OpenAPI metadata, dependencies,
production issuer/grants and schemas are unchanged. No release, publication,
commit/push/rebase or global Java/Node/runtime change. Community-only evidence does
not establish RHBK/OpenShift compatibility or immediate revocation.

## Previous change-navigation checkpoint — 2026-09-19

The [change-navigation correction](d1-change-navigation-2026-09-19.md) retains
**0.8.1-SNAPSHOT / 0.8.1-dev.0**, report **1.1** and Flyway **V1–V10**. Changes are
frontend route/request ownership, response identity checks, localized controls/tests
and a synthetic loopback browser fixture. Backend/application/MCP/OpenAPI metadata,
dependencies, auth transport, issuer/grants, deployment and schemas are unchanged.
No release, publication, commit/push/rebase or global runtime change.

## Previous browser-session checkpoint — 2026-09-19

The [browser-session correction](d1-browser-session-2026-09-19.md) retains
**0.8.1-SNAPSHOT / 0.8.1-dev.0**, report schema **1.1** and Flyway **V1–V10**.
Only frontend transport/session behavior, regression coverage and documentation change:
obsolete requests/events are rejected and current authentication failures remove
protected content. Dependencies, backend, issuer/grants, defaults, deployments and
API/schema contracts remain unchanged. No release, tag, publication or global runtime
change. Immediate distributed JWT/SSE revocation remains unsupported.

## Previous read-metadata checkpoint — 2026-09-19

The [read-metadata/error correction](h1-read-metadata-2026-09-19.md) retains
**0.8.1-SNAPSHOT / 0.8.1-dev.0**, report schema **1.1** and Flyway **V1–V10**.
Existing read DTO shapes, dependencies, default grants/read-only, deployments and UI
stay unchanged. Recognizable credential metadata is masked only in selected output
copies/post-engine health retention; registered identity paths and typed count maps
are preserved. Provider metadata identifiers may become lossy display values.
The environment REST endpoint now enforces the existing target READ grant; this
deliberately closes previously unauthorized access rather than adding permission.
Generic MCP unknown diagnostics become fixed messages. No historical migration,
release, tag or publication; fresh evidence and limitations are in the ledger.

## Previous controlled-change checkpoint — 2026-09-19

The [controlled-change/audit metadata correction](h1-change-metadata-2026-09-19.md)
retains **0.8.1-SNAPSHOT / 0.8.1-dev.0**, report schema **1.1** and Flyway **V1–V10**.
It adds stricter credential-pattern admission and safe history/error projections,
without new endpoints, dependencies, permissions or a UI change. Existing unsafe
metadata may now require operator cleanup/replanning; no redaction marker becomes
applied state. Historical rows and fingerprints remain unchanged, as do canonical
identity fields. The preceding [report-metadata increment](h1-metadata-trust-2026-09-19.md)
changed new snapshot hash inputs, not old bytes. Both are unreleased corrections,
not tags/publications or production-write certifications; ledgers retain their
separate source, test and runtime evidence.

## Previous capability checkpoint — 2026-09-19

The [capability/source correction](h1-capability-coverage-2026-09-19.md) retains
**0.8.1-SNAPSHOT / 0.8.1-dev.0**, report schema **1.1** and Flyway **V1–V10**.
Discovery/inventory DTOs add configured-versus-observed coverage; legacy constructors
remain available, but absent discovery cannot establish completeness. New dynamic
snapshot/report summaries add discovery and `collectionComplete`; stored historical
bytes are not migrated. Missing legacy coverage is presented as PARTIAL. No endpoint,
dependency, permission or UI change. This is an unreleased corrective increment,
not a release, tag or publication. Tests and package/source hashes are in the ledger.

## Previous inventory-envelope checkpoint — 2026-09-18

The [inventory-envelope correction](h1-inventory-envelopes-2026-09-18.md) retains
**0.8.1-SNAPSHOT / 0.8.1-dev.0**, report schema **1.1** and Flyway **V1–V10**.
It changes backend collection/transport behavior and tests, not dependencies, UI,
grants, deployment or public REST/MCP shapes. Infrastructure response bounds and
strict envelopes can make formerly optimistic results partial/UNKNOWN; explicit
cluster clients disable automatic retries and redirects while preserving configured
authentication/TLS. Operators must use the approved final API endpoint and retry
failed higher-level operations explicitly. No release/publication or Git write.

## Previous scrape-readiness checkpoint — 2026-09-18

The [scrape-readiness correction](h1-scrape-readiness-2026-09-18.md) retains
**0.8.1-SNAPSHOT / 0.8.1-dev.0**, report schema **1.1** and Flyway **V1–V10**.
It adds an internal provider contract and corrects existing dynamic evidence;
there is no new REST/MCP endpoint, persisted schema, dependency or RBAC change.
Readiness now requires actual binary `up` observations pinned to the registered
`target_id`, plus conservative installation/ServiceMonitor association. Missing
labels or unsupported configurations can legitimately produce UNKNOWN where the
old implementation was optimistic. Existing semantic query selectors are unchanged.
No release, tag, publication, commit/push or global runtime selection is implied.

## Previous inventory/temporal checkpoint — 2026-09-18

The [inventory/temporal correction](h1-evidence-temporal-2026-09-18.md) changes
backend source/tests within the existing uncommitted increment, retaining
**0.8.1-SNAPSHOT / 0.8.1-dev.0**, report schema **1.1** and Flyway **V1–V10**.
Public DTO shapes and dependency versions are unchanged; warnings now have fixed
safe text and unknown resource identifiers become null. More metric/evidence
results can legitimately be unavailable/partial instead of optimistic. Historical
stored bytes are not migrated, though warning rehydration normalizes text. No
release, tag, publication, commit/push or global runtime selection.

## Previous dependency checkpoint — 2026-09-18

The [dependency remediation](h1-dependency-fix-2026-09-18.md) retains product identifiers **0.8.1-SNAPSHOT / 0.8.1-dev.0**, schema 1.1 and Flyway V1–V10. It updates dependency versions and Node engine compatibility inside the current uncommitted corrective scope: Quarkus 3.39.4/MCP 1.13.2, Router 7.18.4/Vite 7.3.6/Vitest 4.1.11, Node 24 LTS for CI/build image. The UI lockfile records exact resolution and the new source manifest includes CI. Application/test source, permissions and persistence contracts are unchanged; runtime/build compatibility changes and executed regressions are explicit in the ledger. No tag/release, global runtime switch, image publication or commit/push.

## Previous failure/bounds checkpoint — 2026-09-18

The [H1 failure/bounds correction](h1-failure-bounds-2026-09-18.md) changes backend behavior/tests within the existing uncommitted development increment: backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**. UI, grants, dependency inputs, report schema 1.1 and Flyway V1–V10 are unchanged. Consumers must handle UNKNOWN health and unavailable range statistics where prior behavior was optimistic; no released compatibility guarantee is implied. The [dependency review](h1-dependency-review-2026-09-18.md) records open remediation without installing upgrades. A future dependency migration needs its own version/compatibility decision and regression; no release, tag or image publication was performed.

## Previous installation checkpoint — 2026-09-18

The [installation identity validation slice](d1-installation-identity-2026-09-18.md) retains backend/application/MCP/OpenAPI `0.8.1-SNAPSHOT` and UI/package-lock root `0.8.1-dev.0`. It adds disposable lab configuration, a bounded synthetic cluster fixture, verification scripts, shared runner locking and fail-closed JWT log checks. Backend/UI product code, default grants, dependencies, Flyway V1–V10 and report schema 1.1 are unchanged. Its fresh tests, repeated automated installation runs and verified browser setup/reader flow do not imply H1/D1 completion, a release or real-cluster compatibility.

## Previous version-alignment checkpoint — 2026-09-11

The D1 local-workflow increment aligns the backend Maven artifact and application/MCP/OpenAPI metadata to `0.8.1-SNAPSHOT`, and the UI package/lockfile root metadata to `0.8.1-dev.0`. Deployment image templates follow those development identifiers; no images were published or deployed. This supersedes the earlier same-day documentation-only checkpoint, whose historical ledger is unchanged. Unreleased changes describe the working tree, not a published release. Historical milestone labels, Flyway V1–V10 and report schema 1.1 are independent version axes.

## Before the next functional slice

The report-trust correction retains `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report schema 1.1 and Flyway V1–V10. It corrects the dynamic inventory summary projection to nullable sections/counts, without changing retained snapshots or hashes. Report consumers must handle those null values. No dependency, release or global assessment-policy change; an explicit heap threshold is confined to the disposable lab.

The D1 metrics-report validation increment retains the same development versions. It changes only disposable lab configuration, verification scripts and documentation; backend/UI contracts, dependencies, migrations and report schema are unchanged. Its ledger separately records real Prometheus/REST/MCP/browser observations, not a release or production compatibility claim.

The safe OIDC return-path increment also retains the existing development versions. It changes only UI navigation/gating and its tests/guides, not backend contracts, dependencies, migrations or report schemas; the new evidence ledger records its own validation.

The subsequent health/overview corrective slice retains `0.8.1-SNAPSHOT` / `0.8.1-dev.0`: it fixes the current uncommitted development increment, with no dependency, migration, report-schema or release change. Its evidence ledger identifies the new tested source separately.

1. Inspect Git/working-tree state and preserve existing work. Select and record one development version for the intended scope; it need not wait for deferred realm administration. Do not infer production release approval.
2. Update `pom.xml`, application/MCP/OpenAPI metadata, `ui/package.json`, the top-level and root-package versions in `ui/package-lock.json`, plus active image/runtime/docs references as appropriate. Use a valid npm prerelease identifier rather than blindly copying a Maven version.
3. Preserve dated ledgers, previous release notes and migration numbers; never rewrite historical versions as current.
4. Run proportional metadata/build/test validation (Java 21 via per-command jenv); record artifact/report/migration versions separately.
5. Reconcile CHANGELOG Unreleased, project-state, roadmap/milestone status and compatibility. A milestone is complete only when its acceptance evidence exists.

Pure documentation maintenance may leave artifact versions unchanged and state that explicitly. Development-version alignment is now applied; milestone acceptance and production release approval remain separate gates.

## Publication

Commit, push, release/tag, container publishing and deployment require corresponding explicit authorization. Never tag a SNAPSHOT as a production release. Record exact artifacts/digests and tested source; a branch name or passing old ledger is not proof the current working tree was tested.
