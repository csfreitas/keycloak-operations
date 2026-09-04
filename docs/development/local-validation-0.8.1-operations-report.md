# Local validation — 0.8.1 Slice 1 and Operations Report

## Repository and validation source

- Repository: `https://github.com/csfreitas/keycloak-operations.git`
- Expected local directory: `keycloak-operations`
- Remote name: `origin`
- Rewritten `main` baseline before the feature: `6ac9109`
- Validation branch: `feature/0.8.1-client-urls-operations-report`
- Validation source: the exact remote branch HEAD supplied by the cloud Work

The 0.8.1 and Operations Report implementation is published on its dedicated
validation branch and is not part of `origin/main` until reviewed and merged.
Do not validate `origin/main` and present that as the new implementation.

If no validation branch, commit SHA, pull request, or patch containing the
pending changes was provided, stop and report exactly:

```text
BLOCKED: the cloud changes are not available in Git. Provide a validation
branch/commit/PR or a patch before running the local validation.
```

Do not invent a branch name and do not recreate the implementation manually.

## Obtain the repository

If it is not cloned yet:

```bash
git clone https://github.com/csfreitas/keycloak-operations.git
cd keycloak-operations
```

If it already exists locally:

```bash
cd keycloak-operations
git remote get-url origin
git status --short
```

Do not overwrite or discard local modifications. If the working tree is not
clean, stop and report it before checkout.

After the cloud Work provides `<VALIDATION_BRANCH>` and `<EXPECTED_SHA>`:

```bash
git fetch origin
git switch --create <VALIDATION_BRANCH> --track origin/<VALIDATION_BRANCH>
git rev-parse --short HEAD
git status --short
```

If the branch already exists locally, use:

```bash
git switch <VALIDATION_BRANCH>
git pull --ff-only
git rev-parse --short HEAD
git status --short
```

The resolved HEAD must equal `<EXPECTED_SHA>`. If it does not, stop. Never run
the tests against a different commit and describe them as validation of the
cloud implementation.

## Project instructions

From the repository root, read completely:

1. `AGENTS.md`
2. `docs/project-state.md`
3. `docs/milestones/README.md`
4. `docs/milestones/0.8.1-realm-client-administration.md`
5. `docs/milestones/0.8.2-fleet-reporting-target-onboarding.md`
6. `docs/development/local-cloud-workflow.md`

Identify the current commit and inspect the working tree before running anything.
Git, code, tests, and repository documentation are the source of truth. Do not
rely on previous conversation history. Do not change code, commit, push, or mark
a milestone complete during this validation task.

## Objective

Execute the validation that the cloud workspace cannot perform because it lacks
Java 21 and a Docker/Podman daemon. Validate the current working tree against the
disposable community stack without exposing credentials or internal endpoints.

## Preconditions

- Java 21
- Maven 3.9+
- Node.js 20+
- Docker Compose or Podman Compose
- `curl` and `jq`

## Required validation

Run all commands from the checked-out `keycloak-operations` repository root.
First record the exact source and toolchain:

```bash
pwd
git remote get-url origin
git branch --show-current
git rev-parse --short HEAD
git status --short
java -version
mvn -version
node --version
npm --version
docker info
docker compose version
```

The working tree must be clean before validation. Then run the mandatory build
and unit-test gates:

```bash

mvn clean verify

cd ui
npm ci
npm run test:run
npm run build
cd ..

docker compose -f dev/compose.yaml up -d --wait postgres keycloak prometheus
docker compose -f dev/compose.yaml ps
./scripts/setup-dev.sh

export POSTGRES_JDBC_URL=jdbc:postgresql://localhost:5432/kcops
export KEYCLOAK_URL=http://localhost:8080
export KEYCLOAK_CLIENT_SECRET=change-me
export PLATFORM_METRICS_PROMETHEUS_ENDPOINT=http://localhost:9090

java -jar target/quarkus-app/quarkus-run.jar
```

Keep the application running. In a second terminal, enter the same repository
and execute, after readiness succeeds:

```bash
cd keycloak-operations
curl -fsS http://localhost:9001/q/health/ready | jq .
./scripts/smoke-mcp.sh
```

Always stop the disposable stack after collecting the result:

```bash
cd keycloak-operations
docker compose -f dev/compose.yaml down -v
```

## Evidence to return

Return a concise report with:

```text
Repository URL, branch, exact commit SHA, and clean/dirty working-tree state
Java, Maven, Node, and container runtime versions
mvn clean verify: PASS/FAIL and test/skip counts
UI tests/build: PASS/FAIL and counts
Compose health: PASS/FAIL per service
MCP smoke: PASS/FAIL and tools exercised
Operations Report: completeness, health status, assessment status, metrics status
Controlled client URL write IT: SKIPPED unless a real plan/apply/read-back/restore flow ran
RHBK: NOT VERIFIED unless an authenticated RHBK target was actually exercised
Failures and sanitized logs
```

Do not paste tokens, passwords, client secrets, kubeconfig, customer names,
internal URLs, or unrestricted environment output. Do not claim that the
placeholder `*IT` classes prove compatibility.
