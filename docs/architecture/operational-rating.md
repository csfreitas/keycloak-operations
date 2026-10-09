# Explainable operational rating

Status: **PROPOSED — NOT IMPLEMENTED**, 2026-09-19. Delivery is tracked by
[SCORE1](../milestones/score1-explainable-rating.md). This design adds a backlog item;
it does not change current scores, report schemas, release versions or milestone dates.

## Purpose and existing behavior

An operator should see a **1–100 rating**, the applicable assessment profile, the
observed product/version, and exactly why that rating was assigned. Evidence coverage,
freshness and confidence remain separate from the condition of the environment.
The AI explains a backend result; it never chooses weights, findings or the rating.

The current [scoring implementation](../scoring.md) starts at 100 and deducts points
per actionable finding. Realm-level findings and aggregated client findings have
different cardinalities, so the score is not a normalized measure of environment
quality. One critical finding alone leaves 75. Keeping a 100-point upper bound does
not fix those methodological problems; the proposed normalized rubric is distinct
from the legacy 0–100 algorithm. Existing availability guards must not be weakened.

## Profile and version applicability come first

Record distribution (`KEYCLOAK` or `RHBK`), observed server version/build, artifact
identity when available, runtime, capabilities, installation scope and observation
time/source. A configured tag or declared version is not proof of the running build.
Do not transfer upstream support or vulnerability status to RHBK by version similarity.

Each control declares product/version ranges, required capabilities, supported
runtime scope, evidence requirements, thresholds/units, aggregation policy, exact
official references and rule/rubric revision. Track tested ranges separately from
documented applicability. Unsupported by this tool is not unsupported by the vendor.
Unknown or conflicting identity, missing capability evidence and untested matching
logic produce an explicit inconclusive result, not PASS or convenient N/A.

For mixed-version installations, retain per-instance observations and inventory
coverage. One server endpoint does not establish the version of every replica.
Assess version-dependent controls only for the proven scope; a rolling transition
requires the applicable documented upgrade/support conditions, not a blanket failure
or approval. VM, standalone, Compose, container and cluster profiles must express their
own requirements; absent collectors remain a limitation, not invented evidence.

## Draft rubric for calibration

The following weights are an initial **project proposal**, not a Keycloak/Red Hat
standard, compliance certification or approved production-readiness threshold.

| Dimension | Draft weight | Scope examples, where evidence exists |
|---|---:|---|
| Security | 30% | Authentication/configuration controls and confirmed applicable vulnerabilities |
| Availability | 25% | Topology, failure domains and required availability controls |
| Configuration | 15% | Validity and suitability for the actual product/version/profile |
| Operations | 10% | Supported lifecycle, patch process and demonstrable recovery practices |
| Observability | 10% | Required health/metrics visibility and collection quality |
| Capacity/performance | 10% | Resource/runtime observations against declared budgets |

Examples are scope candidates, not claims that current collectors prove them.
The existing `performance`/`capacity` categories need an explicit versioned mapping.
Business outcomes and SPI assurance enter only when their source/acceptance gates
exist; no synthetic business or custom-code safety score is inferred from config.

Proposed first rubric: each applicable control has a positive weight and binary
satisfaction (1 compliant, 0 confirmed noncompliant). Warning/partial credit requires
explicit criteria before introduction. Missing evidence is neither 0 nor 1.
Aggregate subjects once per control, initially using the worst confirmed applicable
result, while showing affected/evaluated/expected subject counts. Repeated findings
or duplicate CVE records must not multiply a control's deduction; adding healthy
replicas/realms must not hide an existing failed control. Group correlated controls
explicitly so pack expansion does not accidentally multiply one underlying risk.

For each dimension, `q = sum(controlWeight × satisfaction) / sum(applicableWeight)`.
Normalize applicable dimension weights to sum to 1, then calculate
`rawRating = 1 + 99 × sum(dimensionWeight × q)`.
The proposed scale is 1–100: complete applicable evidence with all controls failing
gives 1 before caps, and all controls passing gives 100. Caps remain within this
range; unknown inputs yield no numeric rating. The current legacy floor of 0 is
unchanged until a reviewed implementation/migration, not silently relabelled as 1.
Only proven N/A may leave the denominator; retain its evidence and show redistributed
weights. A dimension with all controls proven N/A is itself N/A, not a score of 100;
its weight redistribution must be visible. No applicable controls in the entire
assessment, unknown denominator, incomplete required scope or expired required
evidence means **rating unavailable**, never 1, 0 or 100.

Apply reviewed critical-control caps after the weighted result, then round once to
one decimal. A confirmed critical blocker must prevent a favorable overall label,
even when other controls pass. Cap values and rating bands remain an explicit
calibration/approval gate before implementation, not arbitrary fixed policy here.
Persist exact contributions and raw/capped/displayed values. Coverage is not a
multiplier: lowering coverage cannot improve the rating. At initial integration,
retain the current overall availability guard for category presentation too.

## Public lifecycle, patch and vulnerability intelligence

Public sources make this feasible, but metadata publication does not guarantee
complete artifact coverage or entitlement to download/support a product. Prefer
supplier applicability over generic version matching:

| Input | Official starting point | Intended use and boundary |
|---|---|---|
| RHBK vulnerability/errata status | [Red Hat Security Data](https://access.redhat.com/security/data), including Security Data API and CSAF/VEX | Match product/build/component to affected, fixed or not-affected statements; retain advisory/CVE references |
| RHBK lifecycle | [RHBK life cycle and support policies](https://access.redhat.com/support/policy/updates/red_hat_build_of_keycloak_notes) | Resolve the specific RHBK release stream and dated support phase; do not infer customer entitlement |
| Upstream advisories | [Keycloak security advisories](https://github.com/keycloak/keycloak/security/advisories) | Upstream affected/fixed ranges and conditions, not RHBK applicability |
| Upstream releases | [Keycloak releases](https://github.com/keycloak/keycloak/releases) | Published patch/release notes; a newer release is not proof the installed one is vulnerable |

Red Hat publishes machine-readable CSAF/VEX and security data with attribution
requirements. Its [backporting practice](https://access.redhat.com/security/updates/backporting)
explains why an older component version may already contain a security fix.
The [CSAF/VEX guidelines](https://redhatproductsecurity.github.io/security-data-guidelines/csaf-vex/)
describe public product-status data; correction-only advisories cannot establish the
absence of unfixed vulnerabilities. Distinguish stable releases from prerelease/nightly
builds, and allow reviewed lifecycle metadata if no structured lifecycle feed exists.
Preserve publisher, original URL, advisory revision, publication/update/retrieval
times, source digest and applicable product identifiers in a retained snapshot.

Keep separate fields for vendor impact statement, installed-artifact applicability,
fix availability, installed fix evidence, support phase and observed exposure.
Normalize status as affected / fixed / not affected / under investigation / unknown
without losing original vendor values. An advisory offering a fix does not prove
the running deployment installed it. Missing product matches are unknown, not clean.
Image tags alone are insufficient for component CVE matching; use available digest,
build/package identifiers and supplier SBOM/VEX linkage. Custom SPI dependencies,
JDK and base OS need their own authorized inventories; core version is not enough.

Only a confirmed applicable unresolved vulnerability reduces the security rating.
Use a versioned severity/impact policy; retain vendor severity and any CVSS version,
vector and source separately. CVSS is not the environment's 1–100 operational rating.
Exposure/mitigation observations can inform prioritization, but unknown reachability
does not dismiss an affected artifact and a mitigation does not mean a patch is
installed. Correlated CVE/advisory/package entries are deduplicated before scoring.
Lifecycle expiry may fail a separate reviewed operations control; age or a newer
available patch alone does not prove insecurity. Avoid charging the same missing
security fix again as a generic patch-lag penalty.

An expired feed, publication gap, source disagreement, rate limit or unavailable
service yields explicit stale/unknown coverage, never a fabricated zero-CVE result.
The requested overall environment rating requires product/version, lifecycle and
vulnerability intelligence for its declared component scope; such gaps block that
rating. A configuration-only assessment may still show its findings and a separately
labelled limited configuration rating when complete, but must say vulnerability
posture was not assessed. It cannot substitute for the overall environment rating
or be directly compared with a full-security profile. The control catalog must state
which components are included, without implying a complete dependency-chain audit.

Fetch bounded public feeds through administrator-approved HTTPS endpoints, never
LLM-supplied URLs. Validate schemas, sizes, timeouts, redirects and destination rules;
do not execute feed content or follow embedded arbitrary links. Prefer periodic
public-catalog synchronization with local matching, no target identifiers, credentials,
realm/user data or environment inventory sent to suppliers. An authenticated feed
would need a separately approved connection. Cache snapshots for bounded offline use;
approved offline imports need provenance checks. A digest detects changes, not publisher
authenticity. Keep source licensing/attribution, retrieval health and expiry visible.
The feed adapter is read-only: no scanning exploits, patch download/application or
new network access from an assessed target is implied.

## Minimal architecture and explanation contract

Extend the existing assessment domain/scoring package with an immutable evaluation
and a normalized vulnerability-evidence adapter at the collection boundary. Keep
policy evaluation pure and independent of networking; no new microservice, TSDB,
generic rule language or LLM scoring engine is needed.

The calculation must consume the complete bounded, coverage-qualified control ledger,
not the number of persisted actionable findings or a truncated MCP export. Current
finding history does not retain all PASS/applicability decisions. Durable explanation
and replay depend on [D3E](../milestones/d3e-evidence-replay.md); trustworthy document
integration is coordinated with [D3R](../milestones/d3r-trustworthy-documents.md).

Retain target/assessment/report binding, scope/window, observed product/build,
profile/rule-pack/rubric revisions, applicability decisions, population counts,
evidence references, source/feed snapshots and complete arithmetic. Each control
explanation must show observed versus expected, result, weight/deduction, relevant
cap, risk/impact, evidence/version applicability, recommended action and recheck.
An accepted exception is visible, scoped and expiring, not a deleted finding or an
unexplained increase in the default rating.

REST, reports, UI and MCP consume the same sanitized authorized evaluation. The
existing bounded MCP finding list cannot be used to recompute the rating. Any new
transport extension needs its own compatibility/size/omission contract; present
unavailable explanations honestly instead of inventing contributions. Historical
ratings remain immutable. Changed scope, version, profile, rules or feed revision
must be labelled in comparisons; a recalculation is a new evaluation, not an overwrite.

Illustrative arithmetic only: full, applicable evidence and no critical cap could
produce `100 − 19.80 security − 14.85 availability − 4.95 observability = 60.4/100`.
The UI should show those reasons, drill down to controls, and separately say which
product/version, profile, observation window and catalog revision support the result.
This example is not a measurement of any current environment.

## Acceptance and source basis

Golden fixtures cover known complete/partial scopes, all-N/A, unknown version,
mixed builds, unsupported product, stale feeds, duplicate CVEs/findings, fixed and
backported artifacts, source disagreement, unknown inventories, critical caps and
1-versus-100 equivalent realms. Under fixed scope/rubric, correcting a failed control
must not worsen the result; reducing coverage must not produce a better available
rating. Replayed arithmetic and all transports must agree exactly, with cross-target
denials and malicious external text retained as data. Calibrate with reviewed local
fixtures and independently reviewed environments before accepting grade bands;
community fixtures do not establish RHBK acceptance, which still needs D2 evidence.

Production-control candidates should cite the version-specific
[Keycloak production guide](https://www.keycloak.org/server/configuration-production)
and [RHBK production guide](https://docs.redhat.com/en/documentation/red_hat_build_of_keycloak/26.6/html/server_configuration_guide/configuration-production-).
These describe production concerns, not the project's proposed numerical rubric.
Source entry points were checked on 2026-09-19; implementation must resolve current,
exact-version references rather than treating these links as a frozen support matrix.
