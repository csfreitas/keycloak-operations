# Rule catalog (0.5)

Packs are registered in `rules/index.yaml`. Deprecated `rules/minimum-replicas.yaml`
is **not** indexed (HA min-replicas is `KC-OCP-HA-001` in the `ha` pack).

## health-check (`rules/common/production.yaml`)

| Id | Severity | Condition (fires when) |
|----|----------|------------------------|
| KC-PROD-001 | MEDIUM | `realm.registrationAllowed` = true |
| KC-PROD-002 | LOW | `realm.loginTheme` = keycloak |
| KC-PROD-003 | MEDIUM | `keycloak.metrics.enabled` = false |
| KC-PROD-004 | MEDIUM | `keycloak.health.enabled` = false |
| KC-PROD-005 | MEDIUM | `keycloak.http.maxQueuedRequests.configured` = false |

## security-baseline (`rules/common/security.yaml`)

| Id | Severity | Condition |
|----|----------|-----------|
| KC-SEC-001 | HIGH | `realm.bruteForceProtected` = false |
| KC-SEC-002 | MEDIUM | `realm.sslRequired` = none |
| KC-SEC-003 | HIGH | `keycloak.clients.wildcardRedirectUri` > 0 |
| KC-SEC-004 | HIGH | `keycloak.clients.wildcardWebOrigin` > 0 |
| KC-SEC-005 | HIGH | `keycloak.clients.implicitFlowCount` > 0 |
| KC-SEC-006 | HIGH | `keycloak.clients.publicWithoutPkceS256` > 0 |
| KC-SEC-007 | HIGH | `keycloak.management.publiclyExposed` = true |

## ha (`rules/openshift/ha.yaml`)

| Id | Severity | Condition |
|----|----------|-----------|
| KC-OCP-HA-001 | HIGH | `deployment.replicas` < 2 |
| KC-HA-002 | HIGH | `keycloak.replicas.readyBelowDesired` = true |
| KC-HA-003 | HIGH | `keycloak.topology.singleZoneConcentration` = true |
| KC-HA-004 | MEDIUM | zone spread missing (PRD) |
| KC-HA-005 | MEDIUM | hostname spread missing (PRD) |
| KC-HA-006 | MEDIUM | replicas ≥ 2 and PDB absent |
| KC-HA-007 | HIGH | HPA present and minReplicas < 2 (PRD) |
| KC-HA-008 | HIGH | `keycloak.topology.singleNodeConcentration` = true |

## capacity (`rules/capacity.yaml`)

| Id | Severity | Condition |
|----|----------|-----------|
| KC-CAP-001 | MEDIUM | CPU request not present |
| KC-CAP-002 | MEDIUM | memory request not present |
| KC-CAP-003 | MEDIUM | memory limit not present |

## admin-security (`rules/common/admin-security.yaml`)

| Id | Severity | Condition |
|----|----------|-----------|
| KC-ADM-001 | HIGH | master realm registration allowed |
| KC-ADM-002 | HIGH | master realm sslRequired = none |

Rules with `appliesWhen.evidenceRequired` become `NOT_EVALUATED` (no score impact)
when evidence is missing. See [rule-development.md](rule-development.md).
