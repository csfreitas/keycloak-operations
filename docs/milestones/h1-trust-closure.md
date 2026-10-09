# H1 — Trust hardening acceptance

Status: **IMPLEMENTED LOCALLY / ACCEPTANCE OPEN**. Planning target: 25 September 2026, inherited from the roadmap. Owner: development + security reviewer. No cluster required for this gate.

## Objective and dependencies

Close the correctness/security foundation before accepting the local demonstration. Existing H1 code and dated validation are inputs, not a new acceptance result. Read [project state](../project-state.md), [ADR 0009](../adr/0009-trust-boundaries-before-administration.md) and the [identity model](../identity-model.md).

Roadmap: GOV-01, CHG-01, CFG-01. Requirements: SEC-AUTHZ-001, SEC-MULTI-001, SEC-REPORT-001, SEC-AI-001, FR-ASSESS-003/005, NFR-TEST-001/002, NFR-CHANGE-001.

## Deliverables

- Reproducible tested-source manifest covering tracked and untracked implementation, migrations and dependencies; consolidated Unreleased notes and explicit development-version decision.
- Contract review across REST/MCP/SSE/UI for fail-closed defaults, target filtering, trusted actor, distinct APPROVE/WRITE and BIND/read-only gates.
- Regression evidence for realm-scoped rules, missing data, score availability and report sanitization.
- Tracked residual risks, including raw exception propagation, response bounds, dependency advisories and unsupported identity behaviors; no silent waiver.

## Exit criteria

- [x] Java 21 via jenv and UI baselines pass on recorded source; latest [AGT1 profile ledger](../development/agt1-profile-2026-09-19.md) records a fresh backend baseline (1684 passed) and UI (195 passed on Node24), 2026-09-19. This does not close the other criteria.
- [ ] Anonymous/wrong-target/invalid-token cases fail across applicable surfaces; no secret canaries in output/logs.
- [ ] Reversed realm order produces identical scoped findings; missing/denied/truncated evidence cannot produce favorable scores.
- [ ] Legacy pre-V8 plans remain historical but cannot bypass replan/policy checks; separate permission/read-only negative cases pass.
- [ ] Reviewer accepts remaining limitations or records a blocker; architecture, context, changelog and versions agree.

## Boundaries and handoff

Latest optional integration: [AGT1 profile foundation](../development/agt1-profile-2026-09-19.md)
adds a local host-side fixed tool/target contract, scalar projection and structural
fact/reference checks, without changing product source or backend grants. **45 new
profile/CLI tests** and **81 real local OIDC/MCP checks** pass; no model is evaluated.
Free-form semantic safety remains deliberately unproven; H1 acceptance is unchanged.

Current local validation: [browser identity negatives](../development/d1-browser-negatives-2026-09-19.md)
adds real local browser-to-backend wrong-issuer/audience/expiry rejection, independent
signature checks and a valid positive control. The isolated `/me` 401s unmount
protected UI; expiry is a controlled transport delay. Backend **1684 passed / 9 ITs
skipped**, UI **195 passed**, build passed; **142 runner/realm + 15 fixture tests**,
**75 automated identity checks**, log scan and owned cleanup passed. This closes only
the D1 local browser criterion with earlier session evidence, not the cross-surface
H1 negatives criterion or reviewer acceptance. Unknown-secret/diagnostic/all-source
limits and JWT/SSE revocation limits remain. Historical pending statements below
belong to their checkpoint; current local next work is AGT1/operator reproduction.

Previous local increment: [change-navigation boundary](../development/d1-change-navigation-2026-09-19.md)
closes the identified stale route/target/filter and action-presentation races. Response
identity checks and synchronous duplicate guards supplement unchanged backend gates.
Fresh baseline **1684 backend tests passed / 9 opt-in ITs skipped**; final **195 UI
tests passed** and build passed. Retained red runs, a corrected test-only build error,
synthetic browser checks and clean inventory are recorded. Broader identity negatives,
all-source trust and reviewer acceptance remain open; no real-write or H1 completion.

Previous local increment: [browser-session boundary](../development/d1-browser-session-2026-09-19.md)
binds REST/SSE to local authentication generations, discards late results and removes
protected routes after current 401 or local invalidation. Backend/grants are unchanged;
copied JWTs, connected SSE and other-tab session detection retain documented limits.
Red regressions precede fixes; final UI **160 passed** and build passed. Browser/runtime
evidence is separate. Broader negative cases, ChangeDetail stale-route handling,
all-source trust and reviewer acceptance remain open; this is not H1 completion.

Previous: [read metadata/environment authorization](../development/h1-read-metadata-2026-09-19.md).
READ is checked before REST environment discovery; read-only DTO projections preserve
fixed canonical identities and typed counts without changing raw observations or rules.
Health metadata is filtered after evaluation/on history; ten non-change MCP families
share sanitized domain errors and a fixed unexpected-error response. Baseline **1469**,
final **1684 passed / 9 opt-in ITs skipped**, **215 new invocations**; red regressions
and final-package runtime/cleanup are recorded separately. Provider resource identifiers
may be masked when credential-shaped. Unknown formats, trusted identity text, other
diagnostics, all-source coverage and browser/revocation/reviewer acceptance stay open.
No H1 closure or universal secret/prompt-injection safety is claimed.

Previous: [controlled-change/audit metadata](../development/h1-change-metadata-2026-09-19.md).
Admission rejects unsafe intent/observations rather than substituting applied state;
unsafe executable historical plans require replanning, while read-only history stays
available as a filtered copy. Read-back cannot normalize unsafe metadata into a success.
Optional audit payloads and scoped REST/change-MCP errors are filtered, without changing
identity, target isolation or mandatory installation audit. Fresh baseline **1385**,
focused **117**, final **1469 passed / 9 opt-in ITs skipped**; retained red runs establish
the regressions. Runtime/cleanup evidence is separate. Generic tool families,
trusted-identity text, browser/revocation and reviewer acceptance remain open.

Previous: [metadata trust projection](../development/h1-metadata-trust-2026-09-19.md).
Report/snapshot/assessment presentation and new persistence hide recognizable credentials
after evaluation; historical reads use copies and keep stored hashes intact. JSON drives
Markdown, with literal untrusted metadata and no authority granted by its contents.
Operational change state is not silently redacted. Fresh baseline **1304**, focused
**122**, final **1385 passed / 9 opt-in ITs skipped**. Runtime validation has its own
ledger; other legacy paths, arbitrary-secret handling, browser/revocation and reviewer
acceptance remain open. This is not universal prompt-injection protection.

Previous: [observed capability/source coverage](../development/h1-capability-coverage-2026-09-19.md).
Configured type is separate from observed runtime and fixed Route/config API
advertisement. Inventory consumes that observation, and assessment, snapshot/report
and infrastructure health share a conservative completeness policy. Missing legacy
coverage remains inconclusive. The ledger records current validation; full all-source
authorization/freshness reconciliation, metadata filtering, browser/revocation and
reviewer acceptance remain open. No real-cluster or H1 completion claim.

Previous: [Admin response/diagnostic boundaries](../development/h1-admin-boundaries-2026-09-18.md)
add scoped raw/decoded body/token/list limits, known-schema validation before
defaults, exact realm identity and safe diagnostic admission. Baseline **1050**,
final **1242 passed / 9 opt-in ITs skipped**; source **571 inputs**. Actual local
runtime/cleanup evidence is recorded in the ledger: metrics 75 / installation 83 passed,
while the original default run failed after 56 checks. Subsequent
[authorized recovery](../development/h1-admin-boundaries-recovered-2026-09-18.md)
restored Podman; fresh default 70 checks/JWT scan passed but automatic cleanup failed
(runner exit 1). Separate cleanup exited 0 and final inventory/ports/lock were clean.
The later [19 September runner validation](../development/d1-runner-validation-2026-09-19.md)
closes that local runner follow-up; previous failed results are retained. Ordinary administration,
UI/dependencies/grants/migrations/development versions remain unchanged. Full
capability/source coverage, metadata filtering, dynamic diagnostic reconfiguration
and remaining browser/revocation acceptance stay open; no real-cluster claim.

Previous: [shared-collection budgets](../development/h1-compound-collection-2026-09-18.md)
cover compound report/assessment/health/inventory/metrics reads, without renewing
nested deadlines. Valid earlier observations survive partial collection; scoped
Admin transport is separate from ordinary/controlled writes. Source: 564 inputs;
fresh tests/runtime/cleanup are in the ledger. Not a hard persistence/rendering or
already-blocked transport deadline. Admin payload bounds, third-party DEBUG cause
logging, metadata/source/capability reconciliation and remaining acceptance gates
are explicitly open; no new UI, dependency, migration, grant or release claim.

Previous: [inventory-envelope correction](../development/h1-inventory-envelopes-2026-09-18.md)
validates raw list/GET identity before model defaults and applies per-response
byte/parser/count/deadline bounds to fixed cluster reads. Failed discovery remains
UNKNOWN, valid Kubernetes list conventions remain supported, and explicit clients
disable automatic retries/redirects without new grants. Whole-operation budgets,
complete schema/capability reconciliation and the remaining trust gates stay open.
Baseline **765**, final **970 passed / 9 opt-in ITs skipped**. The source manifest
records 557 inputs; runtime execution results and cleanup are in the
ledger. No UI/dependency/migration/development-version change or live-cluster claim.

Previous: [scrape-readiness correction](../development/h1-scrape-readiness-2026-09-18.md)
uses actual scoped binary `up` observations, not presence counts, and exclusive
installation/ServiceMonitor configuration association. Baseline **622**, final
**765 passed / 9 opt-in ITs skipped**; UI/dependencies/permissions/versions unchanged.
Unknown/unsupported/shared configuration cannot establish favorable monitor evidence.
This does not prove effective Operator selection, raw-source freshness or complete
instance coverage. Its then-next typed-list/response-bound correction is recorded
above; report deadlines and broader evidence/source coverage remain open.

Previous: [compound metrics budgets](../development/h1-operation-budgets-2026-09-18.md)
shares a monotonic deadline across summary/category queries, preserves interruption
and prior observations, rejects late replies, propagates abort partiality and avoids
caching unobserved absence. Configured metrics credentials now fail closed before
transport. Aborts also omit unproven source-availability evidence. Backend baseline **561**, final **622 passed / 9 opt-in ITs skipped**;
UI/dependencies/permissions/migrations unchanged. This does not bound the whole
assessment/report. Its then-open ServiceMonitor defect is addressed by the subsequent
slice above: counting `up` series is not scrape-health evidence. Broader deadlines, inventory
bounds, metadata/raw-source coverage and remaining trust gates are still open.

Previous: [inventory/temporal correction](../development/h1-evidence-temporal-2026-09-18.md)
withholds uncertain inventory facts, sanitizes warning text, propagates partial
collection and validates complete returned metric evaluation grids. Sustained
findings do not substitute instant samples; query failures do not imply idle
traffic. Backend baseline **496**, final **561 passed / 9 opt-in ITs skipped**;
UI/dependencies/permissions/migrations unchanged. Local lab results are recorded in
the ledger. These are partial acceptance inputs: metadata filtering, inventory
bounds, total-operation budgets and raw-source coverage remain open.

Previous: [dependency remediation](../development/h1-dependency-fix-2026-09-18.md) updates the selected Quarkus/MCP/Keycloak alignment and UI router/build/test dependencies. Backend **496 passed / 9 opt-in ITs skipped**, UI **133 passed on Node 24.21.0**, builds and both npm audits passed (zero reported vulnerabilities). Its real local identity/metrics, installation and browser observations remain dated to that slice. Comprehensive backend/image/JDK scanning and maintenance checks remain open. CI's Node 24/audit gate was configured, not executed on GitHub.

The [2026-09-18 failure/bounds correction](../development/h1-failure-bounds-2026-09-18.md) adds deterministic UNKNOWN-preserving health, safe selected failure paths, bounded management/Prometheus bodies and parser/deadline controls, plus unavailable temporal summaries on range failure. Fresh baseline 392 and final **496 backend tests passed**, **9 opt-in ITs skipped**; **133 UI tests** and both builds passed. Real local identity regressions passed **75 metrics / 70 default checks**, each with JWT log verification and owned-resource cleanup. These are recorded partial acceptance inputs, not closure of the combined exit criteria above.

The earlier [dependency review](../development/h1-dependency-review-2026-09-18.md) retains its original nonzero audits and applicability decisions; the subsequent remediation above has separate source/results. Current body/series bounds do not prove global boundedness. The local browser checks narrow the evidence gap but do not establish full browser/cluster acceptance or a comprehensive vulnerability scan.

The [real-identity installation validation](../development/d1-installation-identity-2026-09-18.md) exercises BIND/read-only, actor/target/revision/UID denial and mandatory binding audit through local OIDC/REST/PostgreSQL plus the browser, against a synthetic cluster API. Two installation runs and the normal reader/report regression passed with unchanged default grants. This narrows the installation evidence gap, not full trust acceptance. The subsequent provider/bounds correction and dependency review above have their own evidence and residual work.

The [real metrics-report slice](../development/d1-metrics-report-2026-09-11.md) preserved inconclusive scores while proving actual JVM samples through REST/MCP/browser. Its two review items received a [report-trust correction](../development/d1-report-trust-2026-09-11.md): report-only unknown-infrastructure normalization, and explicit local heap-policy validation explaining the previously missing derived evidence. Original snapshots and broader collector behavior remain unchanged; unset policies are not silently selected. These local fixes do not close full H1 acceptance.

The [D1 browser findings](../development/d1-browser-workflow-2026-09-11.md) received a [verified health/overview correction](../development/d1-health-corrections-2026-09-11.md): the local false outage originated in null-version result construction, not an observed endpoint outage. Access/authentication gaps also receive UNKNOWN reason codes, raw exception messages are omitted from this component, and missing overview counts are explicit. No privileges were broadened. Other provider/engine failure handling, dependency review and broader trust acceptance remain open; development-version alignment is complete.

No production-write certification or new permission grant is implied. Dependency upgrades are limited to the recorded corrective slice. Durable remote execution remains P2. D1 acceptance depends on closing this gate; local D1 test preparation can proceed now.
