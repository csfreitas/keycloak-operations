# ADR 0011 — Access-aware assistance alongside the operator console

- Status: Accepted product direction; implementation proposed and gated by AGT2.
- Date: 2026-09-19

Implementation update: the first [restricted configuration-read subset](../configuration-reads.md)
is locally implemented and validated separately. The accepted direction below remains
broader than that subset; all-domain policy, delegation and multi-tool host acceptance
are not implied.

## Context

The operator wants conversational access to infrastructure and IAM configuration
according to personal access, and explicitly retains the application for direct
assessment/health/report workflows. AGT1 is a report-only local profile, while
current backend grants are target-wide. Neither foundation proves per-realm,
resource or field restrictions or safe multi-tool model context.

## Decision

Keep the console and optional MCP client as complementary adapters over the same
deterministic services and backend authorization. Add a separately versioned,
read-only AGT2 capability/evidence contract. Bring fine-grained **read** authorization
forward as its prerequisite; keep P2 mutation safety/approval separate. Intersect
human, client, target/resource, data-class/disclosure and source access; no shared
admin identity standing in for a human. Do not forward Identity A credentials to
target Admin APIs or expose Identity B to a model.

Question-driven reads retrieve only necessary authorized evidence and distinguish
observations, evaluated findings and hypotheses. The UI remains functional without
AI, including its own evidence navigation and review. No new microservice, mandatory
RAG store, embedded chat or particular model/provider is required.

## Alternatives and consequences

- Report-only is a useful first profile but not the full requested product scope.
- Full-admin agents, prompt-only filtering and full-environment context ingestion
  cannot enforce the operator's resource/data access and are rejected.
- Replacing the console with chat would remove independent inspection and is rejected.
- Additional scope/field checks, cross-surface tests and explicit grant migration
  add work before expanded tool exposure; they cannot be postponed until after
  sending private data to a model.
- Rollout is opt-in and additive. Existing AGT1 contract is retained; disable an
  expanded profile by removing its exposure, not by widening grants to recover it.
  Future persisted policy/contract changes need explicit migration/rollback design;
  already disclosed model data cannot be recalled by switching the profile off.
- Revisit if a validated deployment needs target-native delegated authorization or
  an embedded assistant. Neither may bypass the shared policy or disclosure gates.

The [architecture](../architecture/access-aware-operations-assistant.md) defines
the gap and flow; [AGT2](../milestones/agt2-access-aware-assistance.md) defines
incremental acceptance. No new runtime capability is delivered by this ADR.
