# Disposable authenticated local lab

This is a local Community Keycloak fixture, not RHBK/OpenShift certification. It provides an independent platform IdP, targets A/B and a PostgreSQL database on tmpfs. The default identity/report scenario uses no cluster connection. Global read-only stays enabled; demo actors have only READ/ASSESS on their own target. By default both targets have metrics explicitly NONE, avoiding inheritance of the regular lab's Prometheus endpoint. The separate opt-in installation scenario below uses only a synthetic loopback cluster API and dedicated setup permissions. The opt-in restricted-configuration scenario uses two human identities with no broad target grants.

## Run from the repository root

Prerequisites: running Podman machine/Compose provider, cached `quay.io/keycloak/keycloak:26.7.1` and `docker.io/library/postgres:16`, Java 21 installed in jenv, Node/npm and installed UI dependencies. The runner uses `--pull never`; arrange missing images separately. It refuses existing named resources or occupied fixture ports instead of replacing them. The explicit default connection is `podman-machine-default-root`; select another approved **local machine** connection with `IDENTITY_LAB_PODMAN_CONNECTION=name`. The selected loopback SSH URI and identity-file path are captured once and passed to every runtime operation; changes to Podman's default connection cannot redirect the run. Remote/non-machine connections are rejected. These runners require macOS/Linux POSIX process groups, not Windows.

```bash
JENV_VERSION=21 jenv exec mvn clean verify
npm --prefix ui ci
npm --prefix ui run test:run
npm --prefix ui run build
bash scripts/validate-identity-lab.sh
```

The automated run checks real signed identity, REST/MCP isolation, actual target-filtered SSE delivery, persisted report assessment ownership, missing metrics, fixture-secret canaries and negative tokens. It then removes its four containers and Compose network, including disposable database state. Cached images and unrelated resources are preserved; no global prune runs. Logs are retained in the printed `/private/tmp/kcops-identity.*` directory for diagnosis.

## Bounded execution and owned cleanup

The identity, installation and restricted-configuration runners use the same supervisor/runtime contract:

- Runtime reads/removals: 30 seconds per command, up to three attempts **only** for recognized SSH EOF/reset/broken-pipe errors, with a one-second delay and a shared three-minute phase budget. Permission/configuration errors, timeouts, output limits and uncertain process termination are not retried. Each retry is reported.
- Compose startup: two minutes, **never retried** after ambiguous failure. Partial creations are discovered by the run label during cleanup; the VM is never restarted automatically.
- Readiness: three minutes per identity/setup endpoint, two minutes for the default backend and 30 seconds for its optional UI. Installation UI uses the three-minute readiness helper. Individual HTTP probes have a two-second limit, accept only loopback HTTP 2xx headers, cancel bodies and do not follow redirects. This is reachability, not a semantic health assertion.
- Functional checks: ten minutes; backend/UI/fixture supervisors: one hour maximum; browser walkthrough: 15 minutes. Owned process groups receive TERM, then KILL after two seconds, with a further one-second verification allowance measured from the actual KILL callback. Deliberately daemonized processes escaping that group, SIGKILL of the supervisor and host crashes are outside this guarantee.
- Each run has a UUID label on containers/network plus purpose/project labels. Cleanup inspects ownership, removes only full container IDs (no volume flag), and removes only the verified empty owned network by full ID, without force. It verifies absence before releasing the lock. No image or volume deletion/pruning occurs. The lab stores database/TSDB state on tmpfs and creates no reusable data volumes.

Existence-check errors are **unknown**, not absent. The original test failure or interruption remains nonzero even if cleanup succeeds. A transient cleanup retry may succeed, but its diagnostic remains visible. Ambiguous network-removal acknowledgement is accepted only after both the exact ID and expected name are verified absent. Unconfirmed resource or process cleanup retains `/private/tmp/kcops-identity-lab.lock` and fails the run, preventing another run from claiming an empty lab.

`runtime.json` in the private diagnostic directory records the selected connection, SSH identity **path** (not key contents) and run UUID. Recovery is manual: verify that the original runner/processes are stopped, inspect the exact run labels/IDs and network attachments on that recorded connection, and resolve only those resources. Do not delete the lock blindly, run a global prune, or restart the VM to suppress a failed result. A VM restart requires separate explicit approval. Old/manual fixtures without the run UUID are not adopted or deleted.

Harness regressions (fake runtime, real local child processes/loopback probes; no containers):

```bash
node --test scripts/lab-process.test.mjs scripts/identity-lab-runtime.test.mjs scripts/identity-lab-runner-common.test.mjs
```

See the [runner-hardening evidence](../../docs/development/d1-runner-hardening-2026-09-18.md) and [19 September validation](../../docs/development/d1-runner-validation-2026-09-19.md) for actual executions and limitations. A transient process-group probe error remains unknown until independently verified absence; persistent uncertainty at the deadline still fails cleanup and retains the lock. There is no claim that this fixes the underlying VM/SSH instability or completes browser/real-cluster acceptance.

## Optional real metrics and MCP reports

Cache `docker.io/prom/prometheus:v2.55.1` before using the optional mode:

```bash
bash scripts/validate-identity-lab.sh --metrics
# Add --browser to inspect the same scenario through the UI.
```

The metrics profile adds only `kcops-identity-prometheus`, using loopback port 18490 and tmpfs storage with one-hour retention. It scrapes the two Keycloak management endpoints on the private Compose network, with separate target/service labels. Only A is configured to consult Prometheus; B remains NONE even though its samples exist. The runner sets the fixture-only `IDENTITY_LAB_METRICS_TYPE` for the backend process, without changing global configuration. No metrics endpoint or arbitrary query is accepted from MCP callers.

Both modes generate authenticated reports through REST and the actual MCP protocol, check target identity/schema/section completeness, unavailable assessment score/replay and fixture-secret canaries, and deny foreign-target report access. Unconfigured infrastructure sections must be null in JSON/Markdown, not zero/false defaults. The metrics mode additionally verifies positive JVM heap measurements, missing container memory as null, and a still-inconclusive assessment. The fixture sets `assessment.performance.heap-utilization-warning-percent=85` explicitly: heap policy evidence must no longer be missing when A has metrics. This test threshold is not a production recommendation; other unset policies remain unevaluated. These are real runtime samples, not load, HA, container-infrastructure coverage or IAM business indicators. The scripted totals are 75 checks in default mode and 80 with metrics, plus the separate compact-JWT log check; consult the [read-boundary evidence](../../docs/development/h1-read-metadata-2026-09-19.md) and [earlier metrics evidence](../../docs/development/d1-metrics-report-2026-09-11.md) for actual executions.

Both identity modes also exercise `/environment` with each real caller: own-target
scope succeeds, a foreign target returns 403, and a signed principal without target
grants is denied. This endpoint observes registered targets; it does not grant
installation candidate discovery or binding rights. Browser lifecycle/revocation
remains a separate gate.

Cleanup removes four containers by default or five with metrics, the owned network and disposable database/TSDB data. It retains reusable images and creates no persistent data volume. Never globally prune to clean up this fixture.

## Browser operator walkthrough

For the optional provider-neutral reference-profile rehearsal, run
`bash scripts/validate-identity-lab.sh --reference-agent`. This adds **ten** checks
to the selected identity mode (**85** without metrics; **90** with metrics) while
reusing its MCP reports and owned resources. It validates the fixed-tool adapter,
fact projection, exact report/assessment finding binding, evidence pointers and
structural-only explanation result, **not** an LLM/provider.
The [profile guide](../reference-agent/README.md) and
[execution ledger](../../docs/development/agt1-grounding-2026-09-19.md) distinguish
expected counts from the combinations actually run. Existing default counts above
are unchanged when the flag is absent.

```bash
bash scripts/validate-identity-lab.sh --browser
```

Wait for all automated checks and **Browser lab ready**. Open **http://127.0.0.1:18300/** exactly; port 3000 is deliberately not used. The loopback origin is also the fixture's exact redirect/logout/CORS allowlist. Do not substitute localhost or expand origins to wildcards. The backend is on 18081, management on 19001, IdP on 18280, targets on 18080/18180 and PostgreSQL on 15432.

1. Open `http://127.0.0.1:18300/targets/lab-keycloak-a/report` directly and sign in as `alice-a` using public disposable password `Local-fixture-only-2026!`; login should return to A's Report page. The IdP callback remains the exact root origin, without wildcard redirect URIs. Fleet should list only A and show connected events.
2. Open A → Report → Generate Report. Confirm target identity and explicit incomplete/missing evidence, not a favorable invented score.
3. Wait at least 60 seconds from login **without reloading** and generate another report or health run. The UI client's 45-second access token requires renewal; a successful new operation tests continuity beyond the original token lifetime. This is not proof of immediate token revocation.
4. Navigate directly to `/targets/lab-keycloak-b/report`; expect `Failed to load target` / not authorized, with no Report page or B data. Return to Fleet, then Sign out. Sign in as `bob-b`; only B should appear. Sign out again.
5. To check remote-session loss separately, sign in again as Alice, leave A's report open, and open a second tab at the same exact UI origin. Sign out in the second tab. After at least 60 seconds, attempt an operation in the first tab without reloading; token renewal should fail and protected content must not remain usable. The adapter may redirect to the IdP sign-in page. Do not claim immediate cross-tab notification: the UI disables login iframe checks and detects loss on authentication/refresh failure.
6. Close only the lab tabs and press Enter in the runner terminal. Browser mode stops automatically after 15 minutes if forgotten; cleanup also runs on failure, interrupt or termination (not SIGKILL/host crash).

An invalid disposable-password attempt must remain on the IdP sign-in page, never
mount protected routes. Current REST/SSE 401 invalidates local UI authentication;
target 403 must keep the authorized session usable. Automated frontend regressions
also exercise refresh/logout and late-response races that are not reliably reproduced
by manual clicking. See the [session-boundary ledger](../../docs/development/d1-browser-session-2026-09-19.md)
for actual observations and their limits. Local abort does not roll back an operation
already received by the server; copied JWTs and previously admitted SSE have separate
expiry/revocation limits. Wrong issuer/audience and expired-token API checks do not
automatically establish equivalent browser coverage.

With `--metrics --browser`, A's generated report should have a COMPLETE performance section with JVM samples; B should have SKIPPED performance. Neither should gain a favorable assessment score from incomplete evidence. The default mode has SKIPPED performance for both targets.

Do not save fixture passwords in the browser. Fixture secrets are public test data and must never be reused elsewhere. Do not leave the lab running after validation. Inspect final owned resources; if cleanup fails, inspect exact names before removing anything. Browser observations, installation confirmation with real local identity + mock cluster and agent integration are separate acceptance evidence, not implied by the default automated check count.

## Optional real browser identity negatives

```bash
node --test dev/identity-lab/browser-fixture-contract.test.mjs dev/ui-browser-negatives/transport.test.mjs
bash scripts/validate-identity-lab.sh --browser-negatives
```

This opt-in mode runs the normal automated checks first, then reuses the same owned
lab for `wrong-issuer`, `wrong-audience`, `expired`, and `valid` browser scenarios.
Each has a separate Vite process/log, using the same exact port 18300. At each
**Browser negative lab ready** message, open a new lab tab at `http://127.0.0.1:18300/`,
sign in as the disposable `alice-a` account if requested, and inspect the safe evidence
banner and the application's actual auth state. Close that tab before pressing Enter
to advance. Browser launch/readiness/acknowledgments share a 15-minute budget; owned
process shutdown and resource cleanup have their separate bounded deadlines. Timeout
or EOF fails this mode instead of implying a completed walkthrough. An acknowledgment
alone is not a passed security test: record observed evidence separately.

The [development-only browser entry](../ui-browser-negatives/README.md) uses the real
App, Keycloak adapter, AuthProvider/API client and backend. Two additional public PKCE
clients differ from the normal client only by audience mapper or short access-token
lifetime (and client IDs). No users, roles, backend grants, wildcard origins or
read-only defaults change. Wrong issuer uses the same realm/client/key via the alternate
loopback hostname; compare actual signature/issuer/audience observations before
claiming that case is isolated.

For expiry only, the fixture deliberately delays the first fixed identity GET until
its real signed token expires. The normal adapter still renews before requests; this
is a controlled in-flight timing fault, not ordinary refresh failure. The banner
reports booleans/status only. Never print tokens, export credential-bearing traces or
save fixture passwords. The final valid case must mount the authorized UI; the three
negative cases must show real API 401 without protected routes. A null signature
result is unavailable evidence, not a valid signature. These observations do not add
immediate JWT/SSE revocation, RHBK compatibility or production-write acceptance.

## Optional installation discovery and confirmation

```bash
node --test scripts/installation-cluster-fixture.test.mjs
bash scripts/validate-installation-lab.sh
# Add --browser for the manual review/selection workflow below.
```

This separate scenario reuses the same four named containers, but not a running lab or its database. All three runners refuse concurrent execution and existing resources, including the optional metrics container. They remove only verified per-run resource IDs, including partial startup creations. A lock remaining after unconfirmed cleanup, SIGKILL or host failure is not automatically broken: verify no runner or owned resource is active before recovering it. Cleanup cannot preserve disposable tmpfs database contents; cached images are retained.

`installation.properties` explicitly connects both targets to `http://127.0.0.1:18590`, namespaces `lab-a` and `lab-b`. The Node fixture exposes two metadata-only Deployments per namespace, no Operator resources and no real cluster. Workload metadata is not proof of Keycloak identity/health. Cluster requests are bounded and recorded without headers/bodies; cluster writes are refused. A different fixture-only control credential injects UID replacement and permission denial. The expiry case updates only the runner-owned transient database; it does not wait ten minutes or claim a real elapsed-time test.

Fixture SQL uses the captured runtime endpoint and an inspected full PostgreSQL container ID, requiring matching run/purpose/project labels and tmpfs storage. Each command has a ten-second limit, a 1 MiB output cap and no retry; SQL never follows the current default connection or a replaceable container name. Its child processes remain inside the functional-check supervisor's process group.

The setup backend uses ports 18081/19001 with process-local `INSTALLATION_LAB_READ_ONLY=false`. A second backend, sharing the transient PostgreSQL database, uses 18082/19002 and stays globally read-only. Both use Java 21 through per-command jenv; no global Java setting changes. The normal configuration and normal reader grants remain unchanged. `setup-a`/`setup-b` grant READ, DISCOVER and BIND only for the corresponding target; special test identities cover a second actor, both targets and BIND without DISCOVER. No WRITE, ADMIN or APPROVE is granted. The new IdP roles have no setup grants in the normal report scenario.

Automated checks cover exact persisted API version/kind/name/UID, revision, consumed run and mandatory actor/target audit; reader read-back; stale/replayed/superseded/expired runs; resource recreation; actor/target isolation; cluster denial and global read-only denial. Negative confirmations compare binding, runs and success audit before/after. This is REST/real-IdP/local-database integration against synthetic Kubernetes responses, not a real Kubernetes RBAC, OpenShift or HA test. No MCP confirmation tool exists.

For browser review, run `bash scripts/validate-installation-lab.sh --browser`, wait for **Installation browser lab ready**, then:

1. Open `http://127.0.0.1:18300/targets/lab-keycloak-a/installation` and log in as `sam-setup-a` with the public password `Local-fixture-only-2026!`.
2. The scripted scenario leaves A bound to `alternate-a` / `uid-a-2`, revision 2. Discover candidates; confirm must be disabled before an explicit selection and acknowledgement.
3. Select `sso-a` / `uid-a-1`, review the old/new UID and namespace, tick the review checkbox and confirm. Expect revision 3 and the saved/audited message; no cluster resource changes.
4. Sign out, sign in as reader `alice-a` and revisit Installation. The saved binding is visible but discovery/confirmation is unavailable. Sign out and close only the lab tab.
5. Press Enter in the runner. The 15-minute fallback removes the owned lab if forgotten. Inspect containers, volumes, network and listeners; do not globally prune.

The runner retains diagnostic logs under `/private/tmp/kcops-installation.*`, scans backend/fixture logs for compact JWT material and fails if scanning itself fails. A local scan is a bounded check, not proof that every possible sensitive output is covered. The installation ledger records actual runs and outstanding acceptance gaps separately.

## Optional authenticated restricted-configuration inspection

Use this separate, opt-in scenario after the build prerequisites above and with an
approved local Podman connection. It uses the same four default containers and
loopback ports, with no metrics or cluster fixture:

```bash
bash scripts/validate-configuration-lab.sh
# Add --browser to keep the owned lab available for the walkthrough below.
```

The runner loads [configuration.properties](configuration.properties), the
[platform IdP fixture](idp/operations.json) and [target-A fixture](target-a/target.json).
The two human accounts have only their dedicated configuration role; this scenario
defines no legacy target READ/ASSESS/PLAN grants. Do not add broad grants to make
Fleet, reports or target overview available: restricted grants do not reduce rights
already supplied by another role. Global read-only remains enabled.

| Human account / role | Permitted configured handles | Exact fields |
|---|---|---|
| `rhea-realm` / `config-realm` | `realm-settings`, `realm-stale` | `registrationAllowed`, `resetPasswordAllowed`, `bruteForceProtected`, `verifyEmail` |
| `chris-client` / `config-client` | `portal-settings`, `client-stale` | `enabled`, `publicClient`, `standardFlowEnabled`, `directAccessGrantsEnabled`, `serviceAccountsEnabled` |

Both accounts use the existing public disposable password
`Local-fixture-only-2026!`. Never reuse or save it outside this fixture. Both scopes
resolve to `lab-keycloak-a` / `target-a`; the configured client resource is `portal-a`.
The valid handles pin internal IDs `agt2-realm-a-v1` and `agt2-portal-a-v1`.
The stale handles deliberately configure different internal IDs. They test a pin
mismatch against an existing fixture, **not actual resource deletion/recreation**.
Their presence in an authorized catalogue is permission metadata, not evidence
that the source exists or can be read.

The checker authenticates each human separately through authorization code with
PKCE S256, using public client `keycloak-ops-ui` for REST and `keycloak-config-mcp`
for MCP. The backend validates the signed identity; the policy also checks the
verified token client (`azp`) and channel. A REST token is not approved for MCP merely
because `clientInfo` names the MCP client, and an MCP token is not approved for these
REST reads. The outbound Identity B account is separately configured as
`configuration-reader`, with only `view-realm` and `view-clients` fixture assignments.
Those source roles can read broader representations than the permitted Boolean
projection; they do not themselves implement the platform's per-human scope policy.
Neither the platform identity nor its token becomes a source-admin credential.

[configuration-lab-checks.mjs](../../scripts/configuration-lab-checks.mjs) is authored
to exercise own/foreign/unknown handles, stale pins, client/channel separation,
REST/MCP fact parity, denial of legacy aggregate/history/report surfaces and an
independent source-credential boundary check. It uses bounded, allowlisted loopback
requests and keeps bearer tokens, login cookies and login HTML in memory. REST and
MCP perform separate collections, so observation IDs/times need not match. The
returned product version remains `UNKNOWN`; `COMPLETE` covers only configured fields,
not a whole-environment or health assessment. See the [contract](../../docs/configuration-reads.md).

The same runner now also exercises the unchanged [reference client 0.1.0](../access-aware-client/README.md)
through [a separate fixed-loopback host](../../scripts/configuration-client-lab.mjs),
using both real MCP-approved human tokens. It validates explicit scope selection,
canonical facts/references, foreign/unknown refusal, stale-pin failure/recovery,
REST-client non-upgrade, replacement-operator isolation and local host/MCP sign-out.
Finite JSON/SSE validates response IDs, session and negotiated protocol; protocol or
session changes fail closed. Caller cancellation reaches the bounded HTTP/body reader.
Two controlled cases hold an actual catalogue reply at the host, then invalidate or
time out: these are **injected delivery delays**, not a provider latency benchmark.
No model/provider is invoked. Sign-out is local host + MCP session DELETE, **not**
IdP logout or revocation of copied access tokens. No source configuration is mutated.

Offline adapter checks are included in CI and can be reproduced independently:

```sh
node --test scripts/configuration-client-lab.test.mjs scripts/configuration-lab-checks.test.mjs dev/access-aware-client/*.test.mjs
```

See [22 September client evidence](../../docs/development/agt2-client-live-2026-09-22.md)
for the measured local scope. The harness is not a production credential/transport
implementation; unsupported SSE resumption and server-initiated messages are rejected.

The runtime connection is pinned using the shared ownership/cleanup contract above.
The runner refuses implicit root/config/UI `.env` or application configuration files
listed in its preflight; use an isolated checkout instead of deleting another task's
configuration. Backend and UI start with an allowlisted process-local environment,
without inherited OIDC, target or authorization overrides. Logs, `runtime.json` and
the safe `configuration-checks.json` aggregate remain under the printed
`/private/tmp/kcops-configuration.*` directory. Writers are stopped before the compact
JWT log scan, including failure/interruption paths; this bounded scan does not prove
universal secret filtering. Cleanup removes only owned containers/network and tmpfs
database state, retains images, creates no persistent data volumes and never prunes.
Unverified cleanup preserves the shared lock; do not break it or restart the VM
automatically.

For an optional operator walkthrough, run
`bash scripts/validate-configuration-lab.sh --browser`, wait for **Configuration
browser lab ready**, then:

1. Open `http://127.0.0.1:18300/configuration` exactly and sign in as `rhea-realm`.
   Expect only `realm-settings` and `realm-stale`. Choose `realm-settings`, then
   **Run inspection**; selecting a scope alone must not collect source values.
   Check the four configured Boolean fields, observation time and unknown product
   version. `registrationAllowed` is false, not unavailable.
2. Select `realm-stale`: previous facts must disappear immediately. Run its
   inspection and expect an unavailable-evidence error without facts. Chris's
   client handles must not be offered. The automated checks test direct foreign
   scope IDs as well; selector absence alone does not establish authorization.
   Fleet should be empty and a direct visit to `/targets/lab-keycloak-a` should be
   denied, with no fallback to broad target reads.
3. Sign out, confirm protected observations disappear, then sign in as `chris-client`
   at `/configuration`. Expect only `portal-settings` and `client-stale`. Inspect
   `portal-settings` and check its five client fields; inspect `client-stale` for
   an unavailable result. Rhea's realm handles must not be offered.
4. Optionally repeat selection, inspection and sign-out with keyboard navigation
   and a narrow/mobile viewport. Record actual focus, layout and error observations;
   scripted API checks do not establish browser or accessibility acceptance.
5. Sign out, close only the lab tabs and press Enter in the runner. Its 15-minute
   walkthrough deadline fails on timeout/EOF and invokes owned cleanup. An Enter
   acknowledgment is not a passed walkthrough; record observed results separately.

With `--browser`, an ordinary failed automated check with verified process termination
still exposes the owned UI for bounded diagnosis; uncertain process termination goes
directly to cleanup. The runner preserves a nonzero result after the walkthrough. It never
turns a failed scripted run into a pass. Without the flag, failure immediately cleans up.

These are executable instructions and expected outcomes, not a record that the new
run passed. Keep actual counts, browser observations and cleanup evidence in the
dated validation ledger. Community Keycloak **26.7.1** is not RHBK/OpenShift acceptance,
and AGT2 remains partial. This scenario calls no AI model or external provider and
does not extend the reference-agent allowlist. Existing JWT/SSE revocation limits
and the distinction between local sign-out and remote token revocation still apply.
