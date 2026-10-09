# H1 shared collection budgets — 2026-09-18

Status: **IMPLEMENTED / LOCAL REGRESSION PASSED; H1 ACCEPTANCE OPEN**.

This slice extends the [inventory-envelope controls](h1-inventory-envelopes-2026-09-18.md)
with one synchronous, target-bound collection deadline across report sections,
assessment sources, health checks, inventory and metrics follow-on work.

## Baseline and version decision

Fresh Java 21/jenv `mvn clean verify` passed **970 tests**, **9 opt-in ITs skipped**,
exit 0 at **2026-09-18 16:54:12 −03:00**, before implementation. Raw log:
`/private/tmp/kcops-h1-compound-collection-baseline-20260918.log`.

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`; accumulated tracked/untracked work is preserved.
No commit/push/rebase/tag/release, image publication, dependency upgrade or global
runtime change. Backend/application/MCP/OpenAPI remain **0.8.1-SNAPSHOT**;
UI/root lock **0.8.1-dev.0**, report **1.1**, Flyway **V1–V10**. This is continued
unreleased hardening, not a new product release. UI, deployment, grants and migration
schemas are unchanged. AGENTS.md invariants remain applicable without modification.

## Implemented contract

- `CollectionBudget` defaults to 30000 ms, maximum 120000 ms, configured by
  `collection.operation-timeout-ms`. Same-target nested scopes use the minimum
  remaining time, never restart the parent; different-target nesting rejects.
  Non-inheritable thread-local scope restoration is tested, with explicit captured
  budget objects for HTTP callbacks. No asynchronous collection worker is created.
- Report/snapshot boundaries authorize before budget/fallback. Expiry or retained
  interruption prevents later network calls, rejects unmarked late success, and
  preserves earlier validated facts through explicit partial contracts. Snapshot,
  health and assessment persistence stays synchronous; no background work outlives
  a timed-out wrapper. Budget failures are fixed codes without provider error text.
- Assessment records every eligible budget-skipped source as failed. Partial
  material sources prevent a complete assessment/available score; optional metrics
  keep the existing static-profile policy. Event text distinguishes inconclusive
  assessments instead of advertising an unavailable numerical score.
- Inventory includes a `collection-budget` warning while retaining independent
  completed sections. Per-response Fabric8 deadlines inherit remaining time and
  reject late absence/discovery responses. Independent candidate collection checks
  an existing scope; it does not invent a target when its API has none.
- Metrics keep their tighter configured local deadline and inherit the common
  deadline, including availability, inventory and ServiceMonitor follow-ons.
  Management health shares time across paths and header/body reading; late or
  unexecuted checks are UNKNOWN. Earlier observed WARNING/CRITICAL results remain
  visible with explicit incomplete-collection details.
- Scoped Keycloak reads use a separate client cache and request-local transport
  timeouts capped by remaining time and 5 seconds. Token/Admin HTTP requests and
  response reads check the captured budget. Ordinary/controlled-write clients do
  not receive per-request mutations or changed timeout semantics.

## Evidence and remaining gates

The first focused attempt stopped at compilation: a RESTEasy entity-stream accessor
was protected. The implementation now initializes the wrapper through the supported
`Response.hasEntity()` API, without buffering the body. No assertion or production
budget check was removed. Loopback tests use a long fake-clock deadline (advanced
explicitly), avoiding accidental 100 ms real socket limits in the test harness.

The second focused run executed **410 tests**, with **0 failures / 2 errors** in
the new assessment-event fixture: `Target` requires nonnull Keycloak configuration.
The fixture now supplies a local dummy configuration; no assertion was weakened.

The third focused run passed **410 tests / 0 failures / 0 errors**, exit 0 at
**17:06:54 −03:00**. The first full clean run then executed **1048 tests**, with
**2 failures / 0 errors**: standalone interrupted candidate discovery had changed
its exception contract, and an older inventory interruption test expected the old
generic warning. Standalone discovery retains its prior safe public exception;
active scoped calls retain budget propagation. The inventory test now requires the
specific interrupted-budget warning and reliably clears its test interrupt flag.

Final clean verification passed **1050 tests / 0 failures / 0 errors**, **9 opt-in
ITs skipped**, exit 0 at **2026-09-18 17:12:26 −03:00**: **80 additional test
invocations** versus baseline. Raw log:
`/private/tmp/kcops-h1-compound-collection-verify2-20260918.log`.
The [test references](evidence/h1-compound-collection-tests-2026-09-18.json)
retain hashes of 105 unit reports and five skipped-integration reports. No UI/browser
run is implied; prior 133 UI tests on Node24 remain historical. Existing framework
warnings and deprecated RESTEasy engine integration require continued maintenance;
this is not a warning-free or comprehensive vulnerability-scan claim.

The [source manifest](evidence/h1-compound-collection-source-2026-09-18.json)
records **564 inputs**, aggregate SHA-256
`638cc43ff36dec5c588ec691e6fca2c9cc926177063187b36af83a452d80c302`.
Compared with the preceding inventory-envelope manifest, **36 existing files changed,
seven added, none removed**. These are unsigned local working-tree references, not a
committed/signed release.

## Final-package local laboratories

| Scenario | Executed result | Scope |
|---|---|---|
| Identity + metrics | **75 checks passed**, JWT log scan passed, exit 0 after cleanup | Real local signed identity, Community Keycloak targets, PostgreSQL and Prometheus; not a real cluster |
| Installation discovery/confirmation | **83 checks passed**, JWT log scan passed, exit 0 after cleanup | Same disposable identity/database fixture plus synthetic loopback cluster API; expiry uses explicit fault injection, not a real wait |
| Default identity/report | **70 checks passed**, JWT log scan passed, exit 0 after cleanup | No cluster connection or configured metrics |

Metrics diagnostic directory: `/private/tmp/kcops-identity.sRVHZZ`.
Installation diagnostic directory: `/private/tmp/kcops-installation.AWzInD`.
Default diagnostic directory: `/private/tmp/kcops-identity.ABYnt7`.
Source/test verification matched **564 source inputs / 112 test references** before
and after these runtime checks. No new tests modify a real RHBK/OpenShift environment.
The [run references](evidence/h1-compound-collection-runs-2026-09-18.json) preserve
hashes/excerpts for all nine baseline/intermediate/final/runtime logs and three
packaged artifacts. Node **25.6.1** ran standalone lab scripts only, not a new UI
build; Java **21.0.10** was selected per command through jenv.

Final Podman inspection: **0 containers**, **0 volumes**, default `podman` network
only. Four reusable images retain their original IDs: Community Keycloak 26.7.1
`cc689d358fe6`, PostgreSQL16 `02ad0fee02ae`, cached RHBK26.6 `e7affbc8b409`, and
Prometheus2.55.1 `f59c592ea6d9`. No image pull/global prune was performed. All eleven
fixture ports were bindable and `/private/tmp/kcops-identity-lab.lock` was absent.
Only runner-owned transient containers/network and disposable tmpfs database/TSDB
state were removed. That test data is not recoverable; diagnostic logs, source
evidence, reusable images and build/dependency caches were retained.

Documentation review covers **128 Markdown documents**, **728 local links** and
**15 indexed milestones**, with zero errors/warnings and six parser self-check
groups passed. `git diff --check` passed. These offline checks do not establish
external-link correctness or milestone acceptance. Architecture, context, roadmap,
milestones and Unreleased notes are aligned; historical ledgers were not rewritten.

The deadline is cooperative **collection time**, not a hard response-time SLA:
database persistence, client initialization, deterministic evaluation/rendering,
serialization and already-blocked DNS/TLS/I/O phases can finish later. Request/body
checks and best-effort abort do not promise immediate resource termination. Admin
payload/schema bounds, raw-source freshness/instance coverage, configured-versus-
observed capability reconciliation, broader metadata filtering and remaining
browser/revocation trust gates remain open. Application-owned abort/close diagnostics
are fixed; inherited RESTEasy transport diagnostics may still include raw causes at
DEBUG if an operator enables them. No global logger override or blanket log-safety
claim is introduced; third-party diagnostic filtering remains an H1 review item.
No UI/browser rerun, CI execution,
container-image scan/build, native execution or real RHBK/OpenShift acceptance is
implied. H1/D1 remain open; D2 requires the explicitly approved dedicated environment.
