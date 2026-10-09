// Lab-only host adapter. No endpoint discovery, token passthrough to targets or model.
import { createConfigurationClient, ClientError, PROFILE } from '../dev/access-aware-client/client.mjs';
import { parseJson, exactKeys, handle } from '../dev/access-aware-client/contract.mjs';

export const LAB_MCP = 'http://localhost:18081/mcp';
const PROTOCOL = '2025-11-25';
const requireValue = (value, code = 'INVALID_RESPONSE') => { if (!value) throw new ClientError(code); };
const safeError = error => new ClientError(error instanceof ClientError ? error.code : 'TOOL_FAILED');
const record = value => value !== null && typeof value === 'object' && !Array.isArray(value);

/** Narrow finite JSON/SSE response codec; never executes server requests/notifications. */
export function labRpcResult(response, expectedId) {
  requireValue(response.status === 200);
  const type = response.headers.get('content-type')?.split(';')[0].trim().toLowerCase();
  requireValue(typeof response.text === 'string' && Buffer.byteLength(response.text) <= PROFILE.limits.maxResponseBytes,
    'RESPONSE_BUDGET');
  let messages;
  if (type === 'application/json') messages = [response.text];
  else {
    requireValue(type === 'text/event-stream');
    const frames = response.text.replaceAll('\r\n', '\n').split('\n\n').filter(frame => frame.trim());
    requireValue(frames.length <= 4);
    messages = frames.map(frame => {
      const data = []; let event = false, id = false;
      for (const line of frame.split('\n')) {
        if (!line || line.startsWith(':')) continue;
        if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
        else if (line.startsWith('event:')) {
          requireValue(!event && line.slice(6).trim() === 'message'); event = true;
        } else if (line.startsWith('id:')) {
          requireValue(!id && /^[\x21-\x7e]{1,128}$/.test(line.slice(3).trim())); id = true;
        } else requireValue(false); // No reconnect/retry or unknown frame semantics.
      }
      return data.join('\n');
    }).filter(text => text.trim());
  }
  requireValue(messages.length === 1);
  const message = parseJson(messages[0]);
  requireValue(exactKeys(message, ['jsonrpc', 'id', 'result']) && message.jsonrpc === '2.0'
    && message.id === expectedId && record(message.result));
  return message.result;
}

/** request is the existing fixed-destination, bounded lab transport, not model input. */
export async function openReferenceSession({ request, token, timeoutMs } = {}) {
  requireValue(typeof request === 'function' && typeof token === 'string'
    && /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(token)
    && token.length <= 65536, 'INVALID_REQUEST');
  let bearer = token, sessionId, client, closing, sequence = 1;
  let context = Symbol('authenticated lab host generation');
  const lifetime = new AbortController();
  const headers = () => ({ Authorization: 'Bearer ' + bearer, 'Content-Type': 'application/json',
    Accept: 'application/json, text/event-stream',
    ...(sessionId ? { 'Mcp-Session-Id': sessionId, 'MCP-Protocol-Version': PROTOCOL } : {}) });
  async function close() {
    if (closing) return closing;
    context = null;
    client?.close();
    lifetime.abort();
    closing = (async () => {
      try {
        if (sessionId) {
          const response = await request(LAB_MCP, { method: 'DELETE', headers: headers(), limit: 65536 });
          requireValue([200, 202, 204, 404].includes(response.status), 'TOOL_FAILED');
        }
      } catch (error) { throw safeError(error); }
      finally { bearer = null; sessionId = null; }
    })();
    return closing;
  }
  async function send(method, params, id, signal = lifetime.signal) {
    try {
      requireValue(context !== null && !signal.aborted, 'CONTEXT_CHANGED');
      const response = await request(LAB_MCP, { method: 'POST', headers: headers(), signal,
        limit: PROFILE.limits.maxResponseBytes,
        body: JSON.stringify({ jsonrpc: '2.0', ...(id === undefined ? {} : { id }), method, params }) });
      requireValue(context !== null && !signal.aborted, 'CONTEXT_CHANGED');
      if (response.status === 401) throw new ClientError('UNAUTHENTICATED');
      if (response.status === 403) throw new ClientError('NOT_AUTHORIZED');
      if (response.status === 503) throw new ClientError('SOURCE_UNAVAILABLE');
      const returnedSession = response.headers.get('mcp-session-id');
      requireValue(!sessionId || !returnedSession || returnedSession === sessionId);
      return response;
    } catch (error) { throw safeError(error); }
  }
  try {
    const response = await send('initialize', { protocolVersion: PROTOCOL, capabilities: {},
      clientInfo: { name: 'keycloak-operations-configuration-lab-host', version: '1' } }, sequence++);
    const candidate = response.headers.get('mcp-session-id');
    requireValue(typeof candidate === 'string' && /^[A-Za-z0-9._-]{1,128}$/.test(candidate));
    sessionId = candidate;
    const initialized = labRpcResult(response, 1);
    requireValue(initialized.protocolVersion === PROTOCOL && record(initialized.capabilities)
      && record(initialized.capabilities.tools) && record(initialized.serverInfo));
    const ready = await send('notifications/initialized', {});
    requireValue(ready.status === 202 && ready.text.trim() === '');
    client = createConfigurationClient({ timeoutMs, getContext: () => context,
      callTool: async (tool, options) => {
        requireValue(context !== null && options.context === context, 'CONTEXT_CHANGED');
        const catalogue = tool.name === PROFILE.allowedTools[0];
        requireValue(catalogue && exactKeys(tool.arguments, [])
          || tool.name === PROFILE.allowedTools[1] && exactKeys(tool.arguments, ['scopeId'])
            && handle(tool.arguments.scopeId), 'INVALID_REQUEST');
        const id = sequence++;
        const response = await send('tools/call', tool, id, AbortSignal.any([options.signal, lifetime.signal]));
        return JSON.stringify(labRpcResult(response, id));
      } });
    return Object.freeze({ client, close });
  } catch (error) {
    // A failed handshake must still attempt cleanup of its validated session ID.
    try { await close(); } catch { throw new ClientError('TOOL_FAILED'); }
    throw safeError(error);
  }
}

const deferred = () => { let resolve; const promise = new Promise(yes => { resolve = yes; }); return { promise, resolve }; };
async function expectCode(promise, expected) {
  try { await promise; } catch (error) {
    requireValue(error instanceof ClientError && error.code === expected, 'TOOL_FAILED'); return;
  }
  requireValue(false, 'TOOL_FAILED');
}

/** Real authenticated calls; held replies below are explicitly injected host delays. */
export async function runReferenceChecks({ request, identities, operators, check, phase }) {
  let oldAnswer, oldClient;
  for (const operator of operators) {
    phase('Reference client real MCP handshake: ' + operator.name);
    const host = await openReferenceSession({ request, token: identities[operator.name].mcp.token });
    try {
      const catalogue = await host.client.inspect({ scopeIds: [] });
      check('Reference client explicit authorized selection: ' + operator.name,
        catalogue.state === 'CLARIFICATION_REQUIRED' && catalogue.usage.toolCalls === 1
        && catalogue.scopes.length === 2 && catalogue.scopes.every(scope =>
          [operator.scopeId, operator.staleId].includes(scope.scopeId)));
      const answer = await host.client.inspect({ scopeIds: [operator.scopeId] });
      const observed = answer.observations[0];
      check('Reference client exact live facts and references: ' + operator.name,
        answer.state === 'OBSERVED' && answer.usage.toolCalls === 2 && answer.observations.length === 1
        && Object.keys(observed.facts).length === Object.keys(operator.facts).length
        && Object.entries(operator.facts).every(([key, value]) => observed.facts[key] === value)
        && answer.references.every(ref => ref.observationId === observed.observationId
          && ref.scopeId === operator.scopeId && ref.value === observed.facts[ref.pointer.slice(7)]));
      if (oldAnswer) check('Replacement operator cannot adopt old client evidence',
        !host.client.isCurrent(oldAnswer) && !oldClient.isCurrent(oldAnswer));
      for (const id of [operator.foreignId, 'unallocated-handle']) {
        phase('Reference client denies excluded selection: ' + operator.name);
        await expectCode(host.client.inspect({ scopeIds: [id] }), 'NOT_AUTHORIZED');
      }
      check('Reference client foreign and unknown handles rejected: ' + operator.name);
      phase('Reference client provider identity mismatch: ' + operator.name);
      await expectCode(host.client.inspect({ scopeIds: [operator.staleId] }), 'TOOL_FAILED');
      check('Reference client stale provider pin is failure, never facts: ' + operator.name);
      const refreshed = await host.client.inspect({ scopeIds: [operator.scopeId] });
      check('Reference client recollects after failure: ' + operator.name,
        refreshed.observations[0].observationId !== observed.observationId && host.client.isCurrent(refreshed));
      oldAnswer = refreshed; oldClient = host.client;
    } finally { await host.close(); }
    check('Local host sign-out invalidates evidence and terminates MCP session: ' + operator.name,
      !oldClient.isCurrent(oldAnswer));
    await expectCode(host.client.inspect({ scopeIds: [] }), 'CLOSED');
    const wrong = await openReferenceSession({ request, token: identities[operator.name].rest.token });
    try {
      const answer = await wrong.client.inspect({ scopeIds: [] });
      check('Reference host cannot upgrade REST-only client: ' + operator.name,
        answer.state === 'NO_AUTHORIZED_SCOPES');
    } finally { await wrong.close(); }
  }
  // Response already came from the real server; only delivery to the client is delayed.
  for (const mode of ['signout', 'timeout']) {
    const arrived = deferred(), release = deferred();
    const delayed = async (url, options) => {
      const response = await request(url, options);
      const body = options.body ? JSON.parse(options.body) : null;
      if (body?.method === 'tools/call' && body.params.name === PROFILE.allowedTools[0]) {
        arrived.resolve(); await release.promise;
      }
      return response;
    };
    phase('Reference client real response with injected ' + mode + ' delay');
    const host = await openReferenceSession({ request: delayed, token: identities[operators[0].name].mcp.token,
      ...(mode === 'timeout' ? { timeoutMs: 1000 } : {}) });
    try {
      const pending = host.client.inspect({ scopeIds: [] });
      const expected = expectCode(pending, mode === 'timeout' ? 'TIME_BUDGET' : 'CONTEXT_CHANGED');
      void expected.catch(() => {});
      // Observe failures before the fixture receives a reply instead of hanging forever.
      await Promise.race([arrived.promise, pending.then(() => requireValue(false))]);
      if (mode === 'signout') await host.close();
      await expected;
      check('Reference client rejects held live reply after ' + mode);
    } finally { release.resolve(); await host.close(); }
  }
}
