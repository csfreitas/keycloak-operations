# Local and cloud development workflow

Use Git and the versioned project documentation as the durable handoff between local Codex/IDE work, cloud agents, CI, and human reviewers. Chat transcripts and agent reports are supporting context, not project state.

## Source of truth

Before any task, read in this order:

1. current code and `git status`;
2. executed test results;
3. `git log`;
4. `AGENTS.md` and `docs/project-state.md`;
5. current milestone, requirements, architecture, and ADRs.

## Recommended split

| Environment | Best use |
|---|---|
| Cloud workspace | Planning, implementation, review, pure unit tests, frontend tests, documentation |
| Local workstation | Java 21 build, Compose/Testcontainers, live Keycloak, Prometheus, and disposable integration fixtures |
| OpenShift/Kubernetes lab | Infrastructure inventory, RBAC, monitoring, Operator/CR, topology, and deployment validation |
| CI | Reproducible mandatory gates and published test artifacts |

## Local prerequisites

```text
Java 21
Maven 3.9+
Node.js 20+
Docker or Podman
curl and jq
optional: oc and kubectl
```

RHBK integration additionally requires an authorized Red Hat registry/subscription path. Never store registry credentials in the repository.

## Handoff from cloud to local

1. Put reviewed changes on a dedicated Git branch or download the reviewed diff.
2. On the local workstation, check out the exact commit and confirm `git status`.
3. Start the disposable dependencies:

   ```bash
   docker compose -f dev/compose.yaml up -d
   ./scripts/setup-dev.sh
   ```

4. Run the authoritative Java 21 and UI validation:

   ```bash
   java -version
   docker info
   mvn clean verify
   cd ui
   npm ci
   npm run test:run
   npm run build
   ```

5. Run only explicitly provisioned opt-in integrations. Record every `RUN_*_IT` value and whether the test ran or skipped.
6. Return the exact commit SHA, commands, summaries, failures, and skipped counts to the reviewing Work/PR.

For the current 0.8.1 Slice 1 and Operations Report checkpoint, use the
versioned procedure in
[`local-validation-0.8.1-operations-report.md`](local-validation-0.8.1-operations-report.md).

## Handoff from local to cloud

Push a focused branch or open a draft pull request. Do not copy credentials, kubeconfig, logs with tokens, customer names, or internal endpoints into prompts or Git. Update `docs/project-state.md` only with verified, reusable facts.

## Context preservation

The minimum recovery packet is already versioned:

- `AGENTS.md` — workflow and invariants;
- `docs/project-state.md` — current compact state;
- `docs/milestones/README.md` — current/next delivery slice;
- milestone requirements, architecture, ADRs, and tests.

This permits a new local or cloud agent to resume without relying on a private conversation. A report from an agent never proves implementation or test execution by itself.

## Validation labels

Always use explicit labels:

```text
IMPLEMENTED
PARTIAL
PLANNED
NOT IMPLEMENTED
NOT VERIFIED
TESTED
SKIPPED
```

Running compilation with a different Java release is diagnostic only. It does not replace the Java 21 project baseline.
