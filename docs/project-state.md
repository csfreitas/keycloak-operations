# Project state (HEAD)

Continuation context updated **2026-10-09**. Git, code, tests and repository documentation are authoritative; conversation history is not a specification. Derive current HEAD and inspect the working tree before continuing.

## Current checkpoint — consolidated source integration, 2026-10-09

The operator explicitly authorized commit/push and integration into `main` via PR,
without rebase or force-push. This development checkpoint consolidates the accumulated
security/collection, discovery, console, reference-client and ONB1 work described
below; it is not a release or production deployment. Git/PR state identifies the
actual publication commits; historical ledgers retain their original no-commit state.

[Fresh integration evidence](development/repository-integration-2026-10-09.md):
**2117 Java**, **549 Node** and **232 UI tests passed**; backend/UI builds passed,
**9 optional provider ITs skipped**. A new npm audit finding was corrected by pinning
transitive development package `source-map-js` **1.2.2**; a clean lockfile install,
repeat UI tests/build and final zero-finding npm audit passed. No product version,
permission or default changed. Podman finished with zero containers/volumes and the
same four reusable images. No new live OIDC/browser/cluster/model acceptance.

ONB1/AGT2 remain PARTIAL, OP1 PLANNED; H1/D1/AGT1 and independent release/upgrade
acceptance remain open. **Next: governed legacy adoption/ownership transfer before
public registration or upgrading retained populated installations.** V11 limitations
below still apply even when this source checkpoint is integrated into `main`.

## Previous checkpoint — ONB1 configuration ownership and revision foundation

[ADR 0014](adr/0014-registry-ownership-before-managed-writes.md) and the
[ownership contract](architecture/registry-ownership.md) add conservative Flyway V11,
separate ORM registry revision, no-op configuration reconciliation and mandatory
atomic SYSTEM audit. New seeds explicitly become CONFIGURATION-owned; historical
rows stay LEGACY_UNCLASSIFIED, never adopted by matching names/content. Database/
composite no longer falls back to configuration on initialization failure or emptiness.

This is a compatibility boundary: configured IDs colliding with pre-V11 records
block reconciliation. No retained/customer database was migrated; only disposable
PostgreSQL tests are in scope. Governed adoption/transfer and restore/mixed-version
upgrade acceptance remain open. No public registration, grants, administrative write
policy, credential/network validation or global read-only/BIND change is delivered.
Preflight **0.1.0** and public target/installation contracts are unchanged.

[Fresh evidence](development/onb1-ownership-2026-10-09.md) records the pre-code baseline,
new migration/ORM/transaction tests, final regression and resource cleanup separately
from historical real OIDC/provider results. H1/D1/AGT1 gates remain open, ONB1 and AGT2
PARTIAL, OP1 PLANNED. No release or Operator/cluster acceptance is implied.

Fresh final `mvn clean verify`: **2117 passed / 9 opt-in ITs skipped**, including
64 new ownership/revision/migration/transaction cases. A reviewed binding-resurrection
edge was reproduced before fixing it; the ledger retains both RED and GREEN evidence.
Post-run Podman: zero containers/volumes, default network, same four reusable images.

HEAD `572cb7b`, branch `feature/0.8.1-client-lifecycle`: **414 initial dirty/untracked
entries**, **915-file** source hash baseline. Existing changes preserved; no staging,
commit/push/rebase/tag/release. Backend **0.8.1-SNAPSHOT**, UI **0.8.1-dev.0**, AGT1
**0.2.1**, AGT2 client **0.1.0** stay unchanged; database migrations advance **V1–V11**.
No global Java/runtime/configuration change in this slice. The prior authorized
Podman repair remains intact; tests use the explicitly selected local connection.

Next ONB1 slice: governed legacy adoption and config/API ownership transfer with
expected revisions, mandatory review/audit and safe restoration; only then public
registration/bootstrap/grants and approved destination checks. Preserve consolidated
H1/source review and independent reproduction before any conditional release.

## Previous checkpoint — ONB1 administrative preflight 0.1.0

[Contract](registry-preflight.md) and
[ADR 0013](adr/0013-registry-preflight-before-registration.md) deliver a default-closed
REST draft preflight: exact verified issuer/subject/role/client, authorization before
application body parsing, closed 8 KiB input, syntax and local configuration/database
ID/reference checks. No candidate endpoint request, credential-value resolution,
registration, grant, UI/MCP, migration or read-only/BIND policy change.

ONB1 is **PARTIAL**, not completed. Explicit ownership/revisions, transactional and
audited registration, governed first-admin/grant bootstrap, destination approval,
rate/time budgets and real OIDC admission remain separate next steps. OP1 stays
PLANNED, AGT2 PARTIAL and H1/D1/AGT1 acceptance/release gates stay open.

Fresh validation and local Podman repair are recorded in the
[evidence ledger](development/onb1-preflight-2026-10-09.md), including the initially
failed baseline, approved backup, bounded correction and cleanup. Do not relabel
synthetic HTTP identities as real OIDC/provider or OpenShift acceptance.
Final `mvn clean verify`: **2053 passed / 9 opt-in ITs skipped**, including 233 new
preflight cases. Packaging passed; final selected Podman inventory: zero containers/
volumes, default network, same four reusable images. Documentation checks passed.

HEAD remains `572cb7b`, branch `feature/0.8.1-client-lifecycle`; **401 initial dirty/
untracked entries** and a **898-file** pre-edit hash inventory were preserved.
Product versions stay backend **0.8.1-SNAPSHOT**, UI **0.8.1-dev.0**, AGT1 **0.2.1**,
AGT2 client **0.1.0**; only the new preflight response has independent **0.1.0**.
No commit/push/rebase/tag/release or cluster access. The operator separately authorized
repair of the malformed local Podman registry file with a backup; no global Java
change, image/volume pruning or guessed private registry endpoint.

Next: consolidate local evidence and H1/source review, then design ONB1 ownership,
revision/precedence and audited write/bootstrap contracts before adding persistence.
A successful preflight is never permission or a reusable registration plan.

## Previous checkpoint — Operator-managed installation design

[ADR 0012](adr/0012-operator-managed-portable-platform.md) accepts a separate
installation Operator, portable Operations hub and optional future site collectors.
The [architecture](architecture/operator-managed-platform.md) and
[draft installation contract 0.1](architecture/operator-installation-contract.md)
define fields, single-replica/authenticated/read-only limits, ownership, reference
security, status, upgrade and deletion boundaries. The reserved-group YAML example
is deliberately non-deployable. No controller, CRD, bundle or remote collector exists.

[OP1](milestones/op1-operator-installation.md) is a new PLANNED installation track,
not a new presentation deadline or D2 prerequisite. ONB1 retains registration,
explicit first-administrator/grant bootstrap and ownership; current global read-only
still blocks BIND. Changing that policy requires its own reviewed implementation
slice. Empty hub availability is not an onboarded or assessed fleet. Pre-existing
database adoption, runtime UI OIDC, re-encryption and safe migrations remain gates.

This slice changes documentation and one design example only. Product/runtime/test/
deployment files, dependencies, grants and artifact versions stay unchanged:
backend **0.8.1-SNAPSHOT**, UI **0.8.1-dev.0**, AGT1 **0.2.1**, AGT2 client **0.1.0**.
No Java/UI/lab/model/cluster tests or container operations were run. Fresh offline
document checks and limits are recorded in the [ledger](development/operator-architecture-2026-10-09.md);
earlier runtime results below remain historical.

HEAD remains `572cb7b` on `feature/0.8.1-client-lifecycle`; **395 initial dirty/
untracked status entries** were preserved. No staging/commit/push/rebase/tag/release.
H1/D1/AGT1 acceptance and AGT2 PARTIAL remain unchanged; H1's 25 September planning
target is past and open, not silently rescheduled. Existing conditional release
authority is not exercised by this documentation delivery.

Next local product slice: ONB1 bounded registration/ownership and administrative
policy/initial-access contract. Consolidated H1/source review and independent D1
reproduction remain acceptance/release gates. D2 can validate reviewed manifests
before OP1 in an explicitly approved cluster; no cluster is needed for design work.

## Previous checkpoint — authenticated local client 0.1.0 exercise

The [new evidence](development/agt2-client-live-2026-09-22.md) connects the unchanged
client to real local Community Keycloak 26.7.1 through a lab-only host. Two human
code/PKCE identities, separate approved MCP sessions and the actual source validate
permitted facts/references, excluded scopes, stale-pin errors/recovery, replacement
operators and local/MCP sign-out. A bounded finite JSON/SSE adapter verifies protocol,
request IDs and session identity; cancellation reaches the lab HTTP/body reader.
Held live replies use explicitly injected host delays, not measured upstream slowness.

Fresh baseline: **1820 Java passed / 9 opt-in ITs skipped**. New adapter: **37 tests**;
focused suites **206**, combined Node **534**, all passed. **72/72 real checks passed
twice** in fresh owned labs (55 prior server checks + 17 client checks), with JWT log
scan and verified cleanup. No AI, RHBK/OpenShift, new UI/browser run, source mutation,
IdP logout, copied-token revocation or general production OAuth transport is claimed.
All artifact versions, product Java/UI, core client/AGT1 code, dependencies and grants
remain unchanged; only lab adapter/checker/tests, CI and documentation change.
Final inventory independently confirms zero containers/volumes, no lab lock/listeners,
only the default network and the same four cached reusable images. The manifest
identifies 670 source inputs and preserves all 29 historical model-trial artifacts.

HEAD remains `572cb7b`, branch `feature/0.8.1-client-lifecycle`; **392 initial dirty/
untracked entries** were preserved. No staging/commit/push/rebase/tag/release.
Conditional commit/release authorization remains valid but is not yet exercised:
the **local client integration gate is now met**, while consolidated H1 review and
independent operator reproduction/release-source review remain open. Do not keep
calling local MCP integration missing, and do not silently waive unrelated gates.
AGT2 remains **PARTIAL**; all whole milestone criteria and H1/D1/AGT1 status remain open.

Next: consolidate the reviewed experimental-release scope and H1 acceptance evidence,
then independently reproduce the documented local workflow. Select/stage coherent
changes only after review; align release versions and test exact artifacts before
using the conditional publication authority. Provider/model disclosure, wider domains
and production/RHBK/OpenShift acceptance stay separate, not implied by local success.

## Previous checkpoint — offline AGT2 reference client 0.1.0

The [independent client contract](../dev/access-aware-client/README.md) now implements
catalogue → explicit scope selection → bounded sequential reads → immutable facts
and observation references, without a model. Exact closed response validation,
question budgets, freshness, context invalidation and stuck-adapter protection are
executable. The synthetic CLI runs without a server or containers. AGT1 **0.2.1** and
production Java/UI, grants, dependencies and schemas are unchanged. CI adds only the
new offline test suite; profile/answer **0.1.0** is not a product release number.

[Fresh evidence](development/agt2-client-2026-09-21.md): Java baseline **1820 passed /
9 opt-in ITs skipped**; new client **99 passed**, combined Node regression **497
passed**. No new UI/browser, authenticated provider, RHBK/OpenShift or model run is
claimed. The previous two-human lab remains historical evidence for the server,
not validation of this client's new adapter boundary. Local Podman inventory after
the baseline confirms zero containers/volumes; the new client creates neither.

HEAD remains `572cb7b` on `feature/0.8.1-client-lifecycle`; the **390** initial dirty/
untracked status entries were preserved. The operator conditionally authorizes a
commit/release **when ready for the stated use**. That condition is not met for an
integrated operational client: its real authenticated transport/host is not delivered
or exercised, the accumulated working tree needs review, and development artifacts
remain SNAPSHOT/dev. No commit/push/tag/release was performed; authorization is not
misreported as absent. AGT2/H1/D1/AGT1 acceptance remains open/partial.

Next: connect this exact 0.1.0 adapter to the approved two-operator local OIDC/MCP lab,
test logout/context changes and source errors, still **without AI**. Then review the
scoped commit/release candidate and version/artifact provenance. Provider disclosure
and repeated model evaluation require separate authorization and acceptance.

## Previous checkpoint — two authenticated restricted AGT2 operators

The approved [local authenticated validation](development/agt2-authenticated-operators-2026-09-19.md)
uses two dedicated human accounts, real code/PKCE and signed OIDC tokens, distinct
console/MCP client approvals, and an independent `view-realm`/`view-clients` source
account. Both operators were exercised in the real console; the corrected live
checker passed **55** REST/MCP/provider checks. AGT2 stays **PARTIAL**.

Only disposable lab fixtures, bounded validation scripts/tests and documentation
change. Production Java/UI/profile, dependencies, schemas and all artifact versions
remain unchanged. New harness corrections follow observed localhost Secure-cookie
semantics and Quarkus MCP's per-item catalogue encoding; failed artifacts are retained.
Baseline **1820 passed / 9 opt-in ITs skipped**, UI **232 passed / 23 files** and build
passed; final harness **224 passed**. The fresh full runner repeated **55/55**, exit 0;
cleanup and independent inventory confirm zero containers/volumes, no lab network,
lock or fixture listeners, and the same four reusable images. The ledger/manifest
record 660 source inputs and preserved historical evidence.

HEAD remains `572cb7b`; all 386 initial dirty/untracked entries were preserved. No
model, RHBK/OpenShift run, commit, push or rebase. No product-wide acceptance is closed.
Next: the separately versioned bounded multi-tool host/profile contract and evaluation
matrix, before new client/model disclosure approval. Actual recreation, persisted
audit inspection, delegation, broad PII/domain coverage and revocation remain open.

## Previous checkpoint — first restricted AGT2 configuration reads

[Contract 1.0](configuration-reads.md) implements an additive, default-closed channel
for exact configured realm/client Boolean fields: shared policy/service, REST, two MCP
tools and the independent `/configuration` operator screen. Exact role + verified JWT
client + channel grants, pinned internal resource identities, bounded collection and
explicit null/unknown semantics prevent broader target disclosure through this channel.
The existing broad roles are not narrowed or migrated. No model or scopes are enabled.

[Local validation and limitations](development/agt2-configuration-reads-2026-09-19.md)
record fresh baselines, new policy/HTTP/MCP/UI tests, synthetic browser checks and final
verification. Synthetic JWT/provider fixtures do not establish signed-token or live
RHBK/OpenShift acceptance. No external model calls, commit, push or rebase. HEAD remains
`572cb7b`; the 368 pre-existing dirty/untracked entries were preserved, not reset.

AGT2 is **PARTIAL**; H1/D1/AGT1 and all whole AGT2 exit criteria remain open. Next:
approved local OIDC/provider exercise with two dedicated restricted principals, then
the separately versioned multi-tool host contract. Token revocation, delegation,
user/PII policy, scoped aggregates/history and broader domain coverage remain gaps.
Backend/UI development versions stay unchanged; new configuration observation schema
is independent **1.0**, with no migration or default grant. AGENTS invariants now
state the new channel's target binding and additive-grant boundary explicitly.

## Previous checkpoint — AGT2 direction and first-class operator application

The operator explicitly expanded the direction to question-driven assistance across
infrastructure and realm/user/group/role/policy/client configuration according to
the user's access, **while retaining the application for direct evaluation**.
[AGT2](milestones/agt2-access-aware-assistance.md), its
[architecture](architecture/access-aware-operations-assistant.md) and ADR 0011 record
this accepted direction as **PLANNED**, not implemented multi-tool capabilities.
The console and optional MCP client use common services, facts and backend policy;
the console remains usable without a model.

Source inspection confirms current grants are target/operation-level and target
service credentials are separate from the human. AGT1 0.2.1 still exposes only one
report tool. Fine-grained read authorization, field/PII projection, scoped aggregates,
conversation/cache/history isolation and multi-tool evidence contracts are explicit
prerequisites, brought forward from P2 read governance without authorizing writes.
Five new requirements and UI/identity/security/roadmap documentation are reconciled.

This is documentation only: no code, provider/client runtime, permission, version,
schema, migration, dependency or environment mutation; no model, container, cluster,
commit or push. [Validation and limits](development/agt2-direction-2026-09-19.md)
record source inspection and fresh offline documentation checks, not a new Java/UI
or model test run. Prior evidence stays historical; HEAD remains `572cb7b` and
pre-existing dirty work is preserved. AGENTS workflow/invariants are unchanged.

H1/D1/AGT1 acceptance and existing dates remain open/unchanged. AGT2 adds no deadline
or broader presentation promise. Next design-to-implementation slice: a concrete
question/source/field/permission matrix and restricted realm/client read policy
shared by UI/REST/MCP, proven with two different operators before wider tool exposure.
Reference-profile re-evaluation still requires its own new bounded authorization.

## Previous checkpoint — SecOps/IAM catalogue, not implemented capabilities

The [scenario catalogue](development/secops-iam-scenarios.md) records three
operator journeys as four bounded backlog items: IAM-06 password-policy assessment
and SECOPS-01 investigation under P1; HLP-01 temporary access and SECOPS-02 separately
approved containment under P2. Four new requirements and 39 authored synthetic cases
capture scope, version/provider capability, evidence gaps, approval and uncertain
outcomes. Offline catalogue checks are not service, model or live-feature acceptance.

The [slice ledger](development/secops-catalogue-2026-09-19.md) records fresh Java
21/jenv baseline **1705 passed / 9 opt-in ITs skipped**, offline checks and scoped
cleanup. No product/backend/UI/profile implementation, schema, migration, dependency,
permission or product-version change. Reference profile remains **0.2.1 READ/ASSESS**;
no model request, event collection, actual user mutation, cluster, commit or push.
Existing dirty work and historical evidence are preserved; HEAD remains `572cb7b`.

H1/D1/AGT1 acceptance and the delivery queue remain open/unchanged, P1/P2 stay PLANNED.
Next within this new backlog: bounded read-only IAM-06 source/semantics feasibility,
then deterministic implementation and approved version-specific lab validation.
The revised-profile model evaluation requires its own new bounded authorization;
the earlier three-response budget is not renewed by this catalogue approval.

## Previous checkpoint — AGT1 local contract alignment, profile 0.2.1

The [local alignment slice](development/agt1-contract-alignment-2026-09-19.md)
publishes the existing explanation limits from shared constants: 20 items/category,
1000 UTF-16 units/text and 1–10 distinct existing references/item. Tests require exact
agreement between the instructions, README, descriptor and evaluator. Bounds and
source facts are unchanged; profile **0.2.1** requires its exact matching identity,
not relabeled old responses. All three historical 0.2.0 failures remain intact.

Fresh backend baseline: **1705 passed / 9 opt-in ITs skipped**, Java 21 selected
per command through jenv. Profile regressions cover exact boundaries, Unicode/controls,
version mismatch and retained failures; an offline trial-event reader distinguishes
client diagnostics, tool activity and inconclusive logs. CI now runs the offline
profile tests with Node 24; no GitHub run is claimed. Detailed validation is in the ledger.
No Java/UI/backend behavior, product version, schema, migration, permission or dependency
change. The selected local Podman has zero containers/volumes after the baseline;
the four cached reusable images are unchanged. No commit/push or real-environment access.

No new model request or disclosure occurred. Next: obtain a new bounded evaluation
authorization for the revised instructions/packets, then inspect actual responses;
do not claim that local contract tests prove model compliance. Authenticated MCP host
integration, repeat variability, independent operator acceptance and H1/D1/AGT1 remain open.

## Previous checkpoint — AGT1 synthetic trial completed, contract acceptance failed

After explicit disclosure approval, the [bounded model trial](development/agt1-synthetic-model-trial-2026-09-19.md)
received **three responses; 0/3 passed structural validation**. Codex CLI
**0.155.0-alpha.9.2**, requested **gpt-6-astra**, used the existing ChatGPT login.
The resolved model snapshot is not attested by its JSONL. All facts were copied
exactly and agent-assisted semantic review found no violation in these samples,
including the injection case. However, narrative items cite 11/12 paths where the
contract allows at most 10; this upper bound is missing from the supplied instructions.
No answer was repaired or rerun. All three authorized responses have been consumed.

The [bundle](development/evidence/agt1-synthetic-model-trial-2026-09-19.json) retains
29 exact artifacts. Zero tool calls were observed; six client diagnostics were
misclassified by the conservative runner as tool events. This does not attest full
isolation, MCP authentication, real-environment compatibility or general model safety.
The [prior preflight](development/agt1-synthetic-preflight-2026-09-19.md), including
70 profile tests, 12 fixture checks and the failed startup/denied retry, is historical
and unchanged. The new explicit consent allowed resubmission, not a bypass.

Next: align instructions with enforced limits and test the contract locally; fix
trial event classification without repairing these failed samples. New external
evaluation requires a new authorized response budget. Product/profile source digest
(622 inputs), versions and dependencies remain unchanged; no containers, cluster,
global configuration, commit or push. H1/D1/AGT1 remain open.

## Backlog update — SCORE1 (documentation only)

[SCORE1](milestones/score1-explainable-rating.md) adds the requested proposed 1–100
operational rating with reasons, coverage/freshness separate from condition, explicit
product/version applicability and public lifecycle/patch/CVE evidence. The
[architecture proposal](architecture/operational-rating.md) covers reviewed weights,
critical caps, RHBK backports, unknown/stale sources, no inventory disclosure and
retained/replayable explanations. Official source entry points were checked; no
customer environment was assessed and no vulnerability applicability is claimed.
The operator confirmed a 1–100 scale; the proposed formula, example, requirements
and indexes now agree. The existing implementation still permits a legacy score of
0; this planning adjustment does not change historical values or runtime behavior.

This is backlog/design, not implementation. Current 0–100 scoring, runtime, API/MCP
contracts, dependencies and product versions are unchanged. The scoring guide's
completeness description was corrected against the actual engine implementation.
H1/D1/AGT1 next work below and existing milestone dates remain unchanged. No Java/UI
test rerun, container/volume/image operation, cluster access, commit or push is part
of this documentation task. An independent design review prompted explicit separation
of a limited configuration assessment from the requested full environment rating,
and clarification of proven N/A dimensions versus an entirely N/A assessment.

Fresh offline validation: **144 Markdown documents**, **953 local links**, **143
requirement definitions** and **16/16 milestone structures**, zero errors/warnings;
six parser self-check groups and `git diff --check` passed. External sources were
reviewed separately; the offline checker does not certify their content or acceptance.
The **622-input** implementation aggregate remains
`f2ed1cbafd68347352ff77ff286af0da0748dd9b3566c1c6b7f37e4800a80516`, identical to the
latest AGT1 checkpoint. HEAD remains `572cb7b`; accumulated unrelated work is preserved.

## Previous implementation checkpoint — AGT1 report-bound structured findings

The [structured-grounding slice](development/agt1-grounding-2026-09-19.md) adds MCP
findingDetails **1.0**, built only from the same sanitized generated report. Identity,
original source indices and omission counts are explicit; oversized/unsupported
findings are omitted whole. No history join, second assessment or Markdown parsing.
Reference profile **0.2.0** requires this extension and validates exact report/target/
assessment linkage, bounded values and escaped JSON references. Text stays untrusted
data and structural explanation checks still require semantic review.

Fresh backend baseline **1684 passed / 9 opt-in ITs skipped**; focused **52 passed**;
final backend **1705 passed / 9 opt-in ITs skipped**. UI **195 passed / 22 files** and
production build passed. **234 Node contracts** passed (70 profile/CLI + 164 existing
runner/fixture invocations); **85 real local OIDC/MCP checks**, JWT scan and owned
cleanup passed. Independent inventory **19:12:43 −03**: zero containers/volumes,
default network only, four cached images unchanged, twelve ports free and lock absent.
No provider/model, browser walkthrough, real cluster or Git write.

Source: **622 inputs**, **11 changed / 5 added / none removed** versus the profile
foundation; aggregate `f2ed1cbafd68347352ff77ff286af0da0748dd9b3566c1c6b7f37e4800a80516`.
The profile also rejects packets above **256 KiB** including expanded JSON pointers;
this is not a bound on already-decoded host objects or the full raw MCP transport.

Product versions remain **0.8.1-SNAPSHOT / 0.8.1-dev.0**, canonical report **1.1**,
Flyway **V1–V10**; the MCP extension/profile revisions are not product releases.
REST, dependencies, engine decisions, issuer/grants and UI source are unchanged.
HEAD `572cb7b`, accumulated dirty work and dated prior evidence preserved.
AGENTS invariants remain unchanged; H1/D1/full AGT1 acceptance remain open.

Next: an explicitly selected client/model and approved payload/provider scope, then
repeated adversarial/model evaluations. No external credentials or disclosure may
be inferred from continuing local work. Independent operator reproduction and H1
trust limits remain tracked; D2 requires the dedicated approved RHBK/OpenShift lab.
Structured performance/no-traffic proof and retained report/evidence replay remain
absent. Earlier checkpoints describe their historical state, not the current queue.

## Previous checkpoint — AGT1 provider-neutral profile foundation

The [19 September AGT1 slice](development/agt1-profile-2026-09-19.md) adds optional
[reference profile 0.1.0](../dev/reference-agent/README.md), operational instructions,
a fixed report-tool/operator-target adapter, strict compact-report fact projection,
offline CLI, deterministic fallback and structural explanation checks. No model,
provider, SDK, external account or disclosure is enabled. The original report is
unchanged; metadata/Markdown is excluded from the projected context. Exact source
facts/references are checked, but free-form prose still requires semantic/security
review. A test deliberately demonstrates that contradictory prose can pass structurally.

Fresh Java 21/jenv baseline **1684 passed / 9 opt-in ITs skipped**; UI **195 passed /
22 files**, production build passed. **45 new profile/CLI tests** plus 157 existing
runner/fixture tests pass (**202 total**). The optional `--reference-agent` identity
lab passed **81 checks**, JWT scan and owned cleanup, exit 0. It uses actual local
Community Keycloak 26.7.1 OIDC and MCP, not a model or new browser walkthrough.
Independent final inventory **18:38:55 −03**: zero containers/volumes, default network
only, four cached images unchanged, twelve ports free and lock absent.

Source: **617 inputs**, aggregate
`8f0569a5e4f18732f176fb93169738cb61b8f4835d9e6f89d8c1646bd90ba906`;
three changed / eight added / none removed versus the browser-negative manifest.
Product source, dependencies, grants, migrations and backend contracts are unchanged.
HEAD `572cb7b`; accumulated dirty work preserved, no commit/push/rebase/release.
Product versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10;
the new profile's **0.1.0** is an independent contract revision, not a product release.
AGENTS invariants are unchanged. H1/D1 and full AGT1 acceptance remain open.

Important source gap: compact MCP reports do not expose structured findings/evidence
values, and target finding history is not report-bound. No Markdown parsing or history
join fills that gap. Next: a bounded report-bound findings/evidence contract, then
an explicitly selected client/provider integration and repeated model evaluations.
Independent operator reproduction and remaining H1 trust limits are still tracked.
Do not configure external credentials/disclosure by inference; D2 still requires the
explicitly approved dedicated RHBK/OpenShift lab. Older “next” statements are historical.

## Previous checkpoint — D1 real-browser identity negatives

The [19 September browser-negative validation](development/d1-browser-negatives-2026-09-19.md)
uses the actual App/AuthProvider/PKCE adapter/API client, a disposable Community
Keycloak 26.7.1 IdP and the packaged backend. Wrong issuer, wrong audience and expired
signed tokens each received native **401**, with protected UI removed. The valid
control received **200**, displayed only Alice's target and connected events. Signature
verification succeeded independently in all four cases. Expiry was induced by an
explicit bounded transport delay, not simulated HTTP or normal refresh failure.

Fresh Java 21/jenv baseline **1684 passed / 9 opt-in ITs skipped**, UI baseline/final
**195 passed / 22 files**, production build passed. Runner/realm tests **142 passed**,
browser-fixture tests **15 passed**; **75 automated real-identity checks**, JWT log scan
and owned cleanup passed, all exit 0. Independent inventory **18:02:35 −03**: zero
containers/volumes, default network only, four reusable images unchanged, twelve ports
free and lock absent. All four temporary browser tabs closed. No real cluster used.

Source: **609 inputs**, aggregate
`6244c8d482b2ba39c2f8f37525a421d4281628ace3ebaeca449266eaacc7f972`;
three changed / seven added / none removed versus the navigation manifest. Changes
are test fixtures, two restricted public lab clients, supervised runner mode and docs;
product source, grants, dependencies and schemas are unchanged. HEAD `572cb7b`, dirty
work preserved; no commit/push/rebase/release. Versions remain `0.8.1-SNAPSHOT` /
`0.8.1-dev.0`, report 1.1, V1–V10. AGENTS invariants are unchanged.

Combined with prior login/renewal/logout/denied-target evidence, this closes only the
D1 local browser criterion. H1/D1 acceptance and independent operator reproduction
remain open; copied JWT/SSE revocation limits are unchanged. Next local work: **AGT1
reference-agent prototype**, alongside tracked all-source/metadata/diagnostic trust
limits. External model/provider access needs explicit configuration and data scope.
D2 still requires the explicitly approved dedicated RHBK/OpenShift lab. Earlier
checkpoint “next” statements below describe their historical state, not the current queue.

## Previous checkpoint — D1 change-navigation boundary

The [19 September navigation slice](development/d1-change-navigation-2026-09-19.md)
fixes demonstrated stale detail/list results and action controls across route, target
and status-filter changes. Keyed visits and request generations discard old success,
error and completion callbacks, including A→B→A. A synchronous guard prevents duplicate
actions; response change/target identities are checked. Reload clears old controls.
Secondary controls reuse readable styles and filters expose their selected state.

Fresh Java 21/jenv baseline: **1684 passed / 9 opt-in ITs skipped**, exit 0. UI baseline
**160 passed**; pre-fix detail **18 failed**, list **12 failed / 3 passed**. Two later
control regressions failed before correction. Final UI **195 passed / 22 files**,
production build passed, exit 0 with per-command Node **24.19.0**. A test-only TypeScript
selector error was corrected; its failed build log is retained. Real browser testing
used real components with **synthetic HTTP only**, not OIDC or real writes. Owned Vite
process/tab closed; final inventory **17:42:51 −03**: zero containers/volumes, default
network only, four reusable images unchanged, twelve fixture ports free, lock absent.

Source: **602 inputs**, aggregate
`67ac915aeb11231720ab0057a777cfbce4b88e38e8c1159bdfd49c4f54da6303`;
three existing inputs changed, six added, none removed versus the session manifest.
HEAD `572cb7b`, accumulated dirty work preserved; no commit/push/rebase or cluster.
Versions unchanged: `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10. Backend,
auth transport, grants, dependencies and AGENTS invariants are unchanged. The UI
does not cancel/undo server work or establish production-write safety. H1/D1 stay open.

Next local work: remaining browser issuer/audience/expiry cases, then AGT1. Broader
source coverage, metadata/diagnostic limits and production-write recovery stay tracked.
D2 still requires the explicitly approved dedicated RHBK/OpenShift lab.

## Previous checkpoint — D1 browser-session transport boundary

The [19 September session slice](development/d1-browser-session-2026-09-19.md)
fixes demonstrated refresh/logout and late-response races. REST/SSE work is bound to
an in-memory authentication generation, aborted on replacement, and checked again at
asynchronous boundaries. Current 401 invalidates local authentication and removes
protected routes; target 403 does not, and stale 401 cannot revoke a new generation.
AuthProvider invalidates on logout, adapter logout, identity failure and unmount.
No backend, permission, dependency, persistence or issuer configuration change.

Fresh Java 21/jenv baseline **1684 passed / 9 opt-in ITs skipped**, exit 0. UI baseline
**133 passed**; pre-fix provider tests **6 failed / 4 passed**, transport **10 failed /
2 passed**, both exit 1. Corrected provider **10 passed**; final UI **160 passed / 21
files**, production build passed, both exit 0 with per-command Node **24.19.0**.
The default browser-mode runner passed **75 automated checks**, JWT scan, verified
cleanup and exit 0. Browser observations include rejected password, successful renewal
after 102 seconds, A↔B denial, logout in a second tab and first-tab renewal loss detected
after 140 seconds. Independent inventory **17:22:54 −03**: zero containers/volumes,
default network only, four reusable images unchanged, eleven ports free, lock absent.
Source: **596 inputs**, aggregate
`6dca0adb6abb274079f4ff1e1d3f539fb26e3cddb1309d2531b6b5c59579c543`;
five existing inputs changed, three added, none removed. Manual browser scope does not
equal wrong-issuer/audience coverage everywhere; source/package evidence is in the ledger.

HEAD `572cb7b`, accumulated dirty work preserved; no commit/push/rebase or cluster
access. Versions unchanged: `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10.
AGENTS invariants unchanged. H1/D1 remain open. JWT validation is not mandatory session
introspection: copied tokens may remain valid until expiry, and admitted SSE retains
its initial grants for up to five minutes. Cross-tab logout is refresh-detected, not
immediate; abort cannot roll back dispatched server work.

Next local work: the separately identified ChangeDetail route stale-result/action
boundary, remaining browser issuer/audience/expiry cases and AGT1. Broader H1 source
coverage, metadata/diagnostic limitations and production-write recovery remain open.
D2 still requires the explicitly approved dedicated RHBK/OpenShift lab.

## Previous checkpoint — H1 read metadata and environment target authorization

The [19 September read-boundary slice](development/h1-read-metadata-2026-09-19.md)
adds the missing target READ check before REST environment discovery. Ordinary read
DTOs and selected target/inventory/metrics/overview/fleet outputs now use filtered
copies; fixed canonical identity paths and typed count maps are preserved. Raw
collectors, rules, installation binding and operational change state are unchanged.
Health metadata is filtered after engine evaluation and on historical reads. Ten
non-change MCP tool families share a cause-free error projection; unexpected failures
use a fixed message. Provider resource names/IDs may be masked when credential-shaped,
so they are not universally preserved follow-up handles.

Fresh Java 21/jenv baseline **1469 passed** (BUILD SUCCESS); pre-fix failures are
retained in the ledger. Core focused **191**, combined focused **214**, final clean
verification **1684 passed / 9 opt-in ITs skipped**, exit 0 (**215 new invocations**).
Final-package labs passed **83 installation / 75 default / 80 metrics** checks,
each with JWT scan, verified cleanup and exit 0. Five new real-identity environment
authorization checks extend default/metrics coverage. Independent inventory at
**17:00:59 −03** confirms zero containers/volumes, default network only, four cached
images unchanged, eleven fixture ports free and lock absent. Source: **593 inputs**, aggregate
`5671d064a7d8d79001ae633cbfa25ec1b1f40ea6e2634611b6b35ad43d54cf3f`;
24 existing inputs changed, six added, none removed versus the preceding manifest.

HEAD `572cb7b`, accumulated dirty work preserved; no commit/push/rebase or cluster
access. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10.
AGENTS invariants are unchanged. H1/D1 remain open. Next local work: browser negatives
and session revocation, then AGT1. Arbitrary secret formats, explicitly preserved
identity text, internal/framework diagnostics, all-source coverage/freshness, agent
adversarial evaluation and production-write recovery remain tracked limitations.
No OpenShift is required now; D2 still needs the explicitly approved dedicated lab.

## Previous checkpoint — H1 controlled-change/audit metadata

The [19 September change metadata slice](development/h1-change-metadata-2026-09-19.md)
rejects recognizable credentials in caller intent before provider reads/persistence,
and refuses unsafe observed/executable legacy state instead of replacing applied
values. Full client updates check unselected metadata and clear known secret/token
fields; unsafe read-back stays an inconclusive failure. Change history/idempotent
returns and optional audit payloads use safe copies, preserving stored plans/hashes,
canonical identities and target authorization. REST McpException/change-MCP messages
are filtered; optional audit warnings omit causes. Mandatory binding audit stays
transactional. No new UI, grant, migration, dependency or model integration.

Fresh Java 21/jenv baseline **1385 passed**; three pre-fix regression runs demonstrate
the defects (10/16 audit, 14/26 errors, 33/39 change failures), each retained. Focused
green run **117 passed**; final clean verification **1469 passed / 9 opt-in ITs skipped**,
exit 0 (**84 new invocations**). Final-package runtime validation and independent
cleanup are recorded in the new ledger: **83 installation / 70 default / 75 metrics**
checks, JWT scans, cleanup and exit 0 for all three. Independent inventory at
**16:32:37 −03**: zero containers/volumes, default network only, four reusable images
unchanged, eleven ports free and lock absent. Source: **587 inputs**, aggregate
`754fef2a3e6dd81539ba312a6336442dd0724382264d4fd4761c8ef60f9ceb84`;
six existing implementation files changed, one guard and four tests added, none removed.

HEAD `572cb7b`, accumulated dirty work preserved; no commit/push/rebase or cluster
access. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10.
AGENTS' output-versus-authoritative-state invariant is unchanged. H1/D1 remain open.
Next local work: generic metadata/tool error boundaries and browser negatives/revocation,
then AGT1. Unknown secret formats, trusted identity text, all-source coverage/freshness,
agent adversarial evaluation and production-write recovery remain explicit limitations.
No OpenShift is required now; D2 still needs the explicitly approved dedicated lab.

## Previous checkpoint — H1 metadata trust projection

The [19 September metadata slice](development/h1-metadata-trust-2026-09-19.md)
adds explicit post-evaluation redaction to report/snapshot/assessment metadata and
historical diffs. Reports render sanitized structured data, keep instruction-like
metadata literal and preserve typed facts. Historical rows/hashes are not rewritten;
new snapshot hashes use sanitized inventory. Operational desired/applied state keeps
its separate structural filter, preventing silent configuration rewrites. AGENTS now
records this boundary. No UI, grant, dependency, migration or external model change.

Fresh Java 21/jenv baseline **1304 passed**; focused rerun **122 passed**; final clean
verification **1385 passed / 9 opt-in ITs skipped**, exit 0 (**81 new invocations**).
The ledger preserves the first focused assertion failure, pre-review passes and final
tilde-fence correction. Final-package labs passed **83 installation / 70 default /
75 metrics** checks, each with JWT scan, verified cleanup and exit 0. Independent
inventory at **15:57:20 −03**: zero containers/volumes, default network only, four
reusable images unchanged, eleven ports free and lock absent. Source: **582 inputs**,
aggregate `3120dcc67bf8c2f6f3cb99e5b35822d64c38660ab2729a0cd7091c817bbfa2db`;
12 existing source/test files changed, two tests added, none removed.

HEAD `572cb7b`, accumulated dirty work preserved; no commit/push/rebase or cluster
access. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10.
H1/D1 remain open. Next local work: remaining legacy metadata/mutation/audit boundary
review and browser negatives/revocation, then AGT1. Generic secret detection, normalized
all-source authorization/freshness/coverage and full agent adversarial evaluation are
not delivered. No OpenShift is required for the immediate local work; D2 still needs
the explicitly approved dedicated lab.

## Previous checkpoint — H1 observed capabilities and source coverage

The [19 September capability slice](development/h1-capability-coverage-2026-09-19.md)
separates configured infrastructure type from bounded observed Route/config API
versions. Inventory reuses that observation instead of configured client hints;
unsupported versions and failed discovery/version remain gaps while independent
facts survive. Assessment, snapshots/report and infrastructure API health now share
a conservative completeness policy. Legacy missing discovery/coverage cannot become
COMPLETE or HEALTHY; configured VM is unsupported, not unconfigured. No new collector,
permission, dependency, migration, endpoint, UI or release is introduced.

Fresh Java 21/jenv baseline **1242 passed**; focused rerun **243 passed**; final clean
verification **1304 passed / 9 opt-in ITs skipped**, exit 0. The ledger preserves the
first focused fixture failures and their corrections. Final-package local labs passed
**83 installation / 70 default / 75 metrics** checks, each with JWT scan, automatic
owned cleanup and exit 0. Independent final inventory at **15:20:10 −03**: zero
containers/volumes, default network only, four reusable images unchanged, eleven ports
free and lock absent. Source manifest: **580 inputs**, aggregate
`7fa2db8765f4a665607b75ce41e53e07420ee824e2c2f182917a2d5cd06828c6`.
Twenty existing backend/source-test files changed, five added, none removed.
HEAD `572cb7b`, accumulated dirty
work preserved; no commit/push/rebase or cluster access. Versions stay
`0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10. H1/D1 remain open. Next local
work: metadata trust boundary, browser negatives/revocation, then AGT1; normalized
all-source authorization/freshness/coverage remains staged. No OpenShift is needed
for that immediate work; D2 still requires the explicitly approved dedicated lab.

## Previous checkpoint — D1 runner validation

The [19 September runner validation](development/d1-runner-validation-2026-09-19.md) completes the tested local runner reliability slice. Per-run ownership, pinned runtime identity, bounded operations and full-ID fixture SQL are implemented. Transient process-group probe errors remain unknown until independently verified absence; persistent uncertainty still fails and retains the lock. Fresh installation **83**, default identity **70** and metrics **75** checks passed, each with JWT scans, verified cleanup and runner exit 0.

Fresh Java 21/jenv baseline: **1242 passed / 9 opt-in ITs skipped**; final harness: **138/138 passed**; targeted reproduction: **12/12** with the expected timeout status and verified termination. Source manifest: **575 inputs**, aggregate `8dc10a89784ca84ed3454054c9afbb506631aca014be46981127f732a4e658fe`; source/log/package/inventory hashes are recorded in the ledger. Final inventory at **14:49:05 −03**: zero containers/volumes, default network only, four cached images unchanged, all eleven ports free and lock absent. No VM restart, global prune, cluster access, commit/push/rebase or release. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10. H1/D1 remain open; next local product work is capability/source coverage, remaining metadata/browser trust gates and AGT1.

## Previous checkpoint — Podman recovered and cleaned

The user explicitly authorized forced power-off after disclosure of VM-wide interruption/data-loss risk. The [authorized recovery](development/h1-admin-boundaries-recovered-2026-09-18.md) stopped the exact VM, retained its disk/configuration, removed a verified stale network helper and restored Podman. An initial start printed success but did not remain active; the subsequent independent-session start was verified through actual inventory. Podman automatically reassigned SSH port 60082 to 62342; machine CPU/memory/disk remain unchanged. Only the four verified old lab containers and their network were removed.

Fresh default rerun: **70 functional checks and JWT scan passed**, but runner **exit 1** is retained because automatic cleanup received SSH EOF. Podman responded immediately afterward; the new four lab containers/network were re-inspected and a separate scoped cleanup exited **0**. Final independent inventory at **18:08:20 −03**: **zero containers/volumes**, default network only, four cached reusable images unchanged, all eleven fixture ports free, lock absent. Disposable test data are not recoverable; logs/evidence/import files retained. No global prune or additional forced restart.

The 571 source / 117 test / 10 prior run-artifact references still match; recovery adds hashes for 12 logs/inspections and unchanged package artifacts. Prior 1242 backend tests, 75 metrics and 83 installation checks are historical, not rerun here. Code, architecture, AGENTS, product versions (`0.8.1-SNAPSHOT` / `0.8.1-dev.0`), report 1.1, V1–V10, dependencies and grants unchanged. No commit/push/rebase/release or cluster access.

The environment blocker is cleared, but the original freeze and intermittent SSH EOF causes remain unknown. Next operational follow-up: review bounded runner readiness/cleanup and transient runtime failure handling before unattended tests; do not erase the failed exit. Then resume capability/source coverage, metadata/browser trust gates and AGT1. H1/D1 remain open; real D2 needs the explicitly approved dedicated cluster.

## Previous checkpoint — local Podman recovery blocked

The user approved a normal restart and scoped lab cleanup/rerun. The [recovery attempt](development/h1-admin-boundaries-recovery-2026-09-18.md) records that normal Podman stop blocked; the same VM's control interface accepted a normal Stop request (HTTP 202), but still reported running at 17:52:01 −03. Only the blocked CLI invocation was terminated. Proposed forced power-off was rejected before execution by safety review because it risks VM-wide data loss and needs explicit approval. No machine restart, forced stop, resource deletion or rerun occurred; no workaround was attempted.

Immediate next step: request explicit approval for forced power-off of `podman-machine-default`, explaining that all workloads in that VM can be interrupted and data may be lost/corrupted. After recovery, inspect exact resources from the original failure ledger, complete scoped cleanup and rerun the default scenario; do not globally prune. Earlier source/test/artifact hashes still match (571/117/10 references), but test counts below are historical. Code, versions, architecture and AGENTS invariants unchanged; only continuity documentation updated. H1/D1 remain open; no commit/push or cluster access.

## Previous checkpoint — H1 Admin boundaries

Latest increment: [Admin response/diagnostic evidence](development/h1-admin-boundaries-2026-09-18.md). Scoped collection caps raw/decoded Admin bodies at 1 MiB, successful token JSON at 64 KiB and realm/client lists at 500, validates known schema before model defaults, and rejects malformed or mismatched realm evidence without invented empty/count results. Unsafe transport DEBUG/TRACE admission fails closed without altering operator logging; native challenge/cookie processing is disabled while Keycloak OAuth remains enabled. Adapter errors are fixed and cause-free in scope. Ordinary administration/controlled writes remain unchanged.

Source manifest: **571 inputs**, aggregate `11ba9bf7fda4320263784fde565f87dbb6d3730eab0af21cf85aba00bd0be417`; six existing source/test files changed, seven added, none removed versus the shared-collection manifest. UI/dependencies/deployment/grants/migrations unchanged. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10. HEAD `572cb7b`, accumulated dirty/untracked work preserved; no commit/push/rebase/release/global runtime change. AGENTS.md invariants unchanged.

Fresh Java 21/jenv baseline **1050 passed**; final clean verification **1242 passed / 9 opt-in ITs skipped**, exit 0 (**192 added invocations**). The ledger preserves the first focused gzip compatibility error and its scoped fix; focused rerun passed 252 tests before the final additional identity regression. All **571 source inputs / 117 test references** matched. Final-package metrics **75** and synthetic-cluster installation **83** checks passed, including JWT scans and cleanup. Default lab **failed after 56 checks** with a report timeout; local Podman/VM connectivity then failed independent checks and cleanup could not finish. No UI/browser rerun or real-cluster acceptance is implied.

**Immediate recovery required:** do not start another lab or claim Podman is clean. Its API/listing and local VM SSH stopped responding; root cause is unknown. The backend and two identified stuck commands were stopped, but the last four named `kcops-identity-*` containers/network could not be inspected/removed. Ports 15432/18080/18180/18280 remain occupied; other fixture ports are free, owned lock released. No VM restart/global prune/forced deletion was performed. Ask approval before restarting the machine, inspect only the exact resources in the ledger, complete cleanup and rerun the default scenario. Raw diagnostics: `/private/tmp/kcops-identity.zr31NV`. Separate post-failure JWT scan passed. Seven run logs/three artifact hashes retain the failed attempt.

Remaining limits include conservative partial schema coverage, large legitimate environments becoming partial, accessible lists not proving full visibility, broad metadata filtering and logger reconfiguration during blocked I/O/cleanup. Collection deadlines remain cooperative. Next local work: configured-versus-observed capability/source coverage, remaining metadata and browser/revocation trust gates, then AGT1. H1/D1 remain open; D2 still needs the dedicated explicitly approved RHBK/OpenShift environment. No cluster is required for the next local work.

## Previous checkpoint — H1 shared collection budgets

Latest increment: [shared-collection evidence](development/h1-compound-collection-2026-09-18.md). Report, snapshot, health, assessment, inventory and metrics follow-on work now inherit one target-bound monotonic collection deadline (30 seconds by default, at most 120 seconds). Nested calls cannot renew it. Aborted/unexecuted observations remain partial/UNKNOWN, earlier validated facts survive, and no worker outlives a timeout wrapper. Scoped Admin reads use a separate request-bounded client cache; ordinary/controlled-write transport remains unchanged. Partial assessment events no longer advertise an unavailable score.

Source manifest: **564 files**, aggregate `638cc43ff36dec5c588ec691e6fca2c9cc926177063187b36af83a452d80c302`; 36 existing source/test/config files changed, seven added, none removed versus the inventory-envelope manifest. UI/dependencies/deployment/grants/migrations unchanged. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10. HEAD `572cb7b`, accumulated dirty/untracked changes preserved; no commit/push/rebase/release/global runtime change. AGENTS.md invariants unchanged. Fresh validation and cleanup are recorded in the new ledger, not inferred from preceding results.

The deadline is cooperative collection time, not a hard limit for persistence, rendering, DNS/TLS or already-blocked I/O. Admin payload/schema bounds and third-party DEBUG exception logging remain explicit gaps. Next local work: configured-versus-observed capabilities/source coverage and remaining metadata/diagnostic trust gates, followed by the read-only AGT1 prototype. H1/D1 stay open; D2 still needs the dedicated explicitly approved RHBK/OpenShift environment. No cluster is required for this next local work.

Fresh Java 21/jenv baseline **970 passed**; final clean verification **1050 passed / 9 opt-in ITs skipped**, exit 0 (**80 added invocations**). The ledger records corrected interim failures. Final-package labs passed **75 metrics / 83 synthetic-cluster installation / 70 default identity checks**, each with JWT log verification and exit 0 after cleanup. All **564 source inputs / 112 test references** matched hashes. Final Podman: **0 containers/volumes**, default network only, four reusable images unchanged, all eleven fixture ports free and shared lock absent. Disposable tmpfs test data was removed; diagnostics/caches retained. UI/browser tests were not rerun; previous unchanged-UI evidence remains historical. No real-cluster acceptance is implied.

## Previous checkpoint — H1 inventory envelopes

Latest increment: [inventory-envelope evidence](development/h1-inventory-envelopes-2026-09-18.md). Fixed internal cluster reads validate raw typed envelopes and resource identity before model defaults, with per-response byte/parser/count/deadline bounds. Failed discovery remains UNKNOWN; valid typed-list item TypeMeta omission and custom API versions remain compatible. Explicit infrastructure clients retain configured authentication/TLS but disable automatic retries/redirects. Inventory reuses one node observation, rejects missing policy structures and contradictory Pod readiness, and preserves independent valid observations.

Source manifest: **557 files**, aggregate `838b08901744a957673d0177d8bf11336ad17c227671698f801cd42304740f34`. Twelve existing backend files changed and six source/test files were added; none removed. UI/dependencies/deployment/permissions/migrations unchanged. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10. HEAD `572cb7b`, accumulated dirty/untracked work preserved; no commit/push/rebase/release/global runtime change. AGENTS.md invariants unchanged.

Fresh Java 21/jenv baseline **765 passed**; final clean verification **970 passed / 9 opt-in ITs skipped**, exit 0, **205 added invocations**. Runtime validation and cleanup results are recorded in the ledger; UI/browser and real-cluster acceptance are not implied.

Final-package local labs passed **75 metrics / 83 synthetic-cluster installation / 70 default identity checks**, each with compact-JWT log verification and exit 0 after cleanup. All **557 source inputs / 107 test-evidence references** matched. Final Podman: **0 containers/volumes**, default network only; four reusable images unchanged, all 11 fixture ports free and shared lock absent. Disposable tmpfs database/TSDB data was removed; diagnostics/caches retained.

Per-response limits are not a compound inventory/assessment/report deadline, complete schema validation or atomic snapshot. Configured/observed platform reconciliation, raw-source coverage, metadata filtering and remaining browser/revocation gates are still open. Next local work: compound collection/report budgets and remaining trust gates, then the read-only AGT1 prototype. H1/D1 remain open; D2 requires the dedicated explicitly approved RHBK/OpenShift environment. No cluster is required for the next local work.

## Previous checkpoint — H1 scrape readiness

Latest increment: [scrape-readiness evidence](development/h1-scrape-readiness-2026-09-18.md). Health uses actual binary `up` observations, never series counts. The scrape query pins the registered `target_id`; all returned series must pass scope, uniqueness, cardinality and temporal validation. ServiceMonitor association requires the bound installation and a conservative exclusive same-namespace Service/endpoint match; malformed, shared, unsupported or incomplete monitor configuration remains UNKNOWN. No arbitrary PromQL, new RBAC or Secret reads.

Fresh Java 21/jenv baseline **622 passed**; final **765 passed / 9 opt-in ITs skipped**, exit 0, **143 added test invocations**. Source: **551 files**, aggregate `df49827c1ee6528a026b4b653e3e8785fdb75bb63d6c20d5f65191c397c1d418`. Only backend source/tests changed in the source manifest; UI/dependencies/deployment/permissions/migrations remain unchanged. Versions stay `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10. HEAD `572cb7b`, dirty/untracked work preserved; no commit/push/rebase/release/global runtime change. AGENTS.md invariants unchanged.

Final-package labs passed **75 metrics / 83 synthetic-cluster installation / 70 default identity checks**, each plus compact-JWT log verification and exit 0 after cleanup. All **551 source inputs / 104 test-evidence references** matched hashes. Final Podman: **0 containers/volumes**, default network only; four reusable images preserved, all 11 fixture ports free and shared lock absent. Disposable tmpfs database/TSDB state was removed; evidence/build caches retained. No new UI/browser, actual Kubernetes/OpenShift/RHBK, CI/image/native acceptance is implied.

Configuration association and scrape success are independent: they do not prove effective Operator selection, exact monitor-to-series provenance, raw scrape freshness or expected-instance coverage. Its then-next inventory-envelope/default and response-bound correction is recorded above; whole-report deadlines and broader source coverage remain open. H1/D1 stay open; D2 needs the explicitly approved dedicated cluster. The original ledger preserves its historical limitations.

## Previous checkpoint — H1 compound metrics budgets

Latest increment: [operation-budget evidence](development/h1-operation-budgets-2026-09-18.md). Prometheus summaries/categories now share a monotonic configurable budget (30 seconds by default, at most 120 seconds), including header/body time. Expired/interrupted calls stop further queries and reject late results; earlier valid observations survive. Failed presence probes remain unknown and do not poison caches. Budget-aborted performance reports/required-metrics assessments are explicitly partial; optional metrics do not invalidate fully observed static profiles. Configured metrics credential failures no longer fall back to anonymous HTTP requests.

Fresh Java 21/jenv baseline **561 passed**; final **622 passed / 9 opt-in ITs skipped**, exit 0, **61 added test invocations**. Final review additionally corrected unproven source availability on abort. Source manifest: **545 files**, aggregate `954370e3714b7f2b0d16b49c33b848ae0099e5627ef5d60586c204b9ef2350c0`. Backend source/tests and metrics configuration changed; UI/dependencies/permissions/migrations did not. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10. HEAD `572cb7b`, accumulated dirty/untracked tree preserved; no commit/push/rebase/release or global Java/Node change. AGENTS.md invariants unchanged.

This is **not a whole-assessment/report deadline** or hard real-time/resource-termination guarantee. Successful collectors still perform separate inventory/ServiceMonitor work; non-abort missing metrics retain the legacy completeness policy. No browser/UI rerun or real cluster/CI/image-build acceptance is implied.

Final-package local labs passed **75 metrics / 83 synthetic-cluster installation / 70 default identity checks**, each plus compact-JWT log verification and exit 0 after cleanup. All **545 source inputs / 100 test-evidence references** match their hashes. Final Podman: **0 containers/volumes**, default network only; four reusable images preserved, all 11 fixture ports free and shared lock absent. Temporary database/TSDB state was disposable; evidence/build caches retained. Architecture, evidence/operator guides, roadmap/milestones, context and changelog are updated.

At that checkpoint, the next correction was ServiceMonitor readiness: counting `up` series could mistake a present `up=0` for a healthy scrape. That defect and its association/diagnostic review are addressed by the subsequent slice above; the original dated ledger preserves the then-open finding. Broader report/inventory bounds, metadata/raw-source coverage, browser negatives/revocation and AGT1 remain pending. H1/D1 are open; D2 needs the explicitly approved dedicated cluster.

## Previous checkpoint — H1 inventory evidence and temporal metrics

Latest increment: [inventory/temporal evidence](development/h1-evidence-temporal-2026-09-18.md). Inventory warnings use fixed messages/allowlisted resources; missing/denied sections cannot supply favorable default evidence. Known independent observations, including zero/false, remain usable. Exact counts, complete placement and collection markers constrain assessment completeness/confidence/score availability. Prometheus semantic metrics reject invalid timestamps, incomplete evaluation grids, finite subsets and ambiguous series; sustained database-awaiting findings never use instant fallback. NO_TRAFFIC requires corroborating observed zero request rate and known empty percentile evidence.

Fresh Java 21/jenv baseline **496 passed**; final **561 passed / 9 opt-in ITs skipped** after fixing three new Mockito setup errors, with no removed assertions. Independent review found no scoped blocker. Source manifest: **541 files**, aggregate `56443c142e7d16b4a2b6b42bf5d47a507d7e43c1214e7f0aa7058a071c038d9c`. Only backend source/tests changed since the dependency-fix manifest; UI/build/dependencies/permissions/migrations unchanged. Product versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10. HEAD `572cb7b`; accumulated dirty/untracked work preserved, no commit/push/rebase/release.

Fresh local labs passed **75 metrics / 83 synthetic-cluster installation / 70 default identity checks**, each plus JWT log verification and exit 0 after cleanup. Final Podman: **0 containers/volumes**, default network only, four reusable images preserved; fixture listeners/lock absent. All 541 source hashes match. UI tests/build/browser were not rerun; their previous results remain historical. Complete returned evaluation points do not prove raw scrape continuity or complete source-instance coverage. Warning rehydration normalizes text, without migrating persisted payload bytes. General metadata filtering, inventory bounds and total-operation deadlines remain open.

Next: whole-operation budgets and remaining evidence/metadata boundaries, raw-source freshness/coverage, broader browser negatives/revocation and AGT1. H1/D1 remain open; D2 still needs the explicitly approved dedicated cluster. No cluster is required for the next local work.

## Previous checkpoint — H1 dependency remediation

Latest increment: [dependency-fix evidence](development/h1-dependency-fix-2026-09-18.md). Quarkus **3.39.4**, MCP **1.13.2**, BOM-aligned Keycloak Admin/common **26.0.12**; UI Router **7.18.4**, Vite **7.3.6**, Vitest **4.1.11**. UI CI/Docker builder now select Node **24 LTS**, with a moderate-or-higher npm audit gate in CI. Fresh backend baseline and final verification each passed **496 tests / 9 opt-in ITs skipped**; UI **133 tests** and production build passed on **Node 24.21.0**. Both full/runtime npm audits now report **zero vulnerabilities** (previously seven/two affected nodes); this is not a comprehensive backend/image/JDK/OS scan.

Real local metrics/identity regression passed **75 checks + post-browser JWT log verification**; installation regression passed **83 checks + JWT verification** against a synthetic loopback cluster API; default identity regression passed **70 checks + JWT verification**. Browser observations confirmed deep-link login, authenticated operation beyond the initial token lifetime, A metrics/B unavailable metrics, bidirectional foreign-target denial and logout. Full browser negatives/revocation and real cluster acceptance remain open. Application/test sources, permissions, schema/migrations and product versions did not change: `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, report 1.1, V1–V10.

Source manifest: **537 files**, aggregate `a4cd60240687a6248d0a7f7df880dae351c54d0bdf6d8b5df44951bbb15f6771`, now includes CI configuration. Inventory records **231 UI package entries / 354 packaged JARs** with hashes. HEAD `572cb7b`, accumulated dirty/untracked work preserved; no commit/push/rebase/release or global Java/Node change. GitHub CI, container-image build and real RHBK/OpenShift were not executed.

Final verification matched all source hashes and 453 inventory file references. Podman: **0 containers/volumes**, default network only, four reusable images preserved; fixture listeners/lock absent. Task-only Node runtime/archive removed; reusable caches and evidence retained. Independent review found no blocking issue within this dependency slice.

Next: remaining inventory-warning sanitization/legacy assessment evidence defaults, total-operation and temporal-metrics completeness, broader D1 browser negatives/revocation and the read-only AGT1 prototype. Selected dependency fixes are locally verified, but broader dependency scanning and maintenance rechecks remain required before freeze/release; Quarkus 3.39 support is not guaranteed through January. H1/D1 stay open. D2 still requires the dedicated explicitly approved environment; no cluster is needed for the next local slice.

## Previous checkpoint — H1 provider failures and response bounds

Latest increment: [failure/bounds evidence](development/h1-failure-bounds-2026-09-18.md). Fresh backend baseline **392 passed / 9 opt-in ITs skipped**; final **496 passed / 9 opt-in ITs skipped**, **133 UI tests passed**, both builds successful. Health preserves missing/malformed observations as UNKNOWN with deterministic aggregation; selected errors use safe reason codes/messages. Management-health and Prometheus bodies have byte/parser/deadline bounds and reject redirects. Failed ranges no longer fabricate window statistics from an instant sample. Local real-identity regressions passed **75 checks with Prometheus** and **70 without**, each plus JWT log verification; no new browser or installation run is claimed.

At that checkpoint, the [dependency review](development/h1-dependency-review-2026-09-18.md) left remediation open: npm reported seven affected package nodes including development tools, two moderate runtime nodes; backend review identified maintenance/alignment and conditional advisory work, not a comprehensive clean scan. No dependencies changed in that slice; the subsequent remediation is recorded above. Per-response bounds are not an overall operation deadline or complete temporal-metrics validation.

Source manifest: **536 files**, aggregate `85dd6fa7989a53eae40c99ec9003a88b9bf14d5b359f1413c418d15cedf90408`; HEAD `572cb7b`, accumulated dirty/untracked work preserved. Backend code/tests changed; UI, grants, schemas/migrations and dependency inputs did not. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, schema 1.1, V1–V10; no commit/push. Final Podman: **0 containers/volumes**, default network only, four reusable images preserved, fixture listeners/lock absent. H1/D1 acceptance remains open; no real RHBK/OpenShift/Operator compatibility claim or cluster access. AGENTS.md invariants remain unchanged.

## Previous checkpoint — D1 real-identity installation workflow

Latest increment: [installation identity evidence](development/d1-installation-identity-2026-09-18.md). Fresh baseline **392 backend tests passed / 9 opt-in ITs skipped**, **133 UI tests passed**, both builds successful; backend/UI source unchanged in this fixture/test increment. Two clean installation runs passed **83 checks each**, plus JWT log checks; browser setup selection/review/confirmation and ordinary-reader denial passed. Post-browser A revision 3 and matching consumed-run/audit were verified; B remained unchanged; synthetic API recorded **zero writes**. Separate setup/read-only processes and new fixture identities leave normal reader grants/defaults untouched. Both runners now share an ownership lock and fail-closed post-browser JWT scan; 7 fixture and 13 runner tests passed.

Source manifest: **530 files**, all hashes match; HEAD `572cb7b`, accumulated dirty/untracked work preserved. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, schema 1.1, V1–V10; no commit/push. The normal identity/report regression passed **70 checks + JWT scan**. Offline documentation review passed for 120 documents, 573 local links and 15 milestone specs. Final Podman: **0 containers/volumes**, default network only, all four reusable images preserved; fixture listeners and shared lock absent.

Next: remaining H1 provider/engine failure/bounds and dependency review, broader D1 browser negatives and the read-only AGT1 prototype. Local real-identity installation confirmation is no longer the next pending slice. H1/D1 remain open; no real RHBK/OpenShift/Operator compatibility claim and no cluster access is needed for the next local work. D2 requires the dedicated approved environment.

## Previous checkpoint — D1 report trust correction

Latest increment: [report-trust correction](development/d1-report-trust-2026-09-11.md). Backend baseline 376 passed; updated backend **392 passed / 9 opt-in ITs skipped**, UI **133 passed**, both builds successful. Report-only inventory projection now distinguishes uncollected/denied data from observed zeros/false values while preserving original snapshots and hashes. The observed heap-rule gap came from an unset optional threshold, not lost numeric samples; explicit test-only heap policy now reaches REST/MCP assessment evidence. The live metrics run passed **75 checks**, default mode **70**, each plus the JWT log check. Final Podman: 0 containers/volumes; four reusable images preserved. Source manifest: 523 files. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`, schema 1.1, V1–V10; uncommitted, no push.

Next: local-identity installation discovery/review/confirmation against an explicitly configured synthetic cluster API. Do not change global defaults or broaden demo reader permissions; use separate setup identity. Remaining provider/engine hardening, browser negatives, dependency review and reference-agent prototype keep H1/D1 open. No OpenShift access is needed yet.

## Previous checkpoint — D1 real metrics and authenticated reports

Latest increment: [metrics-report evidence](development/d1-metrics-report-2026-09-11.md). **376 backend tests passed / 9 opt-in ITs skipped**, **133 UI tests passed**, both builds successful; production source unchanged in this fixture/test slice. Two consecutive disposable Prometheus runs passed **69 checks each**; default mode passed **66**, each with the separate JWT log check. Authenticated REST/MCP reports and browser reports show real JVM data for A and SKIPPED metrics for B, with denied cross-target access and inconclusive assessments preserved. Final Podman: 0 containers/volumes, default network only, four reusable images retained. Source manifest: 521 files; versions/schema/migrations unchanged; uncommitted, no push.

Next: review the report's legacy unknown-infrastructure projection and metric-to-rule evidence gaps, then local-identity installation confirmation against an explicitly configured synthetic cluster API. Browser negatives, dependency review and reference-agent prototype remain open. H1/D1 are not complete; no OpenShift access is needed yet.

## Previous checkpoint — D1 safe OIDC return paths

Latest increment: [safe return-path evidence](development/d1-return-path-2026-09-11.md). Backend baseline **376 passed / 9 opt-in ITs skipped**, unchanged backend; UI **133 passed**, build successful; **54 real local checks passed**. Browser login returned to A's requested report, while direct B report navigation visibly denied access without mounting its report page. Fixed root callback and permissions unchanged. Source manifest records 520 files; Podman finished with 0 containers/volumes and reusable images preserved. Next: metrics-present/MCP reporting and real-local-identity installation confirmation against the approved synthetic cluster API, not OpenShift yet.

Previous corrective slice: [health and missing-data evidence](development/d1-health-corrections-2026-09-11.md). **376 backend tests and 97 UI tests passed**, both builds successful; **9 opt-in integrations skipped**; **54 real local identity/report/event checks passed**. The live metadata endpoint was reachable but returned no version: the previous null-detail map failure is fixed. Authentication/authorization gaps are also classified explicitly. Overview counts now preserve unknown versus observed zero. Historical health runs/raw snapshots are not rewritten. Versions remain `0.8.1-SNAPSHOT` / `0.8.1-dev.0`; no release or permission change. Final Podman: 0 containers/volumes, four reusable images preserved. D1/H1 acceptance remains open; safe OIDC return paths were addressed by the subsequent increment above.

- Last inspected HEAD: `572cb7b`. The latest implementation is in the **uncommitted working tree**, including untracked source/tests/migrations and documentation. HEAD alone does not contain these deliveries. Preserve all existing changes.
- Delivered locally: explicit infrastructure connections, exact installation binding (V9), Service/Ingress/Route association, and existing-target candidate confirmation with Installation UI, DISCOVER/BIND permissions and mandatory transactional audit (V10).
- Fresh D1 baseline and post-alignment verification: **358 backend tests passed**, **94 UI tests passed**, both builds successful; **9 opt-in integrations skipped**. The [D1 workflow evidence](development/d1-browser-workflow-2026-09-11.md) records real identity/report/event runs, browser observations and limits separately.
- **Development versions are aligned:** backend/application/MCP/OpenAPI `0.8.1-SNAPSHOT`; UI/package-lock root metadata `0.8.1-dev.0`. Deployment image templates follow these identifiers; no images were published. [Version workflow](development/release-versioning.md) distinguishes artifact, milestone, migration and report versions. Earlier dated documentation-review results remain historical.
- [Fifteen executable milestone specifications](milestones/README.md) now cover the existing roadmap, including D3E evidence/replay, D3R documents, AGT1 reference agent, ONB1 registration and PORT1/PORT2 collectors. Existing dates were preserved; no new cluster, model provider or release commitment was selected. [Documentation map](README.md) and [review ledger](development/documentation-review-2026-09-11.md) provide continuity.
- Next work: finish the remaining [D1 local acceptance](milestones/d1-local-workflow.md) / [H1 trust closure](milestones/h1-trust-closure.md). Selected dependency remediation and provider failure/bounds corrections have dated evidence; broader inventory/evidence trust, dependency scanning, browser negatives and the reference agent remain open. Real RHBK/OpenShift requires [D2](milestones/d2-rhbk-openshift.md) and the dedicated approved cluster. No ambient or unrelated cluster access.
- No commit, push, rebase, tag, release or default-permission change was performed. Java is selected per command with jenv. Last recorded Podman inventory was zero containers/volumes with four reusable images preserved; recheck before new runtime work, and never globally prune.
- Confirmation needs READ + DISCOVER + BIND and global read-only disabled. Do not enable these automatically: disabling global read-only can affect other already-granted write operations. New target/connection registration and host/container collectors remain unimplemented.

## D1 identity slice — local, uncommitted

The [D1 identity ledger](development/local-identity-validation-2026-09-11.md) records the OIDC/PKCE UI implementation, authenticated event transport, runtime tenant configuration correction, JDBC-safe SSE subscription and reproducible disposable two-target lab. Final regression: **280 backend tests passed** (9 opt-in ITs skipped), **89 UI tests passed**, both builds successful. Final real-token run passed **32 checks plus the compact-JWT log check**, covering REST/MCP target isolation, separate Identity B credentials and negative token cases. Cleanup confirmed zero containers/volumes and preservation of reusable images. Earlier H1 results below are historical, not evidence of browser login.

D1 is **in progress**, not complete. The subsequent [browser workflow increment](development/d1-browser-workflow-2026-09-11.md) adds real browser observations and authenticated REST report/SSE checks. Full browser negative/late-response coverage, isolated wrong-issuer cases, privileged global-read-only denial, immediate revocation and dependency advisories remain open. No OpenShift cluster was accessed, and this slice has not been committed or pushed. Preserve existing reference-agent roadmap edits.

## Product and direction

**Portable discovery direction (2026-09-11):** approved coverage includes OpenShift/Kubernetes, VMs/physical hosts, Docker, Podman, Compose, standalone JVM/services and extensible other runtimes. [Design and acceptance](architecture/portable-environment-discovery.md) and FR-DISC-001–004 / FR-INV-003 / SEC-INFRA-004–005 separate connection, environment, installation and observation. The [explicit connection slice](development/explicit-infrastructure-connections-2026-09-11.md) removes ambient credentials/global probing; the [exact binding slice](development/exact-installation-binding-2026-09-11.md) adds configured API/kind/name/UID and ownership-scoped workload/pods. The [networking slice](development/installation-networking-2026-09-11.md) associates Services/Ingresses/Routes without name guessing; connectivity/certificates remain unverified. The [installation confirmation slice](development/installation-onboarding-2026-09-11.md) now adds an Installation tab for existing persisted targets, explicit DISCOVER/BIND permissions, expiring retained candidates, UID/context/revision revalidation, V10 managed binding and mandatory transactional audit. Latest local validation: **358 backend tests**, **94 UI tests**, both builds passed; **9 opt-in ITs skipped**, Java 21 via jenv. Podman **0 containers/0 volumes**, four cached images preserved. No real cluster access, commit/push or default-permission change. New connection/target registration, host/container collectors and inventory history remain **not delivered**. Next local gate: end-to-end browser/identity workflow using approved fixtures; real RHBK/networking validation requires the dedicated cluster.

**Keycloak / RHBK Operations Platform** (keycloak-operations-mcp + ui/) provides registered multi-target discovery, configuration/security assessments, health checks, semantic runtime metrics and evidence-based explanations through shared MCP/REST services and a Fleet UI. Deterministic rules and policies decide findings and permitted changes; AI explains them.

Repo: https://github.com/csfreitas/keycloak-operations

The immediate goal is a trustworthy **read-only** demonstration for **15 January 2027** (year inferred from the supplied date; duration not confirmed). Local validation comes first, followed by a dedicated real RHBK/OpenShift environment. Community Keycloak, a configured product label or a local container is not evidence of RHBK/OpenShift HA.

Delivery order: **H1 trust hardening → D1 local workflow → D2 RHBK/OpenShift → D3 trustworthy documents → D4/D5 pilots, rehearsal and readiness**. The [complete roadmap](roadmap.md) defines dates, dependencies, owners and acceptance. The [presentation specification](milestones/2027-01-demo-readiness.md) maps the submitted abstract to go/no-go checks.

## Version and milestones

Backend artifact and application/MCP/OpenAPI metadata are **0.8.1-SNAPSHOT**; UI package and root lockfile metadata are **0.8.1-dev.0**. These are development identifiers, not a published release or a declaration of production readiness. Unreleased changelog entries include the D1 increment; report schema 1.1 and Flyway V1–V10 are unchanged.

| Area | Status |
|---|---|
| Milestones 0.1–0.8 | Historical completed foundations; not production certification |
| 0.8.1 administration | Client Slices 1–3 implemented; realm Slice 4 explicitly deferred to P2; milestone not automatically complete |
| Current delivery | H1 corrective implementation locally validated in working tree; D1/D2 acceptance still open |
| 0.8.2 reporting/onboarding | Partial implementation: on-demand reports and existing-target installation confirmation; new target/connection registration, full history and replay incomplete |
| 0.8.3 authorization/governance | Essential identity/target controls brought forward into H1; full governance remains planned |
| Version index | [milestones/README.md](milestones/README.md) |

Historical milestone commits: 0.7 610e444, 0.6.1 9ebadc9, 0.6 81eff56, 0.5 c0d00a3, 0.4 4d01a9a. These are historical references, not current HEAD.

## Implemented foundation

- Modular Quarkus backend, React UI, shared REST /api/v1 and MCP services, target registry/credential references, PostgreSQL history and Flyway V1–V10 in the current working tree.
- Stable Keycloak Admin REST reads, OpenShift/Kubernetes inventory collectors, deterministic rules/profiles, health engine, semantic Prometheus queries and target isolation.
- Controlled client administration: URL/origin sets; typed PKCE/flows/public-confidential settings; creation and enable/disable. These are separate from the principal read-only presentation flow.
- On-demand operations report combines snapshots, health, assessments/findings and optional metrics into structured JSON and deterministic Markdown. It is not independently persisted as a report artifact.

## H1 changes applied

**Identity and isolation.** Packaged applications default to authenticated, fail-closed access and loopback listeners. Explicit local-lab, dev and test profiles support unauthenticated local use. OIDC protects REST, MCP and SSE; audience and intended role-claim path are configured. Exact role → target → permission grants restrict services, target/fleet/MCP discovery, audit counts/pages and SSE events. APPROVE is distinct from WRITE; audit/approval provenance uses trusted identity instead of caller actor strings. Raw MCP traffic logging is disabled. See [identity model](identity-model.md).

**Evidence and conclusions.** Realm rules evaluate scoped evidence. Duplicate unscoped keys do not silently resolve to the first realm. Denials, truncation and missing metadata remain explicit gaps; permitted realm/client reads continue when server-info metadata is unavailable. Observed product/version is distinct from configured type; keycloak.version.raw is an API observation when available, not a copied fixture label. Partial/unavailable assessments have no favorable overall score in UI/Markdown/compact MCP; legacy numeric fields remain with an explicit scoreAvailable contract.

**Change integrity.** Legacy scalar updates no longer accept PKCE; the typed semantic security path owns it. Idempotency is bound to normalized intent; approval/apply validate integrity, target context and policy. V8 preserves historical plans but requires pre-context pending plans to be replanned before execution. Same-plan lifecycle changes use a database row lock. This is containment, not durable exactly-once remote execution.

**Report provenance.** Schema **1.1** identifies collection start/end, INDEPENDENT_SECTION_COLLECTIONS, packaged rule-catalog SHA-256 and retainedEvidenceReplayAvailable=false. Snapshot/health/assessment IDs are references. Neither the hash nor these IDs prove replay, runtime-rule equivalence, a signature, an atomic snapshot or correctness of every conclusion. See [reporting architecture](architecture/operations-reporting.md).

**Output safety.** JSON/Markdown remain the implemented document formats. Markdown findings include bounded entity/evidence context, escaped metadata and report-specific string redaction. These controls do not provide PDF/DOCX export, signed evidence or offline replay. Current health checks are lightweight, not the complete dependency/component health design in the roadmap.

**Deployment/validation.** OpenShift templates use an authenticated read-only assessor configuration, separate Identity A/B/database secrets and one backend replica; placeholders require provisioning/review. A genuine opt-in read-only RHBK IT replaces the flag-only placeholder. Templates and synthetic-identity HTTP tests do not establish actual OpenShift or real OIDC token validation.

## Designed, not delivered

| Capability | Boundary / plan |
|---|---|
| High-confidence documents | [Design](architecture/trustworthy-reporting.md): immutable retained evidence bundles, report history, replay, export QA and optional signatures remain future work |
| PDF/DOCX and executive documents | Planned outputs from a canonical record, not implemented/export-validated formats today |
| IAM/business observability | [Design](architecture/iam-business-observability.md): login/application indicators, DAU/WAU/MAU, MFA usage, SLO impact and alerts require source/denominator/privacy gates; runtime metrics are not that complete suite |
| SPI assurance | [Design](architecture/spi-assurance.md): artifact review and isolated harness/runtime are planned; no arbitrary SPI upload/execution/deployment endpoint |
| Reference agent | Planned optional versioned MCP profile, setup/adaptation guide and grounding/security evaluations (AGT-01/AGT-02 in the [roadmap](roadmap.md)); D1 prototype, D2 integration, D3 acceptance, D4 rehearsal. No agent runtime/model selected or implemented by this planning update |
| Durable remediation | P2: execution attempts, uncertain-outcome reconciliation, cross-plan coordination and stronger approval governance |
| Production platform | P4: compatibility matrix, scale, retention, backup/restore, independent security review and recovery acceptance |

## Validation: baseline, interim and final

The [final local evidence ledger](development/trust-hardening-validation-2026-09-04.md) records the tested source manifest, failures corrected during validation, results and cleanup. It does not certify event or production readiness.

| Check | Recorded result / scope |
|---|---|
| Before H1: Java 21 via jenv, mvn clean verify | **205 passed**, 0 failed; **8 opt-in ITs skipped** |
| Before H1: UI tests/build | **51 passed**; build **SUCCESS** |
| Interim H1: backend verify | **251 passed**, 0 failed; **8 opt-in ITs skipped**; later RHBK/metadata corrections require revalidation |
| Interim H1: UI tests/build | **64 passed**; build **SUCCESS** |
| Final UI tests/build | **69 passed**; build **SUCCESS**, reported by coordinator after final UI corrections |
| Synthetic Identity A boundaries | REST/MCP/SSE denial and role/target-scoped REST + actual MCP HTTP calls exercised; **not real IdP cryptographic/token validation** |
| Local runtime | Named disposable PostgreSQL, Community Keycloak26.7.1, Prometheus and cached RHBK; live tests completed and resources removed |
| RHBK read-only IT | **2 passed** on fixture26.6.3.redhat-00002; permitted reads, partial report and metadata uncertainty preserved; no RHBK writes |
| Final integrated backend | **280 passed**, 0 failures/errors; **7 selected live ITs passed**, 0 failures/errors/skips; clean verify/build SUCCESS at2026-09-04 20:07:39 -03:00 |
| Packaged MCP/REST | **PASS**; report1.1, PARTIAL/healthUNKNOWN/scoreAvailable=false, 4 evaluated+8 not evaluated; semantic metrics collected; unknown-target denial |
| Final resource inventory | **0 containers, 0 volumes**; validation network removed; 4 preexisting reusable images and default podman network preserved |

Previous Slice 3 validation exercised four controlled-write cases on disposable Community Keycloak 26.7.1 and cleaned up its fixtures. The changed H1 path was separately rerun with the four Community controlled-write cases in the final seven ITs. RHBK writes, actual OpenShift HA, real IdP authentication and Web Origin + semantics remain **NOT VERIFIED**.

## Remaining safety/readiness limits

- Grants are target-level, not realm/client ACLs. Separate APPROVE/WRITE permissions do not prove human presence or two humans; MCP approval remains available to an appropriately authorized principal.
- Remote mutation is not atomic with PostgreSQL. Crashes/timeouts can leave uncertain outcomes; row locking coordinates one plan, not every plan or external writer touching a resource. Production writes remain gated behind P2.
- SSE/MCP state is in-process. SSE grants are captured at subscription and refreshed through bounded reconnect; immediate revocation and multi-replica fan-out are not implemented.
- Completeness is a bounded source/rule heuristic, not statistical confidence or a universal inventory denominator. Coverage is limited to implemented rules/collectors and accessible sources.
- Retained-evidence replay, independent report history, signing, retention enforcement and planned document/IAM/SPI capabilities remain incomplete.
- VM/Docker inventory collectors are absent. Local containers do not establish OpenShift topology, failover or cluster-RBAC compatibility.
- Read-only credentials can legitimately lack server-info metadata. Record unknown product/version or partial evidence; do not elevate privileges or fabricate observations to improve a demo.

## Next operator/agent

1. Read [AGENTS.md](../AGENTS.md), [roadmap](roadmap.md), current milestone and related requirements/architecture.
2. Inspect Git and the latest H1 shared-collection ledger/source manifest at the top of this file; use the dependency-fix inventory for unchanged dependency versions, not current application JAR hashes. The tested working tree is uncommitted. Earlier checkpoints, next-step statements and source digests are historical; they do not identify all current changes or supersede the top checkpoint.
3. Preserve the aligned development versions and keep the consolidated changelog current. Do not silently choose a production release or mark an unfinished milestone complete.
4. Health/overview and return-path ledgers supersede the earlier three D1 findings; do not repeat them as open bugs. Complete remaining D1/H1 local acceptance before D2. Use the executable milestone specifications, not historical 0.x "next" sections. Do not resume broad realm administration ahead of them.
5. Update this context, the relevant milestone, changes and evidence after each slice; explicitly record whether artifact versions changed or remain pending.
6. Preserve user changes. Do not commit, push, rebase, release, publish or provision external environments without corresponding authorization.

Earlier procedure remains a historical handoff: [0.8.1 local validation](development/local-validation-0.8.1-operations-report.md).
