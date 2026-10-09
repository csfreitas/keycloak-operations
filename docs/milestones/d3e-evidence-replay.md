# D3E — Retained evidence and reproducible assessments

Status: **PLANNED**. Part of D3, planning target 4 December 2026. Owner: backend developer + evidence/security reviewer.

## Objective and dependencies

Preserve the actual bounded inputs used by an assessment and reproduce its findings without contacting the target. Depends on H1/D1; local development can precede D2, but RHBK claims require D2 evidence. Existing snapshots/report schema 1.1 are a foundation, not replay.

Roadmap: DOC-03, CFG-02 base. Requirements: FR-EVID-001, FR-REPORT-001–006, FR-INV-003, FR-ASSESS-003, FR-PERS-001, NFR-DET-001, SEC-REPORT-001, SEC-MULTI-001. Design: [trustworthy reports](../architecture/trustworthy-reporting.md), [snapshots](../snapshots.md).

## Implementation slices

1. Define immutable collection/evidence identifiers, target/installation revision, exact root identity, source windows, collector/schema/rule/profile revisions and coverage/errors. Preserve identity in evidence digests; legacy snapshot hashes are not installation identity.
2. Retain sanitized normalized inputs plus actual rule/profile evaluation configuration; packaged resource hash alone is insufficient. Reuse collection within the declared window where possible; label independent reads.
3. Persist assessment/report references atomically where applicable; authorize list/get/export/replay by current target grants. Introduce bounded retention/deletion policy and migration strategy.
4. Replay offline with pinned evaluator/rules, declaring unsupported historical revisions instead of silently applying current rules. A new evaluation with new rules is a distinct run.

## Exit criteria

- [ ] Original run and offline replay yield identical normalized findings on retained evidence; replay makes zero provider calls.
- [ ] Tampered/missing evidence or incompatible rules fail explicitly; hashes are consistency checks, not trusted-origin signatures.
- [ ] Recreated UID, changed scope, denied/truncated collection and unknown denominators remain visible; disappearance is not inferred from failed collection.
- [ ] Cross-target IDs/exports, secret canaries, populated migration, retention and rollback tests pass.
- [ ] Documentation identifies exact replay boundaries, storage budgets and recovery procedures; no claim of atomic cluster/metrics snapshot.

Next: D3R consumes this canonical record; full continuous drift/alerting remains P1.
