# AGT1 — Optional reference agent and evaluations

Status: **IN PROGRESS — LOCAL CONTRACT PROTOTYPE**. Owner: agent integration developer + security reviewer + presenter. Milestone spans existing D1 prototype (16 Oct), D2 integration (13 Nov), D3 evaluation (4 Dec), D4 freeze (18 Dec 2026); not an extra critical-path deadline.

## Objective and dependencies

Provide a reusable versioned MCP client profile, setup/adaptation guide and grounded explanation tests. No model runtime or provider is selected by this milestone. Depends incrementally on H1 identity, D1 local contracts, D2 evidence for RHBK claims and D3 report contracts for retained-evidence claims.

Roadmap: AGT-01/02. Requirements: FR-AGENT-001, FR-AI-001–003, FR-ASSESS-003/005, SEC-AI-001/002, SEC-MULTI-001, SEC-REPORT-001, NFR-AI-001. Architecture: [AI-assisted operations](../architecture/ai-assisted-operations.md).

## Deliverables

- Versioned read-only operation/explanation instructions, sample grounded interactions and MCP/OIDC setup using secure references; clearly distinct from AGENTS.md development instructions.
- Guide to adapting another agent; record the actually validated client, model/provider, profile and authentication versions. Organizational approval precedes external data disclosure.
- Fixed cases: normal findings, no evidence/no traffic, wrong target, invalid credentials, malicious metadata, fabricated IDs, inconsistent narrative and unavailable providers.
- Deterministic no-AI fallback; model unavailability cannot block collection/reporting.

## Exit criteria

Implemented locally: [profile 0.2.1 and guide](../../dev/reference-agent/README.md),
reviewed operational instructions, one fixed report tool with operator-owned scope,
strict provenance/fact projection, no-AI fallback and offline CLI. MCP findingDetails
1.0 provides bounded whole sanitized findings/evidence from the same report, original
indices and explicit omission counts. The profile validates identity/counts/bounds
and exact JSON references, with an independent 256 KiB serialized-packet ceiling.
The [grounding ledger](../development/agt1-grounding-2026-09-19.md) records new Java,
profile/CLI and actual OIDC/MCP validation; the [foundation ledger](../development/agt1-profile-2026-09-19.md)
retains its earlier results. These are not language-model evaluations.

Every structural explanation pass retains semanticReview=REQUIRED; contradictory
prose can still pass structurally. Source text is untrusted, omissions may hide
important findings and report-local references are not retained raw evidence IDs.
The [synthetic preflight](../development/agt1-synthetic-preflight-2026-09-19.md)
preserves 70 passing profile tests, 12 local checks and a failed startup/denied retry.
Subsequent explicit disclosure approval allowed the [three-response trial](../development/agt1-synthetic-model-trial-2026-09-19.md)
using Codex CLI 0.155.0-alpha.9.2 and requested gpt-6-astra (resolved snapshot not
attested). **0/3 responses pass the contract**: narrative references exceed 10, a
limit not supplied in the operational instructions. All facts match; agent-assisted
semantic review found no violation in these samples, including embedded malicious
instructions. Zero observed tool calls is not proof of effective isolation. No MCP
host authentication or actual target connection was tested; this is not an accepted
client/model integration. No failing response was repaired or repeated.
The [local alignment patch](../development/agt1-contract-alignment-2026-09-19.md)
now publishes the unchanged limits through shared constants and synchronized guidance,
with local boundary/regression tests and an offline diagnostic/tool-event classifier.
Profile 0.2.1 requires its own exact identity; no old response is repaired or upgraded.
CI includes the offline tests, but no remote CI execution or new model result is claimed.
Next: a newly authorized bounded evaluation of the revised profile, then authenticated
integration and broader/repeated evaluation. The original three-response budget is
exhausted. All complete exit criteria below remain open.

- [ ] Setup works without backend changes; READ/ASSESS only, never DISCOVER/BIND/PLAN/APPROVE/WRITE or arbitrary endpoints/commands.
- [ ] Explanations cite real report/finding/evidence IDs, preserve collection windows and UNKNOWN/PARTIAL, and separate observation from hypotheses/recommendations.
- [ ] Backend findings are unchanged with or without the agent; no invented score, severity or PASS.
- [ ] Evaluation ledger records cases, expected outcomes, client/model/profile revisions and zero critical disclosure/unauthorized-action violations; repeated runs expose variability.
- [ ] Presentation combination is frozen and rehearsed, including safe refusal and no-AI fallback.

## Exclusions

The operator-approved [AGT2](agt2-access-aware-assistance.md) adds a separate future
multi-tool read profile with fine-grained authorization and console parity. AGT1's
report-only contract remains intact; its acceptance does not certify that broader
scope. The operator application remains first-class and usable without either model
profile. AGT2 requires fresh contracts/evaluations, not simply a larger tool allowlist.

No universal client/model compatibility, autonomous remediation, human-approval claim from a prompt, or unrestricted customer data ingestion. The reference agent remains optional for product adoption.
