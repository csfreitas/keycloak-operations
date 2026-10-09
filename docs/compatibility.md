# Compatibility

Honest matrix for the current **0.8.1-SNAPSHOT** working tree. Do not treat untested targets as verified. Historical rows retain their original scope; development-version alignment does not revalidate every integration.

## Platform / product

| Target | Version | Status | Notes |
|--------|---------|--------|-------|
| Keycloak Community | 26.7.x | **TESTED on 26.7.1** | Disposable Admin REST version check; controlled client URL, security/flow, create, enable, and disable apply/read-back; stale-plan rejection; MCP smoke, health, assessment, and operations report |
| Keycloak Community | 26.6.x | **Design-compatible, NOT VERIFIED** | Same stable Admin API family; not the default compose image |
| Red Hat build of Keycloak (RHBK) | Fixture `26.6.3.redhat-00002` | **Historical local read-only validation; not public CI** | Two checks recorded on 2026-09-04; no claim for other releases, current cluster or writes |
| Java | 21 | Required | |
| Quarkus | 3.39.4 | Java 21 build/tests passed locally | Maintained 3.39 branch at the 2026-09-18 review; recheck before release |
| Quarkiverse MCP Server | 1.13.2 | Build/tests and local HTTP lab | MCP protocol 2025-11-25; no migration to MCP 2.x |
| Keycloak Admin Client / client-common-synced | 26.0.12 / 26.0.12 | Packaged versions aligned by Quarkus BOM | Stable Admin REST API (not Admin API v2) |
| Node.js | 24.21.0 locally tested | 133 UI tests and build passed | Node 24 LTS selected for CI/build image; engines also permit 22.12+ within 22.x, not separately executed here |
| UI routing/build/tests | Router 7.18.4 / Vite 7.3.6 / Vitest 4.1.11 | Local tests/build passed | React 18.3.1 retained; Vite default browser target is not a full browser-support matrix |
| Kubernetes | No validated range declared | Inventory locally implemented; live matrix incomplete | Exact binding, ownership, policy and networking; mocks do not prove cluster compatibility |
| OpenShift | No validated range declared | Inventory locally implemented; live matrix incomplete | Kubernetes evidence plus Routes, Operator/CR and OpenShift metadata |
| VM | — | **NOT IMPLEMENTED** | Runtime classification exists; no host/process/config collector |
| Docker | — | **NOT IMPLEMENTED** | No explicit runtime type or inventory collector |
| Podman / Compose / physical host / standalone | — | **Infrastructure collectors NOT IMPLEMENTED** | Local Keycloak containers validate API integration, not hosting inventory |

## What “tested” means here

- **Tested:** the disposable compose + setup path and opt-in integration tests
  completed against Community Keycloak 26.7.1, followed by the packaged MCP
  smoke and operations-report generation. This does not extend the claim to
  Keycloak 26.6, RHBK, OpenShift, Kubernetes, or Web Origin `+` semantics.
- **Not auto-tested:** no CI job in this repository pulls RHBK from
  `registry.redhat.io`. Manual validation with a subscribed registry is welcome
  but not claimed as done by default releases.

Latest evidence: [dependency remediation](development/h1-dependency-fix-2026-09-18.md), with 496 backend and 133 UI tests passing and nine opt-in integrations skipped. Actual local identity/report/browser/installation runs and their limits are recorded there; CI execution, image builds/scans, native/STDIO profiles and real RHBK/OpenShift were not validated by the dependency update. Historical [2026-09-04 live validation](development/trust-hardening-validation-2026-09-04.md), [D1 identity](development/local-identity-validation-2026-09-11.md) and [installation identity](development/d1-installation-identity-2026-09-18.md) apply only to their recorded source/fixtures. Full browser/IdP and real cluster acceptance remain open in [D1](milestones/d1-local-workflow.md) and [D2](milestones/d2-rhbk-openshift.md).

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
