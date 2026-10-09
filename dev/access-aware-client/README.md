# Access-aware reference client — contract 0.1.0

**Offline prototype**, separate from AGT1's unchanged report profile **0.2.1**.
This is a provider-neutral, dependency-free Node 24 client library with an executable
synthetic CLI. It implements bounded orchestration and evidence validation; it does
**not** implement OAuth login, HTTP/JSON-RPC/SSE transport, a model SDK, natural-language
understanding or a production-ready chat application. No credentials, endpoints,
network calls, model calls or persisted conversations are included.

Its only tools are `keycloak_list_configuration_scopes` and
`keycloak_read_configuration`. The [server contract 1.0](../../docs/configuration-reads.md)
is authoritative for configured grants and facts. Backend authorization is mandatory
on every call; the profile and host checks are defense in depth, not a replacement.

## Run without an environment or AI

```sh
node --test dev/access-aware-client/*.test.mjs
node dev/access-aware-client/cli.mjs demo rhea
node dev/access-aware-client/cli.mjs demo rhea synthetic-realm
node dev/access-aware-client/cli.mjs demo chris synthetic-client
node dev/access-aware-client/cli.mjs demo partial synthetic-realm
node dev/access-aware-client/cli.mjs demo empty
```

The first command with Rhea lists approved synthetic handles and requests selection;
it does not automatically read even a single available scope. Chris cannot inspect
`synthetic-realm`, nor Rhea `synthetic-client`. Every output is marked
`SYNTHETIC_OFFLINE_ONLY`: these fixtures are not real authentication or an assessment.
No container, volume or image is needed for the synthetic CLI. It rejects arbitrary files, URLs,
tokens, commands and provider arguments and does not echo invalid input.

## Versioned API and host obligations

### Optional authenticated local host (separate test harness)

[configuration-client-lab.mjs](../../scripts/configuration-client-lab.mjs) now adapts
this unchanged 0.1.0 library to the existing authenticated Community laboratory.
It is run by `bash scripts/validate-configuration-lab.sh` after the documented build
and explicit local Podman selection; see the [lab runbook](../identity-lab/README.md).
The harness uses real human code/PKCE tokens, one separate MCP session/context per
operator, fixed loopback `http://localhost:18081/mcp`, bounded finite JSON/SSE and
strict response-ID/protocol/session matching. The new adapter exposes only the two
profile tools; the pre-existing checker separately probes legacy tools for denial.
No arbitrary endpoints, token forwarding to targets, model or credential store.

The host supports the lab's negotiated `2025-11-25` revision, initialized notification
and explicit session DELETE, but not general OAuth discovery, refresh, TLS deployment,
SSE resumption/retries or arbitrary server-initiated requests. Unsupported shapes
fail closed. Failed handshakes attempt cleanup of any validated session ID; DELETE
failure remains a failed validation, not claimed cleanup. Caller cancellation reaches
HTTP/body reads through the existing bounded lab transport. Aborting local I/O does
not prove remote cancellation or rollback of work already dispatched.

This closes the **bounded local adapter exercise**, not production client acceptance.
Local host sign-out closes MCP and invalidates displayed/queued ownership; it is not
IdP logout or immediate JWT revocation. Controlled delay cases hold a response after
the real server replied; they are not measured upstream slowness. Details and counts
are in the [authenticated-client ledger](../../docs/development/agt2-client-live-2026-09-22.md).
The profile descriptor stays a prototype; no published package or release is implied.

### Portable library contract

`profile.json` fixes profile/answer version **0.1.0**, observation version **1.0**,
allowed tools and budgets. `contract.mjs` is its executable closed-shape validator;
`client.mjs` implements `createConfigurationClient`, `inspect`, `invalidate`,
`isCurrent` and `close`. This descriptor is not a generic MCP client configuration.
Unsupported versions/shapes fail; there is no coercion, migration or fallback to AGT1.

```js
import { createConfigurationClient } from './dev/access-aware-client/client.mjs';

// Supplied by a separately reviewed authenticated host, NOT a model/tool argument.
let context = Symbol('session generation');
const client = createConfigurationClient({
  getContext: () => context,
  callTool: authenticatedBoundedCallTool,
});
const choices = await client.inspect({ scopeIds: [] });
// Operator explicitly selects exact handles from choices.scopes.
const answer = await client.inspect({ scopeIds: ['approved-handle'] });
if (client.isCurrent(answer)) renderAsData(answer);

// On logout or any actor/client/target-policy/provider/disclosure change:
context = null;       // Or a NEW Symbol after the new session is established.
client.invalidate();  // Abort pending work AND clear visible host content.
clearVisibleContent();
client.close();       // Permanent local shutdown when the host is disposed.
```

The example's host, rendering and login functions are integration points, not shipped
implementations. Use a fresh non-reused Symbol for every context generation (including
A → B → A); never use JWTs, usernames or resource IDs as this token. A Symbol is local
ownership metadata, **not identity authentication or a delegation proof**.

`callTool({name, arguments}, {signal, context})` must use an already approved session
bound to that exact context; a mutable global "current token" is insufficient. It
returns a UTF-8 JSON **string of the MCP CallToolResult**, not a decoded object, REST
body or full JSON-RPC response. The host must authenticate the endpoint and human/
client, enforce TLS/redirect/audience/session/request-ID checks, cap transport bytes
before buffering/parsing, handle the protocol and observe cancellation. None of
these network/authentication protections are supplied by this library.

The host may map verified authentication/access/unavailability failures to
`ClientError('UNAUTHENTICATED' | 'NOT_AUTHORIZED' | 'SOURCE_UNAVAILABLE')`. Generic
throws and MCP `isError` yield fixed `TOOL_FAILED`; raw error messages/causes never
enter an answer. Do not classify source failures by matching arbitrary error prose.
Input is the exact data shape `{scopeIds: string[]}`, not user prose or a raw tool
request; executable JavaScript and the two host callbacks are trusted code.

## Orchestration and evidence contract

Every question fetches a fresh authorized catalogue. Empty selection returns
`CLARIFICATION_REQUIRED`, or `NO_AUTHORIZED_SCOPES` for an empty catalogue; neither
means that the environment is empty, unavailable or healthy. Selection requires
1–3 distinct exact scope IDs, with no fuzzy names or auto-select-all. If any selected
ID is absent, the whole question fails identically for unknown/forbidden handles,
before resource reads. The server reauthorizes each selected read. No retries,
pagination, raw API, infrastructure collector or broader legacy-tool fallback exists.

The observed Quarkus catalogue representation is one JSON TextContent per descriptor;
an array in one block or catalogue `structuredContent` is unsupported. Observations
may use one JSON TextContent, `structuredContent`, or both only when structurally
equal. Unknown envelope/content fields, annotations, resource links, non-text blocks,
continuation cursors, duplicate JSON keys and nesting beyond 12 are rejected.
This narrow codec is not a universal MCP compatibility claim.

Scope descriptors must exactly identify target/realm/kind and the sorted permitted
Boolean fields. Each observation must match its descriptor, carry schema 1.0 and a
canonical v4 UUID, UTC completion timestamp, `KEYCLOAK_ADMIN_API` and product version
`UNKNOWN`. Facts must contain exactly the configured Boolean/null fields. Nulls must
match `missingFields` and PARTIAL; COMPLETE means field coverage, never security or
policy correctness. Duplicate observation IDs across reads fail. These structural
checks do not prove authenticity; that remains the host/server boundary.

Successful answer 0.1.0 contains exactly `schemaVersion`, `profileId`, `profileVersion`,
`mode`, `state`, `scopes`, `observations`, `references`, `usage`, `limitations`.
Mode is always `DETERMINISTIC_NO_AI`. State is one of the two catalogue states or
`OBSERVED`. References are `{observationId, scopeId, pointer, value}`; `pointer` addresses
an exact `/facts/<field>` in that observation. They are **local references**, not URLs
or server retrieval handles. Follow-ups must select/recollect currently authorized
scope IDs; the client never dereferences prior UUIDs or imports old answer objects.
Multiple reads are independent observations, not an atomic cross-resource snapshot.
Any failed read rejects the entire question; already collected facts are not returned
as a misleading partial success. A successful PARTIAL observation remains legitimate.

## Budgets and invalidation

At most 100 catalogue descriptors, three selected scopes, four calls total, one
question/callback at a time per instance; no retries/pages. The default question
deadline is ten seconds across all calls and parsing; a trusted host may only lower
it. Each response is limited to 64 KiB UTF-8 and cumulative replies to 128 KiB;
serialized answers are capped at 64 KiB. Evidence older than 120 seconds or more
than five seconds in the future is rejected, including at combined-answer emission.
These are this profile's conservative admission limits, **not** product freshness
SLAs or upstream modification times; synchronized host/server clocks are required.

Abort bounds asynchronous waiting and rejects late replies; it cannot interrupt
synchronous blocking code, recall previously disclosed facts or undo dispatched server
work. If a transport ignores abort, the instance remains BUSY until that callback
settles; it never overlaps another request to hide uncertain work. The host must
supervise transport termination across instances and handle a permanently stuck
adapter explicitly, not blindly recreate clients/retry. An unobserved token/policy
change cannot be detected by a local Symbol; the host must invalidate proactively.

Answers are immutable; the client retains only weak ownership stamps, no fact/history
cache. `isCurrent` checks that an answer is the latest issued question, its context is
still active and its observations remain locally fresh. Hosts must clear already
rendered content on invalidation and check freshness before reusing it. The library
cannot erase host copies, browser history, logs or provider retention. No model
disclosure is enabled: reading data never approves sending it to a provider.

## Offline acceptance matrix / next gate

| Contract boundary | Automated local evidence | Still not established |
|---|---|---|
| Two operator scopes | Rhea/Chris fixtures; separately, the fixed local host exercises actual OIDC/MCP | General authenticated client deployment and independent operator reproduction |
| Facts/provenance | Exact fields/identities, true/false/null, resolvable references, unknown version | All IAM/infra domains, policy/health/score interpretation |
| Untrusted responses | Closed shapes, duplicate keys, malicious metadata, contradiction, UTF-8/depth/byte bounds | Universal PII/secret detection or arbitrary MCP servers |
| Orchestration | Explicit clarification, fixed tool/argument set, sequential budget, no retries/pages | Natural-language planning or LLM behavior |
| Session/lifecycle | Pre-dispatch/read invalidation, late success/error, stuck transport, expiry, immutable output; local MCP close | IdP logout/revocation/delegation and independent host UI review |
| Offline usability | Five CLI scenarios and invalid-input cases; retained AGT1 regression suite | RHBK/OpenShift/client/model compatibility or production release |

[Validation ledger](../../docs/development/agt2-client-2026-09-21.md) records executed
counts and source identity; the [successor](../../docs/development/agt2-client-live-2026-09-22.md)
records the real local host separately, **without AI**. Next: review the consolidated
experimental-release scope and operator reproducibility. External client/provider
disclosure and repeated model evaluation remain separately approved gates.
AGT2 remains partial; the operator console remains independent and unchanged.
