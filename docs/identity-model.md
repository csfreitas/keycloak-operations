# Two-identity model

Operators and the platform use **different** identities when talking to Keycloak.

New opt-in [configuration reads](configuration-reads.md) bind exact roles and verified
JWT `iss`/`sub`/`azp` to configured resource/field scopes, with separate REST/MCP client
allowlists. They reject local-lab and do not require or grant legacy target READ.
Service-principal tokens are not proof of a human/delegation. These additive scopes
do not narrow existing broad roles; restricted users must have those removed through
an explicitly reviewed grant migration. The source credential remains Identity B.

The [two-operator local validation](development/agt2-authenticated-operators-2026-09-19.md)
exercises actual human code/PKCE identities and distinct REST/MCP clients against
Community Keycloak 26.7.1. Its independent source account has only fixture
`view-realm`/`view-clients` assignments; it is rejected as a platform caller and its
fixed user-read probe is forbidden. This is not upstream per-human role mirroring,
delegation, complete least-privilege certification or RHBK/OpenShift acceptance.

## Identity A — User → Operations Platform

```text
Browser
  | OIDC
  v
Keycloak / RHBK (platform IdP)
  |
  v
Web UI
  | Bearer token
  v
Operations Backend (REST / MCP / SSE)
```

- Authenticates humans (and service accounts) to the Operations Platform
- Drives authorization for Fleet / target APIs (`TargetAuthorizationService`)
- Must **not** be the same credential used to administer customer Targets

The packaged application defaults to authenticated authorization and a loopback HTTP/management listener. It does **not** silently fall back to open access when no IdP is configured. The OIDC extension is included at build time; the `oidc` profile enables its tenant at runtime and protects `/api/v1`, `/api/v1/*`, `/mcp`, and `/mcp/*`. SSE is under the same REST boundary. The Web UI implements authorization code login with PKCE S256, in-memory tokens, refresh before API calls, authenticated SSE and sign-out. Browser end-to-end acceptance remains distinct from implementation and mocked UI tests; see the [D1 evidence ledger](development/local-identity-validation-2026-09-11.md).

Configure the platform IdP and explicit grants, for example:

```properties
quarkus.profile=oidc
# OIDC_AUTH_SERVER_URL, OIDC_CLIENT_ID, OIDC_AUDIENCE and optional OIDC_CLIENT_SECRET are supplied securely.
platform.authorization.grants.ops-assessor.targets=customer-a-prod,customer-a-test
platform.authorization.grants.ops-assessor.permissions=READ,ASSESS
platform.authorization.grants.ops-planner.targets=customer-a-test
platform.authorization.grants.ops-planner.permissions=READ,PLAN
platform.authorization.grants.ops-approver.targets=customer-a-test
platform.authorization.grants.ops-approver.permissions=READ,APPROVE
platform.authorization.grants.ops-executor.targets=customer-a-test
platform.authorization.grants.ops-executor.permissions=READ,WRITE
```

Grant keys are exact roles from the validated Identity A `SecurityIdentity`. Target IDs are exact configured IDs, not URL inputs or wildcard expressions. Permissions are explicit: neither `ADMIN` nor `WRITE` implicitly grants `APPROVE`; multi-step reports need both `READ` and `ASSESS`. Installation discovery requires `READ` + `DISCOVER`; binding confirmation additionally requires `BIND`. These permissions are not inherited from administrative roles. An unrecognized role or missing grant fails closed. Global read-only mode additionally blocks `BIND`, `APPROVE`, `WRITE`, and `ADMIN`, regardless of grants. See [installation confirmation](development/installation-onboarding-2026-09-11.md) for managed binding precedence and mandatory transactional audit.

The secure profile defaults to the backend client/audience `keycloak-operations` and requires that audience in bearer tokens. Configure the IdP's audience mapper accordingly; the UI's own client ID is not automatically a backend audience. `OIDC_ROLE_CLAIM_PATH` defaults to `realm_access/roles`; use an explicit intended claim such as `resource_access/keycloak-operations/roles` when client roles are the authorization contract. Do not accept caller-provided role mappings or copy the target's realm-administration roles into platform grants. Token signature, issuer, expiry, audience, role mapping, and MCP authentication must still be tested together against the chosen real IdP before a production claim.

The same `TargetAuthorizationService` is used by REST and MCP services. Target/fleet discovery and audit query pagination are restricted to readable targets. Targetless audit records are not exposed through the target-scoped audit API. SSE captures readable target IDs at subscription time, filters events, and closes after five minutes to require reconnection/re-authentication; immediate revocation of an already connected stream is not implemented. The stream does not recheck token expiry or grants per event and may outlive the admitting token's expiry.

### Session termination boundaries

The [local browser-negative ledger](development/d1-browser-negatives-2026-09-19.md)
records real PKCE login and backend `/me` rejection for a validly signed wrong-issuer
token, wrong backend audience and expired token, plus a valid 200 control. Independent
boolean observations isolate each failure. Expiry is induced by a bounded transport
delay after normal credential acquisition; no token or backend response is replaced.
The fixture is development-only, uses restricted public clients, and is excluded from
production builds. This supplements, but does not replace, cross-surface/production
IdP acceptance. No grant or revocation semantics changed.

The [local session correction](development/d1-browser-session-2026-09-19.md) ties REST
requests and SSE subscriptions to a browser authentication generation. Changing the
provider/token or invalidating the session aborts old transports; generation checks
discard late token, response, body and event results. A current 401 invalidates the
local session and removes protected routes. Target-scoped 403 does not sign out a
valid caller, and an old 401 cannot invalidate a replacement session. Explicit logout,
adapter logout, identity-load failure and provider unmount also invalidate transport.
No obsolete stream reconnects using a new principal's credentials.

This is local presentation/transport protection, not distributed revocation. JWTs are
normally verified against the configured issuer's JWKS; the backend does not require
IdP session introspection on every request. A copied, otherwise valid bearer can remain
usable until expiry. With `checkLoginIframe=false` and no cross-tab broadcast, another
tab detects remote logout only when authentication/refresh fails, not immediately.
Keycloak's adapter can redirect to login after `clearToken()` in login-required mode.
Aborting a browser request does not roll back an operation already received by the
backend. Logout must not be advertised as production write cancellation.

Changes use trusted platform identity for audit and approval provenance, not caller-supplied `actor` or `approver` strings. OIDC identities are recorded using validated issuer plus subject. Identity grants are currently **target-level**, not realm/client-level ACLs. Distinct `APPROVE` and `WRITE` permissions enable separate roles but do not by themselves prove that two different humans participated. Do not advertise full governance or production readiness from this foundation alone.

### Explicit local laboratory

Use `-Dquarkus.profile=local-lab` for a packaged, unauthenticated local lab. Quarkus development and test profiles also opt into this mode. `/api/v1/me` reports `authMode=OPEN_LAB` and the fixed actor `local-lab`; that marker is **not** a verified human identity. The `authenticated` field in this legacy UI response indicates that the lab UI may be used, not that authentication occurred.

The default application and management bindings are `127.0.0.1`. Container deployments must explicitly configure their listeners and network access. Never publish the unauthenticated local-lab profile to an untrusted network. MCP traffic logging is disabled by default because raw protocol payloads have not passed through application-level redaction.

Configure the implemented Web UI auth module with `VITE_AUTH_MODE` / `VITE_OIDC_*` before building, without Identity B secrets. Current `keycloak-js` setup expects a Keycloak-style realm issuer, not arbitrary IdP compatibility.

### Browser return-path boundary

After successful platform OIDC authentication, the UI restores only allowlisted internal target/change routes before mounting the router. The IdP redirect remains the fixed root URL; no wildcard redirect or `redirect` query parameter is needed. A tab-scoped `sessionStorage` hint holds only pathname and timestamp for at most ten minutes, is consumed once, and is cleared on failure/logout or a fresh root visit. Queries/fragments (including OAuth parameters) are never copied into the hint. Malformed, external, traversal, expired and future hints are discarded; unavailable storage degrades to root navigation. Tokens remain in adapter memory; the hint is not authorization. Target child pages mount only after the backend-authorized overview succeeds; rejected targets do not mount reports or action controls. Keycloak's adapter still validates OIDC state/nonce/PKCE.


## Identity B — Operations Platform → Target Keycloak

```text
Operations Backend
  | credentialRef → CredentialProvider
  v
Target Keycloak / RHBK Admin API
```

- Stored only as `credentialRef` (never plaintext secrets in PostgreSQL)
- Resolved via `ConfigCredentialProvider` from externally supplied configuration; direct Vault / K8s Secret / cloud secret-manager integrations remain future extensions
- Scoped per Target; failures on one Target must not take down Fleet

## Partial access-aware assistance (AGT2)

[AGT2](milestones/agt2-access-aware-assistance.md) brings fine-grained **read**
authorization forward as a prerequisite for broader operational questions in both
the console and MCP. It must constrain target/installation/namespace, realm/resource,
operation and field/data-class as applicable, with explicit human/client binding and
provider-disclosure policy. Legacy grants remain target-wide; the separate default-
closed [configuration channel](configuration-reads.md) pins realm/client/field and
verified role/client/channel scope. Neither mirrors the human's personal target
permissions. No shared privileged MCP login, role text
in a prompt or Identity A token passthrough can substitute for reviewed delegation.
Legacy grant migration, aggregates/history and full conversation/context handling
remain acceptance gates. The [reference client 0.1.0](../dev/access-aware-client/README.md)
uses a trusted host's non-reused context generation and invalidates pending/returned
evidence locally. This Symbol is not authenticated identity: the host must bind calls
to the reviewed session, rotate context on changes and clear displayed copies.
Synthetic tests do not establish OAuth/MCP integration. The subsequent
[two-operator local host run](development/agt2-client-live-2026-09-22.md) exercises
real code/PKCE and backend-verified tokens through a fixed loopback MCP adapter.
It closes local/MCP sessions, not the IdP session or already issued access tokens;
general OAuth discovery/refresh, immediate revocation and delegated human authority
remain unimplemented. No model is enabled.

## Rule

Never mix Identity A access tokens into Target Admin clients, and never expose Identity B secrets to the Web UI or LLM.
