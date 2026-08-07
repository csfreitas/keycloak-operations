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
Operations Backend (REST / SSE)
```

- Authenticates humans (and service accounts) to the Operations Platform
- Drives authorization for Fleet / target APIs (`TargetAuthorizationService`)
- Must **not** be the same credential used to administer customer Targets

OIDC protection of REST/UI is planned; 0.3 exposes REST without Identity A enforcement in local lab.

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
