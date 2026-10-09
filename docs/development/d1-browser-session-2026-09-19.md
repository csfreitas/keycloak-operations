# D1 — Browser session and transport boundary, 2026-09-19

Status: bounded corrective slice with local browser validation. H1/D1 remain open;
this is not distributed token revocation, real RHBK/OpenShift or production-write
acceptance. The backend, issuer configuration and target grants are unchanged.

## Demonstrated cause and correction

Pre-fix regressions showed that a pending token refresh could resolve after logout
and still send an old REST request, including a POST. Old responses and decoded JSON
could also reach callers after a session replacement. SSE could announce a response
after close, deliver old-session frames or reconnect using replacement credentials.
Current 401 responses did not invalidate the local authenticated view.

The new `ui/src/api/session.ts` owns in-memory credential generations. Installing a
provider/token replaces the generation and aborts its predecessor. Explicit invalidation
clears transport credentials, blocks anonymous fallback and notifies the UI once.
REST captures a generation and checks it before send, after headers/response and after
JSON decoding. A current 401 invalidates that generation; an old 401 cannot revoke a
new one. Target-scoped 403 keeps the valid session. Request timeouts now remain active
through body consumption instead of ending at response headers.

SSE captures the same generation for its entire subscription. Replacement/close aborts
transport and removes scheduled retries; no late open/event callback or reconnect may
cross identities. AuthProvider invalidates transport on explicit logout, adapter logout,
identity-load failure and unmount, and drops stale initialization completions. Current
invalidation unmounts protected routes. Logout begins locally before awaiting remote
IdP navigation; a failed remote sign-out remains a visible limitation.

Requirements: SEC-AUTHZ-001, SEC-MULTI-001, SEC-CRED-002/003, NFR-TEST-001/002.
Contracts: [identity model](../identity-model.md), [UI architecture](../ui-architecture.md),
[security](../architecture/security.md), [operator walkthrough](../../dev/identity-lab/README.md).
Independent static reviews found no scoped blocker; no backend introspection/grant
change was inferred from the frontend correction.

## Source and versions

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`. Accumulated dirty/untracked work is preserved. No
commit, push, rebase, tag, release or external cluster access. The
[manifest](evidence/d1-browser-session-2026-09-19.json) compares against the preceding
read-metadata evidence: **596 source inputs**, aggregate
`6dca0adb6abb274079f4ff1e1d3f539fb26e3cddb1309d2531b6b5c59579c543`;
**five existing inputs changed, three added, none removed**. Additions are session
transport state and two regression files. Existing changes are REST, SSE, AuthProvider,
its tests and the lab guide. Hashes are unsigned local consistency evidence only.
The manifest retains **127 Maven report hashes**, **eight run logs** (including both
red runs), **six backend/UI build artifacts** and **three runtime inventories**.

Backend/application/MCP/OpenAPI remain **0.8.1-SNAPSHOT**, UI/package-lock root
**0.8.1-dev.0**, report schema **1.1**, Flyway **V1–V10**. No dependency, backend source,
deployment, permission, default read-only or schema change. This is an unreleased
frontend correction, not a product release. Context, affected architecture, H1/D1
milestones, roadmap, guide, Unreleased notes and version decision are reconciled;
AGENTS invariants and dated prior ledgers are preserved.

## Automated validation

Java **21.0.10** via per-command `JENV_VERSION=21 jenv exec`. UI, build and browser
runner use the bundled **Node 24.19.0**, selected by per-command PATH; global runtime
settings are untouched. Existing installed UI dependencies were used; no install,
upgrade or vulnerability audit was performed. Raw logs use
`/private/tmp/kcops-d1-browser-session-*-20260919.log` and may expire.

| Execution | Observed result |
|---|---|
| Fresh backend `mvn clean verify` baseline | **1684 passed**, 9 opt-in ITs skipped; exit 0, completed 17:07:22 −03 |
| Fresh UI baseline | **133 passed / 19 files**; exit 0 |
| Pre-fix AuthProvider regressions | **6 failed / 4 passed**, exit 1 |
| Pre-fix REST/SSE transport regressions | **10 failed / 2 passed**, exit 1 |
| Corrected AuthProvider focused run | **10 passed**, exit 0 |
| Final complete UI suite | **160 passed / 21 files**, exit 0; **27 new tests** |
| Final production type-check/build | Passed, exit 0; 87 modules transformed |
| Default identity runner with browser mode | **75 automated checks**, separate JWT scan and owned cleanup passed; exit 0 |

Final additions: six AuthProvider tests, eighteen session transport tests, and three
Provider/real-transport integration tests. Original red cases are preserved; six
additional transport cases check idempotent/reentrant invalidation, listener lifecycle,
credential reset and related branches. Simulated races use synthetic tokens and
deferred requests; they are not a browser network-fault injection or server rollback
test. Backend was not rebuilt again after frontend-only edits; its fresh baseline
package is the package exercised by the runtime lab.

Reproduction: `env JENV_VERSION=21 jenv exec mvn clean verify`, then use Node 24 for
`npm --prefix ui run test:run` and `npm --prefix ui run build`. Runtime:
`bash scripts/validate-identity-lab.sh --browser`. The development-server browser
walkthrough is distinct from the production asset build; no nginx/image deployment
was tested. Installation and metrics modes were not rerun in this slice.

## Browser observations

Observed through the Codex in-app browser, two task-created tabs, using only the
disposable local fixture. Community Keycloak **26.7.1**, PostgreSQL **16**, real OIDC
authorization code/PKCE, UI on `http://127.0.0.1:18300`, IdP on loopback 18280.
Default mode has infrastructure NONE and metrics NONE; no real cluster is configured.
No tokens, OAuth callback parameters or private browser state are retained here.

1. An invalid disposable-password attempt stayed on the IdP page with **Invalid
   username or password**; no protected application route appeared.
2. Correct Alice login returned to the exact A report deep link. Identity `alice-a`
   and connected events were visible. Report generation at **17:16:26 −03** completed
   with PARTIAL completeness, UNKNOWN health, inconclusive assessment and SKIPPED
   performance, not fabricated full coverage.
3. A second generation request was issued **102 seconds** after login observation,
   without reloading. The report completed at **17:17:56 −03** under Alice, beyond the
   fixture UI client's 45-second access-token lifespan. This observes continuity
   requiring renewal, without inspecting token contents.
4. Direct navigation to B's report showed **Failed to load target / not authorized
   for target: lab-keycloak-b**, with no report controls or B data. Alice and events
   stayed authenticated; Fleet then showed exactly one target, A.
5. A second tab used the same local SSO session. Its Sign out returned to the IdP
   login form, observed at **17:18:47 −03**. At **14 seconds** afterward, the first
   tab still displayed Alice and A: there is no immediate cross-tab notification.
6. At **140 seconds** after that logout, Generate Report in the first tab triggered
   sign-in instead of a report. The observed page contained the IdP username/password
   form, not Alice's protected content. No token contents were inspected. This validates
   loss detection on attempted renewal, not immediate remote revocation.
7. Signing in as Bob then showed exactly one target, B, with `bob-b` and connected
   events. Direct navigation to A's report returned **not authorized for target:
   lab-keycloak-a**, without A report controls/data. Bob remained authenticated.
8. Bob's final Sign out returned to the IdP login form. Both task-created lab tabs
   were closed; unrelated user tabs were not changed. Final-page console inspection
   returned no warning/error entries; it is not a complete cross-navigation network
   or console capture.

These are bounded manual observations, not a persisted automated browser suite.
Wrong issuer/audience, deliberately expired browser tokens, multi-browser/device
revocation and every REST/MCP/SSE negative combination are not established by these
observations. The existing runtime runner's API token checks remain separate evidence.

## Runtime hygiene

An inventory taken at **17:07:02 −03** while the backend baseline was still running
returned exit 1 because its one named, owned test PostgreSQL container was active;
it is not recorded as a clean preflight. All fixture ports were free, no volumes were
present, images were unchanged and no lab lock existed. A separate pre-lab inventory
at **17:09:28 −03**, after baseline completion, exited 0 and confirmed that the test
lifecycle removed it.
No manual removal, VM restart, lock breaking or global prune was used.

The browser lab used the pinned `podman-machine-default-root`, per-run UUID ownership,
four named disposable containers and tmpfs database state. The runner passed 75 checks,
the compact-JWT log scan, verified owned cleanup and exited 0. Diagnostic directory:
`/private/tmp/kcops-identity.hXTDtR`; run UUID `6719709c-f589-4be0-acbd-68241b2b4bdc`.
Independent final inventory at **17:22:54 −03** confirmed **zero containers/volumes**,
default network only, all four cached reusable images unchanged, eleven fixture ports
free and shared lock absent. Deleted disposable database contents are not recoverable;
diagnostics and images remain. No image/volume pruning or global runtime changes.

Final consistency verification matched **740 referenced hashes** and the source
aggregate. Offline documentation review passed **138 Markdown documents, 864 local
links and 15 milestone specifications**, zero errors/warnings; six checker self-test
groups and `git diff --check` passed. These are static consistency checks, not external
URL validation, full browser automation or milestone acceptance.

## Limits and next work

- This is local transport/presentation invalidation. JWT verification is not mandatory
  per-request IdP session introspection; copied otherwise valid tokens may remain usable
  until expiry. SSE retains initial READ target grants for at most five minutes and
  does not recheck token expiry or grants per event, so a stream may outlive token expiry.
- With login iframe disabled and no cross-tab broadcast, another tab detects remote
  logout on later authentication/refresh failure. The adapter may automatically navigate
  to login after clearing a token in login-required mode. This is not immediate revocation.
- Abort is best-effort transport cancellation. A dispatched backend operation can still
  complete; no write rollback or durable remote reconciliation is added.
- Static review separately found ChangeDetail route state/late-result handling remains
  unsafe during changeId navigation: old content/actions may remain while the next ID
  loads/fails. That was not modified or browser-reproduced here. Add a focused regression
  and correction before expanding administrative UI acceptance.
- Remaining browser issuer/audience/expiry cases, normalized all-source trust, metadata
  and diagnostic limits, broad vulnerability scans and AGT1 remain tracked work.
  No RHBK support claim follows from Community tests. H1/D1 are still open.

Next: ChangeDetail stale-route regression/correction, remaining browser negative cases,
then the AGT1 reference-agent prototype. D2 requires the separately approved dedicated lab.
