# Operator-managed platform — documentation delivery, 2026-10-09

## Scope and source

Authorized scope: formalize the architecture decision, initial installation contract,
configuration ownership and roadmap/context. Documentation and one design YAML only;
no implementation, installation or runtime change.

Inspected repository: `mcp-server-keycloak`, HEAD
`572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`. Initial Git status contained **395** dirty/untracked
entries. A pre-edit SHA-256 inventory captured **892** tracked/nonignored files.
Existing work was preserved; nothing staged, committed, pushed, rebased or released.

## Delivery and review

- [ADR 0012](../adr/0012-operator-managed-portable-platform.md): accepted direction,
  alternatives and recovery; separate installer and portable modular hub.
- [Architecture](../architecture/operator-managed-platform.md): actual source gaps,
  direct versus optional remote/offline collection, identity and trust boundaries.
- [Draft installation contract 0.1](../architecture/operator-installation-contract.md)
  and [non-deployable example](../architecture/examples/keycloak-operations-v1alpha1.yaml):
  closed initial field set, ownership, status, lifecycle and unimplemented prerequisites.
- [OP1](../milestones/op1-operator-installation.md): planned implementation/acceptance;
  three stable roadmap requirements, linked ONB1/D2/PORT1/PORT2/P4 dependencies.
- Architecture/indexes, roadmap, project-state, AGENTS.md and Unreleased/version
  bookkeeping reconciled. Historical runtime ledgers and acceptance states retained.

Independent architecture and security subagent reviews were read-only. Corrections
incorporated: ONB1 blocks onboarded use rather than empty installation; first-install
database state/ownership requires an explicit safe gate; backend client ID is separate
from audience; first-administrator bootstrap and grants require a governed channel.
These reviews are document review, not independent operator reproduction of software.

## Validation

Fresh offline checks passed. This section must not be interpreted as a Java/UI,
Kubernetes schema, admission or runtime test result.

- **27 slice files** checked: **26 Markdown + 1 design YAML**; **568 relative file
  destinations** exist, **34 fence markers** are balanced, zero reported errors.
- **40 contract/example and bookkeeping assertions** passed: reserved API group,
  non-release placeholder, documented example fields, one replica, read-only, OIDC
  client/audience distinction, TLS proposal, forbidden fields, unique requirements,
  open OP1 criteria and unchanged product metadata.
- YAML parsed with local **Ruby 2.6.10 / Psych safe loading**; synthetic fields only.
- **`git diff --check` passed**. Existing-source SHA-256 comparison limits changes
  to **21 existing documentation files** and **6 new documentation/example files**;
  no removed files. All **871 other pre-existing files** in the baseline inventory
  are unchanged, including product, tests, deployment templates and dependencies.

These are documentation checks, not 40 product/Operator tests. Mermaid source was
reviewed; no rendered-diagram or cluster/API-server schema validation was performed.

Safe repeat checks include `git diff --check` and, from the repository root:

```sh
ruby -rjson -ryaml -e 'puts JSON.generate(YAML.safe_load(File.read(ARGV.fetch(0))));' docs/architecture/examples/keycloak-operations-v1alpha1.yaml
```

Document inspection checks relative file destinations after excluding code fences;
it does not claim an exhaustive Markdown renderer/anchor/external-link audit.
Contract-example checks validate this author's proposed field/invariant agreement,
not a deployed CRD. The reserved API group and placeholder release must remain.
Official Operator-pattern, Kubernetes RBAC and RHBK 26.4 guide references were
consulted; they do not attest Operations compatibility with any live cluster.

## Unchanged versions and limits

Backend/application/MCP/OpenAPI **0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**,
AGT1 **0.2.1**, AGT2 client/answer **0.1.0**, observation **1.0**, report **1.1**,
findingDetails **1.0**, Flyway **V1–V10**, dependencies and permissions are unchanged.
Draft **0.1** and proposed **v1alpha1** are separate non-released design identifiers.

No Java/UI test/build, lab, model/provider, container, cluster API or deployment was
run. No cleanup was needed because this slice created no runtime resources; this is
not a fresh inventory assertion about the user's Podman. There is no controller,
installed CRD, bundle, release catalogue, remote collector or new HA capability.
No API-server dry-run or schema compatibility result can be claimed for the example.

H1/D1/AGT1 and AGT2 PARTIAL remain open/unchanged. D2 still requires an approved real
RHBK/OpenShift lab and can use reviewed manifests before OP1. Prior dated test results
remain historical. No new schedule or presentation promise was introduced.

## Next handoff

ONB1 minimum registration/ownership and first-access/write-gate policy are the next
local product slice. Preserve H1 consolidated source review and independent D1
reproduction as acceptance/release gates. Resolve UI runtime OIDC, TLS, empty-registry
bootstrap and migration/ownership gaps before claiming OP1 installation acceptance.
