# H1 installation-associated scrape readiness — 2026-09-18

Status: **IMPLEMENTED / LOCAL REGRESSION PASSED; H1 ACCEPTANCE OPEN**.

This increment corrects the ServiceMonitor/`up` defect recorded in the
[previous operation-budget ledger](h1-operation-budgets-2026-09-18.md). It does
not authorize cluster access, change permissions or certify whole-installation
health from metrics presence.

## Baseline and version decision

Fresh `JENV_VERSION=21 jenv exec mvn clean verify` passed **622 tests**, **9 opt-in
ITs skipped**, exit 0 at **2026-09-18 15:53:58 −03:00**, before coding. Raw log:
`/private/tmp/kcops-h1-scrape-baseline-20260918.log`. Java **21.0.10** is selected
per command with jenv; global Java/Node settings are unchanged.

HEAD remains `572cb7ba5aa9a132f3e04280de9378da08cc61e3` on
`feature/0.8.1-client-lifecycle`; accumulated tracked/untracked changes are
preserved. No commit, push, rebase, release/tag or image publication. Backend,
application, MCP and OpenAPI remain **0.8.1-SNAPSHOT**; UI/root lockfile
**0.8.1-dev.0**, report schema **1.1**, Flyway **V1–V10**. No UI, dependency,
permission, deployment, migration or public REST/MCP shape changes. The new
provider DTO is internal; existing dynamic evidence values become more conservative.
AGENTS.md workflow/invariants remain unchanged.

## Delivered behavior

- `probeScrape` reads nonaggregated `up` observations. All matching valid ones
  yield observed scrape success; any valid zero yields scrape failure. Merely
  counting present series is never health evidence. Empty/invalid/failed/stale
  responses remain unavailable, not zero or healthy.
- The query always pins `target_id` to the registered target ID, even with a
  shared job or forged alternative target tag, and retains configured scope and
  job/service/pod selectors. Returned labels must agree. Legacy semantic query
  selectors are unchanged. Operators must arrange this identity label in their
  monitoring pipeline; the application neither adds it nor falls back broadly.
- Every series must be unique and contain one finite binary sample, the same
  evaluation time as the other series, no future/expired evaluation, and matching
  series/sample labels. Cardinality, existing response/parser limits, fail-closed
  credentials and the metrics operation budget remain enforced. The aggregate
  DTO contains counts/time and fixed reasons only, with no raw labels or errors.
- Monitor association requires an explicit bound installation, READ authorization,
  unchanged target registration and exact client/target namespace. A shared
  installation-Service collector verifies root UID/controller ownership and
  rejects shared workload selectors/foreign Pods. Namespace lists request 501
  and reject over 500, visible truncation, duplicates and malformed identities.
  The raw Service snapshot remains internal, never a report/MCP payload.
- The monitor list requests 101 and accepts at most 100. A dedicated typed list
  preserves omitted `items` as unknown rather than Fabric8's default empty list;
  wrong/missing envelope fields, pagination and duplicate identities are rejected.
  Association uses Service metadata labels, not the Service's Pod selector, and
  supports equality plus In/NotIn/Exists/DoesNotExist with validated syntax.
- Only one current-namespace monitor selecting one exclusively associated Service
  through one named TCP Service port is supported. Related broad/shared selectors,
  multiple candidates, nonempty target relabelings, targetPort-only endpoints and
  malformed configurations remain UNKNOWN. An unrelated first monitor is ignored.
  An unrelated selectorless/ExternalName Service does not invalidate an otherwise
  proven association, but remains visible to shared-selector detection.
- Missing associated Services is UNKNOWN. A complete monitor list with no match
  for known associated Services means SERVICEMONITOR_MISSING **in this namespace**.
  Optional durations remain null unless supplied; supplied ordered positive
  durations are bounded and explicit timeout cannot exceed explicit interval.
  No inherited defaults are fabricated. Denied monitor lists return
  PERMISSION_DENIED; failure details/logs do not include provider exception causes.

The semantics follow [Prometheus jobs/instances](https://prometheus.io/docs/concepts/jobs_instances/),
the [Operator API](https://prometheus-operator.dev/docs/api-reference/api/), and
[Kubernetes label selectors](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/).
The supported association is deliberately narrower than the full Operator API;
unsupported does not mean an operator configuration is invalid.

## Validation record

Final clean verification passed **765 tests / 0 failures / 0 errors**, **9 opt-in
ITs skipped**, exit 0 at **2026-09-18 16:08:27 −03:00**: **143 added test
invocations** versus baseline. Raw final log:
`/private/tmp/kcops-h1-scrape-verify-20260918.log`. The
[test-report references](evidence/h1-scrape-readiness-tests-2026-09-18.json)
hash 97 unit reports and five skipped-integration reports. Existing Quarkus
REST/RESTEasy, deprecation and telemetry warnings remain; this is not a
warning-free or comprehensive vulnerability-scan claim.

The first two focused attempts stopped during test compilation: a generated
Fabric8 list builder did not expose the assumed nested metadata builder, then a
new test lacked its DeploymentMethod import. The third attempt executed **159
tests**, with **three failures** in malformed-Service fixtures: the list builder
copied the Service, but those cases modified the original instead of the returned
item. The fixtures now mutate the actual response items; no assertions were
removed or weakened. Test-only HTTP retries are disabled for deterministic 500
handling; production retry policy is unchanged. Raw focused logs retain all three
attempts. The third run's **91 monitor / 23 provider / 16 networking tests** passed.

Independent reviews additionally prompted timeout ordering, strict monitor
envelopes, valid labels/ports, unrelated unsupported Service handling and future
DTO timestamp validation. Review does not close the wider H1 acceptance gate.

The [source manifest](evidence/h1-scrape-readiness-source-2026-09-18.json) records
**551 inputs**, aggregate SHA-256
`df49827c1ee6528a026b4b653e3e8785fdb75bb63d6c20d5f65191c397c1d418`.
Six existing files changed and six were added versus the prior manifest, all
backend source/tests; none removed. This is unsigned local working-tree evidence,
not a signed artifact or committed revision.

### Final-package local labs and hygiene

All three scenarios ran sequentially against the package from the 765-test clean
verification, with **exit 0 after cleanup** and separate compact-JWT log checks:

| Scenario | Result |
|---|---|
| Real local OIDC + Community Keycloak A/B + PostgreSQL + Prometheus | **75 checks passed** |
| Real local OIDC + installation paths + synthetic loopback Kubernetes API | **83 checks passed**, no cluster writes or Secret requests |
| Default real-identity/report path without metrics | **70 checks passed** |

These are runtime regression checks, not a real ServiceMonitor/Operator/cluster
acceptance test. New monitor behavior is covered by the JVM tests with synthetic
Kubernetes/Prometheus transports. The installation expiry test uses explicit
disposable-database fault injection, not elapsed-time expiry. Standalone lab
checkers use existing Node **25.6.1**; unchanged UI tests/build/browser were not
rerun, and their earlier Node24 results remain historical. The nine opt-in Maven
ITs remain skipped; these separate labs do not change that count.

[Retained run excerpts](evidence/h1-scrape-readiness-runs-2026-09-18.txt) record all
eight observed process exits, raw-log hashes, diagnostic paths and final package
hashes. All **551 source inputs** and **104 test-evidence references** matched.
Final Podman inventory: **0 containers / 0 volumes**, default network only. Four
preexisting reusable images are unchanged. All **11 fixture ports** are bindable
and the shared lock is absent. Runner-owned disposable tmpfs database/TSDB state
was removed and is not recoverable; diagnostic evidence and reusable caches were
retained. No global prune or unrelated-resource deletion occurred.

Offline documentation checks passed for **126 Markdown documents**, **701 local
links**, **15 milestone specifications**, with **0 errors / 0 warnings** and six
parser self-check groups. These checks validate structure/references, not external
URLs or acceptance. `git diff --check` passed.

## Explicit limits and next step

Configuration association and scrape observations are independent. Neither proves
that Prometheus/Operator selected or reconciled this monitor, that observed series
came from this exact endpoint, or that every expected instance is covered.
Evaluation timestamps do not prove raw-sample freshness/continuous scrapes.
Positive scrape status is not RHBK availability, readiness or an HA certificate.
Absent target identity labels intentionally produce UNKNOWN.

Cluster reads are not an atomic snapshot and have no compound inventory/report
deadline or generic byte/parser cap. The strict missing-items handling added for
ServiceMonitors does **not** repair other Fabric8 typed-list defaults: malformed
omitted fields can still normalize to empty/default data elsewhere. This remains
a correctness risk, not a claim that all malformed cluster payloads fail closed.
Raw-source coverage, broad metadata filtering, larger association shapes,
cross-namespace monitor inventory and actual Operator compatibility are unaccepted.

Next bounded local work: harden generic inventory response envelopes/defaults and
collection bounds, followed by whole-report budgets, source coverage, remaining
browser negatives/revocation and the read-only AGT1 prototype. H1/D1 remain open.
D2 needs the explicitly approved dedicated RHBK/OpenShift environment; no real
cluster is required for the next local correction. UI/browser, actual cluster,
GitHub CI, image/native/STDIO execution are not implied by this backend slice.
