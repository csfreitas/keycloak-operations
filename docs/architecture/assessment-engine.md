# Assessment Engine

The Assessment Engine turns **Evidence** into **Findings** and a numeric **score**.
It is designed to be platform-agnostic: rules never depend on Fabric8 or Keycloak
representation types.

## Pipeline

```
Collectors → Evidence → EvidenceContext → Rules → Findings → AssessmentScoring → AssessmentResult
```

### Evidence

```java
record Evidence(String targetId, String source, String category, String key,
                Object value, Instant collectedAt, EvidenceSubject subject)
```

Example key: `deployment.replicas` with numeric value `1`.

### Rules

Rules implement:

- `applies(EvidenceContext)` — whether the rule is relevant
- `evaluate(EvidenceContext)` — optional `Finding` when a problem is detected

Java rule example: `MinimumReplicasRule` (`KC-OCP-HA-001`).

YAML packs under `src/main/resources/rules/` provide declarative rules/loaders
(`YamlRuleLoader`) for the same conditions.

### Findings

Findings include id, title, category, severity (`CRITICAL`…`INFO`), status
(`OPEN`, `PASS`, `WARNING`, `FAIL`, `NOT_EVALUATED`, `SKIPPED`), description,
evidence map, impact, recommendation, references, and optional `EvidenceSubject`.

Declarative YAML packs under `src/main/resources/rules/` (indexed by
`rules/index.yaml`) are the primary rule source. Java `MinimumReplicasRule`
remains for unit tests; production HA uses `KC-OCP-HA-001` from the `ha` pack.

### Scoring

Measurements and configured policy are distinct inputs. A numeric metric may be available without the threshold needed for its derived boolean evidence; the affected rule remains NOT_EVALUATED. The [performance assessment guide](../performance-assessment.md) explains this boundary. Report-only inventory normalization does not rewrite retained evidence or change rule evaluation.

Category scores and NOT_EVALUATED handling are implemented. The legacy numeric score is not an available posture conclusion unless `scoreAvailable` is true; partial evidence is inconclusive. Realm-scoped evidence must not resolve by first-match aggregation. See [scoring.md](../scoring.md) and [ADR 0009](../adr/0009-trust-boundaries-before-administration.md).

Source completeness is independent of rule applicability. The production pipeline
recognizes target/source-bound `keycloak.collection.complete`,
`infrastructure.collection.complete` and `metrics.collection.complete` markers;
any nontrue marker makes its source partial. Even if every selected rule evaluates,
a material partial source prevents COMPLETE
and `scoreAvailable`, caps the completeness heuristic below 100 and prevents HIGH
confidence for incomplete infrastructure. The 99 cap is not measured coverage or
statistical confidence. Metrics partiality/failure is material only when the profile
requires metrics; it alone does not invalidate a fully observed static profile.
Missing evidence for applicable performance rules still prevents their evaluation.
The [compound-metrics budget](../development/h1-operation-budgets-2026-09-18.md)
propagates interruption/expiry without discarding earlier measurements or beginning
follow-on inventory/ServiceMonitor work. See the [inventory/temporal correction](../development/h1-evidence-temporal-2026-09-18.md)
for executed evidence and remaining boundaries.

## Collection boundaries

Infrastructure completeness now comes from a shared policy also used by snapshots
and infrastructure API health. It requires same-target confirmed discovery with
known supported API coverage and the existing workload/topology/section prerequisites;
empty warnings alone are insufficient. Failed version/discovery observations remain
explicit gaps while independently valid workload/networking facts survive. A
configured runtime or legacy inventory without discovery cannot supply complete
source evidence. See [capability coverage](../development/h1-capability-coverage-2026-09-19.md).

The evidence pipeline now shares a target-bound `CollectionBudget` with its caller.
Before each eligible source it checks the remaining time, records budget-skipped
sources as failed (never silently omits them), and rejects late unmarked results.
Explicit partial markers retain earlier validated observations. Deterministic rules
still evaluate that partial evidence; optional metrics retain the policy above.
Scope restoration is synchronous and does not move authorization/transactions to
worker threads. The deadline covers collection, not persistence or arbitrary rule
execution. See [reporting budgets](operations-reporting.md).

The [H1 failure/bounds correction](../development/h1-failure-bounds-2026-09-18.md)
also aligns generic Java-rule counters: NOT_EVALUATED and SKIPPED are not counted
as evaluated/matched results; OPEN/FAIL are matches. Production YAML accounting
already follows its separate applicability path. Invalid profile failures expose
a fixed public message, not the provider exception. Selected collector error logs
omit exception text/causes. This does not establish general custom-rule exception
recovery or sanitize every external metadata field. The subsequent inventory
warning/evidence correction is recorded separately; H1 acceptance remains open.

Scoped Keycloak collection now validates raw token/server-info/realm/client JSON
before conversion to Admin Client representations. Its separate client caps
consumed response bodies at 1 MiB after decoding, including gzip and chunked bodies,
and successful token parsing at 64 KiB. Realm/client lists must be actual arrays of
at most 500 objects with valid unique identities; invalid, oversized or duplicate
lists are rejected as a whole, not silently truncated. The existing configured
assessment inspection limits still apply to accepted lists and record partiality
when not all objects are inspected.

The reader rejects duplicate properties, trailing documents and malformed known
boolean/string/list/map fields, with bounded nesting, names, strings, numbers and
container widths. Nullable fields that are missing or explicitly null remain
unknown rather than becoming favorable defaults. Unknown extra properties remain
allowed for cross-version compatibility. This is intentionally not a full schema
validator. Returned feature entries require an explicit Boolean `enabled` because
the upstream model's primitive field would otherwise turn omission into false.

`KeycloakEvidenceCollector` preserves earlier server metadata when the realm list
fails, emits `keycloak.collection.complete=false`, and withholds the unobserved
realm count and favorable empty/zero aggregates. Realm detail identity must match
the requested realm before configuration evidence or client collection is accepted.
Independent valid observations can still support findings, but partial source
status prevents an available overall score. Raw realm identifiers are omitted from
the changed collector warning logs; this does not sanitize all evidence metadata.
Ordinary Admin operations and controlled-write clients are unchanged. The
[Admin-boundary ledger](../development/h1-admin-boundaries-2026-09-18.md) records
validation and remaining limits separately from earlier dated slices.

## Profiles (built-in names)

See [assessment-profiles.md](../assessment-profiles.md) and [rule-catalog.md](../rule-catalog.md).

## Assessment vs health check

See [health-check.md](../health-check.md).

## Planned explainable rating

[SCORE1](../milestones/score1-explainable-rating.md) proposes a version-aware 1–100
rating and retained per-control explanation, including public lifecycle/patch/CVE
evidence. The [design](operational-rating.md) extends the existing assessment/scoring
boundary without moving decisions into the AI. Current finding-based 0–100 arithmetic,
availability guards and transport contracts are unchanged; planned control/feed
ledgers and normalized rating are not delivered components.
