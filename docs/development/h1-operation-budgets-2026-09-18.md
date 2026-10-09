# H1 compound metrics budgets and credential failures — 2026-09-18

Status: **IMPLEMENTED / LOCAL REGRESSION PASSED; H1 ACCEPTANCE OPEN**.

This local increment follows the [inventory/temporal correction](h1-evidence-temporal-2026-09-18.md).
It bounds compound Prometheus collection and closes the configured-credential
anonymous fallback. It does not add a whole-report deadline, cluster access or
production-write authorization.

## Baseline, scope and version decision

Fresh `JENV_VERSION=21 jenv exec mvn clean verify` passed **561 tests**, **9 opt-in
ITs skipped**, exit 0 at **2026-09-18 15:32:24 −03:00**, before edits. Raw log:
`/private/tmp/kcops-h1-budgets-baseline-20260918.log`. Java **21.0.10** is selected
per command with jenv; global Java/Node settings are unchanged.

HEAD remains `572cb7ba5aa9a132f3e04280de9378da08cc61e3` on
`feature/0.8.1-client-lifecycle`, with accumulated tracked/untracked work preserved.
No commit, push, rebase, release/tag or image publication. Backend/application/MCP/
OpenAPI remain **0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**, report schema
**1.1**, Flyway **V1–V10**. No dependency, UI, permission or migration change is
part of this increment. Dynamic availability/evidence maps gain explicit markers;
there is no new persisted report schema. AGENTS.md workflow/invariants are unchanged.

## Implemented behavior

- A summary can perform up to 33 sequential Prometheus requests on a cold cache:
  status, 25 semantic queries, six presence probes and one database range. They
  now share one invocation-local monotonic `MetricsOperationBudget`; category
  queries also share one budget. Default `metrics.operation-timeout-ms=30000`,
  accepted range 1–120000 ms. Invalid configuration fails validation.
- Connection/request timeouts are capped by remaining time. Body handling
  recomputes the remainder when headers arrive. No background wrapper, executor,
  thread-local identity or asynchronous transaction is added. A completed response
  or parse result arriving after expiry is not exposed as a successful observation.
- Expiry/interruption prevents subsequent transport and credential lookup. The
  caller interrupt flag is preserved. Earlier valid measurements survive; missing
  metrics stay unavailable, not zero or NO_TRAFFIC. Fixed reason codes contain no
  provider payloads, credential references or exception details.
- Presence-probe maps contain only actually observed keys. Failed/stale/invalid
  probes are unknown; incomplete/aborted sweeps are not cached. Cache reuse also
  requires the same target configuration. Known observed zero remains false.
- Aborted summaries are DEGRADED with `COLLECTION_BUDGET=NOT_AVAILABLE`; performance
  reports are PARTIAL even if initial reachability was AVAILABLE. Independent core
  sections and earlier values remain. `HTTP_BUCKET_SERIES=UNKNOWN` distinguishes
  the legacy boolean `histogramAvailable=false` from an observed missing histogram.
- The metrics collector emits `metrics.collection.complete=false` on abort and
  avoids follow-on availability/inventory/ServiceMonitor calls. Required-metrics
  assessments become partial with unavailable scores; optional metrics partiality
  alone does not invalidate an otherwise fully observed static profile.
  It also omits `metrics.source.available` on abort: DEGRADED does not prove that
  any connection completed, even when earlier measurements can be retained.
- An explicitly configured credential reference resolving to an exception, null,
  no authentication or invalid supported authentication fails closed before any
  HTTP request. Omitting a credential reference still permits intentional anonymous
  endpoints. A null credential object at the HTTP boundary is unauthorized, not
  silently converted to anonymous access.

The Java HTTP client is shut down with a cancellation request rather than a
graceful `close()` wait after every call. **Cancellation is best effort**, not proof
of immediate socket/thread termination or a hard real-time bound. The body
subscriber cancels its subscription and releases retained byte buffers. See the
[Java 21 HttpClient lifecycle contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html#shutdownNow()).

## Validation

First clean verification passed **618 tests / 0 failures / 0 errors**, **9 opt-in
ITs skipped**, exit 0 at **2026-09-18 15:38:04 −03:00**. A subsequent deterministic
transport-wiring regression was added to distinguish a deadline recomputed after
headers from a send-time duration incorrectly renewed after headers. The next clean
verification passed **619 tests / 0 failures / 0 errors**, **9 opt-in ITs skipped**,
exit 0 at **2026-09-18 15:40:22 −03:00**: **58 added tests** against the baseline.
Raw intermediate log: `/private/tmp/kcops-h1-budgets-verify-final-20260918.log`.

After the additional source-availability review correction described below, a new
complete clean verification passed **622 tests / 0 failures / 0 errors**, **9 opt-in
ITs skipped**, exit 0 at **2026-09-18 15:44:28 −03:00**. This is the final tested
source: **61 added test invocations** versus baseline. Closure log:
`/private/tmp/kcops-h1-budgets-verify-closure-20260918.log`. No assertion was removed
and all three intermediate/final builds passed; the additional correction came
from code review, not a failed test hidden by retries.

The [source manifest](evidence/h1-operation-budgets-source-2026-09-18.json)
records **545 files**, aggregate SHA-256
`954370e3714b7f2b0d16b49c33b848ae0099e5627ef5d60586c204b9ef2350c0`;
20 files changed and four added since the prior manifest, none removed. Only
backend source/tests and application metrics configuration changed within its
scope. The [test-report references](evidence/h1-operation-budgets-tests-2026-09-18.json)
record hashes for 93 unit reports and five skipped-integration reports. This is
unsigned local evidence; build reports/raw temporary logs can be replaced later.
Existing Quarkus REST/RESTEasy Classic, deprecation and OpenTelemetry warnings
remain; no blanket warning-free or vulnerability-free claim is made.

The new regressions cover deadline reuse, nanosecond remainder, expired calls,
interruption before/during transport, late parsing, header/body sequencing,
configured-credential denial, target isolation, partial-cache behavior, missing
histograms, assessment optionality and report sections. Existing security/parser/
temporal tests remain enabled. Independent review caught a further evidence defect
after the 619-test run: aborted/pre-interrupted summaries could still emit
`metrics.source.available=true` solely because their status was DEGRADED. That
unproven availability assertion is now omitted on abort, with dedicated regressions.
This review does not certify the broader H1 gate.

### Local runtime regression and cleanup

All final scenarios ran sequentially against the package built by the 622-test
verification, exited **0 after cleanup**, and passed the separate compact-JWT log
scan:

| Scenario | Final result |
|---|---|
| Real local OIDC + Community Keycloak A/B + PostgreSQL + Prometheus | **75 checks passed** |
| Real local OIDC + installation setup/read-only paths + synthetic loopback Kubernetes API | **83 checks passed**, zero cluster writes/Secret requests |
| Default real-identity/report path without metrics | **70 checks passed** |

The earlier 75-check metrics and 83-check installation runs preceded the final
review correction; both were repeated on the final package. The installation
expiry case uses disposable-database fault injection, not ten minutes of elapsed
time. These lab runs are separate from the nine skipped opt-in Maven ITs and do
not establish actual Kubernetes/OpenShift/RHBK or browser acceptance. Standalone
lab checkers used existing Node **25.6.1**, not an assertion of UI support for that
version; the UI was unchanged and not rebuilt/retested here.

[Retained run excerpts](evidence/h1-operation-budgets-runs-2026-09-18.txt) include
all observed exits, raw-log hashes, intermediate/final results, diagnostic paths
and final application-package hashes. All **545 source inputs** and **100
test-evidence references** matched their recorded hashes.

Final Podman inventory: **0 containers, 0 volumes, default network only**. Four
preexisting reusable images were preserved. All **11 fixture ports** were free
and the shared lab lock absent. Runner-owned disposable tmpfs database/TSDB data
was removed and is not retained; diagnostic evidence/build/dependency caches remain.
No global prune, unrelated deletion or ambient cluster access was performed.

Offline documentation validation passed for **125 Markdown documents, 685 local
links and 15 milestone specifications**, with **0 errors / 0 warnings**; six parser
self-check groups also passed. These checks validate structure/references, not
external URLs or milestone acceptance. `git diff --check` passed.

## Explicit remaining boundaries and next step

This is a **summary/category Prometheus deadline**, not a whole collector,
assessment or report deadline. Admin REST, Fabric8, database persistence and
credential-provider execution itself are not preemptible by this budget. A slow
credential provider is checked after it returns; no transport follows an expired
result. Successful metrics assessments can still perform separately budgeted
availability probes and separate ServiceMonitor/inventory work. Third-party
MetricsProvider implementations have cooperative pre/post guards and must supply
their own transport enforcement. Per-call HTTP-client construction remains.

Ordinary non-abort missing metrics still follow the legacy provider-status-based
report completeness policy; COLLECTION_BUDGET=AVAILABLE means the deadline was
not exhausted, not that all metrics were observed. Raw-scrape continuity, source-
instance coverage, label/metadata filtering and broad inventory bounds remain open.

The review also identified a separate ServiceMonitor readiness defect: its `up`
probe currently counts matching series, which is not proof that their values are
1. A present `up=0` series can therefore look healthy; first-item monitor association
and safe failure diagnostics also need review. **That path was not fixed or
certified in this increment and is the next bounded local correctness task.**

Broader dependency/image/JDK scanning, browser negatives/revocation and AGT1 remain
open. UI tests/build and browser flows were not rerun here; the prior 133-test
Node24 result remains historical. No actual Kubernetes/OpenShift/RHBK acceptance,
GitHub CI, image build or native STDIO test is implied. D2 still needs the dedicated
explicitly approved cluster; the next correctness slice does not.
