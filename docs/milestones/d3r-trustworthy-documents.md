# D3R — Technical and executive environment documents

Status: **PLANNED**; on-demand JSON/Markdown foundation exists. Part of D3, planning target 4 December 2026. Owner: reporting developer + technical/business reviewers.

## Objective and dependencies

Deliver high-trust assessment/health documents whose conclusions can be traced to retained evidence. Depends on D3E; document UI work can use local fixtures earlier, but RHBK statements require D2. Optional IAM indicators depend on validated P1 source semantics.

Roadmap: DOC-01/02/03, IAM-01/02 optional increment, AGT-02. Requirements: FR-DOC-001, FR-EVID-001, FR-REPORT-001–006, FR-AI-003, SEC-REPORT-001, NFR-REPORT-001. Design: [trustworthy reporting](../architecture/trustworthy-reporting.md).

## Deliverables

- Canonical technical report: scope/product/version, source coverage, assessment and component health, runtime windows/units, entity findings, rationale, evidence IDs, recommendations and verification steps.
- Executive view derived from the same record: observed risks, mapped applications, priorities and limitations, not unsupported financial impact or certification.
- Authorized report history/detail/export/replay references; deterministic JSON/Markdown as required formats. PDF/DOCX are optional after the core gate or move to P1, with render/visual QA required before claiming support.
- Explicit human-review state and optional AI explanation separately labeled; no rewriting deterministic facts or retrospective evidence.

## Exit criteria

- [ ] Every material conclusion traces to evidence and rule revision; gaps/no evaluated rules never become a favorable summary.
- [ ] JSON and rendered views agree on counts, units, timestamps, severity, windows and unavailable values.
- [ ] History/export respects current target authorization and retention; canaries absent from exports and prompts.
- [ ] Replayed report is clearly offline and dated; live refresh creates a new collection, not an overwritten artifact.
- [ ] Technical/business reviewers sign off scope and wording; AGT1 evaluations preserve facts and uncertainty.

## Exclusions

No guarantee to detect every issue, mandatory external LLM, automatic compliance attestation, unsupported IAM population estimates or implied PDF/DOCX availability.
