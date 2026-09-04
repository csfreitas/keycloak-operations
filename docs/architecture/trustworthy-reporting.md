# Trustworthy environment reports

Status: **EVOLUTION DESIGN**. On-demand report composition exists. Shared immutable collection bundles, persistent report history, offline replay and signed attestations are not all implemented; see project-state.

## High confidence means traceability

A document explains what it inspected, how it knows each fact, what it could not inspect and why a conclusion follows. Score, attractive PDF and fluent AI text are not evidence of correctness. Complete means complete for a declared scope, applicable rules, accessible sources and observation window.

## Document family

1. Technical assessment: architecture/configuration, version matrix, per-entity findings, applicability, severity, evidence and remediation prerequisites.
2. Health report: component/dependency probes, durations/timeouts, runtime metrics, drift and explicit gaps.
3. Executive/IAM report: prioritized observed risks, application scope, authentication use/trends, suggested owner/actions and limitations. No invented financial or causal claims.
4. Evidence annex: sanitized structured data, source references/states, timestamps, rule revisions and reproduction instructions. Future SPI annex uses separately approved harness results.

## Canonical record — target design

- Report/schema/collection IDs; target ID and non-secret revision; observed versus configured product version; runtime/provider capabilities.
- Start/end and per-source windows; pagination/truncation/failed-call coverage per object/source.
- Coverage distinguishes discovered, inspected, failed and unknown object counts from rule-evaluation coverage. If the true inventory denominator is unknown, do not display an inventory completeness percentage. Confidence is not a statistical probability inferred from the legacy completeness heuristic.
- Rule-pack version/hash, collector/application version and profile; source and rationale for every rule.
- Findings reference specific evidence IDs and `(target, realm, resource, property)`; aggregates declare method and coverage.
- Health retains its own status/timestamps; metrics include units, semantic query ID, window, freshness and availability.
- Recommendations separate observation, interpretation, hypothesis, impact and verification procedure.
- Suppressions/exceptions include reason, owner and expiry; do not rewrite evidence.
- Export/sanitization versions, canonical digest and retained-evidence references. Hashes establish consistency, not truth or third-party certification.

Collect once per defined source/window when possible; assessment/rendering consume immutable evidence. Do not promise an atomic global snapshot across Keycloak/Kubernetes/Prometheus. Independent section collections must be identified until shared collection is implemented.

## Rendering and AI

Canonical JSON drives Markdown/HTML and subsequent PDF/DOCX. Views agree on counts, dates, units, severity and unknowns. Render/inspect paginated formats before delivery for clipped tables, missing references or misleading charts.

AI is an optional explanation overlay labeled with report/model/prompt revision and references. Test factual grounding and contradictions; resource metadata never becomes instructions. Deterministic documents work without an external model and do not change facts when wording changes.

Export must remain authorized: architecture can be sensitive after secret redaction. Define retention/share review; do not send data to external models/recipients without configured authorization.

## Acceptance

- Mixed secure/insecure realms in reversed order yield identical entity-specific results.
- Denial, missing provider, truncation and timeout create gaps, never PASS/zero.
- No evaluated rules means no favorable conclusion.
- Identical retained evidence and rule/profile revision reproduce findings; revisions are visible.
- Cross-target historical IDs are denied; unsupported version/runtime/SPI remains unknown.
- Secret canaries absent from JSON/Markdown/exports/logs/MCP; malicious metadata has no authority.
- Executive statements trace to technical facts; estimates/examples marked; health not substituted with collection status.

Before customer sharing, a human checks scope, freshness, completeness, applicability, impact wording, action feasibility, sensitive data and unresolved uncertainties. Record tool provenance and human review separately. A high-confidence report is not a certificate that all possible issues have been excluded.
