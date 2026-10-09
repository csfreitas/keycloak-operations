# D1 installation confirmation with real local identity — 2026-09-18

Scope: exercise the existing installation discovery/review/confirmation implementation through real OIDC identities, REST, PostgreSQL and the browser, against an explicitly configured **synthetic loopback Kubernetes API**. This follows the [report-trust correction](d1-report-trust-2026-09-11.md) and [installation implementation](installation-onboarding-2026-09-11.md). No real cluster, ambient kubeconfig or OpenShift context was accessed.

## Delivered test workflow

- Separate `installation.properties` and `validate-installation-lab.sh` reuse the four named disposable identity-lab containers: IdP, Keycloak A/B and PostgreSQL on tmpfs. Two host backend processes share the transient database: a setup instance and a globally read-only instance. The synthetic API is a separate loopback Node process, not a cluster or a container inventory collector.
- Normal READ/ASSESS identities and configuration remain unchanged. Dedicated setup identities receive READ/DISCOVER/BIND for A, B or both; another actor has the same A permissions and a negative identity has READ/BIND without DISCOVER. Browser fixture `sam-setup-a` has A setup permission only. The setup backend disables read-only **only for that disposable process**; no WRITE/ADMIN/APPROVE grant is added. The counterpart keeps global read-only enabled with identical setup grants.
- The fixture serves two metadata-only Deployments per namespace (`lab-a`, `lab-b`), empty StatefulSet lists and no Keycloak Operator API. All cluster writes are rejected and counted. A separate control credential injects UID replacement and permission denial; response size/time/trace bounds and fixture authentication are tested. Metadata candidates are not proof that workloads run Keycloak.
- The checker compares exact API version/kind/name/UID, managed state, revision, consumed run and mandatory actor/target audit, then reads the binding through an ordinary reader. Every denied confirmation compares binding, discovery runs and success audit before/after; prevalidation failures additionally require no cluster request.
- Both identity and installation runners now hold a shared atomic lock through preflight/start/cleanup. Existing resources or locks are refused; no automatic stale-lock deletion. Cleanup cannot run Compose down when preflight fails. JWT log scanning runs after browser activity, includes its log, and fails if the scanner is missing or errors. Logs are bounded acceptance evidence, not a universal secret-detection guarantee.

No application backend/UI source, API contract, migration, dependency or production default changed in this increment. The [operator guide](../../dev/identity-lab/README.md) contains commands, identities, ports, review steps, ownership and recovery limits.

## Validation and actual results

| Check | Result |
|---|---|
| Fresh Java 21/jenv `mvn clean verify` baseline | **392 backend tests passed**, **9 opt-in ITs skipped**, build SUCCESS; backend source unchanged afterward |
| UI test suite and production build | **133 tests / 19 files passed**, build successful; UI source unchanged |
| Synthetic API tests | **7 passed**; loopback scope, credentials, mutation rejection, UID/denial controls, bounds and invalid ports |
| Shared runner/helper tests | **13 passed**; separate-process exclusion, stale/changed owner, traps, preflight with simulated Podman, scanner exit 0/1/2/127 and sample logs |
| Installation clean run 1 | **83 checks + JWT log check passed**, exit 0 after cleanup |
| Installation clean run 2, followed by browser | **83 checks + JWT log check passed**, exit 0 after cleanup; no source change between the two passing runs |
| Normal identity/report regression | **70 checks + JWT log check passed**, exit 0 after cleanup; REST/MCP reports, scoped SSE and invalid-token cases preserved |
| Documentation/source/hygiene reconciliation | **120 Markdown documents**, **573 local links**, 2 fragments, 138 requirement definitions and **15 milestone specs** checked; no errors/warnings; all **530 source hashes** match |

The retained [check output](evidence/d1-installation-runs-2026-09-18.txt) separates automated checks, browser observations and post-browser persistence verification. This is real local IdP cryptographic authentication and database integration, not the skipped Maven opt-in RHBK/Keycloak/Prometheus IT suite. Prometheus, RHBK and any real Kubernetes/OpenShift cluster were not started for this increment.

Negative cases include ordinary readers, BIND without DISCOVER, anonymous access, a different actor with the same grants, an unreturned candidate, unauthorized target and a wrong-target run even for an actor granted both targets. Also exercised: global read-only with otherwise sufficient grants, replay, an independent actor's stale binding revision, superseded discovery, recreated resource UID, lost cluster permissions and expiry. The expiry case explicitly updates the **owned transient database timestamp**; it is fault injection, not a ten-minute wall-clock test. Failed discovery rolls back its pending-run changes and yields no partial confirmable run. Successful discovery alone never binds or creates a success audit.

The initial exploratory run failed after eight checks because the checker inspected the database before the lazily initialized target registry's first use. Both backend registries are now initialized by authenticated target-list reads before persisted-state assertions. Its cleanup succeeded. This was a test ordering defect; no backend change or passing-result claim was made for that failed run. Review also tightened complete-binding assertions, exact request-path allowlisting, raw response leak checks, scanner error handling and shared runner ownership before the two passing runs.

## Browser observations

Observed through the in-app browser at the exact approved fixture origin `http://127.0.0.1:18300`, with real local IdP login:

1. Setup login returned to A's Installation deep link. The scripted initial binding was `alternate-a` / `uid-a-2`, revision 2.
2. Discovery showed two unselected radio choices and a disabled confirmation button. Selecting `sso-a` / `uid-a-1` displayed the old/new UID and target; confirmation stayed disabled until the review checkbox was checked.
3. Confirmation displayed **Installation binding saved and audited. No cluster resource was changed.**, with `sso-a` / `uid-a-1`, revision 3.
4. After logout, reader `alice-a` saw only A in Fleet and the saved revision 3 binding. Discovery was disabled and setup-permission explanations were visible. Reader logout returned to the IdP login form; the test tab was closed.
5. Direct post-browser database inspection confirmed four binding audits total, exactly one revision-3 browser audit matching its consumed run actor and before/after UID. A's exact binding matched the selected resource; B remained `sso-b` / `uid-b-1`, revision 1. The synthetic cluster trace contained **31 reads, zero writes and zero dropped entries**.

These are observed browser cases, not a recorded video or a complete browser-security suite. Real-IdP Operator/StatefulSet candidate cases, cluster RBAC/HA, concurrent confirmation stress, provider/bounds failures and broad invalid-token/browser-revocation behavior remain separate acceptance work.

## Source, versions and local hygiene

HEAD remains `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch `feature/0.8.1-client-lifecycle`. The accumulated tracked/untracked working tree is preserved and **uncommitted**; HEAD alone does not contain these deliveries. [Source manifest](evidence/d1-installation-source-2026-09-18.json): **530 files**, aggregate SHA-256 **8a370e52cd4ae4571e84640610993adafe4b04c288c117e5bfd137c5fc1b8b4d**, over sorted file hash, two spaces, path and newline. It covers source/build/fixture inputs including their guides, excludes build outputs, installed dependencies, `docs/` and root documentation; unsigned local evidence.

Versions stay backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/package-lock roots **0.8.1-dev.0**, report schema **1.1** and Flyway **V1–V10**. No global jenv change, commit, push, rebase, tag, release, image publication or external deployment.

The two passing installation runs and the normal regression removed their four transient containers, owned Compose network, host processes and lock. Their disposable database contents are not recoverable; no reusable volume or image was deleted. Final inventory: **0 containers, 0 volumes**, default `podman` network only, no fixture listeners on ports 15432/18080/18180/18280/18081/18082/19001/19002/18590/18300, shared lock absent. The four cached reusable images were preserved: Community Keycloak26.7.1, PostgreSQL16, RHBK26.6 and Prometheusv2.55.1. No global prune. Diagnostic directories: exploratory `/private/tmp/kcops-installation.Z3rPSW`, passing `/private/tmp/kcops-installation.AycmFa`, passing/browser `/private/tmp/kcops-installation.hTuaog`, normal regression `/private/tmp/kcops-identity.xmlF63`.

Fresh logs share `/private/tmp/kcops-installation-` prefixes: `baseline-20260918.log`, `ui-tests-20260918.log`, `ui-build-20260918.log`, `fixture-tests-20260918.log`, `runner-tests-20260918.log`, `live1-20260918.log`, `live2-20260918.log`, `live3-browser-20260918.log` and `default-regression-20260918.log`. Local temporary paths can disappear; retained summaries and source hashes do not claim signed evidence or independently reproducible historical runtime artifacts.

Offline documentation checks used `/private/tmp/kcops-doc-review.mjs` (six parser self-tests). The scan covers `docs/`, four root guides and the identity-lab guide; external URLs/factual correctness are not verified and the lightweight Markdown parser is not a full renderer. Two pre-existing milestone ranges included an undefined sixth metrics requirement; the 0.6/0.6.1 references now end at defined FR-METRICS-005 with an explicit correction note, without rewriting historical implementation scope or dated validation ledgers. Different scan scope/parsing means these counts are not directly comparable to earlier ledgers. AGENTS.md was reviewed; its workflow/invariants need no change for this test-only increment.

## Remaining work / handoff

The D1 local real-identity installation gate is now exercised, with unchanged default reader permissions and a separate setup identity. **H1 and D1 remain open.** Next: review provider/engine failure and response-bound handling, record dependency advisories, close the broader browser negatives, and deliver the optional read-only AGT1 reference-agent prototype/evaluations. No AI runtime/provider or external data-disclosure path is selected here. D2 still requires the explicitly approved dedicated RHBK/OpenShift environment; new target/connection registration and portable host/container collectors remain separate planned increments.
