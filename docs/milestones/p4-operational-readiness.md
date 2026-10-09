# P4 — Production readiness and community sustainability

Status: **PLANNED — NO ARTIFICIAL RELEASE DATE**. Owner: maintainer + operations/security reviewers + community contributors.

## Objective and dependencies

Earn readiness claims for a declared supported scope, not every roadmap feature. Depends on accepted core H1/D1/D2/D3 and pilot outcomes. ONB1/portable/P1/P2/P3 gates apply only to capabilities included in the release; unaccepted capabilities remain excluded or clearly experimental.

Roadmap: P4 / historical 1.0. Requirements: FR-OPS-001, FR-PERS-001, NFR-ISO-001, NFR-BOUND-001, NFR-OBS-001, NFR-TEST-001/002, COMPAT-005/006, SEC-REPORT-001.

## Deliverables

If the release includes the [installation Operator](op1-operator-installation.md),
its actual distribution/install/upgrade path must pass OP1 and the relevant recovery
gates here. An available installation is not evidence of healthy evaluated targets.

- Maintained product/platform/client compatibility matrix with exact tested versions and unsupported behavior; reproducible images/build provenance and dependency review.
- Populated upgrade/backup/restore, retention/deletion, capacity and recovery runbooks with owner-approved RPO/RTO/budgets established before testing.
- Deployment model decision: single replica with explicit limits or tested distributed coordination for MCP/SSE/schedules, not naive replicas over in-process state.
- Independent security review and threat model, vulnerability reporting/triage policy, examples, contribution/rule-review process and support boundaries.
- Release/version/rollback procedure and scoped operator acceptance, with explicit approval before publication.

## Exit criteria

- [ ] Declared scope has complete evidence-backed acceptance, no unresolved critical safety defect and an approved limitations register.
- [ ] Upgrade/restore/recovery and load tests meet the pre-agreed budgets; database retention actually runs and is verified.
- [ ] Multi-target confidentiality, credential rotation and failure recovery validated under supported deployment topology.
- [ ] Another operator follows runbooks; maintainers can reproduce builds and triage contributions/security reports.
- [ ] Version metadata, changelog, compatibility and release artifacts agree; readiness/release decision is reviewed, not inferred from a milestone number.

No automatic 1.0 tag, public support promise, production deployment or customer-data collection. A release may remain experimental until these gates genuinely pass.
