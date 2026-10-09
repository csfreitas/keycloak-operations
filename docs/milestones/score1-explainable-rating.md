# SCORE1 — Explainable operational rating (1–100)

Status: **PLANNED — DESIGN/BACKLOG ONLY**. Owner: assessment maintainer + operations/security reviewers.

## Objective and dependencies

As a Keycloak/RHBK operator, understand the environment's rating, why it received
that rating and which evidence-backed corrections could improve it. Product/version,
patch/CVE applicability, coverage and limitations must accompany the number.

Depends on H1 trustworthy coverage/authorization, current D1 assessment contracts,
and D3E for retained explanations/replay. D3R integrates the accepted result into
documents; D2 is required for actual RHBK/OpenShift claims. A local synthetic rubric
prototype can precede those acceptance gates. Portable runtime coverage depends on
the relevant ONB1/PORT milestones, not assumed collectors.

Roadmap: SCORE-01/02/03/04; candidate D3 integration, no new deadline or change to
the current H1/D1/AGT1 queue. Requirements: FR-SCORE-001/002/003/004/005,
FR-EVID-001, FR-DOC-001, FR-AGENT-001.

## Deliverables and work slices

1. **SCORE-01 — Reviewed rubric.** [Architecture proposal](../architecture/operational-rating.md),
   dimension/control weights, product/version applicability, population aggregation,
   critical caps/bands, N/A/unknown semantics and golden fixtures. Weights in the
   proposal are calibration candidates, not approved policy or official certification.
2. **SCORE-04 — Public lifecycle/security evidence.** Bounded read-only adapters for
   supplier advisories, release/lifecycle metadata and Red Hat CSAF/VEX; local artifact
   matching, backport-aware states, deduplication, approved endpoints, attribution,
   expiring cache/offline snapshots and explicit outage/unknown handling. No customer
   inventory disclosure, vulnerability exploitation or automatic patching.
3. **SCORE-02 — One explanation contract.** Deterministic 1–100 result and per-control
   arithmetic, reasons, applicable version, evidence, critical caps, corrections and
   separate coverage/freshness; same authorized result in UI/REST/report/MCP.
   Review additive contracts and legacy score coexistence before changing consumers.
4. **SCORE-03 — Retention and calibration.** Retained rubric/control/feed inputs,
   reproducible replay, labelled noncomparable changes, reviewed examples and operator
   acceptance. Do not reconstruct historical explanations using today's live feeds.

## Exit criteria

- [ ] Reviewers approve the rubric, applicability catalog, weights, thresholds/caps
  and rating labels against stated goals; a 100 means only the evaluated scope passed.
- [ ] Equivalent control populations/duplicate findings do not distort results;
  critical blockers cannot be hidden by favorable dimensions; arithmetic reconciles.
- [ ] Unknown/partial/expired required inputs, unknown denominator, all-N/A across
  the assessment and unsupported version cases withhold the rating. The overall
  environment profile requires scoped lifecycle/CVE evidence; configuration-only
  findings or a limited configuration rating do not substitute for it. Proven N/A
  dimensions and weight redistribution are visible; current guards are not weakened.
- [ ] Upstream versus RHBK, mixed versions, missing build/digest, confirmed fixes,
  backports, unknown CVE matches and vendor source disagreement have tested outcomes.
- [ ] Public source integration demonstrates real bounded retrieval separately from
  fixture matching; cache expiry/offline/rate-limit/schema-change failures are tested.
  Lifecycle/support metadata is not confused with customer entitlement.
- [ ] Explanations reconcile reasons, control contributions, caps, source references,
  product/build and profile/rubric versions across all supported transports.
- [ ] Replay preserves the original inputs and result; comparisons flag changed scope,
  product/version, profile, rule/rubric/feed versions without rewriting history.
- [ ] Target isolation, redaction, untrusted external text and destination restrictions
  are validated; no inventory/secret disclosure or unauthorized runtime write occurs.
- [ ] Independent operator reviews representative ratings and recovery guidance;
  exact tested product/runtime/source versions and limitations are documented.
- [ ] Architecture, requirements, state, changelog and compatibility/version decisions
  agree; real RHBK claims remain blocked until the approved D2 validation is complete.

## Exclusions

No scoring implementation, live CVE scan or source synchronizer is delivered by this
planning change. No automatic remediation/upgrades, new cluster, broad package scanner,
LLM-defined score, universal environment certification, commercial support promise,
release/tag, commit/push or rescheduling of existing milestones is authorized.
