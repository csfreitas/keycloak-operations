# D1 safe OIDC return paths — 2026-09-11

Scope: preserve an internal deep link across platform login and prevent target child pages from mounting before an authorized overview is available. This resolves the deep-link finding from the earlier [browser ledger](d1-browser-workflow-2026-09-11.md); it does not complete all H1/D1 acceptance.

## Implementation and boundary

`returnPath.ts` accepts known target/change routes only, capped at 512 characters. A tab-scoped session-storage hint holds pathname and timestamp, expires after ten minutes and is consumed once after successful adapter authentication, before router creation. Query strings and fragments are never stored; external URLs, traversal/encoded paths, malformed data and expired/future timestamps are rejected. Fresh root visits, authentication failures and logout clear the hint. Unavailable storage permits login without return-path restoration. The exact root callback, OIDC state/nonce/PKCE checks and in-memory access tokens are unchanged. No redirect wildcard or permission grant was added.

`TargetLayout` mounts child routes only after an authorized overview succeeds, hiding report/action pages while access is pending or denied. This is a UI guard, not a substitute for backend authorization. Navigation does not perform an administrative write.

## Fresh evidence

| Validation | Result |
|---|---|
| Backend baseline, Java 21 through jenv, `mvn clean verify` | **376 passed**, 9 opt-in integrations skipped, BUILD SUCCESS; backend source unchanged in this slice |
| UI baseline | **97 passed** |
| Updated UI tests / production build | **133 passed**, 19 test files; build success |
| Real disposable local lab | **54 checks passed**, compact-JWT log check passed, exit 0 after browser cleanup |

Thirty return-path tests cover the allowlist, external/malformed paths, query/fragment exclusion, single consumption, expiry, future timestamps, abandoned hints and storage failure. Three adapter-integration tests cover successful restoration only after authentication, fixed callbacks, shared initialization and authentication failure. Three layout tests cover pending, denied and authorized overview states. These are mocked component tests, distinct from the actual browser observations below.

In Brave, the operator opened `http://127.0.0.1:18300/targets/lab-keycloak-a/report` before login. After authenticating as disposable `alice-a`, the actual URL remained that report path and the UI showed Alice, Lab Keycloak A, Operations Report and Generate Report. The IdP authorization used the fixed root callback and PKCE S256. Direct navigation to `/targets/lab-keycloak-b/report` then preserved that path while displaying **Failed to load target / not authorized for target: lab-keycloak-b**; there was no Operations Report heading or Generate Report control. Sign out returned to the local IdP login form; the lab tab was closed. These are interactive accessibility observations, not a committed browser recording; no token values were read or retained.

The [retained local check output](evidence/d1-return-path-run-2026-09-11.txt) confirms existing real identity, target isolation, report/overview, SSE delivery and negative token checks. This 54-check run is one fresh execution on this slice. RHBK/OpenShift and opt-in Maven integrations were not run.

## Source, versions and hygiene

- HEAD `572cb7b`, branch `feature/0.8.1-client-lifecycle`, existing dirty/untracked work preserved. [Source manifest](evidence/d1-return-path-source-2026-09-11.json): **520 files**; SHA-256 aggregate **3a2d752d1ab6bf2bf0580729d0753c151f72f4b8bf7b12d2cbe25241198fd122** over sorted file hash, two spaces, path and newline. Includes tracked/untracked source/build/fixture inputs, excludes installed dependencies/build outputs and final documentation; unsigned local evidence.
- Versions unchanged: backend/application/MCP/OpenAPI `0.8.1-SNAPSHOT`, UI/lockfile `0.8.1-dev.0`, report schema 1.1, Flyway V1–V10. No dependency, permission or global jenv change; no commit/push/rebase/release/deployment.
- Final Podman inventory: **0 containers, 0 volumes**, only default network. Four reusable images preserved: Community Keycloak 26.7.1, PostgreSQL 16, RHBK 26.6 and Prometheus v2.55.1. Only Community Keycloak/PostgreSQL were used. The runner intentionally removed its nonrecoverable disposable database and owned containers/network, not unrelated resources.
- Logs: `/private/tmp/kcops-return-path-baseline.log`, `/private/tmp/kcops-return-path-ui-baseline.log`, `/private/tmp/kcops-return-path-ui-tests.log`, `/private/tmp/kcops-return-path-ui-build.log`, `/private/tmp/kcops-return-path-live.log`; platform/UI logs `/private/tmp/kcops-identity.vHtmIx`.

## Remaining work

Queries/fragments and unsupported/custom UI routes are deliberately not restored. Expired or unavailable hints fall back to root navigation. Arbitrary issuer compatibility, immediate revocation, complete late-response race coverage, dependency review, metrics-present/MCP report acceptance, local-identity installation confirmation and reference-agent prototype remain open. Next D1 increment: metrics-present and MCP report workflow, followed by the explicitly configured synthetic-cluster confirmation gate. OpenShift is not required for those local gates. Context, architecture, milestones, operator guide, Unreleased and version bookkeeping are updated; historical ledgers are preserved.
