# Integration tests

This directory is reserved for container-based integration tests of
`keycloak-operations-mcp` against real Keycloak / RHBK images.

## Compatibility matrix

| Target | Image | Auth required | Current status |
|--------|-------|---------------|-----------------|
| Keycloak Community 26.7.x | `quay.io/keycloak/keycloak:26.7.1` | No (public Quay) | **Automated read-only MCP smoke path** via `dev/compose.yaml` and CI |
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

```bash
# From repository root
podman compose -f dev/compose.yaml up -d
./scripts/setup-dev.sh
mvn clean verify
export KEYCLOAK_URL=http://localhost:8080
export KEYCLOAK_AUTH_REALM=master
export KEYCLOAK_CLIENT_ID=keycloak-mcp
export KEYCLOAK_CLIENT_SECRET=change-me
mvn quarkus:dev
# in another terminal:
./scripts/smoke-mcp.sh
```

## Scope of the automated community smoke test

The `community-keycloak-integration` CI job starts PostgreSQL, Keycloak 26.7.1,
and Prometheus, configures a disposable service account, starts the packaged
application, and runs `scripts/smoke-mcp.sh`. The smoke covers target discovery,
representative Admin REST reads, health, environment discovery, and generation
of the operations report through MCP.

It does **not** verify controlled writes, Keycloak 26.6, RHBK, OpenShift, or
Kubernetes. The JUnit classes named `*IT` remain opt-in placeholders until they
perform real assertions and must not be cited as compatibility evidence.

Enable RHBK tests explicitly, for example:

```bash
export RHBK_IMAGE=registry.redhat.io/rhbk/keycloak-rhel9:26.6
export RUN_RHBK_IT=true
mvn -Dit.test=RhbkIT verify
```

Until those classes exist, use the compose + smoke script path above.
