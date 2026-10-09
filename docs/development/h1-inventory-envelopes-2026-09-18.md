# H1 inventory response envelopes and bounds — 2026-09-18

Status: **IMPLEMENTED / LOCAL REGRESSION PASSED; H1 ACCEPTANCE OPEN**.

This increment addresses the generic cluster-envelope/default risk recorded in
the [scrape-readiness ledger](h1-scrape-readiness-2026-09-18.md). It does not
authorize a cluster connection, expand permissions or establish full Kubernetes
schema validation, atomic inventory or whole-report deadlines.

## Baseline and versions

Fresh `JENV_VERSION=21 jenv exec mvn clean verify` passed **765 tests**, **9 opt-in
ITs skipped**, exit 0 at **2026-09-18 16:19:46 −03:00**, before implementation.
Raw log: `/private/tmp/kcops-h1-inventory-envelopes-baseline-20260918.log`.
Java **21.0.10** is selected per command using jenv; no global runtime changes.

HEAD remains `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`, with accumulated tracked/untracked changes
preserved. No commit, push, rebase, release/tag or image publication. Backend,
application, MCP and OpenAPI retain **0.8.1-SNAPSHOT**; UI/root lockfile
**0.8.1-dev.0**, report schema **1.1**, Flyway **V1–V10**. No UI, dependency,
permission, deployment, migration or public REST/MCP-shape changes. AGENTS.md
invariants remain unchanged. Existing evidence may become unavailable/partial
where previous default conversion was optimistic.

## Delivered behavior

- Internal `BoundedKubernetesReader` exposes only fixed read-only resource
  descriptors and fixed discovery/version paths, over the already authorized
  Fabric8 transport. It has no caller-supplied URL, raw HTTP, Secret or exec tool.
- One response accepts at most **1 MiB**, and a monotonic deadline of the configured
  positive request timeout capped at **5 seconds** (5 seconds for unset/nonpositive
  values). Header and body time share the deadline; late parsing/conversion results
  are rejected. Interruption is preserved and failure cancellation is best effort.
- JSON rejects duplicate fields, trailing documents, excessive depth (32), strings
  (8192), numbers (128) and container width (1024). List type/API, metadata and
  explicit array-valued `items` are validated before model conversion. Invalid
  items never yield a successful subset or default empty list.
- Identity requires name/UID and exact namespace; duplicate names/UIDs, explicit
  conflicting/null types, continuation, nonzero/invalid remaining counts and
  excessive cardinality fail closed. An absent item `kind`/`apiVersion` inherits
  only a validated typed outer envelope, as allowed by Kubernetes collections;
  individual GETs still require explicit type/API/name/UID and matching namespace
  for namespaced resources (absent/empty namespace for cluster-scoped resources).
- Inventory and Service association lists accept at most **500** items (request
  501); candidates and monitors accept **100** per list (request 101), and candidate
  discovery additionally caps **100 combined** identities. No pagination following
  or silent truncation. API discovery accepts at most **100 groups**, **32 versions
  per group**, including legitimate custom version labels. These are conservative
  application bounds, not upstream Kubernetes limits.
- Discovery uses one validated `/apis` observation; failed/malformed discovery
  remains UNKNOWN rather than confirming a configured runtime. Independent version
  failure retains known runtime with unknown version. Inventory distribution uses
  observed discovery, not configuration hints; a single node observation is reused
  for placement within one inventory call.
- Inventory root, workload/Pod/policy, Service/networking, candidate revalidation
  and ServiceMonitor reads share these checks. Missing HPA/PDB structure and
  Ingress/Route spec cannot establish absence; contradictory Ready conditions
  cannot establish ready Pods. Independent valid facts and explicit zeros survive.
- The explicit infrastructure factory disables automatic request retries and
  does not enable automatic redirects, retaining configured credentials, TLS,
  proxy and interceptors. This intentionally changes transient-failure behavior:
  retrying the higher-level operation is explicit, and redirected API endpoints
  require correcting the approved configuration rather than following them.
  Errors expose fixed diagnostics/status only, not raw provider payloads or causes.

The list/TypeMeta and pagination behavior follows the official
[Kubernetes collection contract](https://kubernetes.io/docs/reference/using-api/api-concepts/#collections).
Discovery accepts the custom versions described in
[Kubernetes CRD version priority](https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definition-versioning/#version-priority).
Review caught both compatibility cases before final validation. Fabric8's CRUD
test server emits generic `List` responses; a test-only adapter normalizes only
its CRUD fallback. Explicit negative wire fixtures are untouched, and production
does not accept a generic `List` in place of the required typed collection.

## Validation record

Final clean verification passed **970 tests / 0 failures / 0 errors**, **9 opt-in
ITs skipped**, exit 0 at **2026-09-18 16:35:14 −03:00**: **205 added test
invocations** versus baseline. Raw log:
`/private/tmp/kcops-h1-inventory-envelopes-verify-20260918.log`.
The [test references](evidence/h1-inventory-envelopes-tests-2026-09-18.json) hash
100 unit reports and five skipped-integration reports. Existing Quarkus
REST/RESTEasy, relocation/deprecation and telemetry warnings remain; this is not
a warning-free or comprehensive vulnerability-scan claim. UI tests/build/browser
were not rerun; unchanged UI results from Node24 remain historical.

The [source manifest](evidence/h1-inventory-envelopes-source-2026-09-18.json)
records **557 inputs**, aggregate SHA-256
`838b08901744a957673d0177d8bf11336ad17c227671698f801cd42304740f34`.
Twelve existing backend source/test files changed and six were added versus the
scrape-readiness manifest; none removed. This is unsigned local working-tree
evidence, not a committed/signed release.

Two initial focused attempts stopped at test compilation because the new raw-HTTP
inventory fixture used unsupported/protected Config constructors. It now uses
`Config.empty()` with ambient configuration disabled. No production behavior or
assertions were weakened to fix those fixture errors.

The third focused attempt ran **376 tests**, with **3 failures / 0 errors**.
Two discovery failures exposed the mock server's default `/version` response
shadowing explicit fixtures; clearing only those test expectations makes both
positive and negative version cases execute their intended responses. The inventory
failure exposed a production conversion bug: Fabric8's nested Quantity deserializer
calls `readTree`, whose following sibling was mistaken for another document.
Strict wire parsing now precedes a separate model-only mapper with that one
trailing-token check disabled; all other restrictions remain. The regression checks
CPU/memory quantities and still rejects an actual appended wire document.
Independent review additionally checked typed-list compatibility, custom group
versions, configured-versus-observed distribution and contradictory Pod readiness.

### Local runtime regression and hygiene

All three scenarios ran sequentially against the package from the 970-test clean
verification, each with **exit 0 after cleanup** and a separate compact-JWT log check:

| Scenario | Result |
|---|---|
| Real local OIDC + Community Keycloak A/B + PostgreSQL + Prometheus | **75 checks passed** |
| Real local OIDC + installation paths + synthetic loopback Kubernetes API | **83 checks passed**, no cluster writes or Secret requests |
| Default real-identity/report path without metrics | **70 checks passed** |

The installation expiry case uses explicit disposable-database fault injection,
not elapsed-time expiry. These labs do not change the nine skipped Maven ITs or
establish real Kubernetes/OpenShift/Operator compatibility. Standalone scripts use
existing Node **25.6.1**; UI/browser were not rerun. The
[retained excerpts](evidence/h1-inventory-envelopes-runs-2026-09-18.txt) record all
eight observed process exits, raw-log hashes, diagnostic paths and package hashes.
All **557 source inputs / 107 test-evidence references** matched after the labs.

Final Podman inventory: **0 containers / 0 volumes**, default network only. Four
preexisting reusable images are unchanged; all **11 fixture ports** are bindable
and the ownership lock is absent. Runner-owned tmpfs database/TSDB state was
removed and is not recoverable; reusable caches and diagnostic evidence were
retained. No global prune, unrelated-resource deletion or real-cluster access.

Offline documentation validation passed for **127 Markdown documents**, **715
local links**, **15 milestone specifications**, with **0 errors / 0 warnings**
and six parser self-check groups. These validate structure/references, not
external URLs or acceptance. `git diff --check` passed. Architecture/operator
guides, context, H1/D1 specifications, milestone index, roadmap, Unreleased notes
and version decision are reconciled; dated ledgers remain unchanged.

## Explicit limits and next step

Per-response bounds do not bound an entire inventory, assessment, report or
candidate operation; sequential calls and repeated collectors accumulate time.
Cancellation cannot guarantee immediate termination of every transport/TLS task.
Limits are conservative: large otherwise-valid environments/annotations can yield
partial/UNKNOWN observations rather than complete inventory. Resource fields
beyond the checked envelope/identity and selected semantic guards do not have a
complete raw schema validator. Metadata filtering, richer association shapes,
raw-source freshness/instance coverage, browser negatives/revocation and broad
dependency maintenance/scanning remain open. No real cluster, Operator, RHBK,
image/native/STDIO or GitHub CI acceptance is implied.

Networking still chooses whether to consult the Route API using the configured
cluster type; this slice does not fully reconcile a configured/observed platform
disagreement. Observed distribution alone is not full capability reconciliation.

Next local work: compound inventory/assessment/report deadlines, then remaining
coverage/trust gates and the read-only AGT1 reference-agent prototype. H1/D1 remain
open; D2 requires the dedicated explicitly approved RHBK/OpenShift lab. No real
cluster is required for the next local correction.
