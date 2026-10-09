# AGT2 — Operator console and access-aware assistance, 2026-09-19

Status: **DIRECTION DOCUMENTED / IMPLEMENTATION PLANNED**.

## Operator request and decisions

The operator requested question-driven assistance across infrastructure and IAM
configuration according to personal access, explicitly retained the operator
application for evaluation, and confirmed adding both to the project direction.

- [AGT2](../milestones/agt2-access-aware-assistance.md) is a separate planned milestone
  with six ordered slices and eight acceptance criteria; no new deadline.
- [Architecture](../architecture/access-aware-operations-assistant.md) and
  [ADR 0011](../adr/0011-access-aware-dual-interface.md) retain UI/REST/MCP over common
  services and evidence. The application remains first-class without a model.
- FR-AGENT-002/003, FR-UI-004, SEC-AUTHZ-002 and SEC-AI-003 add capability/evidence,
  UI parity, fine-grained read and context/disclosure requirements.
- Fine-grained read controls are prerequisites, brought forward from the broader
  P2 backlog. No mutation or model-disclosure authority is added. AGT1 stays the
  separately versioned, report-only profile; no silent allowlist expansion.

## Source evidence, not new runtime tests

Inspected `PlatformAuthorizationConfig.Grant` and `TargetAuthorizationService`:
grants contain target IDs and operations mapped to validated platform roles, not
realm/resource/field restrictions. `UserService` checks target READ before using the
target Admin adapter, and `KeycloakClientFactory` uses separate client credentials.
Thus current target access does not mirror personal permissions in the target RHBK.

`UserDetails` includes identity fields such as name/email; credential redaction
cannot substitute for PII authorization. RealmDetails and ClientDetails expose
selected configuration, not every policy/effective-flow relationship. The existing
profile descriptor allows only `keycloak_generate_operations_report` and retains
external disclosure disabled. The current UI architecture already routes REST and
MCP through application services; it is not being replaced with chat.

Independent read-only agent review corroborated these boundaries and identified
history, aggregate, pagination and SSE scope requirements. These are gaps for the
new granular-access promise, not evidence of a demonstrated cross-target exploit.
The design preserves current session/revocation limits and does not promise to erase
information already disclosed to a provider. Official pinned MCP authorization
guidance was checked for audience separation/no passthrough; no protocol compliance
test or product-version-specific delegation configuration was executed.

The independent review prompted explicit clarification that UI/MCP parity compares
equivalent disclosure context (UI-visible PII is not automatically AI-visible) and
that reading the capability catalogue does not grant installation DISCOVER.

## Validation and version decision

Documentation-only workflow: no Maven, UI, Node product tests, containers, volumes,
images, real Keycloak/RHBK/cluster, external model or client runtime used. Previous
1705 backend / 115 profile / 52 catalogue results remain historical, not fresh tests.
No claims of live authorization, broader model support or complete AGT2 acceptance.

Fresh offline validation: **153 Markdown documents, 1052 local links, 152 requirement
definitions and 17/17 milestone structures**, zero errors/warnings. Six parser
self-check groups and `git diff --check` pass. A renamed roadmap heading initially
broke the demo specification link; a stable legacy anchor now preserves that link.
The documentation checker is extended locally to include the new seventeenth
specification; passing structural checks is not milestone completion or external
source validation. Final log: `/private/tmp/kcops-agt2-docs-20260919.log`.

All **631 implementation/test/deployment inputs** match the preceding SecOps catalogue
manifest exactly (no changed, added or removed source inputs), aggregate SHA-256
`5d12d13d3523a4c182767f01cc6847c54ce9cb13edf4e69c718784bdd4440e04`.
This excludes the changed documentation by design; it is an integrity comparison,
not a fresh test or proof of runtime safety. Product/profile versions were inspected
again. Source tests and the environment were deliberately not run for this doc-only slice.

Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**,
reference profile **0.2.1**, report **1.1**, findingDetails **1.0**, Flyway **V1–V10**
and private fixture catalogue **1** remain unchanged. No dependency, permission,
schema or migration change. AGENTS workflow/invariants are unchanged; no code,
commit/push/rebase/tag or global configuration modification. HEAD remains
`572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch `feature/0.8.1-client-lifecycle`;
existing dirty work and dated evidence are preserved.

## Next bounded slice

Define a concrete question × source × resource/field × permission × version matrix
and a single restricted realm/client read policy, then implement it in shared
services and validate two different operator identities through UI/REST/MCP. Only
after scope, PII and indirect-output gates pass should the multi-tool profile be
exposed. Domain sources and UI views grow incrementally; unknown/denied facts stay
explicit. H1/D1/AGT1 and current presentation acceptance remain separately open.
