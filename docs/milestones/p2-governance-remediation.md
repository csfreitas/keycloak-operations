# P2 — Governance and durable controlled remediation

Status: **PLANNED / PRODUCTION WRITES NOT ACCEPTED**. Indicative planning: Q1/Q2 2027. Owner: backend developer + security reviewer + operations owner.

## Objective and dependencies

Resolve crash/uncertain outcomes and human-approval boundaries before expanding administration. Depends on H1 and persisted evidence/audit contracts; D4 feedback sets actual priority. Existing plan/approve/apply/verify is containment, not exactly-once remote execution.

Roadmap: GOV-01, CHG-01; deferred 0.8.1 realm Slice 4 and 0.8.3–0.8.6. Requirements: FR-CHANGE-001–016, FR-GOV-001, FR-REALM-001/002, SEC-CHANGE-001–005, SEC-REALM-001, NFR-CHANGE-001, COMPAT-007/008. Design: [controlled administration](../architecture/controlled-administration.md).

## Ordered sub-slices

1. Define durable execution attempts, deadlines/expiry, UNKNOWN outcome and reconciliation before retry; preserve audit when remote success precedes local persistence failure.
2. Coordinate different plans for the same resource and account for external writers; validate credential/policy/target revisions and meaningful idempotency boundaries.
3. Define authenticated human/two-person approval where required, revocation and realm/resource ACL needs; a distinct permission or checkbox alone is not human proof.
4. After safety acceptance, finalize the typed realm allowlist and implement Slice 4. Users/groups/roles, flows/scopes and IdPs require demand-driven separate bounded sub-slices, not a generic admin proxy.

Fine-grained **read** scope and field/PII controls are brought forward as an explicit
[AGT2 prerequisite](agt2-access-aware-assistance.md) for both operator console and
MCP, rather than waiting for P2 writes. P2 reuses that accepted foundation and still
owns privileged change approval, durable execution and recovery. Neither is implemented
by this scheduling/design clarification; no grant is silently widened.

## Exit criteria

### Demand-driven user slices (after safety gates)

[HLP-01 and SECOPS-02](../development/secops-iam-scenarios.md) record temporary
consultant access and separately approved user containment (FR-GOV-002/003).
They are future typed user workflows, not extensions silently inherited from client
or realm operations. The synthetic catalogue is prepared; no new tool or mutation
is implemented/accepted. P2 remains PLANNED; dates and presentation priority unchanged.

- [ ] HLP-01 proves minimal effective privileges, identity conflict handling,
  durable expiration/restart/lateness and separate create/grant/expiry recovery.
- [ ] SECOPS-02 proves protected-identity policy, distinct human approval and
  read-back of only the authorized operation, without claiming universal token revocation.
- [ ] Both pass the shared durable-attempt, concurrency, drift, expiry, audit and
  uncertain-outcome gates below for each supported distribution/version.

### Shared safety acceptance

- [ ] Crash/timeout before and after remote success, concurrent plans, external drift and restart recovery tests produce no blind retry or false verified outcome.
- [ ] Expired/modified/wrong-actor approvals fail; human approval policy is actually enforced and reviewed.
- [ ] Audit/history/authorization and populated migrations survive recovery; secrets are never persisted or logged.
- [ ] Each newly supported mutation has risk/policy, drift, read-back and actual product/version tests, including denied weakening cases.

No production execution, generic delete, raw Admin REST or automatic rollback is authorized by this plan. Each external write still needs scoped approval. Broad administration must not delay the read-only presentation gates.
