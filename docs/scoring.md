# Scoring

`AssessmentScoring` is deterministic (no LLM).

## Overall score

Start at **100**. Subtract penalties for findings with status `OPEN`, `FAIL`, or
`WARNING` only:

| Severity | Penalty |
|----------|---------|
| CRITICAL | 25 |
| HIGH | 15 |
| MEDIUM | 8 |
| LOW | 3 |
| INFO | 0 |

`PASS`, `NOT_EVALUATED`, and `SKIPPED` never reduce the score. Floor is **0**.

That arithmetic is a legacy field, not permission to show a favorable conclusion. `AssessmentResult.scoreAvailable` requires COMPLETE status, 100 evidenceCompleteness, at least one evaluated rule, zero not-evaluated rules and no missing evidence. UI/Markdown and compact MCP must show an inconclusive/unavailable score when this gate fails. Category arithmetic does not bypass that guard.

## Category scores

Same algorithm scoped to normalized categories:

- availability (includes `high-availability` / `ha`)
- security
- configuration (includes `production`)
- operations
- observability
- capacity

## Completeness & confidence

Returned on `AssessmentResult` (and persisted in V6 columns / summary JSON):

| Field | Meaning |
|-------|---------|
| `evidenceCompleteness` | Minimum of required-source completeness (rounded percentage) and evaluated-rule completeness (floored percentage); no applicable rules gives 0, a material partial source caps the result at 99 |
| `confidence` | HIGH / MEDIUM / LOW from Keycloak (+ infra when bound) coverage |
| `status` | COMPLETE / PARTIAL / FAILED |

See [assessment-engine.md](architecture/assessment-engine.md).

Completeness/confidence are bounded implementation heuristics, not statistical probabilities or proof that every resource/configuration was inspected. Inventory coverage needs a known denominator; otherwise show counts and gaps. High confidence is traceability, not certification.

## Planned evolution — not the current algorithm

[SCORE1](milestones/score1-explainable-rating.md) proposes a normalized 1–100 rating
with per-control reasons, product/version applicability, reviewed weights/critical
caps and public lifecycle/patch/CVE evidence. Its [architecture proposal](architecture/operational-rating.md)
separates coverage from condition and accounts for RHBK backports. Keeping the upper
bound at 100 does not equate the new rubric with legacy arithmetic, whose floor remains
0. No new rubric, feed integration or rating is implemented
by the planning documentation, and existing availability guards remain in force.
