# Optional reference agent — local prototype 0.2.1

This is a **provider-neutral operational profile and executable contract**, not a
running LLM service, installed plugin or production certification. It is distinct
from the repository's [AGENTS.md](../../AGENTS.md) development workflow. No model,
provider, external account, SDK or additional dependency is selected or invoked.

Implemented: one fixed read/assessment tool, operator-selected target, a compact
fact projection with bounded report-bound finding/evidence values, deterministic
no-AI fallback and structural explanation checks.
Not implemented: natural-language generation, general-purpose MCP transport/OIDC
login, semantic narrative grading or full AGT1
acceptance. See [milestone](../../docs/milestones/agt1-reference-agent.md) and the
[validation ledger](../../docs/development/agt1-grounding-2026-09-19.md).

## Files and quick local check

- `profile.json`: versioned capabilities/limits; a project-specific descriptor, not
  a configuration schema accepted automatically by arbitrary clients.
- `INSTRUCTIONS.md`: reviewed operational agent guidance; report data never becomes
  trusted developer instructions.
- `core.mjs`: fixed request builder, report projection, one-call adapter boundary,
  no-AI fallback and structural evaluator. Uses Node built-ins only.
- `finding-details.mjs`: strict bounded findingDetails1.0 validation and source
  pointers; the profile rejects older envelopes lacking this extension.
- `packet-budget.mjs`: independent **256 KiB** UTF-8 serialized-packet ceiling.
- `explanation-contract.mjs`: shared numerical limits and published output guidance;
  tests keep this guide, operational instructions and descriptor aligned with validation.
- `trial-events.mjs`: pure offline inspection of a bounded single-turn Codex JSONL
  trace; separates diagnostic events from tool/action items, with no client execution.
- `cli.mjs`: offline file-based projection/evaluation; no network or file writes.
- `fixtures/report-partial.json`: entirely synthetic test data, not an environment
  assessment. `reference-agent.test.mjs` / `cli.test.mjs`: deterministic tests, not
  evaluations of a language model.

From the repository root, with Node 24:

```sh
node --test dev/reference-agent/*.test.mjs
node dev/reference-agent/cli.mjs fallback synthetic-target-a dev/reference-agent/fixtures/report-partial.json
```

The fallback prints only a fixed-shape packet containing source identities, exact
facts with JSON paths and explicit limits. It preserves PARTIAL, UNKNOWN,
null score, the 58-percent evidence completeness and the collection window. It does
not copy Markdown, section messages, envelope labels or arbitrary extension fields.
Finding names, descriptions, recommendations, references and sanitized evidence are
now included as **untrusted data**, not instructions. This is narrower than the full UI/REST report,
which remains available without any agent/model.

## Host integration and identity

1. The operator selects one registered target, assessment profile and metrics window
   (`5m`, `15m` or `1h`). Target/profile identifiers use this prototype's conservative
   ASCII letters/digits/underscore/hyphen subset, maximum 128 characters. Unsupported
   names are rejected, not normalized or silently redirected.
2. Use the platform's [Identity A](../../docs/identity-model.md), with exact target
   **READ/ASSESS only**, and leave `mcp.read-only=true`. Do not reuse target Identity B
   credentials or the legacy `scripts/setup-mcp-client.sh` target bootstrap. No realm
   administrator, DISCOVER/BIND/PLAN/APPROVE/WRITE or implicit wildcard grant is needed.
3. The trusted host configures the approved MCP endpoint and authenticated session;
   the model never receives endpoint/credential selection or a raw MCP client. Use
   the client's verified credential reference/OIDC integration, not inline tokens.
   Production transport must validate TLS, refuse redirects, validate protocol/session
   and response IDs, and enforce response-size/time bounds. This prototype is not
   that transport implementation or a generic client compatibility claim.
4. Expose only `keycloak_generate_operations_report`, through `collectReport` with
   fixed operator config. Do not expose the adapter's raw `callTool`, shell, browser,
   arbitrary network or additional MCP connections to the model. Global read-only
   alone is insufficient: some existing planning tools remain read-only dry runs.
5. The callback accepts `{name, arguments}` plus `{signal}`, validates/unwraps the
   actual MCP result, and returns the compact report object (not JSON-RPC or REST).
   It must throw on protocol/HTTP/auth/tool errors without returning provider text.

```js
import { collectReport } from './dev/reference-agent/core.mjs';

// Config is operator-owned; authenticatedCallTool is supplied by the reviewed host.
const packet = await collectReport(
  { targetId: 'operator-selected-target', profile: 'keycloak-production', metricsWindow: '5m' },
  authenticatedCallTool,
);
// No model is called here. Review data/provider approval before any external use.
```

There is one callback per collection, no automatic retry or fallback endpoint.
The 20-second timer bounds asynchronous waiting and signals cancellation; the adapter
must cooperate. It is not a hard process deadline, cannot interrupt synchronous
blocking code, and cannot undo server work. Report generation reads the target but
**persists platform snapshots, health, assessments and audit**. Re-running is a new
collection, not replay, so uncertain results require an explicit operator decision.

The library can be bypassed by a host exposing additional capabilities; the profile
file/prompt is not an access-control system. Backend grants remain authoritative.
Organizational approval of provider, data scope/retention and credentials must precede
external disclosure. No existing local API key or account is automatically adopted.

## Grounding contract and its limits

Only current compact MCP schema **1.1** is accepted. It must have the expected target,
UUID report/assessment identities, UTC collection times, known states/counters,
the four named sections, and independent-collection/no-replay provenance. Invalid
or unsupported data is rejected, never converted to favorable defaults. The packet
is an immutable projection; the source report is unchanged. Local validation does
not authenticate imported files or prove that a supplied UUID/hash is genuine.

The additive `findingDetails` **1.0** extension repeats report/target/assessment
identities, counts and explicit limits. Profile **0.2.1** requires it; legacy compact
reports without the extension are rejected rather than silently treated as complete.
AVAILABLE means the list exists, not that every finding was returned. UNAVAILABLE
has null total/omitted counts; an observed empty list instead has zero counts.

Only the first 20 source findings are considered. Each admitted whole finding is
limited to 256 value nodes, depth 6 (finding root 0) and 8192 UTF-16 text units across
keys and strings. Unsupported/oversized entries are omitted whole, never truncated;
`omittedFindings = totalFindings - returnedFindings`. Numeric JSON facts are finite,
integral values must be JavaScript-safe. Nulls, booleans and empty containers retain
their source meaning. These limits do not cap the preexisting entire MCP envelope.
The [server contract](../../docs/architecture/operations-reporting.md#report-bound-mcp-finding-details)
defines the exact supported projection and omission policy.

Repeated/escaped JSON pointers can amplify source text. The profile therefore
accounts for serialized fact bytes before appending them, then checks the whole
packet (and fallback wrapper) against **262144 bytes**. Exceeding this local context
budget rejects the report with `INVALID_REPORT`, without clipping, extra collection
or model call. This host limit is independent of server-side per-finding omissions.

These are projection bounds, not a hard memory limit for an already-decoded host
object: inspecting its property descriptors can allocate before rejection. The
authenticated transport must enforce response bytes **before parsing**; the offline
CLI separately caps input files at 1 MiB. No unbounded raw MCP response should be
passed through simply because the final packet is bounded.

`sourceIndex` identifies `/assessment/findings/{sourceIndex}` in the original
sanitized report. Actual packet references use `/findingDetails/items/{itemIndex}/finding/...`,
escaping dynamic keys with RFC6901 `~0`/`~1`. Rule `id` values may repeat: they are
not global finding/evidence UUIDs. Counters, ordering, bindings and bounds are checked
before copying; original data remains unchanged. A report UUID is on-demand, not a
persisted retrieval/replay handle; a catalog hash is not complete evaluator attestation.

Evidence is sanitized report output, not raw retained evaluator input. Text and
keys can still contain malicious instructions or sensitive data; keep the packet
as quoted/data content, never privileged system/developer instructions. No reference
URL is fetched. The adapter does not provide universal secret/PII filtering. External
disclosure still requires explicit approval. No structured performance/no-traffic
proof is added, and no Markdown parsing or history join fills gaps.

An explanation proposal has exactly:

```text
profileVersion, targetId, reportId, facts,
observations: [{text, references: [factPath]}],
hypotheses: [{text, references: [factPath]}],
recommendations: [{text, references: [factPath]}]
```

<!-- explanation-contract:start -->
Return only one JSON object, without Markdown fences or surrounding prose, with
exactly these seven fields: `profileVersion`, `targetId`, `reportId`, `facts`,
`observations`, `hypotheses`, `recommendations`. Copy the three identity fields and
the entire `facts` array exactly from the host packet, preserving fact order, paths,
value types, nulls and empty containers. Do not add `limitations`, `kind`, tool calls
or other fields. JSON object key order is not significant; fact array order is.

Each of the three narrative fields is a required array containing 0–20 items;
an empty array is permitted. Each item has exactly `text` and `references`.
`text` must be a nonblank string (not empty after JavaScript trim), with at most
1000 UTF-16 code units, including whitespace. Do not include U+0000–U+001F or
U+007F, including decoded newline or tab escapes. Unicode accents and emoji are
allowed; a supplementary character such as an emoji consumes two UTF-16 units.

`references` must be an array of 1–10 distinct strings per item, each exactly a
`path` present in `facts`. Do not invent paths, normalize escaped paths, or substitute
source URL values for paths. The same path may appear in different items. Select up
to 10 relevant supporting paths; do not reproduce every related path automatically.
If more are needed, split the explanation into independently supported items within
the item limit. Never drop copied facts or invent support to satisfy these bounds.
An operator may impose stricter trial limits, never expand this contract. Invalid
output is rejected, not coerced, clipped, repaired or automatically retried.
<!-- explanation-contract:end -->

The limits are unchanged from 0.2.0; **0.2.1** publishes the previously omitted
requirements. A host must use the matching current instructions and packet revision:
the evaluator requires exact profile identity, so old 0.2.0 explanations are not
silently relabeled or accepted. Historical failures stay unchanged. JSON framing is
enforced by the CLI parser; the evaluator itself receives a decoded object.

The local evaluator
rejects altered IDs/facts/windows/status/score, missing references or invented paths.
It returns **structuralChecks=PASSED, semanticReview=REQUIRED, modelEvaluated=false**.
It never prints the proposal's prose or authorizes actions/disclosure.

```sh
node dev/reference-agent/cli.mjs evaluate operator-selected-target approved-compact-report.json proposed-explanation.json
```

Input files are operator-selected regular UTF-8 JSON files, maximum 1 MiB each.
Nonregular/oversized/malformed inputs fail without echoing paths or parser/source
text. Files on an unresponsive remote filesystem and a blocking trusted host are
outside the local CLI's time guarantees. Do not pass arbitrary file paths chosen by
the model. Files/packet identifiers may still be sensitive; this is not a universal
secret/PII scanner. Review artifacts locally before sharing.

**Structural success is not semantic success.** A sentence claiming that an UNKNOWN
environment is healthy can still cite valid paths and pass structurally; an explicit
test demonstrates this limitation. Human/semantic security review and repeated
model-specific adversarial runs remain mandatory before actual agent acceptance.

## Example interaction — synthetic fixture only

Question: “Can we consider this environment healthy?”

Grounded answer: “No health conclusion is supported by this report: health is UNKNOWN
(`/healthStatus`), report completeness PARTIAL (`/reportCompleteness`), and the
assessment score is unavailable (`/assessment/scoreAvailable`, `/assessment/overallScore`).
Evidence completeness is 58 percent, not a pass. The independent collections cover
21:00:00–21:00:04 UTC on 19 September 2026; retained replay is unavailable. Check
`/findingDetails/returnedFindings` and `/findingDetails/omittedFindings` before
interpreting the bounded details; they cannot establish full collection coverage.
Review the missing collection coverage before drawing operational conclusions.”

This is a human-written example, not model output or current runtime evidence. No
traffic, MFA adoption, active users, configuration validity or root cause can be
inferred from that missing detail.

## Real local contract rehearsal

Use the existing [owned identity lab](../identity-lab/README.md), after its prerequisites:

```sh
bash scripts/validate-identity-lab.sh --reference-agent
```

This optional mode adds ten checks to the normal 75: the profile collects one actual
MCP report for each separately authorized local actor, preserves unavailable score
and input state, verifies report/assessment finding binding and exact evidence JSON
pointers, and returns structural-only results. The same underlying report is
used; no extra report generation is hidden in the projection. Expected total: **85**,
plus JWT log scan and owned cleanup. Model/provider remain **not configured/not run**.
No browser, persistent volume or new image is needed. See the ledger for actual
execution, skips and final inventory; a command's presence is not evidence of success.

## Evaluation status and adaptation

### Offline trial-event inspection

```js
import { inspectTrialEvents } from './dev/reference-agent/trial-events.mjs';

// jsonl is a locally reviewed, already bounded UTF-8 trace string, not a model command.
const observation = inspectTrialEvents(jsonl);
```

This helper accepts up to **1 MiB UTF-8** and **10,000 events** for one recognized
thread/turn. It returns only fixed statuses, counts and booleans: `OBSERVED_NO_TOOL_ITEMS`,
`OBSERVED_TOOL_ITEMS` or `INCONCLUSIVE`. Client `item.type=error` diagnostics are not
tool actions. Command execution, file changes, MCP calls and web searches are action
items; multiple start/update/completion events for an ID count as one `uniqueToolItems`
entry, separately from `toolEvents`. Unknown/malformed/ambiguous input, exceeded
bounds, failures or incomplete lifecycles remain inconclusive, even with zero known
tool items. New client formats may require a separately reviewed parser revision.

The helper never launches Codex, reads credentials, contacts a provider, repairs an
explanation, retries a request or echoes log messages/IDs/content. `securityAttested`
is always false: logs do not prove authenticity, a complete tool catalog, egress
isolation or narrative correctness. No-tool observation is not overall acceptance;
all three historical trial outputs still fail the explanation contract. Keep raw
traces immutable and review them separately under the approved data policy. Callers
must bound file/transport input before decoding; this function receives a string.

### Model evaluation status

Keep client/host revision, profile hash, authentication/target scope, source IDs,
provider/model revision (or explicitly none), cases, expected outcomes and actual
results in an evaluation ledger. Distinguish deterministic contract tests from model
behavior. Required later model cases include malicious metadata, contradictory prose,
unsupported finding IDs, no evidence/no traffic, invalid credentials, wrong target,
provider outage and repeated-run variability. The prototype's own tests exercise
only the contract subset, not a model. A separately authorized
[three-response experiment using profile 0.2.0](../../docs/development/agt1-synthetic-model-trial-2026-09-19.md)
preserved facts but failed all three structural evaluations: the instructions omitted
the ten-reference upper bound. Those raw failures remain unchanged. Profile **0.2.1**
aligns the published instructions, shared constants and descriptor, with local
regressions; it has **not** received a new external model evaluation. The original
three-response authorization is exhausted. New model runs require a separately
approved budget and disclosure scope for the revised instructions/packets.

The design follows structured data boundaries and separate evaluation recommended in
[OpenAI's agent safety guidance](https://developers.openai.com/api/docs/guides/agent-builder-safety)
and [workflow evaluation guidance](https://developers.openai.com/api/docs/guides/agent-evals).
No hosted Builder/Evals dependency is introduced. Other clients can implement the same
profile contract, but each real client/model/auth combination needs its own validation.
