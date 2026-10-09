# D1 — Reproducible authenticated local workflow

Status: **CURRENT — IN PROGRESS**. Planning target: 16 October 2026. Owner: development + local operator. OpenShift is **not required**.

## Objective and dependencies

An operator follows a guide without editing application code and completes the read-only assessment/report workflow using two local targets and real platform identity. Depends on H1 acceptance; builds on [identity](../development/local-identity-validation-2026-09-11.md) and [installation confirmation](../development/installation-onboarding-2026-09-11.md) already implemented locally.

Roadmap: D1, GOV-01, AGT-01 prototype. Requirements: FR-UI-001/002, FR-REST-001, FR-MCP-001, FR-REPORT-001–006, SEC-MULTI-001, SEC-AUTHZ-001, NFR-TEST-001/002.

## Implementation slices

1. Record source/versions; align development-version references before the next functional change (see [release workflow](../development/release-versioning.md)). Provide reproducible named fixtures, fixed scope and cleanup on failure.
2. Exercise browser login, logout, refresh/expiry and authenticated SSE through a real local IdP; denied target navigation and late responses must not retain another target's data.
3. Generate health/assessment/metrics-present and metrics-unavailable reports through UI, REST and MCP. Separate current state from collection completeness.
4. Exercise installation discovery/review/confirmation with the real local identity and explicitly configured mock cluster API. Cover stale run, replaced UID, cross-actor/cross-target and read-only denial; label cluster responses synthetic.
5. Publish a minimal operator quickstart and connect the [AGT1 prototype](agt1-reference-agent.md), with the deterministic no-AI path independently usable.

## Exit criteria

Latest agent foundation: [AGT1 local profile](../development/agt1-profile-2026-09-19.md)
adds fixed scoped report collection, deterministic fact projection/no-AI fallback and
structural-only explanation checks. **45 new profile/CLI tests** and **81 real local
OIDC/MCP checks** passed with owned cleanup. No model/provider is configured or run.
Detailed report-bound finding/evidence grounding, selected-client adaptation and
semantic/model evaluation remain open, as does independent operator reproduction.

Current validation: [real-browser identity negatives](../development/d1-browser-negatives-2026-09-19.md)
records native 401 for wrong issuer, wrong audience and an expired signed token;
the valid control receives 200 and mounts scoped protected UI. Each case records
independent signature/issuer/audience/expiry booleans. The expiry transport delay is
deliberate and bounded, not ordinary renewal behavior. Actual App/AuthProvider/API
and the local Community IdP/backend are used; no synthetic response or added grant.
Fresh backend **1684 passed / 9 opt-in ITs skipped**, UI **195 passed**, build passed,
**142 runner/realm + 15 fixture tests**, and **75 automated identity checks** passed.
JWT scan, owned cleanup and independent final inventory passed. Combined with the
session ledger below, this closes only the local browser criterion; independent
operator reproduction, H1/D1 acceptance and AGT1 remain open. Historical “next” items
in earlier summaries describe those checkpoints, not the current queue.

Previous UI follow-up: [change-navigation boundary](../development/d1-change-navigation-2026-09-19.md)
isolates each change/target/filter visit, including late action failures/completions,
and checks returned identities. Duplicate submissions are blocked synchronously.
Fresh backend baseline **1684 passed / 9 opt-in ITs skipped**, final UI **195 passed /
22 files**, build passed. Synthetic browser checks include delayed reads/actions,
403/404, mismatched IDs, filters, target removal and keyboard focus; local control
styles/selection semantics were revalidated. The fixture was stopped and final runtime
inventory is clean. This closes the identified stale-route defect, not the complete
browser/IdP negative criterion, H1 acceptance, AGT1 or production-write recovery.

Previous UI follow-up: [browser-session boundary](../development/d1-browser-session-2026-09-19.md)
prevents late refresh/REST/SSE work from crossing authentication generations. Current
401 removes protected routes; target 403 and stale-401 behavior preserve valid sessions.
Fresh backend **1684 passed / 9 opt-in ITs skipped**; UI baseline 133, final **160
passed / 21 files** and production build passed. Red runs are retained. Browser and
cleanup observations are distinct from unit coverage. Cross-tab detection occurs at
refresh failure, not immediate revocation; SSE/JWT server limits remain explicit.
The full browser-negative criterion below stays open, as do ChangeDetail route
stale-result handling, H1 acceptance and AGT1.

Previous backend follow-up: [read metadata/environment authorization](../development/h1-read-metadata-2026-09-19.md)
adds target READ before discovery, safe read/health copies and scoped generic MCP
errors while preserving fixed canonical identities and typed counts. Final **1684
backend tests passed / 9 opt-in ITs skipped**. The real-identity runtime scenarios add
five own/foreign/unmapped environment checks. Final-package **83 installation / 75
default / 80 metrics** checks passed, each with JWT scan, owned cleanup and exit 0;
independent inventory is clean with four cached images unchanged. The operator guide
matches these counts. Source manifest: **593 inputs**. Broader browser
negatives/session revocation, H1 acceptance and AGT1 remain open. No browser, real
cluster or live operational-write acceptance is implied by these regressions.

Previous backend follow-up: [controlled-change/audit metadata](../development/h1-change-metadata-2026-09-19.md)
adds rejection at the operational-state boundary and safe historical/audit/error
projections without altering target permissions or retained plan fingerprints.
Final **1469 backend tests passed / 9 opt-in ITs skipped**; final-package labs passed
**83 installation / 70 default / 75 metrics** checks, JWT scans and owned cleanup,
each exit 0. Independent inventory is clean, four reusable images unchanged.
Runtime/source hashes are recorded separately in the ledger. Generic metadata/tool paths,
browser/revocation, H1 review and AGT1 remain open. This is not live write acceptance.

Previous backend follow-up: [metadata trust projection](../development/h1-metadata-trust-2026-09-19.md)
sanitizes report/snapshot/assessment output after evaluation and renders the same safe
structured facts in Markdown. Historical reads do not rewrite rows or hashes; operational
change state remains separate. Final **1385 backend tests passed / 9 opt-in ITs skipped**;
final-package **83 installation / 70 default / 75 metrics** checks passed with JWT
scans, verified cleanup and exit 0. Final independent inventory is clean; four reusable
images are unchanged. The ledger/582-input manifest retain actual source/runtime evidence.
Browser/revocation, remaining legacy metadata paths, H1 reviewer acceptance and AGT1
are still open; no real-cluster or full agent-safety claim.

Previous backend follow-up: [observed capability/source coverage](../development/h1-capability-coverage-2026-09-19.md)
separates configured type from advertised cluster APIs and carries explicit collection
coverage through inventory, assessment, health and reports. Legacy missing coverage
cannot become complete. The dated ledger records validation and cleanup; this does
not close broader browser, metadata, agent or real-cluster acceptance.

Completed operational follow-up: [19 September runner validation](../development/d1-runner-validation-2026-09-19.md) records 138 passing harness tests, full-ID disposable fixture SQL and fresh 83 installation / 70 default / 75 metrics checks, each with automatic owned cleanup and exit 0. Persistent process/resource uncertainty retains the lock. The 575-input manifest, logs/package hashes and independent final inventory agree. Browser/revocation, broader H1 and reference-agent criteria remain open.

Previous backend follow-up: [Admin boundaries](../development/h1-admin-boundaries-2026-09-18.md).
Scoped response/schema limits and diagnostic admission preserve partial evidence
without changing ordinary administration. Final **1242 backend tests passed / 9
opt-in ITs skipped**; source **571 inputs**. The ledger records final-package labs
and cleanup separately: metrics 75 / installation 83 passed; original default failed
after 56 checks. [Authorized recovery](../development/h1-admin-boundaries-recovered-2026-09-18.md)
restored Podman and fresh default 70 checks/JWT scan passed. Automatic cleanup still
failed (runner exit 1); separate scoped cleanup exited 0 and final inventory/ports/lock
were clean. The later runner validation above closes its local operational follow-up;
the failed historical exit remains recorded, without new browser/real-cluster acceptance;
capability/source coverage, metadata, browser/revocation and AGT1 remain open.
Development versions and default grants stay unchanged.

Previous backend follow-up: [shared collection budgets](../development/h1-compound-collection-2026-09-18.md).
Report/assessment/health/inventory and metrics follow-on reads share a target-bound
deadline without background workers; explicit partial outcomes preserve observed
facts. The source/test/runtime ledger records actual validation, not full D1/H1
acceptance. Capability/source coverage, metadata/diagnostic trust gates and AGT1
remain pending. Development versions/default grants stay unchanged; this is not a
hard response-time SLA or a new browser/real-cluster validation claim.

Previous backend follow-up: [inventory envelopes and bounds](../development/h1-inventory-envelopes-2026-09-18.md),
970 passing backend tests with 9 opt-in ITs skipped. Raw typed cluster responses
are validated before defaults, with per-response size/count/time bounds and
UNKNOWN-preserving discovery. Runtime regressions and owned-resource cleanup have
their separate ledger; no new UI/browser or real-cluster acceptance is implied.
Its then-pending compound report budget is addressed above. Full capability/source
coverage, broader trust gates and AGT1 remain pending. Development versions and
default grants stay unchanged.

Previous backend follow-up: [inventory/temporal correction](../development/h1-evidence-temporal-2026-09-18.md),
561 passing backend tests with 9 opt-in ITs skipped. Inventory uncertainty and
incomplete metric evaluation grids cannot supply complete assessment conclusions;
fresh local lab results are recorded separately. UI/build/dependencies/permissions
are unchanged; no new browser acceptance is implied. Whole-operation budgets,
raw-source coverage, broader browser negatives and AGT1 remain pending.

Latest dependency follow-up: [migration and local regression](../development/h1-dependency-fix-2026-09-18.md) retains 496 backend / 133 UI tests, validates the selected maintained/fixed dependencies, and reruns real-identity metrics and installation checks. Browser observations now include post-migration renewal and denied report navigation in both directions. H1 acceptance, broader browser/revocation cases and AGT1 remain open; zero npm advisories does not replace those criteria.

Previous H1 follow-up: [provider failures and response bounds](../development/h1-failure-bounds-2026-09-18.md), with 496 backend / 133 UI tests and passing 75-check metrics plus 70-check default identity regressions. That slice did not rerun browser/installation acceptance or close all H1 criteria. Its [dependency review](../development/h1-dependency-review-2026-09-18.md) preceded the selected remediation above; remaining evidence/temporal-metrics trust, broader dependency scanning, browser negatives and AGT1 are still pending.

Previous local workflow slice: [real-identity installation confirmation](../development/d1-installation-identity-2026-09-18.md) exercises separate setup/read-only instances against a synthetic cluster API, including two passing runs and explicit browser selection/review. The preceding [report trust](../development/d1-report-trust-2026-09-11.md) correction resolves unknown-infrastructure projection and verifies the explicit heap-threshold path. Other unset policies remain unevaluated; this is not complete metrics-rule coverage. Remaining trust, broader browser negatives and agent gates are still open.

Progress: development versions aligned; the [operator guide](../../dev/identity-lab/README.md) and [workflow ledger](../development/d1-browser-workflow-2026-09-11.md) cover the named lab, positive browser login/logout/renewal observations and real target-filtered REST/SSE checks. The subsequent [health/overview correction](../development/d1-health-corrections-2026-09-11.md) resolves the false outage from absent version metadata and missing count rendering. [Safe return paths](../development/d1-return-path-2026-09-11.md) now preserve allowed internal deep links and gate target child pages; real browser permitted/denied navigation passed. [Real metrics and MCP reporting](../development/d1-metrics-report-2026-09-11.md) passed twice locally, with A/B browser observations and a default-mode regression. Local-identity installation confirmation is now exercised against synthetic cluster responses. The current browser-negative ledger above closes the local browser criterion. Broader trust acceptance, independent operator reproduction and the reference agent remain pending.

- [x] Browser recordings/test artifacts show successful login/refresh/logout and failures for wrong issuer/audience, expired token and denied target; revocation behavior/limitations recorded — [three native-401 negatives and valid control](../development/d1-browser-negatives-2026-09-19.md), combined with [real session/target observations](../development/d1-browser-session-2026-09-19.md). Local Community Keycloak only; expiry uses a controlled transport delay. This does not certify every REST/MCP/SSE endpoint, immediate revocation or production writes.
- [x] Authenticated reports and target-filtered events pass twice consecutively with controlled positive/negative fixtures — two 69-check metrics runs in the historical [metrics-report ledger](../development/d1-metrics-report-2026-09-11.md); this closes only this local execution criterion, not all report correctness or D1 acceptance.
- [x] Binding requires explicit selection, permitted actor and UID revalidation; denied confirmation never changes binding/audit state — scoped local real-IdP/REST/browser evidence in the installation ledger, not real-cluster acceptance.
- [x] No default grants/read-only settings changed for convenience; setup identity is separate from READ/ASSESS demo identity — false read-only is process-local to the optional disposable setup instance; counterpart verifies denial.
- [x] Local runner lifecycle and full-ID fixture SQL pass their scoped regressions and all three local scenarios; independent inventory confirms no residual containers/volumes/lock and unchanged cached images — [19 September evidence](../development/d1-runner-validation-2026-09-19.md).
- [ ] Operator runs from documented configuration; test evidence, source manifest, guide and final owned-resource inventory agree.

## Exclusions and next step

No real cluster compatibility, new target registration, production governance or hidden collector installation. Preserve reusable local volumes/images; remove only this run's transient resources. Next local work is report-bound structured grounding for AGT1 and independent operator reproduction, with H1 acceptance still required. A selected client/provider and model evaluations follow that grounding contract. On D1 acceptance, proceed to D2; ONB1 and portable collectors remain separate product increments.
