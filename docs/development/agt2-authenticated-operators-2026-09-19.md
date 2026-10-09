# AGT2 — Two authenticated local operators, 2026-09-19

Status: **LOCAL AUTHENTICATED SUBSET VERIFIED; AGT2 PARTIAL**.
Dates use America/Sao_Paulo; execution artifacts may have 20 September UTC timestamps.

## Scope and provenance

The operator approved two restricted human accounts in the local authenticated lab,
explicitly retaining partial AGT2 status. This exercises the existing configuration
contract, not a new product capability. [Runbook](../../dev/identity-lab/README.md)
and [contract](../configuration-reads.md) describe reproduction and boundaries.

HEAD remains `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`. The initial 386 dirty/untracked status entries were
preserved. No commit, push, rebase, tag, model call, external cluster or production
credential was used. The existing Java/UI implementation, dependencies, schemas and
reference-profile source remain hash-identical to the previous configuration-read
checkpoint. Only disposable lab fixtures, runner/checker tests and documentation change.

| Boundary | Actual lab configuration |
|---|---|
| Identity A / IdP | Community Keycloak 26.7.1, `localhost:18280/realms/operations` |
| Human realm operator | `rhea-realm`, only dedicated `config-realm` assignment |
| Human client operator | `chris-client`, only dedicated `config-client` assignment |
| REST client | Public `keycloak-ops-ui`, authorization code + PKCE S256 |
| MCP client | Separate public `keycloak-config-mcp`, same human code/PKCE flow |
| Backend validation | Real signed tokens; issuer, signature, expiry and backend audience verified by OIDC |
| Source / target | Community Keycloak 26.7.1, target `lab-keycloak-a`, realm `target-a` |
| Identity B | Separate `configuration-reader`; fixture assigns only `view-realm`, `view-clients` |
| Pins | Realm `agt2-realm-a-v1`; client `agt2-portal-a-v1` |
| Database / lifecycle | PostgreSQL 16 on tmpfs; four owned containers and one owned network; no persistent volume |

No legacy READ/ASSESS/PLAN grants exist in this scenario. Two stale handles deliberately
point to different configured IDs; these are **pin-mismatch tests, not actual resource
deletion/recreation**. Source roles are broader than individual platform field grants:
Identity B can receive a broader representation in memory; only approved Boolean fields
leave the shared service. This does not certify every possible upstream permission.

Observed realm facts: registration false, password reset true, brute-force protection
true, email verification true. Observed client facts: enabled/public/standard flow true,
direct grants/service accounts false. `COMPLETE` covers these fields only; product
version in the evidence remains `UNKNOWN`, not an inferred compatibility or health verdict.

## Validation

| Check | Result |
|---|---|
| Pre-change Java 21/jenv clean verify | **1820 passed**, zero failures/errors; 9 opt-in ITs skipped |
| UI regression | **232 passed / 23 files** |
| TypeScript / production UI build | Passed |
| Final harness regressions, Node 24.19.0 | **224 passed**, zero failures/skips |
| Authenticated REST/MCP checker | **55/55 passed** in the active diagnostic lab, then **55/55** in a fresh full runner; final runner exit 0 |
| Real browser | Both operators exercised against the actual local IdP/backend/source |
| Documentation | 156 Markdown files, 17 milestone specifications, 152 requirement definitions; zero errors/warnings; six parser self-test groups passed |

Java 21.0.10 and Node 24.19.0 were selected per command; no global runtime setting was
changed. Maven's skipped opt-in integrations are not counted as the separate live lab.
No product source changed after the baseline; it is not described as a second final
Maven execution. New harness unit tests use fake responses and local temporary files;
only the separately recorded authenticated checker proves actual provider behavior.

### Failed attempts and diagnosed harness corrections

Initial runs failed before source collection at the automated login form. A subsequent
sanitized observation identified HTTP 400 / `COOKIE_MISSING`: three Secure cookies were
retained but none sent. The real browser successfully authenticated against the same
fixture. The test cookie jar now follows the browser's localhost exception **only at
the fixed approved issuer origin/port and realm path**; other ports, hosts and realms
remain rejected. Two focused regressions failed before the correction. This changes
neither Keycloak cookie settings nor application authentication policy. See the
[browser cookie contract](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie#secure).

After that correction, both human identities and the realm operator's REST checks
passed; the checker stopped at MCP catalogue decoding. The actual MCP response was
HTTP 200, not an error: two text content items containing one descriptor object each,
without `structuredContent`. The harness had expected one text item containing an
array. Its catalogue decoder now handles that observed transport representation while
retaining exact scope/field validation, with a red/green regression. This is not a
change to the server contract. One rerun started before the final decoder update was
complete and repeated the earlier failure; only the subsequent frozen-source runs pass.
Failed artifacts remain separate; reruns never overwrite or relabel them.

The runner now keeps `--browser` available for bounded diagnosis after ordinary scripted
failure with verified process termination, but still exits nonzero. Uncertain process
termination and default non-browser failure go directly to cleanup. Browser
acknowledgment alone cannot turn a failed scripted run into a pass.

## Browser observations

Real in-app browser, exact origin `http://127.0.0.1:18300`, real code/PKCE adapter:

1. Deep link `/configuration` returned to that screen after Rhea's login. Only
   `realm-settings` and `realm-stale` were listed; selecting a handle did not collect.
2. Explicit realm inspection displayed the four correct facts, source, observation ID,
   completion time and Unknown product version. No client fields were offered.
3. Selecting stale scope cleared previous facts. Inspection showed only generic
   unavailable evidence. Fleet was empty; direct target navigation was denied.
4. Logout returned to the IdP login page with no protected observations. Chris's login
   showed empty Fleet and only `portal-settings` / `client-stale` under Configuration.
5. Client inspection displayed the five correct facts. Tab reached Run inspection and
   Return invoked it. Stale client inspection cleared facts and failed safely; selecting
   the valid handle again recovered a new successful observation.
6. Chris signed out; protected content disappeared. The temporary tab was closed.

No warning/error entries were returned by the browser console-log query. This is not
a network-log assertion. Native-select arrow-only navigation, narrow viewport,
screen reader, cross-tab revocation and a full WCAG audit were not repeated here.
No tokens were extracted from browser storage, no credential-bearing trace was saved,
and the public fixture password was not saved in the browser. No model was enabled.

## Cleanup, versions and next boundary

Independent final inventory: **0 containers, 0 volumes**, no lab network, no retained
lab lock and no listeners on 15432/18080/18180/18280/18081/19001/18300. All four reusable
images are unchanged: Keycloak 26.7.1, PostgreSQL 16, RHBK 26.6 and Prometheus v2.55.1.
Four owned lab runs were cleaned by their full IDs/run UUID; only temporary containers,
network and unrecoverable tmpfs test data were removed. No prune, image/volume deletion,
VM restart or unrelated runtime mutation. Diagnostic logs remain locally; failed runs
are preserved, including the diagnostic browser run's nonzero exit.

The [manifest](evidence/agt2-authenticated-operators-2026-09-19.json) records all source
inputs, log/result hashes, the four run identities and each actual result. Historical
model artifacts are unchanged. The optional provider reader probe confirms the exact
realm/client reads and a forbidden fixed user lookup; it does not enumerate users or
claim to certify the full upstream permissions space.

Source snapshot: **660 inputs**, four changed and five added, no removals, relative
to the prior configuration-read manifest. Aggregate SHA-256:

```text
28ff0d0551d27e667c27c5357ba98c811bce6fe6fe51ca33611b5a09cb0a47db
```

All 29 retained model-trial artifacts match their original hashes/byte counts. Main
fresh-run evidence paths (full hashes in the manifest):

- `/private/tmp/kcops-agt2-auth-baseline-20260919.log`
- `/private/tmp/kcops-agt2-auth-harness-final-source-20260919.log`
- `/private/tmp/kcops-agt2-auth-ui-20260919.log`
- `/private/tmp/kcops-agt2-auth-ui-build-20260919.log`
- `/private/tmp/kcops-agt2-auth-checker-final-live-20260919.log`
- `/private/tmp/kcops-agt2-auth-lab-final-20260919.log`
- `/private/tmp/kcops-configuration.qmnyba/configuration-checks.json`
- `/private/tmp/kcops-agt2-auth-docs-final-20260919.log`

Live checks cover distinct human subjects across separately approved clients, exact
Boolean projection, REST cache/error contracts, forbidden versus unknown handles,
stale pins, spoofed MCP clientInfo, foreign-principal session reuse, empty legacy
catalogues and denied overview/history/report/audit/legacy MCP reads. Negative history
checks use an empty disposable database, not seeded retained cross-scope content.
Tokens, cookies, HTML and provider bodies are not retained in the safe JSON results.
Compact-JWT scans passed for all run logs; this is bounded evidence, not universal
secret-detection proof. Independent read-only review found no blocker in the final
harness and its two corrections.

All versions remain unchanged: backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**,
UI/lockfile **0.8.1-dev.0**, configuration observation **1.0**, reference profile
**0.2.1**, report **1.1**, findingDetails **1.0**, catalogue **1**, Flyway **V1–V10**.
Public lab data and private checker evidence schema are not new product releases.

AGT2 remains **PARTIAL**; whole milestone exit criteria and H1/D1/AGT1 acceptance stay
open. This is Community-only evidence, not RHBK/OpenShift or model/host acceptance.
Actual resource recreation, independently reproduced operator acceptance, persisted
audit-row inspection, pre-populated cross-scope history, broad PII/domain coverage,
delegation and immediate token/SSE revocation remain outside this run.

Next: define the separately versioned bounded multi-tool host/profile contract and
its evaluation matrix before approving any new client/model/data disclosure. Preserve
AGT1 0.2.1 and the independent no-AI operator application. No broader grant or model
authorization follows automatically from successful local configuration reads.
