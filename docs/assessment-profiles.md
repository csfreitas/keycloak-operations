# Assessment profiles

Profiles select which **rule packs** run and which **evidence sources** are expected.

## Built-in profiles

| Profile | Products | Runtimes | Rule packs |
|---------|----------|----------|------------|
| `keycloak-production` | KEYCLOAK | (any) | health-check, security-baseline |
| `rhbk-production` | RHBK | (any) | health-check, security-baseline |
| `rhbk-openshift-production` | RHBK | OPENSHIFT | + capacity, ha |
| `keycloak-kubernetes-production` | KEYCLOAK/RHBK | KUBERNETES | + capacity, ha |
| `rhbk-openshift-production-ha` | RHBK | OPENSHIFT | + capacity, ha, admin-security |

Alias `default` → `keycloak-production`.

## Resolution

`AssessmentProfileResolver` suggests a profile from target type, runtime evidence,
and environment. **An explicit profile name always wins** — the resolver is only
used when the caller passes blank/null.

## MCP / REST

| Surface | Call |
|---------|------|
| MCP | `keycloak_list_assessment_profiles` |
| REST | `GET /api/v1/assessment-profiles` |
| Run | `keycloak_run_assessment` / `POST /api/v1/targets/{id}/assessments?profile=` |

See [rule-catalog.md](rule-catalog.md) and [scoring.md](scoring.md).
