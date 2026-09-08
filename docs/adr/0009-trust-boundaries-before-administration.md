# ADR 0009 — Trust boundaries before broader administration

- Status: Accepted for the H1 corrective implementation; production execution remains incomplete.
- Date: 2026-09-04

## Context

The January presentation depends on trustworthy read-only discovery, assessment, health, metrics and evidence explanation. Review found realm-scoping/partial-data errors and authorization/policy gaps. New administration or business analytics would magnify these defects.

## Decision

Retain the modular monolith and shared REST/MCP services. Prioritize identity/target grants, truthful partial evidence and report provenance. Bind actor/approval to trusted identity and separate APPROVE from WRITE; actor request text is compatibility metadata only. PKCE uses the typed semantic security path exclusively. V8 invalidates execution of pre-context pending plans, preserves their history and requires replan. Idempotency is bound to normalized intent; lifecycle locks coordinate the same plan only.

Realm rules evaluate scoped evidence; missing/failed/truncated data must not imply PASS/zero. Legacy numeric score survives for compatibility but is not an available conclusion without meaningful complete evaluation. Document actual observation windows and bundled rule resource digest without claiming atomic snapshot or replay.

January demo uses read-only identity and no SPI execution. Business/IAM analytics, durable remediation and isolated SPI testing have explicit source/privacy/execution gates in the roadmap.

## Consequences

Packaged default is fail-closed; local unauthed access needs explicit lab profile and loopback. Integrations must configure OIDC/audience/role-target grants. Old pending plans need new idempotency keys and reapproval. Full human-only approval, realm/resource ACL, durable unknown-outcome reconciliation and cross-plan serialization remain required before production writes. No new release, commit, push or deployment follows automatically from this decision.
