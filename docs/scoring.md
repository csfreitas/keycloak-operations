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
| `evidenceCompleteness` | 0–100 from collected sources minus not-evaluated penalty |
| `confidence` | HIGH / MEDIUM / LOW from Keycloak (+ infra when bound) coverage |
| `status` | COMPLETE / PARTIAL / FAILED |

See [assessment-engine.md](architecture/assessment-engine.md).
