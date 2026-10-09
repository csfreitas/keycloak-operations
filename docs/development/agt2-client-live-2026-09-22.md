# AGT2 — Authenticated local reference client, 2026-09-22

Status: **BOUNDED LOCAL CLIENT INTEGRATION VERIFIED; AGT2 PARTIAL**.
All execution dates use America/Sao_Paulo unless an artifact records UTC.

## Scope and source

The approved next step was the initial reference client's two-operator local
OIDC/MCP integration, **without AI**. The existing server, console, roles and source
fixtures are unchanged. New code is a separate
[lab host/codec/check suite](../../scripts/configuration-client-lab.mjs), with
[offline tests](../../scripts/configuration-client-lab.test.mjs). The existing checker
now invokes that suite and composes caller cancellation with its transport deadline.
CI includes these offline checks; it does not run the authenticated lab automatically.

HEAD remains `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`; **392 initial dirty/untracked Git status entries**
(default grouping) were preserved. No staging, commit, push, rebase, tag or release.
The [source manifest](evidence/agt2-client-live-2026-09-22.json) compares all source
inputs with the previous checkpoint and records the exact tested logs and run IDs.
Historical ledgers/model-trial artifacts are preserved, not rerun or reclassified.

Manifest: **670 source inputs**, four existing inputs changed, two new scripts and
none removed. **594** product/reference-core inputs remain hash-identical, and all
**29** historical model-trial artifacts pass integrity checks. Aggregate source SHA-256:
`a509f724f4c451fb5b182545974ea0a1e886fa78c889ba6e92b433a538c55f83`.
Hashes identify tested bytes; they are not independent security or authenticity proofs.

## Actual boundary exercised

| Component | Actual local configuration |
|---|---|
| Platform and source identity provider | Community Keycloak **26.7.1**, existing disposable fixtures |
| Operator realm scope | `rhea-realm`, `config-realm`, MCP client `keycloak-config-mcp` |
| Operator client scope | `chris-client`, `config-client`, separate signed subject and MCP session |
| Authentication | Existing real authorization code + PKCE S256; backend validates signed OIDC identity |
| Negative client | `keycloak-ops-ui` token cannot gain MCP configuration access |
| Host endpoint | Fixed `http://localhost:18081/mcp`; no arbitrary URL or target-token passthrough |
| MCP | Negotiated **2025-11-25**, initialized notification, bounded JSON/SSE, exact response IDs/session/version headers, explicit session DELETE |
| Reference client | Unchanged profile/answer **0.1.0**, observation schema **1.0**, exactly two scoped tools |
| Provider account | Existing separate fixture `configuration-reader`, only `view-realm`/`view-clients` |
| Runtime | Explicit `podman-machine-default-root`, four per-run-owned containers/network, PostgreSQL tmpfs |

The transport design was checked against the pinned
[MCP transport specification](https://modelcontextprotocol.io/specification/2025-11-25/basic/transports).
This **narrow finite lab adapter is not full protocol conformance**: it has no SSE
resumption/retry, generic server requests, production TLS or OAuth discovery/refresh.
Unknown protocol/session/response shapes fail rather than trigger broader capabilities.
Each session captures one bearer/context generation; caller arguments cannot choose
credentials, endpoints, roles or tools. Raw provider exceptions are not returned.

## Validation results

| Check | Executed result |
|---|---|
| Pre-change clean verify, Java 21.0.10 via per-command jenv | **1820 passed**, zero failures/errors, **9 opt-in ITs skipped** |
| New adapter/codec/cancellation tests | **37 passed** |
| Focused new adapter + existing checker + reference client | **206 passed**, includes the 37 |
| Combined Node regressions, Node 24.19.0 | **534 passed**, zero failures/skips; includes the 206 |
| Real authenticated runner, first fresh lab | **72/72 passed**, exit **0** |
| Real authenticated runner, second fresh lab | **72/72 passed**, exit **0**, same implementation source |
| JWT log scan and owned cleanup | Passed in both runs |
| Git whitespace check | Passed |
| Documentation integrity | **159 documents, 1139 local links, 152 requirement definitions and 17 milestones**, zero errors/warnings; six parser self-test groups passed. External URLs are not checked by the offline checker |

The 72 checks comprise **55 existing server/provider boundaries + 17 new client
checks**. They are not 72 new features or an LLM evaluation. Both operators exercised
actual source reads and exact Boolean facts/reference pointers through the reference
library. Foreign/unknown scopes are rejected, stale configured resource pins fail
without facts, a later allowed collection recovers, and a REST-only client cannot
upgrade its access by claiming to be the MCP client. Prior operator packets cannot
be adopted by another host or reused after local sign-out.

The two delay checks **hold an actual received catalogue response at the host**,
then close the local/MCP session or exhaust the client question budget. No late
answer becomes usable. This is controlled delivery-delay evidence, **not** a measured
slow upstream, remote cancellation guarantee or IdP-token-revocation test. Source
failure here is a configured identity-pin mismatch, not deletion/recreation or an
induced provider outage. No source realm/client was modified to make tests pass.

No UI/browser run, new UI build, RHBK/OpenShift run, model/provider call, broad PII
domain, persistence/audit-row inspection or independent operator reproduction was
performed. Java was not rerun after the baseline because only JavaScript harness/CI/
documentation changed; production Java/UI/dependencies and core profiles are hash-
identical to the previous checkpoint. No global Java/Node/runtime selection changed.

Reproduce after the documented prerequisites, from the repository root:

```sh
env -u RUN_KEYCLOAK_IT -u RUN_RHBK_IT -u RUN_PROMETHEUS_IT \
  JENV_VERSION=21 CONTAINER_CONNECTION=podman-machine-default-root jenv exec mvn clean verify
node --test scripts/configuration-client-lab.test.mjs scripts/configuration-lab-checks.test.mjs dev/access-aware-client/*.test.mjs
node --test dev/access-aware-client/*.test.mjs dev/reference-agent/*.test.mjs \
  dev/secops-scenarios/*.test.mjs scripts/*.test.mjs dev/identity-lab/*.test.mjs
IDENTITY_LAB_PODMAN_CONNECTION=podman-machine-default-root bash scripts/validate-configuration-lab.sh
```

Use Node 24 per command and an explicitly approved local runtime, never an ambient
cluster. [Runbook](../../dev/identity-lab/README.md) documents ports, fixtures,
ownership, cleanup and safe failure recovery. The test-host login is deliberately
fixture-specific, not a production password-automation recommendation.

## Cleanup, provenance and version decision

Fresh runs:

- `30650806-3d82-41a0-a17f-76004d3d538f`, private artifacts `/private/tmp/kcops-configuration.TseKYN`.
- `d3291aeb-2efd-4e46-97e5-43eef6c8a340`, private artifacts `/private/tmp/kcops-configuration.4Qm3SO`.

Both runners stopped owned processes, scanned logs, removed only verified owned
container/network IDs and released their shared lock. Independent final inventory:
**zero containers, zero volumes, only the default podman network**; no lab lock or
listeners on 15432/18080/18180/18280/18081/19001/18300. The same four reusable cached
images remain: Community Keycloak 26.7.1, PostgreSQL 16, RHBK 26.6, Prometheus v2.55.1.
No prune, image/volume deletion or VM restart. Removed tmpfs fixture database state
is disposable and recoverable by recreating the public fixtures, not a retained backup.
Private diagnostic artifacts are retained as evidence, with no recorded bearer JWTs.

Artifact versions remain unchanged: backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**,
UI/root lockfile **0.8.1-dev.0**, AGT1 **0.2.1**, scoped client/answer **0.1.0**,
observation **1.0**, report **1.1**, findingDetails **1.0**, Flyway **V1–V10**,
catalogue **1**. No backend grant/configuration, dependency or workflow-invariant change.
AGENTS.md remains applicable without a duplicate checkpoint log.

## Release decision and next step

The **bounded authenticated local client gate is now met**; do not continue describing
it as unimplemented. AGT2 remains partial, all whole exit criteria remain open, and
there is no model/client/product-wide certification. The skill-guided review preserved
separate core/lab boundaries and explicit cancellation/credential ownership, rather
than silently turning the prototype into a general privileged host.

The user's conditional commit/release permission is retained. It is not yet exercised:
the repository's [H1 review/acceptance](../milestones/h1-trust-closure.md), consolidated
source/diff review and [independent operator reproduction](../milestones/d1-local-workflow.md)
remain open. Existing SNAPSHOT/dev artifacts are not release tags. The passing client
slice alone does not authorize blanket staging of the accumulated working tree or
waiving documented security limitations.

Next bounded delivery: consolidate H1 evidence and review a coherent experimental-
release candidate, including pre-existing changes/dependencies, then independently
reproduce the guide and verify exact release versions/artifacts before publication.
General client deployment, RHBK/OpenShift, IdP logout/delegation/revocation, broader
domains and separately approved provider/model evaluation remain distinct gates.
