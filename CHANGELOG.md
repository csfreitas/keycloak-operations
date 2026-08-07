# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.6.0-SNAPSHOT] — 2026-08-07

### Added

- Semantic metrics stack: `MetricsService` (status/summary/category), `PerformanceSummary`, availability cache
- Real `MetricsEvidenceCollector` + performance rule pack (`KC-PERF-*`) and performance profiles
- REST metrics: `/status`, `/summary`, `/http|database|jvm|cache|authentication|runtime|cluster`
- MCP tools: `keycloak_get_metrics_status`, `keycloak_get_performance_summary`, `keycloak_get_metrics`
- Lab Prometheus compose + `scripts/smoke-metrics.sh`
- Docs: metrics-catalog, prometheus-integration, openshift-monitoring, performance-assessment, performance-slo

### Changed

- Artifact / app version **0.6.0-SNAPSHOT**
- Metrics are optional for assessment completeness unless a performance profile requires them
- No raw PromQL accepted from REST/MCP

## [0.5.0-SNAPSHOT] — 2026-08-07

### Added

- Assessment depth: HA / security / production / capacity / admin-security YAML rule packs
- Profiles: `rhbk-openshift-production-ha`, expanded `AssessmentProfile` metadata, profile resolver
- MCP tools: `keycloak_run_assessment`, `keycloak_health_check`, profile/assessment/findings list/get
- REST `GET /api/v1/assessment-profiles`
- Flyway V6: evidence completeness, confidence, category scores, rule counters, finding subject, health `duration_ms`
- Inventory evidence: readyBelowDesired, zone/node concentration, resource present flags, probes
- Docs: health-check, assessment-profiles, rule-catalog, scoring

### Changed

- Artifact / app version **0.5.0-SNAPSHOT**
- Assessment summaries expose completeness, confidence, category scores, finding counts
- PASS / SKIPPED / NOT_EVALUATED findings are not persisted as lifecycle OPEN

## [0.4.0-SNAPSHOT] — 2026-08-07

### Added

- Real per-target `InfrastructureClientFactory` (OpenShift/Kubernetes, token/kubeconfig/in-cluster)
- Target-aware `EnvironmentDiscovery` and structured `EnvironmentInfo`
- `InfrastructureInventory` + `InventoryService` (workload, pods, topology, HPA, PDB, resources, networking)
- MCP `keycloak_get_inventory`; REST `/environment`, `/inventory`, `/topology`
- Evidence catalog and collectors that emit stable keys with `targetId`
- Snapshots persist sanitized inventory (configurationHash / runtimeStateHash)
- Rule pack index (`rules/index.yaml`), profile pack filtering, duplicate rule-id detection
- GitHub Actions CI (`mvn -B clean verify`)
- Fabric8 kubernetes-server-mock isolation/inventory tests
- Docs: infrastructure-inventory, infrastructure-authentication, evidence-catalog

### Fixed

- Minimum replicas policy aligned to threshold **2** (KC-OCP-HA-001)
- Target registry list order preserved (`LinkedHashMap`)
- Test HTTP ports randomized to avoid local port clashes

### Security

- TLS verification on by default (`trust-insecure=false`)
- Secrets omitted from namespaced RBAC; Secret contents never inventoried
- Infra clients fingerprinted per target (no cross-target credential reuse)

## [0.1.0] — 2026-08-07

### Added

- Quarkus 3.38.1 MCP server (`keycloak-operations-mcp`) with Streamable HTTP transport
  (Quarkiverse MCP Server 1.13.1) and optional STDIO Maven profile.
- **Multi-target**: `Target` / `TargetRegistry` / `TargetResolver` / `CredentialProvider` /
  `KeycloakClientFactory` — one MCP manages many Keycloak/RHBK environments via `targetId`.
- Target tools: `keycloak_list_targets`, `keycloak_get_target`, `keycloak_find_targets`.
- Read-only Keycloak Admin API tools (all require `targetId`): server info, realms, clients,
  users, groups, roles.
- Stable Admin API adapter with Keycloak Admin Client 26.0.12 (client credentials per target).
- Product detection for community Keycloak vs Red Hat build of Keycloak (RHBK).
- Capability detection driven by server feature flags (not rigid version equals).
- Sensitive data filter that redacts secrets, passwords, and tokens from tool output.
- Structured audit logging with `targetId` and Micrometer / OpenTelemetry hooks.
- Assessment engine foundations: Evidence → Rule → Finding → Scoring (all stamped with `targetId`).
- Sample YAML rules for OpenShift HA (`KC-OCP-HA-001`) and common security/production stubs.
- Environment discovery skeleton (`UNKNOWN` when cluster discovery is disabled).
- Local demo stack: Keycloak 26.7.1 via `dev/compose.yaml`, optional second instance
  (`--profile multi-target`) for isolation demos (`lab-keycloak-a` / `lab-keycloak-b`),
  plus **PostgreSQL** for platform persistence.
- **Operations Platform backend**: Flyway migrations, assessment/health/audit/snapshot
  persistence, REST `/api/v1` (fleet, overview, history, semantic metrics), `MetricsProvider`
  abstraction, SSE events stub, UI architecture docs.
- OpenShift and Kubernetes deploy manifests (read-only RBAC, hardened securityContext).
- Unit / persistence / REST tests including multi-target isolation and secret-leakage checks.
- Documentation under `docs/` including persistence, REST API, snapshots, audit, UI concepts.

### Security

- Default `mcp.read-only=true` — no write MCP tools registered in 0.1.0.
- SSRF protection: tools accept only registered `targetId`, never arbitrary URLs.
- Client secrets and `credentialRef` are never returned to the LLM or stored as plaintext in DB.
- Audit mode default `SANITIZED`; `SensitiveDataFilter` applied before persistence and responses.
- OpenShift Secret template uses placeholders only.


### Compatibility notes

- Verified against community Keycloak container `quay.io/keycloak/keycloak:26.7.1`
  with dual-target lab (`mcp-demo` vs `company-b`).
- RHBK 26.6.x is supported by design (same Admin API surface) but **not auto-tested** in CI
  because images require authenticated access to `registry.redhat.io`.
