# P1 — IAM indicators, drift and continuous observation

Status: **PLANNED**. Indicative window: Q1 2027; IAM-01/02 may be a separately accepted optional D3 increment. Owner: observability developer + IAM/business/privacy owners.

## Objective and dependencies

Measure identity-service use/reliability and detect actionable changes with honest denominators, privacy and noise controls. Depends on D3 evidence/report contracts, pilot feedback and approved source capabilities; actual RHBK claims require D2. Design: [IAM/business observability](../architecture/iam-business-observability.md).

Roadmap: IAM-01–05, ALT-01/02, CFG-02, DOC-03 optional formats, historical 0.9. Requirements: FR-IAM-001/002, FR-OBS-001, FR-DRIFT-001, FR-DOC-001, FR-METRICS-001–005 and FR-METRICS-007–009, SEC-METRICS-001–003, NFR-TSDB-001, NFR-BOUND-001.

## Ordered sub-slices (accept separately)

1. IAM-01/02: reset-aware success/failure counts and client ranking from a validated source; zero denominator is NO_ACTIVITY, missing dimension is unsupported, not zero users.
2. IAM-03/04/05: approved pseudonymous unique-user source for DAU/WAU/MAU; distinguish MFA policy/enrollment/actual use; define SLI/SLO and observed versus estimated impact. No counters-as-unique-users or invented financial loss.
3. Coverage-aware drift and reviewed baseline/exceptions; failed collection never implies deletion; respect Terraform/GitOps ownership.
4. Bounded durable schedule/retention plus deterministic alert duration/recovery/deduplication, expiring silences and opt-in delivery with retries/idempotency.
5. Only then statistical anomalies with minimum history/seasonality/low-volume handling and measured false-positive/omission rates. Optional PDF/DOCX export requires QA on D3 canonical records.

## Exit criteria

### Additional bounded IAM/SecOps backlog

The [scenario catalogue](../development/secops-iam-scenarios.md) adds **IAM-06**
(approved organizational password-age assessment; FR-IAM-003) and **SECOPS-01**
(bounded identity-event investigation; FR-IAM-004). Sequence: authoritative-source
and population feasibility → read-only assessment → deterministic investigation.
The initial synthetic cases/checks validate catalogue integrity only, not a working
event collector, detector, compliance evaluator, ACL or model. These do not replace
IAM-01–05 or require a new milestone. P1 remains PLANNED; dates are unchanged.

- [ ] IAM-06 validates effective membership/provider/version semantics, precise
  policy boundary, justified N/A/UNKNOWN and separate population coverage; partial
  samples never establish global compliance.
- [ ] SECOPS-01 validates bounded sources, event kind, deduplication, timestamps,
  retention and geographic uncertainty; signal/absence/denial remain distinct.
- [ ] Actual denied cross-target/realm queries, privacy limits and transport
  agreement are tested before acceptance; actual RHBK claims require approved lab evidence.

### Existing track acceptance

- [ ] KPI dictionary records target/scope/source/window/unit/method/denominator/freshness; actual source capability and versions validated.
- [ ] Fixtures cover resets, replicas, missing labels, no traffic, duplicates, timezone boundaries, service accounts, telemetry gaps and configured-but-unused MFA.
- [ ] Purpose/access/raw-versus-aggregate retention and deletion approved before granular ingestion; exports omit raw identities/PII by default.
- [ ] Drift/alert evidence is reproducible; restart/retry cannot duplicate notifications or cross target scope; missing telemetry is distinguishable from incident recovery.
- [ ] Anomaly quality is measured on labeled cases before notifications; alerts never authorize remediation.

No unrestricted event warehouse, mandatory SPI listener, legal certification or universal "everything happening" collection. External delivery/source enablement requires explicit authorization.
