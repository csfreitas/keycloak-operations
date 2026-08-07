# Fleet dashboard (conceptual)

The fleet view answers: *which Keycloak/RHBK environments exist, and are they healthy?*

## Data

`GET /api/v1/fleet` → `FleetItem[]`:

- Identity: `targetId`, `displayName`, `productType`, `environment`, `tags`
- Signals: `healthStatus`, `latestAssessmentScore`, timestamps

Missing health/assessment is tolerated (`UNKNOWN` / null score).

## UX notes

- Group by `environment` (DEV/TEST/PRD)
- Color by `HealthStatus` (HEALTHY / WARNING / CRITICAL / UNKNOWN)
- Drill-down to target overview, then assessments/findings
- Do not show URLs with secrets; credential refs stay backend-only

Backend: `FleetService` joins `TargetRegistry` with latest rows from assessment and health tables.
