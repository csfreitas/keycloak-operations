# Registry preflight — contract 0.1.0

Status: initial REST-only ONB1 implementation, disabled by default. No UI/MCP tool,
registration, persistent draft, migration, grant or remote validation. See
[ADR 0013](adr/0013-registry-preflight-before-registration.md) and the
[validation ledger](development/onb1-preflight-2026-10-09.md).

## Authority and activation

`platform.registry-administration.enabled` defaults to `false`. The platform must
already verify OIDC JWT signatures, issuer, audience and expiry. Preflight additionally
requires `platform.authorization.mode=authenticated`, a non-anonymous JWT principal
and an exact match of issuer + subject + role + `azp` within **one** configured entry.
Roles from other entries cannot be combined. No target ADMIN fallback, local-lab
bypass, wildcard, first-login bootstrap or caller-supplied identity exists.

Illustrative server-side configuration only (not an instruction to enable production
access or a complete OIDC deployment):

```properties
platform.registry-administration.enabled=true
platform.registry-administration.administrators.initial.issuer=https://identity.example.invalid/realms/operators
platform.registry-administration.administrators.initial.subject=immutable-operator-subject
platform.registry-administration.administrators.initial.role=registry-preflight
platform.registry-administration.administrators.initial.rest-client-ids=operations-console
```

At most 20 entries, each with 1–20 exact REST client IDs. Administrator handles match
`[a-z][a-z0-9-]{0,63}`. Role/client identifiers match
`[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}`. Issuer/subject are nonblank exact claims up to
2048 UTF-16 units, without surrounding whitespace, controls or wildcards. They are
not display names. Configuration is validated and copied at startup: changing it
requires restart and does not provide immediate token revocation. Framework-level
configuration/OIDC diagnostics are outside the policy's fixed-error guarantees.

This is explicit deployment-time authority for preflight only, not the future
audited administrator/grant-bootstrap workflow. No identity or client is enabled in
the application's shipped configuration. Never allow body/token logging upstream.

## Request

`POST /api/v1/registry/targets/preflight`, `Content-Type: application/json`:

```json
{
  "operation": "CREATE",
  "targetId": "production-a",
  "displayName": "Production A",
  "productType": "RHBK",
  "environment": "PRD",
  "keycloak": {
    "url": "https://sso.example.invalid/auth",
    "authRealm": "master",
    "clientId": "operations-reader",
    "credentialRef": "approved-reference"
  }
}
```

Every field is required; string fields must be JSON strings. Both objects are closed.
Unknown keys (including secrets, tokens, tags, enabled, owner/revision, actor, grants,
infrastructure, metrics or management URL) are rejected. Only `CREATE` is accepted
as draft intent; **nothing is created**. Product type is `KEYCLOAK` or `RHBK`;
environment is `DEV`, `TEST`, `HML`, `STAGING`, `PRD` or `UNKNOWN`, case-sensitive.
These declarations do not prove an actual product, version or environment.

Limits:

- Body at most 8192 bytes; application reads at most 8193 before rejecting. Duplicate
  JSON fields, trailing values, excessive nesting/names/strings/numbers and malformed
  input fail. Authorization precedes application parsing, not receipt/buffering by
  the HTTP server. This is not a whole-request deadline or concurrency/rate limit.
- `targetId`: `[A-Za-z0-9._-]{1,128}`, exact, no trimming. Display name: nonblank,
  at most 255 UTF-16 units, no surrounding whitespace, controls, formatting characters
  or unpaired surrogates. Auth realm/client/reference use the role/client grammar above.
- URL: lowercase `https` scheme, ASCII, at most 1024 characters; DNS-style labels
  (including syntactically numeric hosts), host at most 253 and labels at most 63.
  Optional canonical decimal port 1–65535. Optional simple path segments using
  letters/digits/`._~-`. No credentials, query, fragment, percent escapes, dot segments,
  doubled slashes, backslashes, trailing-dot host, Unicode host or IPv6 literal in
  this first subset. No DNS lookup occurs. A private/loopback name is not rejected
  merely for location because **no destination is approved or contacted**.

Existing static configuration accepts a wider set; this slice neither migrates nor
changes it. Supporting more administrative connection types requires a reviewed contract.

## Results and errors

```json
{
  "contractVersion": "0.1.0",
  "validation": "LOCALLY_VALID",
  "issues": [],
  "persisted": false,
  "registrationAvailable": false,
  "connectivity": "NOT_TESTED",
  "credentialValidity": "NOT_TESTED",
  "destinationApproval": "NOT_CHECKED",
  "globalReadOnly": true
}
```

| HTTP / validation | Meaning |
|---|---|
| 200 / `LOCALLY_VALID` | Draft syntax valid; ID absent from configuration/database; reference exists in configured credential map |
| 400 / `REJECTED` | Structural/field rejection, ID collision or unknown reference; no values are echoed |
| 503 / `INCONCLUSIVE` | Registry lookup failed; absence/availability is not inferred |
| 401 / 403 | Framework/policy authentication or authorization denied; no local registry existence information from the service |

Each issue is `{ "field": "fixed.path", "code": "FIXED_CODE" }`. Codes:
`INVALID_JSON`, `BODY_TOO_LARGE`, `INVALID_SHAPE`, `UNSUPPORTED_OPERATION`,
`INVALID_IDENTIFIER`, `INVALID_DISPLAY_NAME`, `INVALID_PRODUCT_TYPE`,
`INVALID_ENVIRONMENT`, `INVALID_HTTPS_ENDPOINT`, `UNKNOWN_CREDENTIAL_REFERENCE`,
`ALREADY_EXISTS`, `REGISTRY_UNAVAILABLE`. Unknown field names are not echoed.
Once structure/fields are valid, credential-map membership and configuration/database
collision checks run. A configuration collision needs no database query; a failed
required lookup yields INCONCLUSIVE even when another issue is known.

The REST filter applies `Cache-Control: no-store` and `Vary: Authorization` to the
registry subtree, including mapped errors. Authentication responses produced before
REST filters and proxies require separate real-deployment verification. No raw draft,
URL, client ID, reference, provider error or credential value appears in the result.

## Not established by a successful preflight

No credential value is resolved or verified; map membership is not permission to use
it. No candidate environment, DNS, cluster, metrics or IdP endpoint is contacted by
preflight. It reads the existing **platform PostgreSQL** for ID collision, so it is
not a zero-I/O operation. It does not invoke the composite registry's seed/fallback,
discovery, providers, persistence, audit writer or binding service.

`persisted` and `registrationAvailable` always remain false. No token/reservation is
returned; a later registration must repeat authorization and validation transactionally.
`globalReadOnly` is informational; false does not enable this endpoint to mutate or
grant BIND/ADMIN. Existing global read-only/BIND behavior is unchanged.

The subsequent [ownership foundation](architecture/registry-ownership.md) adds V11
and audited configuration reconciliation outside this unchanged preflight path.
Remaining ONB1 work: governed adoption/API ownership transfer and expected revisions;
audited administrator bootstrap and grants; destination/egress/redirect and credential-use policy; lifecycle
API/UI and audit; concurrency/time budgets; migration/restore/conflict acceptance.
