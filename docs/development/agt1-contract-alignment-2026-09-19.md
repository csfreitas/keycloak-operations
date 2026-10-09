# AGT1 — Local explanation-contract alignment, 2026-09-19

Status: **LOCAL CORRECTION VALIDATED; MODEL RE-EVALUATION NOT RUN**.
Reference profile **0.2.1** publishes the already enforced explanation contract;
no structural limit is relaxed. H1/D1/AGT1 full acceptance remains open.

## Scope and demonstrated causes

The [preceding authorized trial](agt1-synthetic-model-trial-2026-09-19.md) returned
three fact-preserving responses, all rejected. Four narrative items carried 11/12
references while the evaluator permits at most ten. The supplied instructions did
not publish that upper bound. This local correction fixes the demonstrated mismatch;
it does not prove that any future model response will conform.

The one-off runner also classified every non-message/non-reasoning event as a tool
event. Offline reproduction against the three retained traces wrongly counted two
`item.type=error` diagnostics per case. Those were client messages about disabled
code-mode host and skills context, not observed tool calls. Historical runner, inputs,
outputs, results and the original **0/3 acceptance** are preserved unchanged.

## Delivered change

- [Shared explanation contract](../../dev/reference-agent/explanation-contract.mjs):
  unchanged **20 items/category**, **1000 UTF-16 code units/text**, **1–10 distinct
  references/item**. The evaluator consumes the same immutable numerical constants
  used to render the published guidance. Tests check exact guidance blocks in both
  operational instructions and README, plus descriptor agreement.
- Explicit seven-field JSON shape, exact identity/facts/order, blank/control text
  rejection, Unicode length semantics and existing-path references. The evaluator
  receives decoded objects; JSON framing without Markdown is a parser/CLI boundary.
  References may repeat across different items, never within one item. No clipping,
  repair, automatic retry or reinterpretation of deterministic findings is added.
- [Offline event inspection](../../dev/reference-agent/trial-events.mjs): bounded
  single-thread/single-turn JSONL, at most **1 MiB UTF-8**, **10,000 events** and
  depth **64**. Known diagnostic items are separate from tool/action events and
  unique action-item IDs. Unknown, malformed, duplicate-key, failed or incomplete
  traces are **INCONCLUSIVE**, even when no known tool is counted. Results contain
  fixed statuses, counters and booleans, not source messages, IDs or command text.
  `securityAttested` is always false. This helper does not launch or wrap a client,
  inspect credentials, authenticate logs, call a provider or certify narrative meaning.
- [CI](../../.github/workflows/ci.yml) runs the offline profile tests with Node 24,
  without an SDK, new dependency, credentials, model call or runtime container.
  YAML syntax was parsed locally; no GitHub Actions execution is claimed.

## Fresh validation and retained failures

| Stage | Observed result |
|---|---|
| Existing profile baseline | 70 passed, 0 failed/skipped |
| Required backend baseline before coding | `mvn clean verify`: 1705 passed, 9 opt-in ITs skipped, BUILD SUCCESS |
| Instruction-alignment red regression | 18 tests: 15 passed, 3 failed before the correction |
| Instruction-alignment focused green | 18 passed; a later isolated previous-version negative adds the 19th new contract test |
| Original event-predicate reproduction | 3 failed: diagnostic items miscounted as tools in each retained trace |
| Corrected event-reader tests | 26 passed, including all three original JSONL traces |
| Final complete offline profile suite | **115 passed, 0 failed/skipped**: 70 existing + 19 contract + 26 event tests |

Tests cover numerical boundaries across all three narrative arrays, UTF-16 emoji
length, all C0/DEL characters, decoded newline/tab, whitespace-only text, duplicates,
invented paths, escaped pointers, exact previous-version rejection and input immutability.
Existing tests continue to reject altered facts/identities and demonstrate that
structurally valid prose can still be false: semantic review remains mandatory.

Retained response bytes and hashes stay unchanged. Tests separately confirm their
four reference overflows. Because the current exact profile identity is now 0.2.1,
rejecting a raw 0.2.0 response alone is not evidence of reference-bound enforcement:
new **synthetic negative candidates** use the current packet plus copies of the
overflowing narrative and must also fail. These are not repaired model responses,
new model runs or successful explanations. A separate otherwise-valid current
candidate with only the version changed to 0.2.0 tests version rejection in isolation.

The [manifest](evidence/agt1-contract-alignment-2026-09-19.json) records source hashes,
test-report/log hashes and summaries, all **29 verified historical artifact hashes**,
runtime observations and versions. Initial failing tests remain logged. Independent
read-only review prompted the isolated version test and clearer README wording;
it is not human operator acceptance. No UI, browser, authenticated MCP or external
model test was rerun. No historical validation is presented as fresh.

Fresh offline documentation checks: **147 Markdown documents**, **981 local links**,
**143 requirement definitions**, **16/16 milestone structures**, zero errors/warnings;
six parser self-check groups and `git diff --check` pass. These checks validate
documentation structure, not external sources or milestone acceptance.

## Local environment and version decision

Java **21.0.10** was selected per command using jenv; global Java selection was not
changed. The backend baseline used the explicitly selected local Podman connection
`podman-machine-default-root`, with all real integration flags unset. Existing named,
labelled PostgreSQL test resources used temporary memory-backed data and their normal
cleanup. Before/after inventory: **zero containers, zero volumes**; four reusable
images unchanged (Keycloak 26.7.1, PostgreSQL 16, RHBK 26.6 and Prometheus v2.55.1).
No global prune, VM restart, cluster connection or unrelated resource deletion.

Only the optional profile advances **0.2.0 → 0.2.1**. Shape and limits are unchanged,
but profile identity remains exact: hosts need matching instructions and packets,
not an automatic rewrite of old explanations. Backend/application/MCP/OpenAPI
**0.8.1-SNAPSHOT**, UI/root lockfile **0.8.1-dev.0**, canonical report **1.1**,
findingDetails **1.0**, Flyway **V1–V10**, dependencies and permissions are unchanged.
No backend/UI implementation or assessment-score behavior changed.

HEAD stays `572cb7ba5aa9a132f3e04280de9378da08cc61e3`; existing dirty work is preserved.
The source manifest has **626 inputs**, **6 changed / 4 added / none removed** versus
the previous 622-input checkpoint. Aggregate SHA-256:
`cd95a19e04b37aa6fa856f503673097c4c914137b9a5c9b206ab7a943fb24aeb`.
No commit, push, rebase, tag or publication. AGENTS.md invariants remain unchanged.

## Next gate and limits

No new model response or external payload disclosure was authorized or executed in
this slice. The original three-response budget is exhausted. Obtain a new bounded
authorization covering revised instructions/packets before repeat evaluation; retain
failed outputs, inspect both structure and semantics and never silently retry an
uncertain request. A conforming sample still does not establish repeatability,
authenticated MCP host integration, real-environment compatibility or full AGT1
acceptance. D2 continues to require the separately approved RHBK/OpenShift lab.

The [official Codex non-interactive documentation](https://learn.chatgpt.com/docs/non-interactive-mode)
describes the JSONL event families. Local retained traces establish the diagnostic
shapes used here; unsupported future shapes remain inconclusive, not assumed safe.
