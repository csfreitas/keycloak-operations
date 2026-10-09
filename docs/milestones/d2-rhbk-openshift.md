# D2 — Verified RHBK/OpenShift and operational coverage

Status: **PLANNED / APPROVED ENVIRONMENT REQUIRED**. Planning target: 13 November 2026. Owner: maintainer provisions lab; development + platform reviewer execute acceptance.

## Objective and dependencies

Validate the submitted discovery → HA/security → health → metrics → report flow on real RHBK/OpenShift. Depends on H1/D1 acceptance and a dedicated explicitly approved environment, never an ambient or unrelated cluster. Templates are inputs, not deployment evidence.

Reviewed manifests can establish this gate before [OP1](op1-operator-installation.md).
The installation Operator is not required for the presentation and does not prove
collection compatibility. Wider multi-cluster/VM acceptance is incremental, with
separately approved scope; this design update does not enlarge the demo commitment.

Roadmap: INF-01/02/03, CFG-01, PERF-01, AGT-01 integration. Requirements: FR-DISC-001/003/004, FR-INV-001–003, FR-HEALTH-001/002, FR-METRICS-001–005 and FR-METRICS-007–009, FR-ASSESS-001–005, COMPAT-001–008, SEC-INFRA-001–004.

## Implementation slices

1. Freeze lab scope, exact RHBK/Operator/OpenShift versions and image digests; separate Identity A/B, namespace RBAC, database, TLS and target-bound monitoring. Reviewer approves template values/egress; no Secret reads.
2. Validate exact root ownership and networking associations with multiple workloads, replacement/denial/ambiguity and relevant served Operator APIs. Record configuration evidence separately from reachability/certificate/controller checks.
3. Curate HA/security rules: version/applicability/reference, entity and observed value, expected positive/negative fixture. Do not infer failover from replica count.
4. Complete bounded health component contracts for Admin API, configured OIDC/management endpoints, infrastructure and metrics/dependencies actually observable; unknown internal database state stays unknown.
5. Validate semantic runtime metrics (units, windows, selectors, freshness, histogram/no traffic/missing provider) and integrate the reference-agent profile.

## Exit criteria

- [ ] Sanitized report identifies actually observed RHBK and platform versions, scope, collectors and gaps; restricted server-info stays unknown when denied.
- [ ] Positive/negative topology/security fixtures produce expected entity-specific findings twice; no customer outage injection.
- [ ] Cross-target, namespace denial, missing source and stale metric tests do not create false PASS/zero.
- [ ] Health status, collection completeness and posture are distinct; timeouts/permissions are visible and bounded.
- [ ] Actual client/model/authentication combination, build/source, RBAC, tests/skips, retained artifacts and lab cleanup/retention are recorded.

## Exclusions and contingency

No production failover experiment, SPI deployment, broad administration or support-range certification. If lab access is unavailable, continue local D3 development but mark RHBK acceptance NOT VERIFIED; change demo claims rather than relabel Community evidence.
