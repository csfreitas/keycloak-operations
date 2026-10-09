import test from 'node:test';
import assert from 'node:assert/strict';
import { labRpcResult, openReferenceSession, LAB_MCP } from './configuration-client-lab.mjs';
import { createTransport, LAB } from './configuration-lab-checks.mjs';
import { realmScope, observation, catalogueWire, wire } from '../dev/access-aware-client/fixtures.mjs';

const TOKEN = 'synthetic.token.signature';
const req = { scopeIds: [realmScope.scopeId] };
const rpc = (id, result, sse = false) => ({ status: 200,
  headers: new Headers({ 'content-type': sse ? 'text/event-stream' : 'application/json' }),
  text: sse ? `event: message\ndata: ${JSON.stringify({ jsonrpc: '2.0', id, result })}\n\n`
    : JSON.stringify({ jsonrpc: '2.0', id, result }) });
const initialized = () => ({ protocolVersion: '2025-11-25', capabilities: { tools: {} },
  serverInfo: { name: 'synthetic', version: '1' } });
function fixture({ mutate = value => value, sse = false } = {}) {
  const calls = [];
  const request = async (url, options) => {
    const message = options.body ? JSON.parse(options.body) : null;
    calls.push({ url, options, message });
    let response;
    if (options.method === 'DELETE' || message.method === 'notifications/initialized') {
      response = { status: options.method === 'DELETE' ? 204 : 202, headers: new Headers(), text: '' };
    } else if (message.method === 'initialize') {
      response = rpc(message.id, initialized(), sse);
      response.headers.set('mcp-session-id', 'synthetic-session');
    } else {
      response = rpc(message.id, JSON.parse(message.params.name === 'keycloak_list_configuration_scopes'
        ? catalogueWire([realmScope]) : wire(observation(realmScope))), sse);
    }
    return mutate(response, { url, options, message });
  };
  return { request, calls };
}
const rejected = (promise, code) => assert.rejects(promise,
  error => error.code === code && error.message === code && !error.cause);

for (const sse of [false, true]) {
  test(`real client over synthetic ${sse ? 'SSE' : 'JSON'} transport enforces session/token/version binding`, async () => {
    const { request, calls } = fixture({ sse });
    const host = await openReferenceSession({ request, token: TOKEN });
    const answer = await host.client.inspect(req);
    assert.equal(answer.observations[0].scope.scopeId, realmScope.scopeId);
    assert.equal(answer.usage.toolCalls, 2);
    assert.ok(host.client.isCurrent(answer));
    await host.close(); await host.close();
    assert.equal(host.client.isCurrent(answer), false);
    await rejected(host.client.inspect(req), 'CLOSED');
    assert.equal(calls.filter(c => c.options.method === 'DELETE').length, 1);
    for (const [index, call] of calls.entries()) {
      assert.equal(call.url, LAB_MCP);
      assert.equal(call.options.headers.Authorization, 'Bearer ' + TOKEN);
      assert.equal(call.options.limit, 65536);
      if (index) {
        assert.equal(call.options.headers['Mcp-Session-Id'], 'synthetic-session');
        assert.equal(call.options.headers['MCP-Protocol-Version'], '2025-11-25');
      }
    }
    assert.deepEqual(calls.filter(c => c.message?.id).map(c => c.message.id), [1, 2, 3]);
    assert.ok(!JSON.stringify(answer).includes(TOKEN));
  });
}
for (const bad of [undefined, '', 'opaque', 'a.b.c\nheader', 'x'.repeat(65537)]) {
  test(`invalid credential syntax never dispatches (${typeof bad}/${bad?.length ?? 0})`, async () => {
    const { request, calls } = fixture();
    await rejected(openReferenceSession({ request, token: bad }), 'INVALID_REQUEST');
    assert.equal(calls.length, 0);
  });
}
for (const [name, change] of Object.entries({
  'wrong response ID': response => { response.text = response.text.replace('"id":1', '"id":99'); },
  'wrong protocol': response => { response.text = response.text.replace('2025-11-25', '2025-03-26'); },
  'missing tools': response => { response.text = response.text.replace('"tools":{}', '"other":{}'); },
  'extra RPC field': response => { response.text = response.text.replace('"jsonrpc"', '"extra":true,"jsonrpc"'); },
  'duplicate RPC id': response => { response.text = response.text.replace('"id":1', '"id":1,"id":1'); },
  'RPC error': response => { response.text = JSON.stringify({ jsonrpc: '2.0', id: 1, error: { message: 'CANARY_SECRET' } }); },
  'wrong content type': response => { response.headers.set('content-type', 'text/html'); },
})) {
  test(`failed handshake ${name} cleans validated session and hides raw response`, async () => {
    const { request, calls } = fixture({ mutate: (response, { message }) => {
      if (message?.method === 'initialize') change(response); return response;
    } });
    await rejected(openReferenceSession({ request, token: TOKEN }), 'INVALID_RESPONSE');
    assert.equal(calls.at(-1).options.method, 'DELETE');
  });
}
for (const id of ['', 'has spaces', 'x'.repeat(129)]) {
  test(`untrusted session identifier is never sent back (${id.length})`, async () => {
    const { request, calls } = fixture({ mutate: response => { response.headers.set('mcp-session-id', id); return response; } });
    await rejected(openReferenceSession({ request, token: TOKEN }), 'INVALID_RESPONSE');
    assert.equal(calls.length, 1);
  });
}
test('initialization notification must be accepted without a body', async () => {
  const { request, calls } = fixture({ mutate: (response, { message }) => {
    if (message?.method === 'notifications/initialized') response.text = 'CANARY_SECRET';
    return response;
  } });
  await rejected(openReferenceSession({ request, token: TOKEN }), 'INVALID_RESPONSE');
  assert.equal(calls.at(-1).options.method, 'DELETE');
});
for (const [status, code] of [[401, 'UNAUTHENTICATED'], [403, 'NOT_AUTHORIZED'], [503, 'SOURCE_UNAVAILABLE'],
  [302, 'INVALID_RESPONSE'], [500, 'INVALID_RESPONSE']]) {
  test(`HTTP status ${status} is not usable evidence or raw error text`, async () => {
    const { request } = fixture({ mutate: (response, { message }) => {
      if (message?.method === 'tools/call') return { ...response, status, text: 'CANARY_SECRET' };
      return response;
    } });
    const host = await openReferenceSession({ request, token: TOKEN });
    try { await rejected(host.client.inspect(req), code); } finally { await host.close(); }
  });
}
test('session replacement header during a call is rejected, not silently adopted', async () => {
  const { request, calls } = fixture({ mutate: (response, { message }) => {
    if (message?.method === 'tools/call') response.headers.set('mcp-session-id', 'foreign-session');
    return response;
  } });
  const host = await openReferenceSession({ request, token: TOKEN });
  try { await rejected(host.client.inspect(req), 'INVALID_RESPONSE'); } finally { await host.close(); }
  assert.equal(calls.at(-1).options.headers['Mcp-Session-Id'], 'synthetic-session');
});
test('malformed method/resource arguments never reach the transport', async () => {
  const { request, calls } = fixture();
  const host = await openReferenceSession({ request, token: TOKEN });
  await rejected(host.client.inspect({ scopeIds: [], endpoint: 'http://foreign' }), 'INVALID_REQUEST');
  assert.equal(calls.length, 2);
  await host.close();
});
test('two host instances cannot adopt each other evidence or bearer/session context', async () => {
  const a = fixture(), b = fixture();
  const first = await openReferenceSession({ request: a.request, token: TOKEN });
  const second = await openReferenceSession({ request: b.request, token: 'different.actor.signature' });
  try {
    const answer = await first.client.inspect(req);
    assert.equal(second.client.isCurrent(answer), false);
    await first.close(); assert.equal(first.client.isCurrent(answer), false);
    await second.client.inspect(req);
    assert.ok(b.calls.every(c => c.options.headers.Authorization === 'Bearer different.actor.signature'));
  } finally { await first.close(); await second.close(); }
});
test('failed DELETE is visible, never reported as successful session cleanup', async () => {
  const { request } = fixture({ mutate: (response, { options }) => options.method === 'DELETE'
    ? { ...response, status: 405 } : response });
  const host = await openReferenceSession({ request, token: TOKEN });
  const answer = await host.client.inspect(req);
  await rejected(host.close(), 'TOOL_FAILED');
  assert.equal(host.client.isCurrent(answer), false);
  await rejected(host.close(), 'TOOL_FAILED');
});
for (const raw of ['event: endpoint\ndata: {}\n\n', 'retry: 100\ndata: {}\n\n',
  'event: message\nevent: message\ndata: {}\n\n', 'id: a\nid: b\ndata: {}\n\n',
  'data: {"jsonrpc":"2.0","id":1,"result":{}}\n\ndata: {"jsonrpc":"2.0","id":1,"result":{}}\n\n']) {
  test(`ambiguous/unsupported SSE fails closed (${raw.slice(0, 20)})`, () => {
    assert.throws(() => labRpcResult({ status: 200, headers: new Headers({ 'content-type': 'text/event-stream' }), text: raw }, 1),
      { code: 'INVALID_RESPONSE' });
  });
}
test('finite SSE comments/priming and multiline data preserve exact RPC identity', () => {
  const response = rpc(1, {});
  response.headers.set('content-type', 'text/event-stream');
  response.text = ': heartbeat\n\nid: first\ndata:\n\nevent: message\nid: second\ndata: {"jsonrpc":"2.0",\ndata: "id":1,"result":{}}\n\n';
  assert.deepEqual(labRpcResult(response, 1), {});
  assert.throws(() => labRpcResult(response, 2), { code: 'INVALID_RESPONSE' });
});
test('oversized whole RPC response rejected before decode and scalar result rejected', () => {
  const response = rpc(1, {}); response.text = 'é'.repeat(32769);
  assert.throws(() => labRpcResult(response, 1), { code: 'RESPONSE_BUDGET' });
  assert.throws(() => labRpcResult(rpc(1, null), 1), { code: 'INVALID_RESPONSE' });
});
test('caller cancellation before fetch prevents dispatch', async () => {
  let calls = 0;
  const signal = AbortSignal.abort();
  const request = createTransport({ fetchImpl: async () => { calls++; return new Response('{}'); } });
  await assert.rejects(request(LAB.api + '/mcp', { signal }), { code: 'DEADLINE_EXCEEDED' });
  assert.equal(calls, 0);
});
test('caller cancellation propagates through a stalled response body', async () => {
  const controller = new AbortController(); let cancelled = false;
  const request = createTransport({ fetchImpl: async (url, options) => {
    assert.notEqual(options.signal, controller.signal);
    queueMicrotask(() => controller.abort());
    return new Response(new ReadableStream({ cancel() { cancelled = true; } }));
  } });
  await assert.rejects(request(LAB.api + '/mcp', { signal: controller.signal }), { code: 'DEADLINE_EXCEEDED' });
  assert.equal(cancelled, true);
});
test('caller cancellation reaches fetch and sanitizes its exception', async () => {
  const controller = new AbortController();
  const request = createTransport({ fetchImpl: (url, options) => new Promise((resolve, reject) => {
    options.signal.addEventListener('abort', () => reject(new Error('CANARY_SECRET')), { once: true });
    controller.abort();
  }) });
  await assert.rejects(request(LAB.api + '/mcp', { signal: controller.signal }), error =>
    error.code === 'DEADLINE_EXCEEDED' && !error.cause && !error.message.includes('CANARY'));
});
