# Milestones

Product delivery slices. Status from Git, implementation and recorded validation — not chat history. A milestone ID is not an artifact version or release promise.

## Current delivery priority

The [complete roadmap](../roadmap.md) and [January demo specification](2027-01-demo-readiness.md) govern delivery order: **H1 trust hardening → D1 local read-only workflow → D2 real RHBK/OpenShift → D3 trustworthy documents → D4/D5 readiness**. Essential authorization is brought forward from 0.8.3. Remaining 0.8.1 realm Slice 4 and broad administration are deferred to P2, not silently declared complete. IAM/business reporting, alerts and isolated SPI assurance are specified as separate tracks. Historical milestone numbers below remain references, not a mandate to continue CRUD before these gates.

## Executable milestones — current plan

**2026-10-09: consolidated development source prepared for authorized main integration.**
[Evidence](../development/repository-integration-2026-10-09.md) records fresh
2117 Java / 549 Node / 232 UI passing tests, 9 skipped opt-in ITs, successful builds,
targeted npm advisory remediation and owned Podman cleanup. Commit/push/PR integration
is explicitly authorized; release, V11 populated upgrade and whole milestone gates
are not closed. Next ONB1 work remains governed legacy adoption/ownership transfer.

**2026-10-09: ONB1 configuration ownership/revisions and atomic audit foundation.**
[ADR 0014](../adr/0014-registry-ownership-before-managed-writes.md),
[contract](../architecture/registry-ownership.md) and
[evidence](../development/onb1-ownership-2026-10-09.md) add V11 conservative legacy
classification, explicit configuration ownership, no-op reconciliation, ORM concurrency
and atomic SYSTEM audit. Database-backed modes never use configuration fallback.
No automatic adoption, public registration, grants or read-only/BIND change. Populated
upgrades with old/config-ID collisions are gated on reviewed adoption/restore.
Next ONB1 increment is governed legacy adoption/owner transfer, then public registration
and audited administrator bootstrap; whole milestone and H1/D1/AGT gates stay open.

**2026-10-09: ONB1 PARTIAL — administrative preflight 0.1.0.**
[Contract](../registry-preflight.md) and
[ADR 0013](../adr/0013-registry-preflight-before-registration.md) add opt-in REST draft
checks with exact administrator/client authority, bounded closed input and local
collision/reference validation. No registration, destination request, credential
resolution, grants, UI/MCP or read-only/BIND change. [Fresh evidence](../development/onb1-preflight-2026-10-09.md)
includes unit/synthetic HTTP tests and the explicitly approved local Podman repair.
Next ONB1 slice: ownership/revisions and audited registration/bootstrap design;
whole ONB1 and existing H1/D1/AGT1/AGT2 gates remain open.

**2026-10-09: Operator-managed installation direction and draft contract 0.1.**
[ADR 0012](../adr/0012-operator-managed-portable-platform.md),
[architecture/contract](../architecture/operator-installation-contract.md) and
[OP1](op1-operator-installation.md) define a separate installer, portable hub,
configuration ownership and optional future collectors. Documentation only: no
controller, CRD, bundle, runtime changes or new live acceptance. ONB1 owns registry
policy, D2 can use manifests first, and OP1 adds no presentation prerequisite/date.
H1's 25 September target is historical and acceptance remains open, not rescheduled.
Next local product slice is ONB1 contract/policy work while H1/D1 consolidation and
independent reproduction remain release/acceptance gates. See the
[documentation ledger](../development/operator-architecture-2026-10-09.md).

**2026-09-22: reference client 0.1.0 exercised with two real local operators.**
[Evidence](../development/agt2-client-live-2026-09-22.md) records **72 real checks
passed twice**, **37 new adapter tests / 534 combined Node regressions**, and verified
owned cleanup. Fixed-loopback host transport binds protocol/request/session/context;
no AI, source changes or product version bump. Local/MCP sign-out is not IdP revocation;
held replies are injected host delays. This closes the bounded local adapter run,
not whole AGT2/H1/D1/AGT1. Next: consolidated H1/release-source review and independent
operator reproduction before the conditionally authorized commit/release.

**2026-09-21: independent AGT2 client/answer contract 0.1.0, offline prototype.**
[Client guide](../../dev/access-aware-client/README.md) and
[evidence](../development/agt2-client-2026-09-21.md) add bounded two-tool orchestration,
explicit scope clarification, strict observations/references, lifecycle invalidation
and synthetic CLI. **99 new-client / 497 combined Node tests pass**; no AI calls or
new authenticated-client validation. AGT1 0.2.1 and production Java/UI are unchanged.
AGT2 stays partial: next is this client's real two-operator host/MCP integration,
not a model/provider rollout. Conditional commit/release permission is recorded;
readiness and review gates are not met, so no release/tag/commit/push occurs.

**2026-09-19: two restricted AGT2 operators in the authenticated local lab.**
[Evidence](../development/agt2-authenticated-operators-2026-09-19.md) adds real human
PKCE/OIDC and Community provider validation, console walkthrough, separate REST/MCP
clients, exact permitted facts and 55 live boundary checks. No product implementation
or artifact-version change; no model, RHBK/OpenShift or actual resource recreation.
AGT2 and H1/D1/AGT1 stay partial/open. Next is the separately versioned bounded
multi-tool host/profile contract, not automatic model disclosure or broader grants.

**2026-09-19: first restricted AGT2 configuration reads — partial milestone.**
[Contract 1.0](../configuration-reads.md) adds an opt-in role/client/channel-bound
realm/client fact service shared by REST/MCP and `/configuration` in the console.
Pinned internal identities, closed Boolean fields, missing-value semantics and
attributed metadata audit leave AGT1 and legacy target grants unchanged. No model or
default permission is enabled. [Local evidence](../development/agt2-configuration-reads-2026-09-19.md)
distinguishes synthetic tests from the next approved OIDC/provider lab gate.
H1/D1/AGT1 and whole AGT2 acceptance remain open; no new deadline.

**2026-09-19: access-aware operational assistance and retained operator console.**
[AGT2](agt2-access-aware-assistance.md) is a new **PLANNED** milestone for broader
question-driven infrastructure/IAM reads, fine-grained human/client/resource/data
access and UI/REST/MCP parity. The console remains a full non-AI assessment interface.
[Architecture](../architecture/access-aware-operations-assistant.md) and
[ADR 0011](../adr/0011-access-aware-dual-interface.md) distinguish current target-wide
grants/report-only AGT1 from the planned scope. Read authorization is brought forward
from P2; no writes or default tool/grant expansion. No new deadline or change to
H1/D1/AGT1 acceptance. The [documentation ledger](../development/agt2-direction-2026-09-19.md)
records this design-only addition, source inspection and offline validation.

**2026-09-19: SecOps/IAM catalogue and synthetic acceptance data.** The
[catalogue](../development/secops-iam-scenarios.md) adds IAM-06/SECOPS-01 to P1
and HLP-01/SECOPS-02 to P2, with four explicit requirements and 39 authored cases.
Offline checks establish catalogue integrity, not feature/model acceptance. No user
mutation, event collector or privileged agent profile is delivered. See the
[validation ledger](../development/secops-catalogue-2026-09-19.md). H1/D1/AGT1 remain
the delivery priority; P1/P2 stay PLANNED, existing dates and production-write gates
are unchanged. Next within this backlog: prove the bounded read-only IAM-06 source
contract before implementing its assessment; model re-evaluation stays separately scoped.

**2026-09-19: AGT1 local contract alignment, profile 0.2.1.** The
[alignment ledger](../development/agt1-contract-alignment-2026-09-19.md) records
shared unchanged explanation bounds and synchronized instructions/descriptor/guide,
boundary and historical-failure regressions, offline event classification and CI
wiring. Backend baseline **1705 passed / 9 opt-in ITs skipped**; owned PostgreSQL
cleanup confirmed. This patch does not convert the earlier 0/3 trial into a pass:
there is no new model response, external disclosure or authenticated MCP trial.
Next: separately authorized bounded evaluation of the revised profile, then the
remaining integration/repetition gates. Product versions, acceptance and dates unchanged.

**2026-09-19: authorized AGT1 model trial; 0/3 contract acceptance.** The
[successor ledger](../development/agt1-synthetic-model-trial-2026-09-19.md) records
three actual Codex responses using requested gpt-6-astra after explicit instruction/
synthetic-payload disclosure consent. Facts are exact and agent-assisted semantic
review found no violation in these samples, but 11/12 references exceed the enforced
maximum of 10 absent from the supplied instructions. Responses are retained unchanged.
Zero observed tool calls; six client diagnostics are not tool actions. No resolved
model snapshot attestation, real MCP/target access or full milestone acceptance.
Next: local instruction/contract alignment and runner diagnostic classification,
then separately authorized repeat evaluation. The three-response budget is exhausted.
Product/profile versions, milestone acceptance and dates are unchanged.

**2026-09-19: synthetic AGT1 preflight; no model evaluation completed.** The
[trial ledger](../development/agt1-synthetic-preflight-2026-09-19.md) records 70 passing
profile tests, 12 local case checks and three prepared synthetic cases. Codex startup
failed before model/session events; the execution retry was rejected before start
pending explicit permission to disclose the operational instructions as well as
synthetic packets. No bypass or real target access. This bounded approval step is
next at that historical checkpoint; the successor above records the later consent
and trial. Authenticated MCP and repeated model evaluations remain open. Dates unchanged.

**2026-09-19: SCORE1 added to the backlog, not implemented.** The
[rating specification](score1-explainable-rating.md) defines a proposed deterministic
1–100 rating with control-level explanations, separate coverage, product/version
applicability and public lifecycle/patch/CVE intelligence with backport-aware matching.
Weights and caps require review/calibration. This documentation-only addition changes
neither current scoring nor the H1/D1/AGT1 queue, acceptance states or planning dates.

**2026-09-19: AGT1 report-bound structured grounding.** The
[grounding ledger](../development/agt1-grounding-2026-09-19.md) records additive MCP
findingDetails **1.0**, same-report/assessment identity, bounded whole-finding copies,
explicit omissions and profile **0.2.0** source-pointer checks. Fresh backend baseline
**1684**, focused **52**, final **1705 passed / 9 opt-in ITs skipped**; UI **195** and
build passed. **234 Node contracts** (70 profile/CLI + 164 runner/fixture) and **85
real OIDC/MCP checks**, JWT scan and owned cleanup passed. Independent inventory is
clean; source manifest contains 622 inputs. The profile caps serialized packets at
256 KiB, independently of server finding limits.
No model/provider is enabled; semantic/repeated model evaluation and independent
operator reproduction remain open. Full H1/D1/AGT1 acceptance and dates are unchanged.

**2026-09-19: AGT1 provider-neutral contract prototype.** The
[profile ledger](../development/agt1-profile-2026-09-19.md) records reference-profile
**0.1.0**, a fixed report-tool/operator-target boundary, scalar fact projection,
deterministic fallback and structural-only explanation checks. **45 new profile/CLI
tests** pass alongside 157 existing runner/fixture tests; fresh backend **1684 passed /
9 opt-in ITs skipped**, UI **195 passed**, build passed. The opt-in real OIDC/MCP lab
passed **81 checks**, JWT scan and verified cleanup; independent inventory is clean.
No model/provider ran. Detailed report-bound finding/evidence values, selected-client
integration and semantic/adversarial model evaluation remain open. H1/D1 and full
AGT1 acceptance are unchanged; planning dates are unchanged.

**2026-09-19: real-browser identity-negative gate.** The
[browser-negative ledger](../development/d1-browser-negatives-2026-09-19.md) records
three independently characterized signed-token failures with native 401 and blocked
UI, then a valid 200/scoped-Fleet control. Expiry is a bounded transport fault, not
normal renewal. Fresh backend **1684 passed / 9 opt-in ITs skipped**, UI **195 passed**,
build passed, **142 runner/realm + 15 fixture tests** and **75 real-identity checks**
passed. JWT scan, owned cleanup and independent inventory are clean. This closes
only the D1 local browser criterion with the prior session ledger; H1/D1, independent
operator reproduction and AGT1 remain open. Next local work is AGT1. Dates unchanged.
“Next” statements in older entries below describe their historical checkpoint.

**2026-09-19: change-navigation presentation boundary.** The
[navigation ledger](../development/d1-change-navigation-2026-09-19.md) records
route/target/filter-owned views, discarded late results/errors/completions, duplicate
action prevention and response identity checks. Fresh backend baseline **1684 passed /
9 opt-in ITs skipped**; final UI **195 passed / 22 files**, production build passed.
Pre-fix regressions and the subsequently corrected test-only TypeScript build failure
remain recorded. Synthetic browser checks do not exercise real administrative writes
or identity; the fixture was stopped and the final inventory is clean. H1/D1 remain
open; remaining browser issuer/audience/expiry cases and AGT1 are next. Dates unchanged.

**2026-09-19: browser-session transport boundary.** The
[session ledger](../development/d1-browser-session-2026-09-19.md) records demonstrated
refresh/logout and late REST/SSE races, generation-based cancellation, current-401
invalidation and preserved target-403 behavior. Backend baseline **1684 passed / 9
opt-in ITs skipped**; UI **160 passed / 21 files**, production build passed with Node
24.19.0. Red runs remain recorded. Browser observations and cleanup have separate
evidence. This is local presentation protection, not distributed JWT/SSE revocation.
H1/D1 and dates remain unchanged. ChangeDetail stale-route handling, remaining browser
negatives and AGT1 are next local work.

**2026-09-19: read metadata and environment target authorization.** The
[read-boundary ledger](../development/h1-read-metadata-2026-09-19.md) records the missing
READ gate before environment discovery, filtered read copies, post-engine/historical
health metadata and shared cause-free errors for ten non-change MCP tool families.
Canonical identities and typed counts are preserved only at explicit fixed paths;
raw collectors/rules/bindings/operational state are unchanged. Fresh baseline **1469**,
final **1684 passed / 9 opt-in ITs skipped**, with **215 new invocations** and retained
pre-fix failures. Final-package **83 installation / 75 default / 80 metrics** checks,
JWT scans and owned cleanup passed, each exit 0; independent inventory is clean and
the four cached images unchanged. Source manifest: **593 inputs**. H1/D1 remain
open; next are browser negatives/session revocation and AGT1. Planning dates unchanged.

**2026-09-19: controlled-change/audit metadata.** The
[change metadata ledger](../development/h1-change-metadata-2026-09-19.md) records
admission without rewriting applied values, safe historical/idempotent projections,
full-client metadata checks, inconclusive unsafe read-back and scoped audit/error
boundaries. Canonical identities and mandatory installation audit are preserved.
Fresh baseline **1385**, focused **117**, final **1469 passed / 9 opt-in ITs skipped**;
pre-fix failed regressions are retained. Final-package labs passed **83 installation /
70 default / 75 metrics** checks, JWT scans and verified cleanup, each exit 0.
Independent inventory is clean; all four cached images are unchanged.
Source manifest: 587 inputs. Generic-tool, identity, browser/revocation and agent
limits remain open; H1/D1 and their planning dates are unchanged.

**2026-09-19: metadata trust projection.** The [metadata ledger](../development/h1-metadata-trust-2026-09-19.md)
records explicit report/snapshot/assessment/history filtering after evaluation,
JSON-before-Markdown rendering, literal untrusted metadata and unchanged operational
state. New hashes use sanitized inventory; old rows/hashes stay historical. Fresh
baseline **1304**, focused **122**, final **1385 passed / 9 opt-in ITs skipped**.
Final-package labs passed **83 installation / 70 default / 75 metrics** checks with
JWT scans, verified cleanup and exit 0; independent inventory is clean, four cached
images unchanged. The 582-input manifest records final source/package provenance. This bounded
slice does not certify every legacy endpoint, arbitrary secret detection or agent
prompt-injection resistance. H1/D1 and their planning dates remain unchanged.

**2026-09-19: observed cluster capabilities and shared source coverage.** The
[capability ledger](../development/h1-capability-coverage-2026-09-19.md) records
the local correction: configured type is distinct from observed API advertisement,
inventory reuses that observation, and snapshot/report/assessment/health no longer
infer completeness from empty warnings. Legacy absent coverage is inconclusive.
This is a cluster-specific increment; the normalized all-source authorization and
freshness model remains open. H1/D1 gates and planning dates are unchanged.

Validation: **1304 backend tests passed / 9 opt-in ITs skipped**, **243 focused**,
and final-package **83 installation / 70 default / 75 metrics** checks with JWT scans
and verified cleanup. The 580-input manifest and final inventory preserve provenance;
zero containers/volumes remain, four reusable images are unchanged. No new UI/browser
or real-cluster acceptance is implied.

**2026-09-19: local runner reliability slice validated.** [Final runner evidence](../development/d1-runner-validation-2026-09-19.md) records **138 harness tests**, a fresh **1242-test Java baseline / 9 opt-in ITs skipped**, and **83 installation / 70 default / 75 metrics** checks, all with successful scoped cleanup. Transient process-group probe uncertainty is resolved only by verified absence; persistent uncertainty retains the lock. Final inventory is clean and four reusable images are unchanged. The source manifest contains 575 inputs. This closes the local runner follow-up, not full H1/D1 acceptance.

**Historical recovery, 18 September:** [authorized Podman recovery](../development/h1-admin-boundaries-recovered-2026-09-18.md) restored the exact machine and removed verified lab resources. That default run passed 70 checks but automatic cleanup failed with SSH EOF; its exit 1 remains preserved alongside separate successful recovery. The later runner validation above does not establish the cause of the original VM/SSH instability.

**Next work: AGT1 explicitly selected client/model and approved data scope, then integration/model evaluation; independent operator reproduction; residual metadata/diagnostic limitations and normalized all-source coverage remain tracked work. H1/D1 acceptance remains open; no OpenShift access is required for local agent integration.** [Admin boundaries](../development/h1-admin-boundaries-2026-09-18.md) records scoped response/schema limits and safe diagnostic admission. [Shared collection budgets](../development/h1-compound-collection-2026-09-18.md) and [inventory envelopes](../development/h1-inventory-envelopes-2026-09-18.md) are implemented with explicit limitations, not pending work. Earlier [scrape readiness](../development/h1-scrape-readiness-2026-09-18.md), [metrics budgets](../development/h1-operation-budgets-2026-09-18.md), [inventory/temporal](../development/h1-evidence-temporal-2026-09-18.md), [dependency remediation](../development/h1-dependency-fix-2026-09-18.md) and [installation identity](../development/d1-installation-identity-2026-09-18.md) ledgers retain their dated results. Planning dates and functional owners are unchanged.

| ID / specification | State | Dependencies | Planning target / role |
|---|---|---|---|
| [H1 — Trust closure](h1-trust-closure.md) | Locally implemented; acceptance open | Current tested-source review | 25 Sep 2026; development/security |
| [D1 — Local workflow](d1-local-workflow.md) | CURRENT / in progress | H1 for acceptance | 16 Oct; development/operator |
| [AGT1 — Reference agent](agt1-reference-agent.md) | Local contract prototype; full acceptance open | D1 prototype → D2 integration → D3 evaluation | Existing D1–D4 dates; integration/security/presenter |
| [AGT2 — Access-aware assistance](agt2-access-aware-assistance.md) | Partial; first local scoped configuration UI/REST/MCP slice | H1/D1, live identity/provider acceptance; remaining fine-grained domains and host contract | No new deadline; platform/UI/integration/security/operator |
| [D2 — RHBK/OpenShift](d2-rhbk-openshift.md) | Planned; approved lab required | H1/D1 + lab | 13 Nov; maintainer/platform |
| [D3E — Evidence/replay](d3e-evidence-replay.md) | Planned | H1/D1; D2 for RHBK claims | 4 Dec, D3 sub-slice; backend/evidence |
| [D3R — Trustworthy documents](d3r-trustworthy-documents.md) | Planned; on-demand foundation exists | D3E; D2 for RHBK claims | 4 Dec, D3 sub-slice; reporting/reviewers |
| [SCORE1 — Explainable rating](score1-explainable-rating.md) | Planned; design/backlog only | H1/D1; D3E/D3R integration; D2 for RHBK claims | Candidate D3; no new deadline; assessment/operations/security |
| [D4 — Pilots/freeze](d4-pilots-freeze.md) | Planned | D2/D3E/D3R + AGT1 evaluation | 18 Dec; maintainer/presenter |
| [D5 — Presentation readiness](d5-presentation-readiness.md) | Planned | D4 | 8 Jan 2027; presenter/operator |
| [ONB1 — Environment registry](onb1-environment-registry.md) | Partial: preflight + V11 configuration ownership/revisions/audit; adoption/registration/bootstrap open | H1/D1 contracts | No new date; platform/security |
| [OP1 — Operator installation](op1-operator-installation.md) | Planned; draft contract/design only | H1/D1 contracts + consolidated baseline; approved cluster for acceptance; ONB1 for onboarded workflow | No new date; development/platform/security |
| [PORT1 — Container inventory](port1-container-inventory.md) | Not implemented | Approved portable connection contracts; ONB1 for managed registration | No new date; collector/security |
| [PORT2 — Host/offline inventory](port2-host-offline-inventory.md) | Not implemented | Portable contracts + D3E for import/replay | No new date; collector/security |
| [P1 — IAM/continuous observation](p1-iam-continuous-observability.md) | Planned | D3 + approved sources + pilots | Q1 2027 indicative; observability/IAM |
| [P2 — Governance/remediation](p2-governance-remediation.md) | Planned; containment exists | H1 + durable audit/evidence | Q1/Q2 planning; backend/security |
| [P3 — SPI assurance](p3-spi-assurance.md) | Not implemented | Approved sandbox/artifacts + D3; P2 for promotion | Q2 planning; runtime/security |
| [P4 — Operational readiness](p4-operational-readiness.md) | Planned | Acceptance of declared release scope | No artificial date; maintainer/reviewers |

D3E/D3R split existing D3, and AGT1 spans the existing agent gates; they add no deadline. AGT2 extends the product direction without widening AGT1 or requiring multi-tool scope for the existing presentation; each slice depends on accepted fine-grained read controls. ONB1/PORT1/PORT2 do not block a preconfigured-target presentation. Optional IAM-01/02 may enter D3 only after source/privacy acceptance; unique users/MFA/anomalies/SPI remain off the demo critical path.

OP1 is a product-installation track, not a replacement for D2 or an added demo gate.
An empty hub does not depend on full ONB1; an onboarded workflow requires its accepted
administrative policy. HA, collectors and wider support remain separate milestones.

## Definition of done — applies to every milestone

- Scope, dependencies and requirement IDs are recorded; planned behavior is not presented as delivered.
- Each criterion has evidence on a named source revision/working-tree manifest, fixture versions, commands/results/skips and known gaps. Checkboxes stay unchecked until executed and reviewed.
- Security negatives, partial/unavailable states, migration/recovery and resource hygiene are validated where relevant; local mocks do not close real-cluster gates.
- Architecture, APIs/operator guides, project-state, this index/specification, Unreleased changelog and version status agree. Follow [release bookkeeping](../development/release-versioning.md).
- Functional owner/reviewer accepts the scope or records a blocker; no inferred deployment, publication, commit, push or production-write authority.

## Historical milestone mapping

The following numbers retain their original scope/status. Their dated instructions, validation counts and former "next" steps are historical; the executable table above governs current work.

| Milestone | Description | Status |
|-----------|-------------|--------|
| [0.1](0.1-keycloak-admin-readonly.md) | Keycloak Admin Read-only | COMPLETED |
| [0.2](0.2-multi-target.md) | Multi-target | COMPLETED |
| [0.3](0.3-platform-foundation.md) | Platform Foundation | COMPLETED |
| [0.4](0.4-infrastructure-discovery.md) | Infrastructure Discovery | COMPLETED |
| [0.5](0.5-health-assessment.md) | Health & Assessment | COMPLETED |
| [0.6](0.6-prometheus-metrics.md) | Prometheus Metrics | COMPLETED |
| [0.6.1](0.6.1-metrics-hardening.md) | Metrics Hardening | COMPLETED |
| [0.7](0.7-web-ui.md) | Web UI | COMPLETED |
| [0.8](0.8-controlled-administration.md) | Controlled Administration & Change Management | COMPLETED |
| [0.8.1](0.8.1-realm-client-administration.md) | Realm & Client Administration | IN PROGRESS *(Slices 1–3 implemented; remaining realm slice deferred to P2)* |
| [0.8.2](0.8.2-fleet-reporting-target-onboarding.md) | Fleet Reporting & Target Onboarding | PARTIALLY IMPLEMENTED *(reports and existing-target installation confirmation; new target/connection registration and history/replay incomplete)* |
| 0.8.3 | Platform Authorization & Governance | CORE CONTROLS IMPLEMENTED IN H1; full governance remains planned |
| 0.8.4 | Users, Groups & Roles | PLANNED |
| 0.8.5 | Authentication Flows & Client Scopes | PLANNED |
| 0.8.6 | Identity Providers & Advanced Realm Configuration | PLANNED |
| 0.9 | Schedules / alerts | PLANNED |
| 1.0 | Production-ready platform | PLANNED |

Milestones list **requirement IDs**, scope, and acceptance — not Agent prompts.  
Workflow: [`../../AGENTS.md`](../../AGENTS.md) · HEAD and working-tree checkpoint: [`../project-state.md`](../project-state.md)

## Local checkpoint — Admin boundaries, 2026-09-18

The [Admin-boundary slice](../development/h1-admin-boundaries-2026-09-18.md)
adds scoped token/body/list/schema limits and safe diagnostic admission without
changing ordinary administration. Invalid or mismatched realm responses preserve
partial evidence. The 571-input manifest and fresh test/runtime/cleanup evidence
are recorded in its ledger. Versions, UI, dependencies, grants and migrations
are unchanged. Full capability/source reconciliation, broader metadata filtering,
browser negatives/revocation and AGT1 remain pending; H1/D1 stay open.

## Previous checkpoint — shared collection budgets, 2026-09-18

The [shared-collection slice](../development/h1-compound-collection-2026-09-18.md)
propagates a target-bound deadline through report/assessment/health/inventory and
metrics follow-on reads. Partial evidence is preserved; later reads do not start
after expiry. The 564-input source manifest and fresh test/runtime/cleanup evidence
are recorded in its ledger. Versions, UI, dependencies, permissions and migrations
remain unchanged. This is not a hard persistence/rendering/I/O deadline. H1/D1 stay
open; capability/source reconciliation, Admin bounds, metadata/diagnostic filtering
and remaining trust gates precede AGT1 and dedicated-cluster D2 acceptance.

## Previous checkpoint — inventory envelopes, 2026-09-18

The [inventory-envelope slice](../development/h1-inventory-envelopes-2026-09-18.md)
rejects incomplete raw cluster responses before model defaults and introduces
per-response byte/parser/count/deadline bounds. Source manifest: 557 inputs;
versions, UI, dependencies, permissions and migrations unchanged. Execution/cleanup
results are recorded in its ledger. Baseline **765**, final **970 backend tests
passed / 9 opt-in ITs skipped**. H1/D1 stay open; compound report/collection
budgets and broader evidence/capability reconciliation remain local follow-ups.

## Previous checkpoint — scrape readiness, 2026-09-18

The [scrape-readiness slice](../development/h1-scrape-readiness-2026-09-18.md)
corrects presence-count health and first-monitor attribution. Baseline **622**,
final **765 backend tests passed / 9 opt-in ITs skipped**. The source manifest
records 551 inputs; development versions, dependencies, UI, permissions and
migrations remain unchanged. Its then-next inventory envelope/default and response
bounds are recorded above; observed scrape success is not full source coverage.

## Previous checkpoint — compound metrics budgets, 2026-09-18

The [budget/credentials slice](../development/h1-operation-budgets-2026-09-18.md)
shares a deadline across compound metrics calls, rejects late/aborted observations,
prevents unknown presence from becoming absence, propagates partiality and rejects
configured credential failures before transport. Final review removed unproven
source availability on abort. Baseline **561**, final **622 backend tests passed**,
**9 opt-in ITs skipped**. Source manifest: 545 inputs; no dependency/UI/permission/
migration/version change. The ledger separates final labs from intermediate runs.
At that checkpoint ServiceMonitor readiness was the next bounded correction;
it is addressed above. H1/D1 and broader assessment/report deadlines remain open.

## Previous checkpoint — inventory/temporal correction, 2026-09-18

The [inventory/temporal slice](../development/h1-evidence-temporal-2026-09-18.md)
normalizes public collection warnings, withholds uncertain assessment evidence and
propagates partial collection. Metrics require valid complete returned evaluation
points; missing ranges do not produce sustained DB findings, and failures are not
idle traffic. Fresh baseline **496**, final **561 backend tests passed**, **9 opt-in
ITs skipped**. Source manifest: 541 inputs; UI/dependencies/permissions/migrations
unchanged, product versions retained. No new UI/browser claim, commit/push or real
cluster access. Fresh local labs passed **75 metrics / 83 installation / 70 default
identity checks**, each plus JWT log verification; final Podman had zero
containers/volumes and preserved reusable images. See the ledger for trust limits.

## Previous dependency checkpoint — 2026-09-18

The [dependency-fix slice](../development/h1-dependency-fix-2026-09-18.md) validates Quarkus 3.39.4/MCP 1.13.2, aligned Keycloak clients and the Router 7/Vite 7/Vitest 4 migration with Node 24 LTS. Fresh baseline and final **496 backend tests / 9 opt-in ITs skipped**, **133 UI tests**, both builds and zero-finding npm audits passed. Local metrics/identity, installation and default identity regressions passed **75 / 83 / 70 checks**, each with JWT log verification; browser login/renewal, A/B reports, bidirectional foreign-target denial and logout were observed. Product versions, permissions, schemas/migrations and application/test sources remain unchanged. Source manifest includes CI (537 files); Podman finished with zero containers/volumes and reusable images preserved. No commit/push, real cluster, CI execution or image-build claim; H1/D1 remain open.

## Previous provider/bounds checkpoint — 2026-09-18

The [H1 provider-failure/bounds slice](../development/h1-failure-bounds-2026-09-18.md) preserves UNKNOWN health, constrains management/Prometheus HTTP bodies and parser/deadline behavior, removes range-to-instant temporal-statistic fallback and fixes selected engine failure/counter paths. Backend baseline **392**, final **496 passed / 9 opt-in ITs skipped**; **133 UI tests**, both builds and real local identity regressions (**75 metrics / 70 default checks**, plus JWT log verification) passed. Podman finished with no containers/volumes and four reusable images retained.

Backend source changed; UI, dependency inputs, default grants, versions, schema 1.1 and V1–V10 did not. Source manifest covers 536 files. The dependency review is complete but remediation and broader H1/D1 acceptance remain open. No commit/push or actual cluster access.

## Previous installation checkpoint — 2026-09-18

The [installation identity slice](../development/d1-installation-identity-2026-09-18.md) delivers a disposable setup workflow with real local OIDC identity, synthetic namespace-scoped candidate discovery, explicit confirmation and negative binding checks. A separate setup identity and dedicated application instance preserve ordinary reader permissions and product read-only defaults. Shared runner ownership locking and fail-closed JWT log verification are included. Repeated automated installation runs passed. Browser observations verified deep-link login, explicit selection/acknowledgement, UID review, persisted and audited confirmation, logout and the ordinary reader's disabled setup controls; the other target was unchanged.

Backend/UI product code, default grants, dependencies, migrations and report schema are unchanged in this slice. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, schema 1.1 and V1–V10. Remaining H1 trust/provider/engine/bounds, dependency review, broader browser negatives and the AGT1 prototype keep H1/D1 open. D2 requires a separately approved dedicated RHBK/OpenShift cluster and is not part of this local execution.

## Historical local checkpoint — 2026-09-11

Latest increment: [OIDC return-path ledger](../development/d1-return-path-2026-09-11.md), 133 UI tests/build and 54 real local checks passed; browser returned to the permitted report and denied the foreign target report. Backend baseline 376 passed, unchanged. Broader H1/D1 acceptance remains open.

Latest correction: [health/overview ledger](../development/d1-health-corrections-2026-09-11.md), with 376 backend / 97 UI tests passing and 54 real local checks. Missing version no longer triggers a false outage; missing infrastructure counts are explicit. This does not complete H1/D1 or replace broader browser/cluster gates.

The working tree includes the OIDC/PKCE identity foundation, explicit infrastructure connections, exact installation binding, networking association and existing-target REST/UI confirmation with mandatory audit. The latest [D1 workflow ledger](../development/d1-browser-workflow-2026-09-11.md) adds authenticated reports/event delivery and real browser observations. D1 remains in progress: positive browser observations do not close all negative, metrics-present and installation-confirmation gates. D2 still requires the dedicated approved RHBK/OpenShift environment.

The Unreleased changelog consolidates identity/discovery/binding/networking/confirmation and D1 changes. Development versions are now backend `0.8.1-SNAPSHOT` and UI `0.8.1-dev.0`. Neither milestone labels nor Flyway V10 imply a release. No commit, push or release was performed.

## Git mapping (selected)

| Commit | Notes |
|--------|--------|
| `198c237` | Conceptual 0.1–0.3 (`0.1.0`) |
| `4d01a9a` | 0.4 |
| `5f9a1b7` | Track `target` Java package |
| `c0d00a3` | 0.5 |
| `81eff56` | 0.6 |
| `9ebadc9` | 0.6.1 hardening |
| `610e444` | 0.7 Web UI |
| `0eddf31` | 0.8 Controlled Administration |
