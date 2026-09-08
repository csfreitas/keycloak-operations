# Two-identity model

Operators and the platform use **different** identities when talking to Keycloak.

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

The packaged application defaults to authenticated authorization and a loopback HTTP/management listener. It does **not** silently fall back to open access when no IdP is configured. The `oidc` profile enables bearer-token validation and protects `/api/v1`, `/api/v1/*`, `/mcp`, and `/mcp/*`; SSE is under the same REST boundary. Token propagation/login in the Web UI remains an integration requirement, not a claim of completed end-to-end OIDC browser login.

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

Grant keys are exact roles from the validated Identity A `SecurityIdentity`. Target IDs are exact configured IDs, not URL inputs or wildcard expressions. Permissions are explicit: neither `ADMIN` nor `WRITE` implicitly grants `APPROVE`; multi-step reports need both `READ` and `ASSESS`. An unrecognized role or missing grant fails closed. Global read-only mode additionally blocks `APPROVE`, `WRITE`, and `ADMIN`, regardless of grants.

The secure profile defaults to the backend client/audience `keycloak-operations` and requires that audience in bearer tokens. Configure the IdP's audience mapper accordingly; the UI's own client ID is not automatically a backend audience. `OIDC_ROLE_CLAIM_PATH` defaults to `realm_access/roles`; use an explicit intended claim such as `resource_access/keycloak-operations/roles` when client roles are the authorization contract. Do not accept caller-provided role mappings or copy the target's realm-administration roles into platform grants. Token signature, issuer, expiry, audience, role mapping, and MCP authentication must still be tested together against the chosen real IdP before a production claim.

The same `TargetAuthorizationService` is used by REST and MCP services. Target/fleet discovery and audit query pagination are restricted to readable targets. Targetless audit records are not exposed through the target-scoped audit API. SSE captures readable target IDs at subscription time, filters events, and closes after five minutes to require reconnection/re-authentication; immediate revocation of an already connected stream is not implemented.

Changes use trusted platform identity for audit and approval provenance, not caller-supplied `actor` or `approver` strings. OIDC identities are recorded using validated issuer plus subject. Identity grants are currently **target-level**, not realm/client-level ACLs. Distinct `APPROVE` and `WRITE` permissions enable separate roles but do not by themselves prove that two different humans participated. Do not advertise full governance or production readiness from this foundation alone.

### Explicit local laboratory

Use `-Dquarkus.profile=local-lab` for a packaged, unauthenticated local lab. Quarkus development and test profiles also opt into this mode. `/api/v1/me` reports `authMode=OPEN_LAB` and the fixed actor `local-lab`; that marker is **not** a verified human identity. The `authenticated` field in this legacy UI response indicates that the lab UI may be used, not that authentication occurred.

The default application and management bindings are `127.0.0.1`. Container deployments must explicitly configure their listeners and network access. Never publish the unauthenticated local-lab profile to an untrusted network. MCP traffic logging is disabled by default because raw protocol payloads have not passed through application-level redaction.

The Web UI auth module is OIDC-ready (`VITE_AUTH_MODE` / `VITE_OIDC_*`) without embedding Identity B secrets.

## Identity B — Operations Platform → Target Keycloak

```text
Operations Backend
  | credentialRef → CredentialProvider
  v
Target Keycloak / RHBK Admin API
```

- Stored only as `credentialRef` (never plaintext secrets in PostgreSQL)
- Resolved via `EnvironmentCredentialProvider`, future Vault / K8s Secret / cloud secret managers
- Scoped per Target; failures on one Target must not take down Fleet

## Rule

Never mix Identity A access tokens into Target Admin clients, and never expose Identity B secrets to the Web UI or LLM.
