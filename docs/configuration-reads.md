# Restricted configuration reads — contract 1.0

First local AGT2 implementation, not full fine-grained authorization for all tools.
The operator console at `/configuration`, REST and MCP share
`ConfigurationReadService` and `ConfigurationReadPolicy`. No model is needed.
No scopes or clients are granted by default. This channel rejects `local-lab` mode.

## Questions and evidence matrix

| Question | Kind / approved facts | Source and limit |
|---|---|---|
| Is this realm enabled? Can users register/reset passwords? | REALM: `enabled`, `registrationAllowed`, `resetPasswordAllowed` | One exact realm representation; only configured fields returned |
| Are brute-force protection and email verification configured? | REALM: `bruteForceProtected`, `verifyEmail` | Configuration only, not proof of effective enforcement or MFA use |
| Is this client enabled/public? Which listed flows are enabled? | CLIENT: `enabled`, `publicClient`, `standardFlowEnabled`, `implicitFlowEnabled`, `directAccessGrantsEnabled`, `serviceAccountsEnabled` | Exact internal client ID, inside a pinned realm; not a client search |
| What are its redirect URIs, password policy, effective permissions, users or login statistics? | Not implemented by this channel | Do not fall back to broad legacy tools for a restricted principal |
| Is this configuration valid, secure, compatible or CVE-free? | Not evaluated here | No finding, health verdict, rating or compatibility claim is generated |

Admin API collection uses a shared **cooperative 10-second collection budget**,
strict Boolean wire types, existing response/container limits and no arbitrary URL,
path, field selector or command. The realm's name is only a locator: its internal ID
must match the configured identity. Client reads verify the internal client ID and
re-read the realm identity after collection. A recreated realm/client fails closed.
This does not provide an atomic remote snapshot; do not deliberately reuse IDs.
The budget is not a hard wall-clock limit or SLA: already blocked DNS/TLS/I/O cannot
necessarily be cancelled immediately, and authorization/audit work is not a provider
transport deadline. Late evidence is rejected at the cooperative checkpoints.

`productVersion: UNKNOWN` is intentional: this scope does not authorize a server-info
query. No upstream/RHBK version compatibility is inferred. Missing Boolean values are
`null`, listed in `missingFields`, and produce `PARTIAL`; they are never `false`.
`COMPLETE` means only that every configured field was observed, not that the whole
realm, target or security posture was assessed. `collectedAt` is observation completion
time, not an upstream modification time or freshness SLA. Rerun to collect again.

## Administrator configuration and migration

Prerequisite: the existing authenticated OIDC deployment verifies token issuer,
signature, expiry and audience; see [identity model](identity-model.md). The new policy
requires a verified JWT principal with `iss`, `sub` and string `azp`. It accepts no
actor, role or client identity from tool arguments, HTTP impersonation headers or MCP
`clientInfo`. Exact roles and client IDs have no hierarchy/wildcard semantics.
A service-account token identifies a service principal, not a proven human or a
delegation chain. Human/client token exchange and per-user RHBK role mirroring remain
separate, unimplemented acceptance work.

Example only; replace every identifier using approved operator inventory. This file
does not enable these grants in application configuration:

```properties
platform.configuration-reads.scopes.demo-realm.role=configuration-realm-reader
platform.configuration-reads.scopes.demo-realm.target-id=registered-lab
platform.configuration-reads.scopes.demo-realm.realm=demo
platform.configuration-reads.scopes.demo-realm.realm-id=verified-internal-realm-id
platform.configuration-reads.scopes.demo-realm.kind=REALM
platform.configuration-reads.scopes.demo-realm.fields=registrationAllowed,verifyEmail
platform.configuration-reads.scopes.demo-realm.rest-client-ids=operator-console
# Omit mcp-client-ids unless disclosure to this exact MCP client is approved.
# platform.configuration-reads.scopes.demo-realm.mcp-client-ids=approved-mcp-client

platform.configuration-reads.scopes.demo-client.role=configuration-client-reader
platform.configuration-reads.scopes.demo-client.target-id=registered-lab
platform.configuration-reads.scopes.demo-client.realm=demo
platform.configuration-reads.scopes.demo-client.realm-id=verified-internal-realm-id
platform.configuration-reads.scopes.demo-client.kind=CLIENT
platform.configuration-reads.scopes.demo-client.client-id=verified-internal-client-id
platform.configuration-reads.scopes.demo-client.fields=enabled,publicClient
platform.configuration-reads.scopes.demo-client.rest-client-ids=operator-console
```

`client-id` above is the target resource's **internal immutable ID**, not its OAuth
`clientId`. `rest-client-ids`/`mcp-client-ids` identify incoming platform token `azp`,
not the outbound target credential. Either list may be omitted (no access on that
transport). A grant requires the exact role **and** approved client **and** channel.
Authorized handles can be listed even when the source is unavailable; that catalogue
contains configured permission metadata, not proof of resource existence or reachability.

At most 100 scopes, 20 clients per transport per scope, and the closed field sets above.
Scope IDs: `[a-z][a-z0-9-]{0,63}`. Target IDs: existing target alphabet, at most 128
characters. Realm/resource/role/client identifiers use `[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}`;
other naming conventions are unsupported in this first contract. Invalid definitions
fail policy initialization. Configuration is loaded at application start; changes need
restart and do not instantly revoke already issued tokens or copied results.

**Grants are additive, not a restriction ceiling.** A user with legacy target READ,
ASSESS or PLAN can retain broader rights through existing tools, reports and history.
Do not add a restricted role to a broadly privileged user and call it restricted.
Inventory all role grants; use dedicated restricted principals without target-wide
grants, review the target service credential independently and test negative access
before approving either client. Existing broad permissions are not migrated here.
No source API token is passed through from Identity A to Identity B.

The provider credential must be independently least-privileged for these exact source
reads on the installed product/version. The Admin API may return a broader in-memory
representation; only the configured Boolean projection leaves this service. No user
PII, free-form descriptions, URLs, secrets, provider bodies or realm/client IDs are
returned. Approval of an MCP client does not approve arbitrary external model
retention/disclosure: that client's provider policy and host integration still require
separate organizational approval. AGT1's report-only allowlist remains unchanged.

## API and observations

- `GET /api/v1/configuration-reads`: authorized descriptor array.
- `GET /api/v1/configuration-reads/{scopeId}`: fresh contract-1.0 observation.
- MCP `keycloak_list_configuration_scopes()` and `keycloak_read_configuration(scopeId)`.

Every read resolves the configured `targetId`; callers cannot redirect the scope to
another target/realm/client. Unknown and forbidden scope IDs yield the same fixed 403
before target resolution or provider I/O. Missing/malformed identity yields 401;
provider/identity/budget failures yield a generic 503 without provider details.
REST sets `Cache-Control: no-store` and `Vary: Authorization`, including scoped errors.

```json
{
  "schemaVersion": "1.0",
  "observationId": "11111111-1111-4111-8111-111111111111",
  "scope": {
    "scopeId": "demo-realm", "targetId": "registered-lab", "realm": "demo",
    "kind": "REALM", "fields": ["registrationAllowed", "verifyEmail"]
  },
  "collectedAt": "2026-09-19T12:00:00Z",
  "source": "KEYCLOAK_ADMIN_API", "productVersion": "UNKNOWN", "status": "PARTIAL",
  "facts": {"registrationAllowed": false, "verifyEmail": null},
  "missingFields": ["verifyEmail"]
}
```

Only equivalent role/resource/field/client/disclosure contexts produce equivalent
canonical facts. REST and MCP are separate fresh collections, not the same snapshot;
their timestamps and IDs intentionally differ. No server evidence history/cache,
assessment, export, SSE payload or multi-tool conversation is introduced. Restricted
principals do not acquire access to legacy indirect surfaces. The global event channel
may retain non-data heartbeat/hello events under existing policy.

The UI requests descriptors without Fleet/target overview permission, then requires
an explicit inspection. Scope/session changes clear observations; mismatched envelopes
and late replies cannot replace the selected scope. Invalid/inconsistent evidence is
an error, not a synthesized verdict. Browser-retained copies cannot be recalled.

Optional operational audit retains operation/channel, authorized target/scope, outcome,
duration, observation/correlation ID and SHA-256 principal/client fingerprints, not
raw claims or facts. Fingerprints are pseudonymous correlation, not anonymization or
proof of human presence. This audit honors `platform.audit.enabled`, survives METADATA
mode and uses the existing **best-effort** persister; it is not a mandatory durable
compliance ledger. Unauthenticated requests rejected before a validated caller rely
on the existing authentication boundary, not this attribution record.

## Acceptance boundary

The [initial synthetic evidence](development/agt2-configuration-reads-2026-09-19.md)
establishes local contracts. The subsequent
[two-operator lab](development/agt2-authenticated-operators-2026-09-19.md) exercises real
human code/PKCE, signed OIDC, Community Keycloak 26.7.1 source reads, separate approved
REST/MCP clients and the actual console. It does not establish RHBK/OpenShift,
external-model, actual resource-recreation or full-domain acceptance. AGT2 stays
partial. The subsequent [reference client 0.1.0](../dev/access-aware-client/README.md)
implements bounded orchestration and a local evaluation matrix, without altering
this server schema, grants or AGT1. Its synthetic tests are distinct from the
[subsequent two-operator client integration](development/agt2-client-live-2026-09-22.md),
which uses a fixed local authenticated host and no AI. Neither establishes a general
production host, IdP logout, immediate revocation, RHBK/OpenShift or model acceptance.
Consolidated release review/operator reproduction and separately approved provider
disclosure/model evaluation remain next gates.
