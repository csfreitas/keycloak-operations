# Compatibility

Honest matrix for the current **0.8.0-SNAPSHOT** working tree. Do not treat untested targets as verified.

## Platform / product

| Target | Version | Status | Notes |
|--------|---------|--------|-------|
| Keycloak Community | 26.7.x | **Automated smoke path implemented; current changes NOT VERIFIED live** | Container `quay.io/keycloak/keycloak:26.7.1` via `dev/compose.yaml` |
| Keycloak Community | 26.6.x | **Design-compatible, NOT VERIFIED** | Same stable Admin API family; not the default compose image |
| Red Hat build of Keycloak (RHBK) | 26.6.x (26.6.5 latest noted) | **Design-compatible, not auto-tested** | Images on `registry.redhat.io` require authenticated pull |
| Java | 21 | Required | |
| Quarkus | 3.38.1 | Declared in `pom.xml` | Verified coordinate on Maven Central (Aug 2026) |
| Quarkiverse MCP Server | 1.13.1 | Declared in `pom.xml` | MCP protocol 2025-11-25 |
| Keycloak Admin Client | 26.0.12 | Declared in `pom.xml` | Stable Admin REST API (not Admin API v2) |
| Kubernetes | 1.27+ (typical) | Inventory implemented; live matrix incomplete | Workloads, pods, nodes, topology, policy and networking evidence |
| OpenShift | 4.14+ (typical) | Inventory implemented; live matrix incomplete | Kubernetes evidence plus Routes, Operator/CR and OpenShift metadata |
| VM | — | **NOT IMPLEMENTED** | Runtime classification exists; no host/process/config collector |
| Docker | — | **NOT IMPLEMENTED** | No explicit runtime type or inventory collector |

## What “tested” means here

- **Automated smoke path implemented:** developers and CI can run compose + setup
  + package + smoke against community Keycloak 26.7.1 without registry
  credentials. The current working tree becomes `TESTED` only after that path
  completes and the exact commit/result is recorded.
- **Not auto-tested:** no CI job in this repository pulls RHBK from
  `registry.redhat.io`. Manual validation with a subscribed registry is welcome
  but not claimed as done by default releases.

## Product detection

`KeycloakVersionDetector` classifies:

- **RHBK** when product strings contain `Red Hat`, `RHBK`, or `build of Keycloak`
- **KEYCLOAK** when the product name indicates community Keycloak
- **UNKNOWN** otherwise

Capabilities prefer **feature flags** from server info over rigid `version.equals`
checks. `adminApiV2` remains outside the primary stable Admin REST path by design.

## Admin API strategy

| API | Current role |
|-----|------------|
| Stable Admin REST API | **Primary** |
| Admin API v2 | Stub only (`UNSUPPORTED_CAPABILITY`) |

See also [integration-tests/README.md](../integration-tests/README.md).
