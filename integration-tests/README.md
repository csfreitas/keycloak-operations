# Integration tests

This directory is reserved for container-based integration tests of
`keycloak-operations-mcp` against real Keycloak / RHBK images.

## Compatibility matrix

| Target | Image | Auth required | Current status |
|--------|-------|---------------|-----------------|
| Keycloak Community 26.7.x | `quay.io/keycloak/keycloak:26.7.1` | No (public Quay) | **TESTED**: version, controlled client URL write/restore, stale-plan rejection, and read-only MCP/report smoke |
| Keycloak Community 26.6.x | `quay.io/keycloak/keycloak:26.6.x` | No | **NOT VERIFIED** by the automated smoke job |
| RHBK 26.6.x (e.g. 26.6.5) | `registry.redhat.io/rhbk/keycloak-rhel9:26.6` (exact tag may vary) | **Yes** — Red Hat registry credentials | **Not auto-tested** |

## Why RHBK is not auto-tested

RHBK container images are published on `registry.redhat.io` and require an
authenticated pull (`podman login registry.redhat.io` with a Red Hat account /
service account pull secret). Public CI runners typically cannot pull these
images without secrets that this open-source repository does not ship.

Therefore:

- Do **not** treat RHBK as “tested” in CI badges or release notes unless a
  job with registry credentials actually ran.
- RHBK 26.6.x is expected to work for Admin API read tools because it shares
  the stable Admin REST API with upstream Keycloak 26.6, but that is a
  **design compatibility** claim, not a CI-verified result for 0.1.0.

## Running community Keycloak integration checks locally

Quarkus tests use a disposable PostgreSQL container named
`keycloak-operations-test-postgres-<run-id>`. It carries the project label
`io.github.keycloak-operations.test-resource=postgresql`, is never reused, and
is removed when the test process finishes normally. This keeps Podman Desktop
entries attributable to this repository and prevents completed runs from
accumulating stopped database containers. On macOS, the Maven test forks also
use the Podman CLI with exact resource names and explicit cleanup because Podman
Desktop can leave the Testcontainers Ryuk sidecar running, and its JVM fallback
can prune unrelated volumes. Linux CI and macOS hosts without Podman keep the
standard Testcontainers lifecycle. The test database uses `tmpfs`, so neither
path creates anonymous data volumes.

The local Compose project is named `keycloak-operations`. PostgreSQL and
Prometheus data use clearly named, reusable volumes instead of anonymous ones:
`keycloak-operations-postgres-data` and
`keycloak-operations-prometheus-data`. A normal shutdown preserves them; a
disposable validation reset removes them:

```bash
# Preserve reusable local data
podman compose -f dev/compose.yaml down --remove-orphans

# Full disposable reset (used by validation/CI)
podman compose -f dev/compose.yaml down -v --remove-orphans
```

```bash
# From repository root
podman compose -f dev/compose.yaml up -d
./scripts/setup-dev.sh
mvn clean verify
export KEYCLOAK_URL=http://localhost:8080
export KEYCLOAK_AUTH_REALM=master
export KEYCLOAK_CLIENT_ID=keycloak-mcp
export KEYCLOAK_CLIENT_SECRET=change-me
mvn -Dit.test=KeycloakCommunity26_7IT,ControlledClientChangeIT failsafe:integration-test failsafe:verify
mvn quarkus:dev
# in another terminal:
./scripts/smoke-mcp.sh
```

## Scope of the automated community smoke test

The `community-keycloak-integration` CI job runs for pull requests and pushes to
`main`. It starts PostgreSQL, Keycloak 26.7.1, and Prometheus; configures a
disposable service account; runs the real Community/version and controlled-write
ITs; starts the packaged application; and runs `scripts/smoke-mcp.sh`. The smoke
covers target discovery, representative Admin REST reads, health, environment
discovery, and generation of the operations report through MCP.

The controlled-write IT creates a uniquely named client only in the imported
disposable realm after enforcing the exact local loopback target. It performs
plan, approval when required, apply, read-back verification, restoration, stale
plan rejection, and fixture removal. It does **not** verify Keycloak 26.6, RHBK,
OpenShift, Kubernetes, or the Web Origin `+` sentinel. Other placeholder `*IT`
classes must not be cited as compatibility evidence.

## Opt-in read-only RHBK check

`Rhbk26_6IT` is a real read-only integration check, not a flag-only placeholder.
Provision the disposable RHBK separately at the **exact** `http://localhost:8280`
URL, import the existing `mcp-demo` realm, and provide a read-only client
`keycloak-mcp-readonly` in `master` with the view/query privileges required to
inspect that realm and its clients. The test does not provision the container,
create clients, grant roles, or mutate target configuration. Never add master
administrator privileges just to expose server-info metadata.

Set the full version obtained from the running RHBK distribution as fixture
metadata (for example, the output version from `kc.sh --version`). If the
restricted Admin REST credential exposes a version, the test compares it
exactly. If server-info is forbidden or metadata is missing, the API version
remains unknown; the environment variable is **not** substituted as observed
API evidence. Outages and invalid credentials are failures, not acceptable
unknown-version outcomes.

```bash
export RUN_RHBK_IT=true
export RHBK_URL=http://localhost:8280
export RHBK_EXPECTED_VERSION=26.6.3.redhat-00002  # Replace with the actual full runtime version.
# Supply RHBK_CLIENT_SECRET through the local secret mechanism; never commit it.
mvn -Dit.test=Rhbk26_6IT verify
```

The profile fixes `mcp.read-only=true`, disables infrastructure/metrics
discovery, and opts into local-lab Identity A (not authenticated OIDC). It checks
permitted Admin REST reads, persisted assessment evidence, a partial operations
report, rule-catalog provenance, and credential non-disclosure. Absent
OpenShift/Prometheus evidence must remain missing. This does **not** validate
OpenShift HA, real IdP authentication, metrics, or RHBK controlled writes.
The usual project test resource supplies and cleans up the disposable platform
PostgreSQL database; runtime fixture cleanup remains the caller's responsibility.
