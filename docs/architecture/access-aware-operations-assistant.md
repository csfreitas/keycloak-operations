# Access-aware operational assistance and operator console

Status: **PARTIAL — server subset and reference client 0.1.0 exercised in the authenticated local lab**. Updated 2026-09-22.
The operator application remains a first-class product. An optional MCP client/model
adds question-driven assistance across the same environment; it does not replace
the console, assessments, health checks or deterministic reports. See
[ADR 0011](../adr/0011-access-aware-dual-interface.md) and
[AGT2](../milestones/agt2-access-aware-assistance.md).

The first [contract 1.0](../configuration-reads.md) implements exact configured
realm/client Boolean reads through one service, REST, two MCP tools and the independent
`/configuration` console route. Role + verified JWT client + channel grants are empty
by default; realm/client identities and fields are pinned. This does not replace
legacy target-wide authorization, implement a multi-tool agent or enable any model.
See the [local evidence and remaining gates](../development/agt2-configuration-reads-2026-09-19.md).

The subsequent [authenticated local validation](../development/agt2-authenticated-operators-2026-09-19.md)
exercises two restricted human principals in Community Keycloak 26.7.1, separate
console/MCP clients, exact source reads and the real no-AI console. Identity B has
only fixture `view-realm`/`view-clients` assignments; application field projection
remains the finer boundary. This validates a bounded local subset, not RHBK/OpenShift,
delegation, a multi-tool model, instantaneous revocation or actual resource recreation.

## Product contract

### Implemented local host boundary, 0.1.0

The separate [access-aware reference client](../../dev/access-aware-client/README.md)
now composes the two configuration tools, without a model or server changes. It is a
Node library plus synthetic CLI, not a new service or an implemented OAuth/MCP network
transport. A trusted host injects the authenticated, byte-bounded `callTool` callback
and an opaque non-reused session-generation Symbol. Resource selection accepts only
exact authorized scope handles; endpoint, credential, field and role selection are
not question arguments. The backend still authenticates and authorizes every call.

The client fetches a fresh catalogue per question, asks for explicit selection,
validates schema-1.0 observations and returns immutable answer-0.1.0 facts with
observation-local pointers. Four calls/three scopes, sequential execution, ten-second
shared asynchronous deadline and byte/freshness limits bound collection. Unknown,
denied, malformed, stale and unavailable responses are not favorable defaults.
No history cache, prior-UUID lookup, report join, score, source-version inference,
provider disclosure or additional domain access is introduced.

The host must rotate context and clear visible data on identity/client/policy/target/
provider/disclosure changes; the client aborts/rejects stale work and offers
`isCurrent` before presentation. Non-cooperative transport keeps the instance BUSY
until completion rather than allowing overlapping calls. These controls cannot
detect unreported policy changes, enforce remote termination, revoke JWTs instantly
or erase previously copied facts. TLS/OIDC/session binding, network deadlines and
cross-instance supervision remain the reviewed host's responsibility. The
[offline ledger](../development/agt2-client-2026-09-21.md) is not evidence of those
unimplemented general integrations. The subsequent local adapter exercise below is
separate evidence, not a relabeling of those synthetic results.

### Authenticated local host exercise

The [lab-only adapter](../../scripts/configuration-client-lab.mjs) now connects the
unchanged client to the two-human Community lab. The existing code/PKCE login and
bounded allowlisted transport remain outside the model/library. One captured bearer,
MCP session and non-reused context generation belong to each host instance. Exact
protocol/session/request-ID checks and finite JSON/SSE decoding precede the client
observation validator; only fixed scoped tools can leave that adapter. The transport
now composes caller cancellation with its own deadline across headers/body reads.

Each host closes its MCP session and local context explicitly; uncertain DELETE fails
validation. This is not IdP logout, immediate JWT revocation or proof of remote
cancellation. Held real replies exercise invalidation/timeouts through an explicitly
injected delivery delay, without changing backend/source facts. The
[live ledger](../development/agt2-client-live-2026-09-22.md) identifies actual product,
client scope, repeatability and cleanup. No general OAuth discovery, refresh, TLS
deployment, arbitrary endpoints, model SDK, source mutation or new backend permission
is implemented. The portable core/profile and operator console are unchanged.

### Target product behavior

An operator can inspect infrastructure and IAM configuration directly in the console
or ask a compatible authorized client questions about those domains. The platform
supplies only evidence permitted for that principal, operation, resource and field.
The model can select semantic read capabilities, correlate their outputs and explain
observations; it cannot grant itself access, assign health/risk/score or invent
facts about an environment it did not observe. No specialist agent is mandatory:
the reference profile is optional, and each client/authentication combination needs
its own compatibility and disclosure validation.

"All environment topics" means an extensible capability catalogue, not unrestricted
Admin REST, raw data exports, every product API or a guarantee that every question
can currently be answered. The response must identify unsupported, inaccessible,
stale, ambiguous or uncollected evidence without filling the gap from model memory.
Read access is not permission to modify the environment or send its data externally.

## Two interfaces, one operational core

```text
Authenticated operator
  ├─ Operator console → REST ───────────────┐
  └─ Approved MCP client/model → MCP ──────┤
                                          ▼
                       Shared application services and authorization
                              ↓                  ↓
                    Bounded evidence reads   Deterministic assessments
                              └────────┬─────────┘
                                       ▼
                      Authorized facts, findings and provenance
                        ├─ Console/detail/report
                        └─ Optional grounded explanation
```

Retain the modular monolith and existing adapters; no new microservice, mandatory
vector database or server-side model SDK is required. User questions are orchestrated
in the approved host/client; typed services own resource resolution, collection,
authorization, assessment and projection. A future embedded chat may reuse this
contract but is not a replacement for normal UI workflows or a prerequisite here.

The console continues Fleet/target selection, health, assessment, inventory,
performance, finding detail and current report workflows without a model. New realm,
client, policy and user views are incremental UI work, not implied to exist because
an MCP read exists. History/replay, rating 1–100 and new IAM analytics retain their
own milestone gates. Same principal/scope/observation must yield the same canonical
facts and deterministic findings through UI, REST and MCP. Equivalent scope includes
client/delegation, data-class and disclosure context: permission to view PII in the
console does not permit sending it to the model, so the AI projection can be smaller.
Parity means the same policy under equivalent context, not copying every UI field
into a prompt. Presentation differs; permissions and truth do not. Fresh collections may legitimately have different
timestamps/results and must not be presented as the same observation.

## Observed starting point and gaps

| Domain / example question | Existing foundation | Remaining acceptance |
|---|---|---|
| Infrastructure: "How many replicas are ready, and what evidence indicates an HA risk?" | Inventory, health, assessment, semantic metrics tools | Approved installation/source coverage; D2 for real OpenShift, PORT1/PORT2 for container/host collectors; scoped outputs |
| Realm/policy: "What password policy and brute-force settings are configured?" | RealmDetails exposes selected policy/configuration fields | Effective flow semantics, capability/version and fine-grained scope; configuration alone is not policy enforcement proof |
| Client: "Which redirect URIs and flows does my application use?" | Typed ClientDetails and existing read tools | Per-client authorization/field projection; new derived rules only after acceptance |
| Users/groups/roles: "What can I inspect about users in my realm?" | Search/detail and group/role reads; UserDetails includes names/email | Realm/resource/PII permissions, effective membership/role relationships and bounded pagination |
| Security/use: "Why did logins fail or MFA adoption fall?" | Runtime metrics and selected configuration are available | P1 approved event/analytics sources; do not infer actual MFA use or login causality from configuration |
| Assessment/report: "Why this finding or rating?" | Deterministic findings, current report and AGT1 explanation contract | D3E/D3R retained evidence/documents; SCORE1 future rubric; no recomputation from truncated findings |

[TargetAuthorizationService](../../src/main/java/io/github/keycloakmcp/target/TargetAuthorizationService.java)
and [PlatformAuthorizationConfig](../../src/main/java/io/github/keycloakmcp/config/PlatformAuthorizationConfig.java)
currently enforce exact role × target × operation grants. They do not implement
realm/client/user/group/field ACLs. Ordinary user/realm/client services require target
READ and then use the target's service credential. Therefore today's target grant is
not equivalent to the human's personal permissions in the target Keycloak.

The [reference profile 0.2.1](../../dev/reference-agent/README.md) exposes **one report
tool**, not every read tool listed in [tools](../tools.md). Its fixed fact contract
does not accept arbitrary multi-tool responses. AGT2 must add separately versioned
contracts/evaluations without widening or relabeling AGT1 silently. Existing metadata
redaction is not a personal-data access policy; selected DTOs contain real identity
fields. Do not expose the full present tool set to restricted users as a shortcut.

## Effective access, not an administrator agent for everyone

Use authenticated platform Identity A for the human and validated client identity or
delegation context for the host. Never trust a model-supplied username, role, "approved"
flag or HTTP impersonation header. A shared privileged service login cannot be used
to claim per-human authorization. Until trusted human binding is accepted, identify
service-principal operations as such or deny the requested per-user mode.

The proposed maximum answerable scope is the intersection of:

1. the human's explicit platform grants and organization policy;
2. the approved client's delegated/tool and disclosure scope;
3. registered target/installation, realm and resource constraints;
4. operation and field/data-class permissions (including PII);
5. source capability/version and the least-privilege outbound identity's actual access.

Infrastructure restrictions include approved installation/namespace and object
ownership/identity where applicable; IAM realm rules do not substitute for them.
Neither a cluster credential nor a logical target can silently widen that scope.

Absence of a grant denies access. No role-name hierarchy implies all permissions.
Illustrative personas are policy examples, not new default roles: an infrastructure
operator can view workload/metrics without identity details; an application owner can
read selected clients in one realm without other clients; an IAM auditor can inspect
approved policy/population evidence without write rights. Administrative setup rights
do not automatically grant user PII, external disclosure or remediation rights.

Keep Identity B (server-to-target credentials) secret and separate from Identity A.
If an organization needs to mirror existing personal RHBK permissions, that requires
an explicit, reviewed mapping/delegation design and product/version tests, not automatic
role copying or forwarding the incoming bearer token. The pinned official
[MCP authorization specification](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization)
requires audience-bound tokens and forbids token passthrough. This source informs
the trust boundary; it does not certify current server/client protocol conformance.

Fine-grained read authorization is an **AGT2 prerequisite**, brought forward from the
broader P2 governance backlog. It must apply to REST/UI/MCP, direct IDs, lists/search,
aggregates, reports, history, exports, SSE and drill-downs. Hiding a tool/tab is only
presentation, never enforcement. Define grant migration explicitly: legacy target
READ must not silently become a restricted-realm grant or pretend to enforce one.
New restricted mode defaults closed; inventory and approve legacy full-target readers
before exposing richer capabilities. No automatic legacy grant/configuration migration
occurs. The additive restricted channel does not narrow existing READ/ASSESS/PLAN
rights; a restricted persona must not retain those broad grants. Other domains remain planned.

## Question-to-evidence flow

Target flow below includes planned model/orchestration domains. The implemented
0.1.0 subset uses explicit typed selections and deterministic facts only, as described
above; it does not implement general questions, model planning or a new audit store.

1. Authenticate and establish current principal/client policy context. Show only
   authorized targets/capabilities; clarify ambiguous realm/client names and windows
   from authorized candidates. Do not guess a target from a similar name.
   Capability discovery here means reading the authorized tool/domain catalogue,
   not installation discovery or a grant of the separate DISCOVER permission.
2. Separate general product explanation from questions about observed environment
   state. Any documentation retrieval uses approved, version-specific sources without
   putting customer identifiers/configuration into public searches. Generic knowledge
   must never masquerade as collected evidence.
3. The model proposes a bounded sequence of typed reads; a trusted host validates the
   allowlist, arguments, selected scope and per-question call/time/byte/page/concurrency
   budgets. Backend services reauthorize every call. Model-generated URLs, shell,
   SQL, raw Admin paths, unrestricted filters/PromQL or provider selection are denied.
4. Collect only needed data through authorized adapters. Revalidate source scope and
   resource ownership; if a provider cannot filter safely, perform bounded trusted
   selection/projection or deny the capability. No full realm/user dump to the model.
5. Return versioned answer evidence: authorized target/resource handles, observation
   IDs/timestamps, product/version/capability, typed facts, permitted denominators,
   omissions/freshness and safe availability reasons. Masked display IDs are not stable
   follow-up authority; add scoped opaque handles where identity cannot safely be exposed.
6. Explain using those references, distinguish fact/hypothesis/recommendation, and
   state limits. Deterministic services decide policy findings and scores. Follow-up
   reads repeat authorization and preserve or explicitly change the collection window.
7. Audit principal/client, operation/scope, policy revision, decisions, evidence
   references, timestamps and bounds. Do not log raw questions/responses, PII, tokens
   or raw source payloads by default; audit access/retention are themselves restricted.

This read-only assistant exposes no PLAN/APPROVE/WRITE/ADMIN/DISCOVER/BIND. Asking
"fix it" cannot add a tool. P2 changes remain a separate authorized workflow. Reads
may still write internal audit, snapshots or assessments; uncertain report/assessment
execution must not cause blind repeated collection or duplicate side effects.

## Leakage, aggregation and conversation boundaries

Authorize before collection and again at protected output/history/export boundaries.
Field projections apply before AI context construction, not after the model receives
data. A full-target report may already contain denied realm/user information: do not
send it and ask the model to ignore sections. Require full report-scope permission
or introduce a separately defined scoped assessment with an explicit population and
coverage; removing findings cannot preserve a claimed full-target score or completeness.

Counts, denominators, severity summaries, search suggestions and pagination can leak
hidden resources. Compute only authorized aggregates; suppress totals that cannot be
released safely and label the visible scope without enumerating excluded resources.
Safe denied/not-found behavior must not reveal forbidden names or existence. Unknown
data is not zero, N/A, PASS or healthy. Error taxonomy and diagnostic detail require
review across transports, including the current resolver ordering.

Partition any answer cache, retained report, retrieval index and conversation by
principal/client, target/resource scope, policy revision and disclosure context.
Reauthorize saved references and context on follow-up, export or replay. Identity,
target, privilege or provider change must discard inaccessible cached/queued results
and stop their reuse; concurrency and late-response tests must cover this. Revocation
cannot erase data already delivered to an external client/model. Document token,
session, retention and deletion limits instead of promising retroactive forgetting.

Separate access permission from permission to disclose to an approved provider/model.
Credentials never enter prompts. PII is minimized/aggregated by default and only
released with explicit field and disclosure authority. Local models are not an
automatic policy bypass. Provider retention/training policy and client context handling
are deployment choices requiring validation; no model provider is enabled here.

## Acceptance and incremental delivery

AGT2 is partial, with no new deadline or change to the read-only presentation.
The first question/source/field matrix and concrete realm/client read slice are in
[configuration reads](../configuration-reads.md). Local two-operator UI/REST/MCP
authorization is recorded above. The separate 0.1.0 host/evaluation contract is now
implemented and exercised through the bounded authenticated local adapter. Consolidated
review and separately authorized provider/model validation precede any model-context
expansion. Add users/PII, policies/effective relationships,
infrastructure/performance and P1 analytics incrementally when source contracts exist.
The [milestone](../milestones/agt2-access-aware-assistance.md) specifies paired persona,
cross-surface, direct-ID, aggregate, stale-context and model-evaluation gates.

Alternatives rejected: keeping a report-only assistant as the complete product;
giving every client a shared admin credential; trusting prompt/UI filtering; loading
whole environments into a model/vector index by default; replacing the console with
chat. A policy-engine microservice or embedded chat remains optional future work,
justified only by a demonstrated operational requirement.
