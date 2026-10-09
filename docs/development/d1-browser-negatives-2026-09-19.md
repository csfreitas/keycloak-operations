# D1 — Real-browser identity negatives, 2026-09-19

Status: bounded local browser criterion validated. H1/D1 acceptance remains open.
This uses **Community Keycloak 26.7.1**, not RHBK/OpenShift, and does not establish
immediate revocation or production-write safety.

## Scope and source

The actual UI entry, App, AuthProvider, keycloak-js PKCE adapter and API client run
against the disposable local IdP and freshly packaged backend. The
[development fixture](../../dev/ui-browser-negatives/README.md) observes only exact
GET `http://localhost:18081/api/v1/me`. It passes the original token, arguments,
AbortSignal and native response through unchanged. No synthetic 401 or auth mock is
used. Claims remain in memory; the visible evidence contains fixed labels, booleans
and status only. No HAR, bearer header, decoded claims or callback payload was exported.

The only injected fault is the expired scenario's first identity request: after
normal credential acquisition it waits until the token's expiry plus two seconds,
with an eight-second cap and cancellation. This proves rejection of a real expired
token in transport, **not ordinary adapter refresh behavior**. Prior normal refresh,
logout and denied-target observations are retained in the
[session ledger](d1-browser-session-2026-09-19.md).

The realm adds two public PKCE clients: one omits only the backend audience mapper;
one retains it but sets a two-second access lifetime. Both retain the exact loopback
callback/origin and disable direct grants; no client secret, service account or new
platform grant is added. The original positive client remains unchanged.

Requirements: SEC-AUTHZ-001, SEC-MULTI-001, FR-UI-001/002, NFR-TEST-001/002.
Independent fixture/runner review found no scoped blocker. The runner checks the
remaining shared browser budget before launching each UI process. Unknown cleanup
still fails and retains the ownership lock. No product defect was demonstrated or
product implementation changed in this validation-only slice.

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`; existing accumulated dirty work preserved. No
commit, push, rebase, tag, release, installation, global runtime switch or real cluster
access. Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile
**0.8.1-dev.0**, report **1.1**, Flyway **V1–V10** remain unchanged. Product source,
dependencies, production identity/grants, assessment rules and operational state are
unchanged. AGENTS invariants are unchanged; continuity/architecture/milestones,
Unreleased and the version decision are reconciled.

The [manifest](evidence/d1-browser-negatives-2026-09-19.json) records **609 inputs**,
**three changed / seven added / zero removed** versus the navigation manifest:

`6244c8d482b2ba39c2f8f37525a421d4281628ace3ebaeca449266eaacc7f972`

Changed: identity-lab guide, disposable realm and runner. Added: realm contract test
and six browser-fixture files. Hashes cover working-tree source, build artifacts,
test reports/logs and before/after inventory. They are unsigned local consistency
evidence, not release attestations. Prior dated ledgers are not rewritten.

Final consistency checks: **751 file/log/artifact hashes matched** (609 source
inputs, 127 Maven reports, seven execution logs, six build artifacts and two
inventories). The offline documentation checker passed **140 Markdown documents /
903 local links / 15 milestone specifications**, zero errors/warnings, and six parser
self-check groups. External URLs were skipped. Shell syntax and `git diff --check`
passed. These checks establish consistency/structure, not full factual coverage or
milestone acceptance.

## Automated validation

Java **21.0.10** selected per command with `JENV_VERSION=21 jenv exec`; Node
**24.19.0** selected per command from the already installed runtime. Existing UI
dependencies reused. Logs are `/private/tmp/kcops-d1-browser-negatives-<name>-20260919.log`
and can expire; the manifest retains hashes and selected summaries.

| Run / log | Observed result |
|---|---|
| Fresh `mvn clean verify` / `baseline` | **1684 passed / 9 opt-in ITs skipped**, exit 0; completed 17:52:46 −03 |
| UI baseline / `ui-baseline` | **195 passed / 22 files**, exit 0 |
| Runner/process/runtime/realm contracts / `runner-tests` | **142 passed**, exit 0; 138 existing harness tests + 4 new realm tests |
| Browser observer contracts / `fixture-tests` | **15 passed**, exit 0; synthetic signatures/fetch/clock, not browser acceptance |
| Production type-check/build / `ui-build` | Passed, exit 0; 87 modules |
| `validate-identity-lab.sh --browser-negatives` / `lab` | **75 automated checks**, JWT scan, owned cleanup and exit 0; four browser observations below are separate |
| Final UI / `ui-final` | **195 passed / 22 files**, exit 0; started 18:02:39 −03 |

The 19 new isolated tests cover realm-client equivalence restrictions, exact origins,
public PKCE/no expanded permissions, real-signature proof logic, safe observation,
exact GET-only interception, native argument/response preservation, bounded expiry,
cancel/retry behavior and unavailable proof. No server/containers are created by those
tests. Existing 138 harness tests exercise shared process/runtime/lock safeguards;
the new four-stage shell orchestration was exercised in its successful live path,
not exhaustively fault-injected for every stage/EOF/timeout combination.

Backend source did not change after the fresh build; that package was used in the
lab. Opt-in Maven integration suites were **not** enabled. The separate lab really
ran the identity/target checks; optional metrics/installation profiles were not rerun
here. Product `ui/src`, `ui/dist` and backend source have no fixture import/identifier.

## Browser observations — actual IdP and backend

Run ID **8955d701-fc13-4664-a8ce-34697450a7c5**. Four fresh agent-owned background
browser tabs were opened sequentially and closed before the next UI started. Login
used the public disposable Alice account; later same-origin cases reused its IdP
SSO session. No real credentials or new permissions were used.

The table transcribes the fixture's completed observation visible in the browser.
Each request was ID 1, stage `completed`; no token/claims are retained. Signature is
verified by Web Crypto RS256 against the **fixed expected issuer JWKS**, not a
JWT-supplied URL. All four signatures actually verified; unavailable/null proof would
not have met this criterion.

| Scenario | Signature valid | Expected issuer | Backend audience | Unexpired at dispatch | Delayed | Native `/me` | Protected UI |
|---|---|---|---|---|---|---|---|
| Wrong issuer | true | false | true | true | false | **401** | Absent; “Session expired. Sign in again.” |
| Wrong audience | true | true | false | true | false | **401** | Absent; same sign-in message |
| Expired | true | true | true | false | true | **401** | Absent; same sign-in message |
| Valid control | true | true | true | true | false | **200** | Fleet, Alice, only Lab Keycloak A, event stream connected |

Wrong issuer uses the same disposable realm/key through `127.0.0.1`, while the backend
expects `localhost`. The distinct issuer was **observed**, not inferred from host
spelling. This local `start-dev` behavior is consistent with upstream
[hostname configuration](https://www.keycloak.org/server/hostname); it is not a
recommendation for production dynamic issuers. The fixture uses the real adapter's
flow described in the [JavaScript adapter documentation](https://www.keycloak.org/securing-apps/javascript-adapter).

The valid control's target remained **UNKNOWN** with “Inconclusive — incomplete
evidence”, coverage 58%, two high findings and missing metrics. Successful identity
does not imply a healthy or fully assessed environment. No report generation or
administrative approval/apply was triggered by these browser checks.

The automated runner's Enter acknowledgments only advance its lifecycle. They are
not assertions of browser acceptance; the explicit observations above are the
evidence. This is a manually observed walkthrough, not an automated browser recording
or a claim about every REST/MCP/SSE endpoint's issuer/audience/expiry behavior.

## Runtime hygiene

The pinned connection was **podman-machine-default-root**. One owned network and
four named/labeled containers were reused across all cases; PostgreSQL data was
temporary tmpfs, not a persistent volume. No new image was pulled. A shared
900-second browser budget bounds all four cases; existing bounded cleanup runs
separately. UI processes are sequential and verified stopped before replacement.

The runner passed the fail-closed compact-JWT log scan, verified owned cleanup and
exited **0**. Private diagnostic location: `/private/tmp/kcops-identity.bwQBFl`.
Independent final inventory at **18:02:35 −03** confirms:

- Zero containers and zero volumes; only the default `podman` network remains.
- Four reusable cached images unchanged by ID: Community Keycloak 26.7.1, PostgreSQL
  16, RHBK 26.6 and Prometheus 2.55.1. The latter two were not run in this slice.
- All twelve checked lab/fixture ports free; shared ownership lock absent.
- All four agent-created lab tabs closed; no unknown process/resource was removed.

Only this run's disposable containers/network/processes and their temporary database
state were removed. That disposable state is not recoverable; reusable images were
preserved. No global prune, VM restart, default-context change or persistent-volume
deletion occurred. Before/after inventories are embedded in the manifest.

## Acceptance and next step

Combined with the prior real login/renewal/logout and A↔B denied-target evidence,
this closes **only D1's local browser observation criterion**. H1's broader
cross-surface/reviewer criteria, independent operator reproduction and D1 milestone
acceptance remain open. Production IdP compatibility and D2 require their own approved
environment and evidence; Community is not RHBK.

No new revocation mechanism: copied valid JWTs can survive logout until expiry;
already admitted SSE uses its original grants for up to five minutes and may outlive
the admitting token. Other-tab logout is refresh-detected, not instantaneous. Browser
abort cannot cancel or roll back already dispatched server work.

Next local increment: **AGT1 reference-agent prototype**, with deterministic results
still authoritative, explicit provider/data configuration and no unrestricted
infrastructure or new write authority. Metadata/diagnostic limits and normalized
all-source coverage remain tracked; no OpenShift is needed for that local increment.
