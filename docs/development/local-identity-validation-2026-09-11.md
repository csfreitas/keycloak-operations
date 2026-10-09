# D1 local identity validation — 2026-09-11

Scope: the first D1 authentication/isolation slice, **not completion of D1 or OpenShift readiness**. No commit, push or OpenShift access is part of this run.

## Implementation

- The UI uses `keycloak-js` 26.2.4, authorization code flow with PKCE S256, a public client and in-memory access/refresh tokens. The router mounts only after authentication and the backend identity probe succeed. Mode mismatch fails closed.
- API calls refresh the session before sending the bearer token. SSE uses authenticated fetch rather than native EventSource, refreshes credentials on reconnect and stops on authentication failure. Tokens are never query parameters.
- OIDC extension enablement must occur at build time. The backend now includes the extension in all builds and selects `quarkus.oidc.tenant-enabled` at runtime through the `oidc` profile. The packaged default still denies anonymous platform access.
- The SSE initial grant snapshot uses a worker thread because the composite registry performs JDBC reads.

References: [Keycloak JavaScript adapter](https://www.keycloak.org/securing-apps/javascript-adapter), [Quarkus OIDC configuration](https://quarkus.io/guides/security-oidc-expanded-configuration/).

## Reproduce

From the repository root, with Podman running and Java 21 installed through jenv:

```sh
JENV_VERSION=21 jenv exec mvn clean verify
bash scripts/validate-identity-lab.sh
cd ui
npm ci
npm test -- --run
npm run build
```

The runner requires the existing cached images `quay.io/keycloak/keycloak:26.7.1` and `docker.io/library/postgres:16`; it does not pull images. It refuses existing fixture container names and occupied lab ports. It creates four named `kcops-identity-*` containers and one dedicated Compose network. PostgreSQL uses tmpfs; no persistent data volumes are intended. An exit trap removes the lab containers, volumes and network and stops its own backend process. It does not prune other resources or remove reusable images. A forced host shutdown/SIGKILL cannot run the exit trap; inspect this exact Compose project before recovery.

The three Keycloak instances are separate processes: Identity A IdP on 18280, target A on 18080 and target B on 18180. Platform/database/management ports are 18081/15432/19001, all loopback. Fixture credentials are public, disposable test data; never deploy these imports elsewhere. Identity B uses distinct service-account secrets and view/query roles, without realm administration grants.

For a later browser validation, configure the UI with `VITE_AUTH_MODE=OIDC`, `VITE_API_BASE_URL=http://localhost:18081`, `VITE_OIDC_AUTHORITY=http://localhost:18280/realms/operations`, and `VITE_OIDC_CLIENT_ID=keycloak-ops-ui`. The exact registered redirect/logout URI is `http://localhost:3000/`. Fixture users are `alice-a` and `bob-b`, password `Local-fixture-only-2026!`. The automated runner tears down immediately after its checks; it does not leave a browser demonstration running.

## Evidence and limitations

- Initial backend baseline: 280 tests passed, 9 opt-in integration tests skipped; build succeeded with Java 21. These skips are not passed live tests.
- UI suite: 89 tests passed and production build succeeded, including 20 new session/event/authentication tests. These tests mock the IdP and do not establish browser end-to-end acceptance.
- First real-token run rejected a legitimate token and exposed build-time OIDC disablement. Corrected before rerun.
- Second run accepted real tokens and isolated REST targets, then exposed a JDBC-on-event-loop failure in SSE. Corrected before rerun.
- Live run after the two corrections: **32 checks passed** against real signed Keycloak tokens: anonymous REST/MCP/SSE denied; both principals isolated through REST/MCP; authenticated SSE connected; reader planning denied; distinct view-only Identity B credentials verified; wrong audience, tampered signature and expiry denied; unmapped role sees no targets.
- Log review found the upstream OIDC provider WARN message could contain the rejected compact JWT. Its category is restricted to ERROR and the runner checks for compact JWT material without printing it. **Final post-mitigation rerun passed all 32 live checks plus the compact-JWT log check**, exit 0. Client cancellation can produce a server-side SSE broken-pipe diagnostic; this does not prove a data-filtering failure.
- One intermediate runner attempt was invalidated by editing the shell script during execution; it cleaned up but is not acceptance evidence. The stable runner was then rerun to obtain the 32 checks above.
- `npm audit --omit=dev` reported two moderate dependency entries involving React Router. The installation also reported development dependency vulnerabilities. Dependency remediation is a separate outstanding readiness item; no claim of a clean security audit.

Final backend regression after all backend changes: **280 passed, 0 failures/errors, 9 opt-in ITs skipped**, build SUCCESS at 2026-09-11 00:22:52 -03:00. Final UI: **89 passed**, build SUCCESS after logout race protection. Live results are separate from Maven's skipped ITs. Local logs: `/private/tmp/kcops-d1-final-backend-20260911.log` and `/private/tmp/kcops-d1-live-redacted-20260911.log`; final platform log: `/private/tmp/kcops-identity.0J9OHl/platform.log`. Temporary logs are diagnostic artifacts, not permanent archives.

Final Podman inventory: **0 containers, 0 volumes**, only the default `podman` network and the four preexisting reusable images (Community Keycloak, PostgreSQL, RHBK and Prometheus). Disposable databases were deleted; their test data is not recoverable and can be regenerated from fixtures. No user volumes or images were removed. No OpenShift access, commit or push. `git diff --check` passed.

Remaining acceptance: browser login/logout/refresh with real users; wrong issuer and explicit revocation; cross-target event delivery (a successful SSE connection alone does not prove filtering); token substitution across MCP sessions; a privileged write-capable principal blocked by global read-only; authenticated report/history workflow; OpenShift TLS, RBAC and real RHBK topology. Reader denial of planning is not proof of every write boundary. Existing synthetic checks remain useful but are not substitutes for these live cases.
