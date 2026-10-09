# D1 — Authenticated local workflow evidence, 2026-09-11

Scope: development-version alignment, reproducible local identity/report/event validation and interactive browser observations. **D1/H1 remain in progress; this is not RHBK/OpenShift or complete health-check certification.** No commit, push, rebase, release, container publication or cluster access was performed.

## Source and artifacts

- Repository HEAD `572cb7b`, branch `feature/0.8.1-client-lifecycle`; implementation and documentation are in a dirty working tree containing pre-existing changes and untracked files. HEAD alone is not the tested implementation.
- [Source manifest](evidence/d1-source-2026-09-11.json): **514 files**, tracked and untracked, under backend/UI/scripts/lab/deployment/build inputs. Aggregate SHA-256 `d6fe9d818751ad96b2e8f6b28f793bd7735c6b0bd8ab86d7d66b52fc4a136451`; recomputation after both successful runs found no changed source files. Documentation added after testing is not covered by that source digest. This is an unsigned local manifest, not reproducible-build or supply-chain attestation.
- Backend Maven/application/MCP/OpenAPI: **0.8.1-SNAPSHOT**. UI/package-lock root: **0.8.1-dev.0**. Deployment template identifiers aligned; images not built/published. Report schema **1.1** and Flyway **V1–V10** unchanged.
- Java **21.0.10 Homebrew**, selected per command with `JENV_VERSION=21 jenv exec`; no global jenv change. Node **25.6.1**. Community Keycloak **26.7.1**, PostgreSQL image **16**, cached images only (`--pull never`). No claims for other versions.

## Fresh regression runs

| Run | Result | Local diagnostic log |
|---|---|---|
| Before edits: backend `mvn clean verify` | 358 passed, 0 failures/errors; 9 opt-in ITs skipped; build success | `/private/tmp/kcops-d1-next-baseline.log` |
| Before edits: UI `npm run test:run` | 94 passed / 16 files | `/private/tmp/kcops-d1-next-ui-baseline.log` |
| After version alignment: backend `mvn clean verify` | 358 passed; 9 opt-in ITs skipped; build success at 08:47:13 -03:00 | `/private/tmp/kcops-d1-browser-backend.log` |
| After version alignment: UI tests + production build | 94 passed / 16 files; build success | `/private/tmp/kcops-d1-browser-ui-tests.log`, `/private/tmp/kcops-d1-browser-ui-build.log` |

The opt-in Maven RHBK/Community/Prometheus integrations did not run. The separate real local identity runner did run and is recorded below. No dependency upgrade or advisory review was performed.

## Real local workflow — two consecutive clean successful runs

1. `bash scripts/validate-identity-lab.sh --browser`: **50 checks passed**, compact-JWT log check passed, browser observations below, exit **0** after operator cleanup. [Retained sanitized check output](evidence/d1-run-1-2026-09-11.txt). Full local log `/private/tmp/kcops-d1-browser-run1.log`; platform/UI logs `/private/tmp/kcops-identity.RZCAL4`.
2. `bash scripts/validate-identity-lab.sh`: fresh containers/database, **50 checks passed**, compact-JWT log check passed, cleanup and exit **0**. [Retained sanitized check output](evidence/d1-run-2-2026-09-11.txt). Full local log `/private/tmp/kcops-d1-browser-run2.log`; platform log `/private/tmp/kcops-identity.4CN3Xp`.

Coverage: anonymous REST/MCP/SSE denial; signed Identity A and exact target grants; separate Identity B credentials; authenticated MCP initialization/listing/foreign-target rejection; real simultaneous SSE subscriptions receiving own persisted health IDs and no foreign target events; authenticated REST reports for A/B; metrics explicitly SKIPPED; replay explicitly unavailable; own assessment-history retrieval and foreign 403; known secret/token canaries absent from report JSON; unmapped principal, wrong audience, tampered signature and expired token negatives. Identity B tokens rejected by the platform differ in issuer, keys and audience: this is **not** an isolated wrong-issuer test.

An earlier exploratory run failed after check 35 because the fixture inherited the normal lab's Prometheus configuration (`localhost:9090`). It was not counted as a passing run. The final fixture explicitly sets metrics type NONE for both targets. Its cleanup succeeded; diagnostic log `/private/tmp/kcops-identity.HKwP8D`. A browser visit to port 3000 showed an existing Grafana service; no login/change was made there. The final browser fixture uses **127.0.0.1:18300**, an exact loopback redirect/logout/CORS allowlist, without changing the normal development server settings.

## Interactive browser observations

Observed in Brave using the real local IdP and only public disposable fixture identities; no token values were read or retained. These are interactive accessibility observations, not a committed browser automation suite or video recording.

| Observation | Evidence / qualification |
|---|---|
| Login as Alice | OIDC authorization redirect included PKCE S256; Fleet showed `alice-a`, one target, only Lab Keycloak A and connected events by 11:52:46 UTC |
| Report generation through UI | A report generated at **11:53:06 UTC**, with PARTIAL completeness, INCONCLUSIVE assessment, performance SKIPPED and explicit independent collection window |
| Operation beyond original access-token lifetime | A second report generated at **11:54:05 UTC** in the same SPA session, without reload or new login; the fixture access token lifetime is 45 seconds. This demonstrates successful session continuity beyond the original lifetime, consistent with the adapter refresh path; refresh request internals and immediate revocation were not inspected |
| Direct foreign-target navigation | Navigating to B returned to Fleet without B data. OIDC reinitialization redirects to the root, so this does **not** independently prove a browser-rendered 403; REST foreign-target denial is covered separately |
| Logout and user switch | Alice's Sign out returned to the IdP login form. Bob then logged in and saw `bob-b`, only Lab Keycloak B and connected events, without A data |
| Final logout | Bob's Sign out returned to the login form; the disposable browser tab was closed |

## Findings and remaining acceptance

1. **Health correctness blocker:** UI report showed `keycloak.adminApi = CRITICAL / Admin API unreachable`, despite successful realm/client reads. The health check only probes server-info and turns every runtime exception into CRITICAL, with possible raw exception-message propagation. The fixture's metadata permissions are a likely cause; the exact failed HTTP response was not retained. Classify access denial/missing metadata separately from availability and sanitize failure messages, with regression coverage; do not fix by granting broader privileges. This observation does not establish a Keycloak outage.
2. **Missing-data presentation:** Overview rendered unavailable desired/ready replicas as `-1` and absent topology counts as zero. Present these as unknown/not collected with provenance; do not imply observed zero-capacity infrastructure.
3. **Deep-link behavior:** OIDC initialization resets the requested route to root/Fleet. Preserve safe local return paths before treating direct-route browser negatives as accepted; retain cross-target data clearing.
4. Metrics-present reports and MCP report generation are not covered by these new checks. Real local identity plus explicitly configured synthetic cluster discovery/review/confirmation, privileged global-read-only denial, late-response browser races, isolated issuer negatives and revocation limits still require acceptance.
5. Reference agent prototype, dependency advisories and remaining H1 provider sanitization/classification remain pending. No RHBK, OpenShift, real cluster RBAC, HA/failover, networking certificate or portable-host/container collector claim is added.

## Resource hygiene and handoff

After both successful runs: **0 containers, 0 volumes**; the identity-lab network was removed and only the Podman default network remained. Four existing images were preserved: Community Keycloak 26.7.1, PostgreSQL 16, RHBK 26.6 and Prometheus v2.55.1. RHBK/Prometheus images were not used. No listeners remained on the owned UI/backend ports 18300/18081. The disposable database contents are intentionally not recoverable after cleanup; reusable images and unrelated services were untouched.

Post-browser and post-second-run platform-log scans found no compact JWT or known fixture-secret canary. This is bounded scanning, not proof that every sensitive-data pattern is excluded. Local diagnostic logs are retained, not committed wholesale.

Follow the [operator quickstart](../../dev/identity-lab/README.md). Next: correct the health and missing-data findings, then complete [H1](../milestones/h1-trust-closure.md)/[D1](../milestones/d1-local-workflow.md) acceptance before the approved [D2](../milestones/d2-rhbk-openshift.md) cluster. Keep default permissions and the global read-only boundary intact.

Documentation reconciliation includes project-state, roadmap, H1/D1 specifications, architecture overview, operator guides, active version references and Unreleased. Historical ledgers were preserved; AGENTS.md's existing continuity rules did not need another change. Local-link/requirement/fence checks and whitespace validation were rerun after the update.
