# Assessment screen (conceptual)

Route: `/targets/{targetId}/assessments`

## Purpose

Run assessments, browse history, and triage findings.

## APIs

- `POST /api/v1/targets/{targetId}/assessments`
- `GET /api/v1/targets/{targetId}/assessments` (paginated)
- `GET /api/v1/assessments/{assessmentId}`
- `GET /api/v1/targets/{targetId}/findings` (filters: severity, status, category)

## Layout

1. **Score header** — overall score plus category bars: Availability, Security, Performance, Capacity, Observability, Operations
2. **Finding filters** — Critical / High / Medium / Low / Info
3. **Finding list** — title, ruleId, status lifecycle (`OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `ACCEPTED_RISK`, `SUPPRESSED`)
4. **Evidence panel** — sanitized JSON evidence (never secrets)
5. **History** — compare two runs (future: side-by-side score + finding delta)

## Future MCP (same services)

- `keycloak_list_assessments`
- `keycloak_get_assessment`
- `keycloak_get_latest_assessment`
- `keycloak_compare_assessments`
