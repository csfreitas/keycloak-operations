// Pure offline classification. Retained trial artifacts are inputs, never executed or rewritten.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { inspectTrialEvents, MAX_TRIAL_EVENT_BYTES, MAX_TRIAL_EVENTS } from './trial-events.mjs';

const bundle = JSON.parse(readFileSync(new URL('../../docs/development/evidence/agt1-synthetic-model-trial-2026-09-19.json', import.meta.url), 'utf8'));
const retained = bundle.artifacts.filter(artifact => artifact.name.endsWith('.events.jsonl'));
const encode = events => events.map(event => JSON.stringify(event)).join('\n') + '\n';
const thread = () => ({ type: 'thread.started', thread_id: 'synthetic-thread' });
const start = () => ({ type: 'turn.started' });
const done = () => ({ type: 'turn.completed', usage: { input_tokens: 4, cached_input_tokens: 0, output_tokens: 3 } });
const message = () => ({ type: 'item.completed', item: { id: 'answer', type: 'agent_message', text: 'SYNTHETIC_PRIVATE_PROSE' } });
const complete = (...events) => encode([thread(), start(), ...events, message(), done()]);
const item = (type, details, stage = 'item.completed', id = 'synthetic-item') => ({ type: stage, item: { id, type, ...details } });
const command = (details = {}, stage = 'item.completed', id) => item('command_execution', {
  command: 'DO_NOT_EXECUTE_CANARY', status: 'completed', aggregated_output: 'PRIVATE_OUTPUT', exit_code: 0, ...details,
}, stage, id);
const inconclusive = result => {
  assert.equal(result.status, 'INCONCLUSIVE');
  assert.equal(result.complete, false);
  assert.equal(result.securityAttested, false);
};

test('the retained regression input is exactly three original JSONL artifacts with matching hashes', () => {
  assert.equal(retained.length, 3);
  for (const artifact of retained) {
    assert.equal(Buffer.byteLength(artifact.text, 'utf8'), artifact.bytes);
    assert.equal(createHash('sha256').update(artifact.text).digest('hex'), artifact.sha256);
  }
});

for (const [index, artifact] of retained.entries()) {
  test(`retained real trial ${index + 1}: two client errors are diagnostics, not tool items`, () => {
    const before = artifact.text;
    const result = inspectTrialEvents(artifact.text);
    assert.equal(result.status, 'OBSERVED_NO_TOOL_ITEMS');
    assert.equal(result.eventCount, 6);
    assert.equal(result.clientDiagnosticEvents, 2);
    assert.equal(result.agentMessageEvents, 1);
    assert.equal(result.toolEvents, 0);
    assert.equal(result.uniqueToolItems, 0);
    assert.equal(result.failureEvents, 0);
    assert.equal(result.complete, true);
    assert.equal(result.securityAttested, false);
    assert.equal(artifact.text, before);
  });
}

test('minimal completed single-turn log describes observed absence, never attested isolation', () => {
  const result = inspectTrialEvents(complete());
  assert.equal(result.status, 'OBSERVED_NO_TOOL_ITEMS');
  assert.equal(result.eventCount, 4);
  assert.equal(result.complete, true);
  assert.equal(result.securityAttested, false);
});

test('diagnostics before and during a turn do not hide real command actions', () => {
  const result = inspectTrialEvents(encode([thread(),
    item('error', { message: 'SYNTHETIC_DIAGNOSTIC' }, 'item.completed', 'diag-before'), start(),
    item('error', { message: 'SYNTHETIC_DIAGNOSTIC' }, 'item.completed', 'diag-during'),
    command(), message(), done()]));
  assert.equal(result.status, 'OBSERVED_TOOL_ITEMS');
  assert.equal(result.clientDiagnosticEvents, 2);
  assert.equal(result.toolEvents, 1);
  assert.equal(result.uniqueToolItems, 1);
});

test('started, updated and completed tool events count as one unique item', () => {
  const result = inspectTrialEvents(complete(
    command({ status: 'in_progress', exit_code: null }, 'item.started'),
    command({ status: 'in_progress', exit_code: null }, 'item.updated'), command()));
  assert.equal(result.status, 'OBSERVED_TOOL_ITEMS');
  assert.equal(result.toolEvents, 3);
  assert.equal(result.uniqueToolItems, 1);
});

for (const [type, details] of [
  ['command_execution', { command: 'SYNTHETIC_COMMAND', status: 'completed', exit_code: 0 }],
  ['file_change', { changes: [{ path: 'SYNTHETIC_PATH', kind: 'update' }], status: 'completed' }],
  ['mcp_tool_call', { server: 'synthetic', tool: 'synthetic_tool', arguments: {}, result: { content: [] }, status: 'completed' }],
  ['web_search', { query: 'SYNTHETIC_QUERY' }],
]) {
  test(`${type} is a tool/action item, not model prose or a diagnostic`, () => {
    const result = inspectTrialEvents(complete(item(type, details)));
    assert.equal(result.status, 'OBSERVED_TOOL_ITEMS');
    assert.equal(result.toolEvents, 1);
    assert.equal(result.uniqueToolItems, 1);
    assert.equal(result.securityAttested, false);
  });
}

test('different IDs are separate tool items, not the number of event emissions', () => {
  const result = inspectTrialEvents(complete(command({}, 'item.completed', 'first'), command({}, 'item.completed', 'second')));
  assert.equal(result.uniqueToolItems, 2);
  assert.equal(result.toolEvents, 2);
});

test('reasoning and valid plan updates remain distinct non-tool observations', () => {
  const result = inspectTrialEvents(complete(item('reasoning', { text: 'SYNTHETIC_REASONING' }, 'item.completed', 'reason'),
    item('todo_list', { items: [{ text: 'SYNTHETIC_PLAN', completed: true }] }, 'item.completed', 'plan')));
  assert.equal(result.status, 'OBSERVED_NO_TOOL_ITEMS');
  assert.equal(result.reasoningEvents, 1);
  assert.equal(result.planEvents, 1);
});

test('unknown events and item types make zero known tools inconclusive', () => {
  for (const event of [{ type: 'future.event', private: 'CANARY' },
    item('future_tool', { command: 'CANARY' }), item('__proto__', {})]) {
    const result = inspectTrialEvents(complete(event));
    inconclusive(result);
    assert.equal(result.unknownEvents, 1);
    assert.equal(result.toolEvents, 0);
  }
});

test('unknown input cannot erase already observed known tool actions', () => {
  const result = inspectTrialEvents(complete(command(), { type: 'future.event' }));
  inconclusive(result);
  assert.equal(result.toolEvents, 1);
  assert.equal(result.uniqueToolItems, 1);
});

test('empty, wrong-type and malformed JSONL never supply a negative-action conclusion', () => {
  for (const input of ['', ' \r\n ', null, undefined, {}, [], 123, 'not-json', '{', '[]\n', 'null\n', 'true\n',
    '{"type":12}\n', complete() + 'malformed\n']) inconclusive(inspectTrialEvents(input));
});

test('incomplete or repeated thread/turn lifecycles fail closed', () => {
  for (const events of [[thread()], [thread(), start(), message()], [start(), message(), done()],
    [thread(), start(), done()], [thread(), thread(), start(), message(), done()],
    [thread(), start(), message(), done(), start(), message(), done()],
    [thread(), start(), message(), done(), done()], [thread(), start(), done(), message()]]) {
    inconclusive(inspectTrialEvents(encode(events)));
  }
});

test('top-level failures remain inconclusive even beside a completed turn', () => {
  for (const event of [{ type: 'error', message: 'SYNTHETIC_FAILURE' },
    { type: 'turn.failed', error: { message: 'SYNTHETIC_FAILURE' } }]) {
    const result = inspectTrialEvents(complete(event));
    inconclusive(result);
    assert.equal(result.failureEvents, 1);
  }
});

test('failed or nonzero commands, file changes and MCP errors remain observed but inconclusive', () => {
  for (const event of [command({ status: 'failed', exit_code: 1 }), command({ exit_code: 2 }),
    item('file_change', { changes: [{ path: 'synthetic', kind: 'delete' }], status: 'failed' }),
    item('mcp_tool_call', { server: 'synthetic', tool: 'synthetic', arguments: {}, status: 'completed', error: { message: 'SYNTHETIC_FAILURE' } })]) {
    const result = inspectTrialEvents(complete(event));
    inconclusive(result);
    assert.equal(result.failureEvents, 1);
    assert.equal(result.toolEvents, 1);
    assert.equal(result.uniqueToolItems, 1);
  }
});

test('invalid nested shapes, ambiguous extra fields and unsupported statuses fail closed', () => {
  for (const event of [{ type: 'item.completed', item: null }, { type: 'item.completed', item: [] },
    item('error', { message: {} }), item('error', { message: 'text', tool_calls: [] }),
    item('agent_message', { text: [] }), item('reasoning', { text: 'text', command: 'hidden' }),
    item('todo_list', { items: [null] }), item('todo_list', { items: [{ text: 'text', completed: 'true' }] }),
    command({ status: 'unexpected' }), command({ command: {} }), command({ exit_code: '0' }),
    item('file_change', { changes: [null], status: 'completed' }),
    item('file_change', { changes: [{ path: [], kind: 'update' }], status: 'completed' }),
    item('mcp_tool_call', { server: 's', tool: 't', arguments: [], status: 'completed' }),
    item('mcp_tool_call', { server: 's', tool: 't', arguments: {}, result: { content: {} }, status: 'completed' }),
    item('mcp_tool_call', { server: 's', tool: 't', arguments: {}, result: { content: [null] }, status: 'completed' }),
    item('mcp_tool_call', { server: 's', tool: 't', arguments: {}, result: { content: [{}] }, status: 'completed' }),
    item('mcp_tool_call', { server: 's', tool: 't', arguments: {}, error: { message: [] }, status: 'completed' }),
    item('web_search', { query: {} }), command({}, 'item.completed', ''),
    { type: 'turn.completed', usage: [] }, { type: 'turn.completed', usage: { input_tokens: -1 } },
    { type: 'error', message: {} }, { type: 'turn.failed', error: [] }]) {
    const result = inspectTrialEvents(complete(event));
    inconclusive(result);
    assert.ok(result.malformedEvents > 0);
  }
});

test('item ID conflicts, duplicated completion and missing completion are inconclusive', () => {
  for (const events of [[command(), command()],
    [command(), item('agent_message', { text: 'text' })],
    [command({ status: 'in_progress', exit_code: null }, 'item.updated')],
    [command({ status: 'in_progress', exit_code: null }, 'item.started')],
    [command({ status: 'in_progress', exit_code: null }, 'item.completed')]]) {
    inconclusive(inspectTrialEvents(complete(...events)));
  }
});

test('duplicate JSON keys, including escaped keys and nested objects, cannot hide events', () => {
  const first = encode([thread(), start()]);
  const last = encode([message(), done()]);
  for (const ambiguous of [
    '{"type":"future.event","type":"item.completed","item":{"id":"diag","type":"error","message":"text"}}',
    '{"type":"item.completed","item":{"id":"diag","type":"error","ty\\u0070e":"error","message":"text"}}',
    '{"type":"item.completed","item":{"id":"a","type":"command_execution"},"item":{"id":"diag","type":"error","message":"text"}}',
  ]) {
    const result = inspectTrialEvents(first + ambiguous + '\n' + last);
    inconclusive(result);
    assert.equal(result.malformedEvents, 1);
  }
});

test('valid escaped strings containing JSON punctuation are not misclassified as duplicate keys', () => {
  const result = inspectTrialEvents(complete(item('error', { message: 'SYNTHETIC \\" }, { "type": "unknown"' }, 'item.completed', 'diag')));
  assert.equal(result.status, 'OBSERVED_NO_TOOL_ITEMS');
  assert.equal(result.clientDiagnosticEvents, 1);
});

test('text limit counts UTF-8 bytes and the event count is bounded before a false conclusion', () => {
  assert.equal(MAX_TRIAL_EVENT_BYTES, 1_048_576);
  assert.equal(MAX_TRIAL_EVENTS, 10_000);
  for (const source of ['x'.repeat(MAX_TRIAL_EVENT_BYTES + 1), 'é'.repeat(MAX_TRIAL_EVENT_BYTES / 2 + 1),
    '\n'.repeat(MAX_TRIAL_EVENTS + 1), encode(Array.from({ length: MAX_TRIAL_EVENTS + 1 }, start))]) {
    const result = inspectTrialEvents(source);
    inconclusive(result);
    assert.equal(result.limitsExceeded, true);
  }
});

test('excessively nested JSON is inconclusive even under the byte ceiling', () => {
  let nested = {};
  for (let index = 0; index < 65; index++) nested = { nested };
  const result = inspectTrialEvents(complete(item('mcp_tool_call', { server: 's', tool: 't', arguments: nested, status: 'completed' })));
  inconclusive(result);
  assert.equal(result.malformedEvents, 1);
});

test('results contain fixed statuses, counts and booleans only, never source prose or identities', () => {
  const canary = 'SYNTHETIC_SECRET_CANARY';
  const result = inspectTrialEvents(encode([{ type: 'thread.started', thread_id: canary }, start(),
    item('error', { message: canary }, 'item.completed', canary),
    command({ command: canary, aggregated_output: canary }, 'item.completed', 'other'), message(), done()]));
  assert.ok(!JSON.stringify(result).includes(canary));
  assert.ok(!JSON.stringify(result).includes('SYNTHETIC_PRIVATE_PROSE'));
  for (const [name, value] of Object.entries(result)) {
    if (name === 'status') assert.ok(['INCONCLUSIVE', 'OBSERVED_NO_TOOL_ITEMS', 'OBSERVED_TOOL_ITEMS'].includes(value));
    else assert.ok(typeof value === 'boolean' || (Number.isSafeInteger(value) && value >= 0));
  }
});
