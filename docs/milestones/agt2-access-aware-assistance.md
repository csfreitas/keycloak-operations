# AGT2 — Access-aware operational assistance and console parity

Status: **PARTIAL / SERVER AND CLIENT 0.1.0 EXERCISED IN THE AUTHENTICATED LOCAL LAB**. Owner: platform/agent integration developer +
UI developer + IAM/security reviewer + operator. No new date or presentation promise.

## Objective and dependencies

Answer operator questions about permitted infrastructure, realm/client/user/group/
role/policy evidence while retaining the operator application as a full non-AI
assessment/health/report interface. Extend beyond the AGT1 report-only profile without
widening its current grants or claiming unsupported data coverage. Depends on H1/D1
identity/evidence, explicit fine-grained read controls and typed domain sources.
Actual RHBK/OpenShift claims depend on D2; portable sources on PORT1/PORT2; identity
analytics on P1; retained replay on D3E. It does not wait for or authorize P2 writes.

Roadmap: AGT-03/04, GOV-01, existing UI and domain tracks.
Requirements: FR-AGENT-002/003, FR-UI-004, SEC-AUTHZ-002, SEC-AI-003,
FR-AI-001–003, FR-AGENT-001, SEC-REPORT-001, SEC-MULTI-001.
Design: [access-aware assistance](../architecture/access-aware-operations-assistant.md)
and [ADR 0011](../adr/0011-access-aware-dual-interface.md).

## Ordered implementation slices

Latest validation: [authenticated client exercise](../development/agt2-client-live-2026-09-22.md)
adds a fixed-loopback host around the unchanged client with actual Community OIDC/MCP
and two restricted humans. **72 checks pass twice**, including 17 client checks;
37 adapter tests and 534 combined regressions pass. This closes only the bounded
local host integration part of slice 4. Local/MCP close is not IdP revocation; real
replies are held by the host for controlled delay checks. No model, new product
version/grant, production transport or broader-domain acceptance. Whole exit criteria
remain open; next is consolidated release/H1 review and independent reproduction.

Previous implementation: [reference client/answer 0.1.0](../../dev/access-aware-client/README.md)
implements a bounded part of slice 4: exactly two configuration tools, fresh catalogue,
explicit selection, call/time/byte/concurrency budgets, strict observation references
and context invalidation. Synthetic two-persona tests and CLI require no model or
runtime environment. [Evidence](../development/agt2-client-2026-09-21.md) records
99 client tests and 497 combined regressions. The subsequent bounded real-host gate
is recorded above; none of the eight whole exit criteria closes. Profile
AGT1 0.2.1 stays untouched, and the operator console is not replaced or modified.

Previous server delivery: [restricted configuration contract 1.0](../configuration-reads.md)
and [local evidence](../development/agt2-configuration-reads-2026-09-19.md) cover a
bounded part of slices 1–3: realm/client Boolean facts, exact resource/field/role/client
grants, shared REST/MCP service and `/configuration` UI. No scopes enabled by default.
Legacy grants remain additive and broad; no scoped assessment/history or global ACL
migration. The subsequent [two-operator local run](../development/agt2-authenticated-operators-2026-09-19.md)
adds actual Community Keycloak 26.7.1, signed human code/PKCE, console inspection,
separate client/channel approvals and 55 REST/MCP/provider checks. Stale configured
pins are not actual resource recreation. No RHBK/OpenShift or model acceptance; the
eight milestone exit criteria below remain open as whole criteria.

1. **Question/capability contract:** catalogue concrete questions, approved typed
   sources, facts versus deterministic evaluations, required scope/fields, version,
   freshness and unsupported states. Include simple realm/client questions that do
   not require a complete assessment. Current tools alone are not acceptance.
2. **Read authorization foundation:** validated human/client binding; target → realm
   → resource → operation/data-class restrictions; grant migration and provider
   scope; parity across REST/MCP/services/history/exports/SSE. Bring these read
   prerequisites forward from P2 without bringing writes into scope.
3. **First end-to-end slice:** one realm/client question and matching operator-console
   inspection, using the same typed service and a versioned evidence envelope.
   Two principals with different grants; deny direct-ID/field/aggregate bypasses.
4. **Bounded multi-tool profile:** separately versioned host contract, capability
   catalogue discovery (not installation DISCOVER), clarification, tool/argument budgets, per-call authorization,
   provenance and scoped context invalidation. Keep AGT1 0.2.1 intact.
5. **Incremental domain coverage:** users/groups/effective permissions and PII gates,
   policies/flows, infrastructure/performance, then approved P1 sources. Extend
   corresponding console views and evidence navigation; no model needed to evaluate.
6. **Evaluation and operator acceptance:** approved client/model/data scope, repeated
   adversarial questions and paired UI/MCP inspection; record scope, counts, source
   versions, raw outcomes, limitations and independent operator review.

## Exit criteria

- [ ] Capability matrix distinguishes implemented/observed, denied, unsupported,
  stale and uncollected sources; unknown version is not inherited compatibility.
- [ ] Same human/scope, including client/delegation/data-class/disclosure context,
  has equivalent canonical facts and findings in UI/REST/MCP; PII visible in the
  console is withheld from AI when its disclosure is not permitted;
  core assessments/health/report workflows remain usable with the model disabled.
- [ ] Infra-only, realm-limited, client-limited and PII-limited personas cannot
  retrieve forbidden fields/resources through direct IDs, lists/search, counts,
  nested relationships, assessment/report summaries, history, export or SSE.
- [ ] Identity/delegation is backend-validated; model actor/role text and shared
  privileged credentials cannot impersonate a human. Provider grants cannot widen A.
- [ ] Full-target results cannot leak through scoped answers; scoped assessments
  have explicit populations and coverage, not filtered full-target score claims.
- [ ] Question-level budgets, ambiguity clarification, malicious metadata, denied
  operations, provider errors and partial pagination have measured tests; follow-up
  references are real and authorized, not fabricated or masked-name lookups.
- [ ] Privilege/identity/target/provider changes and late responses invalidate
  inaccessible context; caches/history are reauthorized, with residual disclosure
  and session/token limits documented instead of retroactive-revocation claims.
- [ ] Repeated model tests preserve facts/unknowns and safe refusals; references
  work for the authorized operator; no-AI fallback and independent console workflow
  pass. Each actual product/client/model combination is recorded, not generalized.

## Exclusions and acceptance boundary

No unrestricted source access, model-generated commands/endpoints, automatic external
disclosure, production writes, generic privileged agent or universal "answer anything"
guarantee. Optional embedded chat is not required to retain the console. Adding a
design document does not implement permissions, endpoint discovery, provider adapters,
UI screens or an MCP authorization-protocol revision by itself. The first implemented
subset is explicitly listed above; no universal domain/PII policy is implied. AGT1 acceptance and the current
H1/D1/D2/D3/D4/D5 dates remain unchanged; each AGT2 slice requires its own evidence.
