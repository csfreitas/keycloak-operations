# ADR 0007 — Plan → Approve → Apply change model

- **Status:** Accepted
- **Date:** 2026-08 (milestone 0.8)

## Context

The platform is introducing administrative writes against Keycloak/RHBK.
Allowing AI/MCP clients to execute direct Admin REST mutations creates
unacceptable risks around authorization, unintended changes, auditability,
stale state, and secret exposure.

Raw paths, arbitrary HTTP methods, and arbitrary JSON mutations must remain
forbidden (see ADR 0004). Assessments already proved that deterministic
backend decisions must not be delegated to the LLM (ADR 0003).

## Decision

All mutable Keycloak operations follow a controlled lifecycle:

```text
Request → Plan → Diff → Risk → Policy → Approval → Apply → Verify → Audit
```

- Callers submit **semantic** change requests (resource type, resource id, desired state).
- The backend reads current state, builds a safe diff, classifies risk, and evaluates
  environment policy. Those decisions are **deterministic** and never LLM-owned.
- Apply accepts a previously planned change id (and approved plan fingerprint when
  policy requires approval), not arbitrary desired state.
- Approval is bound to an exact plan fingerprint; any plan modification invalidates
  prior approval.
- Successful Admin API HTTP responses are followed by read-back verification.
- Stale baselines yield `REPLAN_REQUIRED` / conflict — never silent overwrite.
- Secrets are never returned, logged, or persisted in plaintext.

No generic administrative REST/MCP write capability will be exposed.

## Consequences

- Safer AI-assisted administration with a deterministic approval boundary
- Auditable, multi-target, policy-aware changes shared by MCP, REST, and Web UI
- Additional complexity; writes require multiple operations
- Full realm/client/user/IdP administration is deferred to 0.8.1–0.8.4 on this foundation
