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
| `keycloak-production-performance` | KEYCLOAK | (any) | health + security + **performance** (metrics required) |
| `rhbk-production-performance` | RHBK | (any) | health + security + **performance** (metrics required) |
| `rhbk-openshift-production-performance` | RHBK | OPENSHIFT | + capacity, ha, performance (metrics required) |

Alias `default` → `keycloak-production`.

When metrics are configured on a target and no explicit profile is passed, the resolver
may suggest a `*-performance` profile. **An explicit profile name always wins.**

## MCP / REST

| Surface | Call |
|---------|------|
| MCP | `keycloak_list_assessment_profiles` |
| REST | `GET /api/v1/assessment-profiles` |
| Run | `keycloak_run_assessment` / `POST /api/v1/targets/{id}/assessments?profile=` |

See [rule-catalog.md](rule-catalog.md) and [scoring.md](scoring.md).
