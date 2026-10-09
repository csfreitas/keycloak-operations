# H1 — Metadata trust projection, 2026-09-19

Status: bounded local corrective slice. H1/D1 acceptance remains open; no real-cluster,
browser, universal secret-detection, agent-safety or production-write certification.

## Delivered boundary

- `SensitiveDataFilter.redactMetadata` explicitly processes JSON-shaped map/list
  string leaves and DTO projections. Recognizable assignments, authentication/cookie
  headers, API-key fields, compact JWT/JWE, private-key PEM and URI credentials are
  masked. Secret-bearing dynamic keys receive unique opaque labels; reserved keys and
  collisions cannot silently discard entries. Repeated projections are idempotent.
- Typed numeric/Boolean/null facts, status/score, ordinary names, token-lifespan and
  password-policy prose survive. Quoted multiline values and escaped quotes are
  covered. Bounded PEM label grammar, possessive quoted-value matching and a URI
  scheme boundary avoid the reviewed long-string regex failure paths; large-string
  regressions are not a general resource-bound proof.
- Legacy `redact` remains structurally compatible for maps/lists. Review found it
  also feeds controlled-change baseline/desired state; making it recursively lossy
  would silently change applied configuration or conceal drift. The new API is
  output-only, never assessment input or desired/applied state. Direct string
  redaction is strengthened, including existing string-log users; this is not an
  audit of all logging/administration paths.
- Operations reports sanitize the structured draft before deterministic Markdown
  generation. No regex then rewrites serialized Markdown/fenced JSON. HTML/Markdown
  delimiters, backtick/tilde fences and presentation controls are escaped; fenced
  JSON decodes to the same sanitized facts. Instruction-like metadata stays literal
  data, with a fixed notice that it cannot authorize actions or override rules.
  Endpoint-key omission is locale-independent, including dot/dash/underscore variants.
- New snapshots persist sanitized inventory and compute configuration/runtime hashes
  from that projection. Detail/history comparisons sanitize copies, never rewriting
  historical entities or hashes. Secret-only metadata changes do not affect new-policy
  sub-hashes; actual configuration/runtime differences do. Old raw/representation-based
  hashes are not equivalent-policy comparisons, and historical hash drift remains visible.
- Assessment finding text, subjects, references and evidence are sanitized after
  evaluation for persistence/history. Immediate assessment results are projected only
  after original evaluation/persistence/event work. Rule outcomes and original engine
  results remain unchanged; permissions still precede history/collection access.

Requirements: SEC-CRED-002/004/005, SEC-REPORT-001, SEC-AI-001, FR-ASSESS-003,
FR-REPORT-002/004/006, NFR-DET-001, NFR-TEST-001/002. These are scoped acceptance
inputs, not closure of those requirements across every surface.
Architecture: [security](../architecture/security.md),
[reporting](../architecture/operations-reporting.md),
[persistence](../architecture/persistence.md),
[AI boundary](../architecture/ai-assisted-operations.md).

## Source and version decision

HEAD `572cb7ba5aa9a132f3e04280de9378da08cc61e3`, branch
`feature/0.8.1-client-lifecycle`; accumulated uncommitted work preserved. No commit,
push, rebase, tag, publication or cluster access. The accompanying
[source/test/runtime manifest](evidence/h1-metadata-trust-2026-09-19.json) records
tracked/untracked implementation inputs, comparison with the capability slice,
Maven report/log/package hashes and before/after Podman observations.
There are **582 source inputs**, aggregate
`3120dcc67bf8c2f6f3cb99e5b35822d64c38660ab2729a0cd7091c817bbfa2db`;
**12 existing source/test files changed, two tests added, none removed** versus
the capability slice. The manifest retains **119 Maven report hashes**, **11 run
logs** (including the failed assertion and pre-review passes), **three final package
hashes** and **two inventories**. Final reference consistency is recorded below.
Hashes are unsigned local consistency evidence, not independent attestations.

Backend/application/MCP/OpenAPI stay **0.8.1-SNAPSHOT**; UI/package-lock root metadata
stay **0.8.1-dev.0**; dynamic report schema stays **1.1**, migrations **V1–V10**.
This is an unreleased corrective increment with an explicit snapshot-hash policy
compatibility caveat, not a product release or migration. UI, dependencies, grants,
deployments and default read-only mode are unchanged. AGENTS now records the
output-projection versus operational-state invariant.

## Validation

Java **21.0.10** selected through `JENV_VERSION=21 jenv exec` per command; global
Java selection unchanged. Host Node **25.6.1** executes the standalone runners,
not a new UI/Node support claim. Raw logs are local `/private/tmp/kcops-h1-metadata-*-20260919.log`
and can expire; the manifest retains their hashes and summary excerpts.

| Execution | Result |
|---|---|
| Fresh clean baseline | **1304 passed**, 9 opt-in ITs skipped; exit 0 |
| First focused run | 95 invocations, 1 assertion failure; exit 1, retained |
| Focused rerun | **122 passed**, exit 0 |
| Pre-final-review clean verification | **1382 passed**, 9 opt-in ITs skipped; exit 0 |
| Pre-final-review installation/default/metrics labs | **83 / 70 / 75** checks, JWT scans and owned cleanup passed; each exit 0 |
| Final reviewed-source clean verification | **1385 passed**, 9 opt-in ITs skipped; BUILD SUCCESS, exit 0 (**81 added invocations**) |
| Final-package installation lab | **83 checks**, JWT scan and owned cleanup passed; exit 0 |
| Final-package default identity/report lab | **70 checks**, JWT scan and owned cleanup passed; exit 0 |
| Final-package metrics identity/report lab | **75 checks**, JWT scan and owned cleanup passed; exit 0 |

The first assertion incorrectly prohibited an image-like string even inside inert
fenced JSON. The correction checks active Markdown context separately and asserts
literal JSON round trips; no evidence was deleted to satisfy it. Independent final
review then found a real tilde-fence formatting gap. Tildes are escaped only in
free-text Markdown, with three regressions preserving literal structured/fenced JSON.
The earlier successful builds/labs remain historical to that final correction.
Final reviewed-source verification and all three repeated final-package labs passed
as recorded above; the manifest hashes the final package/source, not the superseded
pre-review package.

Regression coverage includes nested/quoted credentials, header/query/URI/private-key
formats, dynamic-key collision/idempotence, typed facts, instruction-like names,
presentation controls, code fences, historical immutability, post-evaluation results,
new snapshot hash sensitivity and unchanged change-plan/apply/drift semantics.

Local installation uses real OIDC/PostgreSQL and a synthetic cluster, not OpenShift.
Default/metrics labs exercise authenticated REST/MCP reports and isolation on
Community Keycloak 26.7.1; metrics use Prometheus 2.55.1. These repeat existing
end-to-end fixture checks, not live injection of every unit-test canary.
UI tests/build, browser lifecycle/revocation, real RHBK/OpenShift, CI, image/native
builds and comprehensive dependency/security scans were not run in this slice.

## Local hygiene

Before runtime validation: zero containers/volumes, default network only, four cached
reusable images unchanged, eleven fixture ports free and the shared lock absent.
All runs explicitly pin `podman-machine-default-root`, use named per-run-owned
temporary resources and verify scoped cleanup. No VM restart, global prune, image
or volume deletion. Removed tmpfs test data are disposable and not recoverable;
diagnostic logs and reusable images are retained.

Independent final inventory at **15:57:20 −03** confirms **zero containers/volumes**,
default Podman network only, all four reusable image IDs unchanged, all eleven
fixture ports bindable and shared lock absent. Final diagnostics:
installation `/private/tmp/kcops-installation.KGyiIY`, default
`/private/tmp/kcops-identity.n3UUeQ`, metrics `/private/tmp/kcops-identity.1kAuLo`.
Pre-review diagnostics remain separately retained under the paths in their logs.

Final consistency checks: **717 referenced hashes matched**, including all 582 source
inputs and their aggregate. Offline documentation review passed for **135 Markdown
documents, 812 local links and 15 milestone specifications**, with zero errors/warnings;
the checker passed six self-test groups. `git diff --check` passed. These establish
reference/structure consistency, not external URL validity or milestone acceptance.
Context, roadmap, affected architecture, AGENTS, milestone index/specifications,
Unreleased notes and the development-version decision were reconciled; previous
dated ledgers remain historical.

## Remaining work

- This opt-in boundary does not cover all generic inventory, target, installation,
  metrics, health, audit and controlled-change metadata/history outputs. In particular,
  legacy change inputs need an explicit admission/output design rather than silently
  substituting redaction markers into applied state. Do not supply secrets in metadata.
- Pattern recognition cannot detect arbitrary unlabeled/encoded secrets or every
  format. Raw arrays/sets/custom Java scalar containers are not the supported
  JSON-shaped metadata boundary. Sensitive architecture may remain after redaction.
- Historical database bytes/backups are untouched, not certified secret-free.
  The displayed historical projection is not what its original digest hashes.
  Full versioned sanitization provenance and retained replay remain D3E work.
- Literal rendering and an untrusted-data warning do not prove agent resistance to
  malicious instructions. External renderers can auto-link bare URLs; the current UI
  uses plain preformatted text. No agent/model provider or external sharing is enabled.
- Browser negatives/revocation, normalized all-source authorization/freshness/coverage,
  dynamic diagnostic configuration, comprehensive scanning and reviewer acceptance
  remain open. H1/D1 are not closed by this slice.

Next local work: remaining legacy metadata/mutation/audit admission boundaries and
browser/revocation acceptance, then AGT1. D2 still requires the explicitly approved
dedicated RHBK/OpenShift lab; no cluster is needed for the immediate work.
