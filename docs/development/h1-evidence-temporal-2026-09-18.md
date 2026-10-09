# H1 inventory evidence and temporal metrics — 2026-09-18

Status: **IMPLEMENTED / LOCAL REGRESSION PASSED; H1 ACCEPTANCE OPEN**.

This local corrective slice follows the [dependency remediation](h1-dependency-fix-2026-09-18.md). It addresses inventory warning safety, unknown-versus-observed assessment evidence and temporal metric response completeness. It does not implement a global operation deadline, raw-scrape/instance coverage, evidence replay or real-cluster acceptance.

## Baseline and version decision

Before editing, `JENV_VERSION=21 jenv exec mvn clean verify` passed **496 tests**, with **9 opt-in integrations skipped**, exit 0 at **2026-09-18 15:14:42 −03:00**. Log: `/private/tmp/kcops-h1-evidence-baseline-20260918.log`. This is a fresh run; UI results in earlier ledgers remain historical until explicitly rerun.

Backend/application/MCP/OpenAPI stay **0.8.1-SNAPSHOT**; UI/root lockfile **0.8.1-dev.0**; report schema **1.1**, Flyway **V1–V10**. Dependency versions, default permissions and identity configuration are unchanged. No commit, push, rebase, release, global Java/Node change or actual cluster access is authorized by this slice. Prior tracked/untracked work is preserved.

## Implemented correction boundaries

- Inventory collection failures must expose fixed warning codes/messages rather than provider exception text. Missing or denied observations must not become zeros, false flags or favorable rule evidence; independent valid observations remain usable.
- Infrastructure collection completeness must propagate through the assessment pipeline. A partial source keeps the assessment partial, confidence bounded and `scoreAvailable=false`, even if the selected rules all evaluated.
- Prometheus results must be structurally and temporally valid before semantic use. A range summary requires the complete returned evaluation grid, not a finite subset or interpolated points. Invalid timestamps, missing steps and ambiguous aggregate series cannot drive sustained findings.
- Database-awaiting sustained findings require an explicitly available validated range with finite average/maximum; a current observation alone is not a window average/maximum. Genuine observed zero remains usable.
- A provider/temporal error must not be relabeled as known absence of traffic.

The existing mapper is `InventoryService.toEvidence`, not a separate mapper class.
Its complete marker requires known cluster/workload identity and counters, full
pod placement, required observation sections, complete networking and no warnings.
It is a conservative implemented-collector heuristic, not exhaustive inventory
coverage; the assessment's 99 cap is not a measured denominator. Warning
normalization also applies when old objects are deserialized, without rewriting
historical persisted bytes. Lists with continuation/remaining-item metadata or
missing/null items are unavailable; comprehensive inventory transport/resource/time
bounds remain pending. Known single-zone concentration now compares against cluster
zone count, and custom-resource instance counts use exact integer conversion.

Range validation requires every expected returned evaluation point; semantic
queries require one aggregate series. Timestamps use exact millisecond conversion,
instant queries pin evaluation time, and range means avoid intermediate sum
overflow. NO_TRAFFIC requires known empty percentile evidence plus histogram
presence and an available zero request rate. Nonfinite percentile provenance remains
insufficient to label no traffic. Permissions, endpoints and query construction are
not broadened.

Prometheus defines inclusive range endpoints and step-based evaluation; explicit evaluation time is supported for instant queries. The local completeness policy is deliberately stricter than accepting a partial API response. A complete returned evaluation grid is **not** proof of complete raw scrapes or constituent instances: lookback and aggregation can conceal missing source observations. Sources: [HTTP API](https://prometheus.io/docs/prometheus/latest/querying/api/), [query evaluation and staleness](https://prometheus.io/docs/prometheus/latest/querying/basics/).

## Validation and remaining work

The first verification reached **561 tests, 0 assertion failures, 3 errors** in new
Mockito test setup: restubbing invoked an earlier answer with null matcher values.
The tests were corrected with non-invoking stubs; no production fix or removed
assertion was used to hide those errors. The complete clean verification then
passed **561 tests / 0 failures / 0 errors**, with **9 opt-in ITs skipped**, exit 0,
BUILD SUCCESS at **2026-09-18 15:23:35 −03:00**. The increment adds **65 tests**.
Existing Quarkus REST/RESTEasy Classic and source deprecation warnings remain.

Independent read-only review found no scoped blocker and confirmed the unknown
versus observed and grid-versus-scrape boundaries. The [source manifest](evidence/h1-evidence-temporal-source-2026-09-18.json)
records **541 inputs**, aggregate SHA-256
`56443c142e7d16b4a2b6b42bf5d47a507d7e43c1214e7f0aa7058a071c038d9c`:
13 existing files changed and four added since the prior manifest, no removals.
UI, dependency/build inputs, fixtures, permissions and migrations match the prior
manifest. UI tests/build and browser scenarios were **not rerun** in this backend
slice; their previous 133-test/Node24 results remain historical. Lab checkers use
the existing Node 25.6.1 only for standalone scripts, not an assertion of UI Node25
support; no global runtime setting changed.

Fresh local labs ran sequentially on the final package, all exit 0 after cleanup:

| Scenario | Result |
|---|---|
| Real local OIDC + Community Keycloak A/B + PostgreSQL + Prometheus | **75 checks + compact-JWT log scan passed** |
| Real local OIDC + installation setup/read-only paths + synthetic loopback Kubernetes API | **83 checks + compact-JWT log scan passed**, zero cluster writes/Secret requests |
| Default real-identity/report path without metrics | **70 checks + compact-JWT log scan passed** |

The installation expiry test uses explicit disposable-database fault injection,
not an elapsed-ten-minute acceptance. These runs are not the nine skipped Maven
integrations, an actual Kubernetes/OpenShift test, a browser run or raw-scrape
continuity proof. No application permissions were broadened.

[Retained run excerpts](evidence/h1-evidence-temporal-runs-2026-09-18.txt) include
the failed intermediate run, successful final run/labs, observed exits and raw-log
hashes. [Final test report references](evidence/h1-evidence-temporal-tests-2026-09-18.json)
retain hashes for **90 unit-test reports and five skipped-integration reports**.
All 541 source inputs were rehashed without mismatch. This is unsigned local
evidence; temporary raw logs and build outputs may be replaced by later runs.

Final Podman inventory: **0 containers, 0 volumes, default network only**. Four
preexisting reusable images were preserved; all fixture listeners and the shared
lab lock were absent. Runner-owned tmpfs data was disposable and removed; no
global prune or unrelated deletion. Dependency/build caches and diagnostic logs
remain reusable. AGENTS.md invariants were reviewed and required no change.

Offline documentation validation passed for **124 Markdown documents, 670 local
links and 15 milestone specifications**, with zero errors/warnings and six parser
self-check groups passed. This validates structure/references, not external URLs
or milestone completion. `git diff --check` passed; all **97 test-evidence file
references** and **541 source inputs** matched their recorded hashes.

H1/D1 remain open.
Broader metadata filtering, total-operation budgets, raw-scrape coverage,
dependency/image/JDK scanning, browser negatives/revocation and AGT1 retain their
own acceptance gates. D2 still requires a dedicated explicitly approved
RHBK/OpenShift environment.
