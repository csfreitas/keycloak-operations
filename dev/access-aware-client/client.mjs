import { performance } from 'node:perf_hooks';
import { PROFILE, ClientError, fail, freeze, exactKeys, handle, decodeCatalogue,
  decodeObservation, checkFreshness } from './contract.mjs';

export { PROFILE, ClientError } from './contract.mjs';

/** Trusted host callbacks only. This module does not authenticate tokens or open sockets. */
export function createConfigurationClient({ callTool, getContext, timeoutMs = PROFILE.limits.maxQuestionMs } = {}) {
  if (typeof callTool !== 'function' || typeof getContext !== 'function'
      || !Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > PROFILE.limits.maxQuestionMs) {
    fail('INVALID_REQUEST');
  }
  let active = null, transportPending = false, closed = false, generation = 0, question = 0;
  const issued = new WeakMap();
  const context = () => {
    let value;
    try { value = getContext(); } catch { fail('CONTEXT_CHANGED'); }
    if (typeof value !== 'symbol') fail('UNAUTHENTICATED');
    return value;
  };
  function invalidate() {
    generation++;
    active?.abort(new ClientError('CONTEXT_CHANGED'));
  }
  function isCurrent(answer) {
    const stamp = issued.get(answer);
    try {
      if (!stamp) return false;
      answer.observations.forEach(observation => checkFreshness(observation));
      return !closed && !!stamp && stamp.generation === generation
        && stamp.question === question && stamp.context === context();
    } catch { return false; }
  }

  async function inspect(request) {
    if (closed) fail('CLOSED');
    if (active || transportPending) fail('BUSY');
    if (!exactKeys(request, ['scopeIds']) || !Array.isArray(request.scopeIds)
        || request.scopeIds.length > PROFILE.limits.maxScopes
        || !request.scopeIds.every(handle) || new Set(request.scopeIds).size !== request.scopeIds.length) {
      fail('INVALID_REQUEST');
    }
    const scopeIds = [...request.scopeIds];
    const stamp = { context: context(), generation, question: ++question };
    const controller = new AbortController();
    active = controller;
    const deadline = performance.now() + timeoutMs;
    let calls = 0, bytes = 0;
    const timer = setTimeout(() => controller.abort(new ClientError('TIME_BUDGET')), timeoutMs);
    let onAbort;
    const aborted = new Promise((_, reject) => {
      onAbort = () => reject(controller.signal.reason);
      controller.signal.addEventListener('abort', onAbort, { once: true });
    });
    // A synchronous failure before the first await must not leave an unhandled rejection.
    void aborted.catch(() => {});
    function check() {
      if (closed || generation !== stamp.generation || context() !== stamp.context) fail('CONTEXT_CHANGED');
      if (controller.signal.aborted) throw controller.signal.reason;
      if (performance.now() >= deadline) {
        controller.abort(new ClientError('TIME_BUDGET'));
        fail('TIME_BUDGET');
      }
    }
    async function call(name, args) {
      check();
      if (++calls > PROFILE.limits.maxToolCalls || !PROFILE.allowedTools.includes(name)) fail('INVALID_REQUEST');
      const wire = await Promise.race([Promise.resolve().then(async () => {
        check();
        transportPending = true;
        try {
          return await callTool(freeze({ name, arguments: args }), { signal: controller.signal, context: stamp.context });
        } finally { transportPending = false; }
      }), aborted]);
      check();
      if (typeof wire !== 'string') fail('INVALID_RESPONSE');
      if (wire.length > PROFILE.limits.maxResponseBytes) fail('RESPONSE_BUDGET');
      const size = Buffer.byteLength(wire);
      bytes += size;
      if (size > PROFILE.limits.maxResponseBytes || bytes > PROFILE.limits.maxTotalResponseBytes) fail('RESPONSE_BUDGET');
      return wire;
    }
    function finish(payload) {
      check();
      const answer = freeze({ schemaVersion: PROFILE.answerSchemaVersion, profileId: PROFILE.id,
        profileVersion: PROFILE.version, mode: 'DETERMINISTIC_NO_AI', ...payload,
        usage: { toolCalls: calls, responseBytes: bytes },
        limitations: ['CONFIGURATION_ONLY', 'PRODUCT_VERSION_UNKNOWN', 'NO_ASSESSMENT_OR_SCORE',
          'INDEPENDENT_OBSERVATIONS_NOT_ATOMIC', 'NO_MODEL_EVALUATION'] });
      if (Buffer.byteLength(JSON.stringify(answer)) > PROFILE.limits.maxAnswerBytes) fail('RESPONSE_BUDGET');
      check();
      issued.set(answer, stamp);
      return answer;
    }
    try {
      const scopes = decodeCatalogue(await call(PROFILE.allowedTools[0], {}));
      check();
      if (!scopeIds.length) {
        return finish({ state: scopes.length ? 'CLARIFICATION_REQUIRED' : 'NO_AUTHORIZED_SCOPES',
          scopes, observations: [], references: [] });
      }
      const selected = scopeIds.map(id => scopes.find(scope => scope.scopeId === id));
      // Unknown and forbidden are indistinguishable; no partial scope names/facts escape.
      if (selected.some(scope => !scope)) fail('NOT_AUTHORIZED');
      const observations = [];
      for (const scope of selected) {
        const observation = decodeObservation(await call(PROFILE.allowedTools[1], { scopeId: scope.scopeId }), scope);
        if (observations.some(previous => previous.observationId === observation.observationId)) fail('INVALID_RESPONSE');
        observations.push(observation);
      }
      observations.forEach(observation => checkFreshness(observation));
      return finish({ state: 'OBSERVED', scopes: selected, observations,
        references: observations.flatMap(observation => observation.scope.fields.map(field => ({
          observationId: observation.observationId, scopeId: observation.scope.scopeId,
          pointer: `/facts/${field}`, value: observation.facts[field],
        }))) });
    } catch (error) {
      // Context invalidation outranks any old transport error. Never leak raw cause/message.
      try { check(); } catch (current) { error = current; }
      controller.abort(new ClientError('TOOL_FAILED'));
      throw new ClientError(error instanceof ClientError ? error.code : 'TOOL_FAILED');
    } finally {
      clearTimeout(timer);
      controller.signal.removeEventListener('abort', onAbort);
      active = null;
    }
  }
  return Object.freeze({ inspect, invalidate, isCurrent, close() { closed = true; invalidate(); } });
}
