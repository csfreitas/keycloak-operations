# Development

AI-assisted workflow: [`development/ai-assisted-development.md`](development/ai-assisted-development.md) and root [`AGENTS.md`](../AGENTS.md).

For a durable handoff between this cloud workspace, a local Docker/Podman environment, and CI, see [`development/local-cloud-workflow.md`](development/local-cloud-workflow.md).

## Prerequisites

- Java 21 (per-command selection with `JENV_VERSION=21 jenv exec` when using jenv)
- Maven 3.9+
- Node.js 24 LTS + npm recommended (Fleet Console in `ui/`; supported engines in `ui/package.json`)
- Podman or Docker (Compose)
- `curl` and `jq` (for setup / smoke scripts)

## Declared dependency versions (repository checkpoint)

| Component | Version |
|-----------|---------|
| Quarkus | 3.39.4 |
| Quarkiverse MCP Server | 1.13.2 |
| Keycloak Admin Client | 26.0.12 |
| Keycloak container (local demo) | 26.7.1 |

## Local Keycloak

```bash
podman compose -f dev/compose.yaml up -d
./scripts/setup-dev.sh
```

This starts Keycloak Community `26.7.1`, imports realm `mcp-demo`, and creates the
`keycloak-mcp` service-account client in `master`.

**Warning:** `setup-dev.sh` grants broad admin roles for local convenience.
Production must use Fine-Grained Admin Permissions (FGAP) / least privilege —
**not** `realm-admin`.

## Build and unit tests

```bash
JENV_VERSION=21 jenv exec mvn clean verify
cd ui && npm ci && npm run test:run && npm run build
```

Default verification does not require a running Keycloak but does start disposable PostgreSQL for persistence/HTTP tests. Opt-in integration/smoke checks require their explicit fixtures. Use [local resource hygiene](../integration-tests/README.md); preserve reusable data and never globally prune.

Current end-to-end work is [D1](milestones/d1-local-workflow.md). Its real-token baseline and local mock-cluster tests are separate from full browser and actual OpenShift acceptance. [Release/version bookkeeping](development/release-versioning.md) defines all metadata to reconcile before the next functional slice.

## Fleet Operations Console (local)

With backend on `:8081` and compose stack up:

```bash
cd ui
npm ci
npm run dev
# http://localhost:3000
```

Production UI image: `ui/Dockerfile` (nginx unprivileged). OpenShift: `deploy/openshift/100-ui-deployment.yaml` (+ service/route). See [`../ui/README.md`](../ui/README.md).

## Run (Streamable HTTP)

For a packaged local lab, explicitly set `QUARKUS_PROFILE=local-lab`; listeners default to loopback. Without a lab profile, operations fail closed until Identity A is configured. For authenticated access use `oidc`, the backend token audience and exact role/target grants in [identity-model.md](identity-model.md). Never expose local-lab on a shared/public interface.

```bash
export KEYCLOAK_URL=http://localhost:8080
export KEYCLOAK_AUTH_REALM=master
export KEYCLOAK_CLIENT_ID=keycloak-mcp
export KEYCLOAK_CLIENT_SECRET=change-me
mvn quarkus:dev
```

MCP endpoint: `http://localhost:8081/mcp`  
Health: `http://localhost:9001/q/health` (MCP management; Keycloak health remains on `:9000`)

## Smoke test

```bash
./scripts/smoke-mcp.sh
```

## STDIO profile

```bash
export KEYCLOAK_URL=http://localhost:8080
export KEYCLOAK_CLIENT_ID=keycloak-mcp
export KEYCLOAK_CLIENT_SECRET=change-me
mvn -Pstdio quarkus:dev
```

See `.vscode/mcp.stdio.example.json` for a sample STDIO host configuration
(env var references; no secrets committed).

## Demo data

| Kind | Value |
|------|-------|
| Realm | `mcp-demo` |
| Users | `alice` / `alice`, `bob` / `bob` |
| Clients | `portal-web` (public), `backend-api` / `backend-api-secret` |
| Roles | `user`, `admin` |
| Groups | `users`, `administrators` |

Demo client secrets are for **local development only**.

## Project conventions

Authenticated end-to-end workflow: [disposable identity/browser lab](../dev/identity-lab/README.md), with named resources, explicit metrics-disabled fixtures, exact loopback origins and cleanup. This is independent of the regular demo data above.

- Package root: `io.github.keycloakmcp`
- DTOs are Java records
- CDI `@ApplicationScoped` services
- Operational reads and planning remain available in default read-only mode; approve/reject/apply require the controlled change lifecycle and `mcp.read-only=false`
- Prefer feature flags over `version.equals` for capability detection
