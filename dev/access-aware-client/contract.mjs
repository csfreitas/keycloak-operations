import descriptor from './profile.json' with { type: 'json' };
import { isDeepStrictEqual } from 'node:util';

export function freeze(value) {
  if (value && typeof value === 'object') {
    Object.values(value).forEach(freeze);
    Object.freeze(value);
  }
  return value;
}
export const PROFILE = freeze(descriptor);
export const FIELDS = freeze({
  REALM: ['bruteForceProtected', 'enabled', 'registrationAllowed', 'resetPasswordAllowed', 'verifyEmail'],
  CLIENT: ['directAccessGrantsEnabled', 'enabled', 'implicitFlowEnabled', 'publicClient',
    'serviceAccountsEnabled', 'standardFlowEnabled'],
});
const CODES = new Set(['INVALID_REQUEST', 'INVALID_RESPONSE', 'RESPONSE_BUDGET', 'STALE_EVIDENCE',
  'CONTEXT_CHANGED', 'TIME_BUDGET', 'BUSY', 'CLOSED', 'UNAUTHENTICATED', 'NOT_AUTHORIZED',
  'SOURCE_UNAVAILABLE', 'TOOL_FAILED']);
export class ClientError extends Error {
  constructor(code) {
    const safe = CODES.has(code) ? code : 'TOOL_FAILED';
    super(safe);
    this.name = 'ConfigurationClientError';
    this.code = safe;
  }
}
export const fail = code => { throw new ClientError(code); };
const record = value => value !== null && typeof value === 'object' && !Array.isArray(value);
export const exactKeys = (value, keys) => record(value) && Object.keys(value).length === keys.length
  && keys.every(key => Object.hasOwn(value, key));
export const handle = value => typeof value === 'string' && /^[a-z][a-z0-9-]{0,63}$/.test(value);
const identifier = value => typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9._:@-]{0,127}$/.test(value);

// Syntax first, then decoded-key/depth checks: JSON.parse alone hides duplicate keys.
// This parser is local to the new contract; AGT1's historical validator is untouched.
export function parseJson(source) {
  if (typeof source !== 'string') fail('INVALID_RESPONSE');
  if (source.length > PROFILE.limits.maxResponseBytes
      || Buffer.byteLength(source) > PROFILE.limits.maxResponseBytes) fail('RESPONSE_BUDGET');
  try {
    const parsed = JSON.parse(source);
    const stack = [];
    for (let i = 0; i < source.length; i++) {
      const char = source[i];
      if (char === '"') {
        const start = i++;
        while (i < source.length && source[i] !== '"') {
          if (source[i] === '\\') i++;
          i++;
        }
        const frame = stack.at(-1);
        if (frame?.object && frame.key) {
          const key = JSON.parse(source.slice(start, i + 1));
          if (frame.keys.has(key)) fail('INVALID_RESPONSE');
          frame.keys.add(key);
          frame.key = false;
        }
      } else if (char === '{' || char === '[') {
        stack.push({ object: char === '{', key: true, keys: new Set() });
        if (stack.length > PROFILE.limits.maxJsonDepth) fail('INVALID_RESPONSE');
      } else if (char === '}' || char === ']') stack.pop();
      else if (char === ',' && stack.at(-1)?.object) stack.at(-1).key = true;
    }
    return parsed;
  } catch { fail('INVALID_RESPONSE'); }
}

export function validateScope(scope) {
  if (!exactKeys(scope, ['scopeId', 'targetId', 'realm', 'kind', 'fields'])
      || !handle(scope.scopeId) || typeof scope.targetId !== 'string'
      || !/^[A-Za-z0-9._-]{1,128}$/.test(scope.targetId) || !identifier(scope.realm)
      || typeof scope.kind !== 'string' || !Object.hasOwn(FIELDS, scope.kind) || !Array.isArray(scope.fields)
      || scope.fields.length === 0 || scope.fields.length > FIELDS[scope.kind].length
      || !scope.fields.every(field => FIELDS[scope.kind].includes(field))
      || new Set(scope.fields).size !== scope.fields.length
      || !isDeepStrictEqual(scope.fields, [...scope.fields].sort())) fail('INVALID_RESPONSE');
  return scope;
}

function resultEnvelope(raw) {
  const result = parseJson(raw);
  if (!record(result) || !Object.hasOwn(result, 'content')
      || !Object.keys(result).every(key => ['content', 'isError', 'structuredContent'].includes(key))
      || (Object.hasOwn(result, 'isError') && typeof result.isError !== 'boolean')
      || !Array.isArray(result.content)) fail('INVALID_RESPONSE');
  if (result.isError) fail('TOOL_FAILED');
  if (result.content.length > PROFILE.limits.maxCatalogueScopes
      || !result.content.every(item => exactKeys(item, ['type', 'text'])
        && item.type === 'text' && typeof item.text === 'string')) fail('INVALID_RESPONSE');
  return result;
}

export function decodeCatalogue(raw) {
  const result = resultEnvelope(raw);
  // Quarkus emits one JSON TextContent per scope, not a JSON array in one block.
  if (Object.hasOwn(result, 'structuredContent')) fail('INVALID_RESPONSE');
  const scopes = result.content.map(item => validateScope(parseJson(item.text)));
  if (new Set(scopes.map(scope => scope.scopeId)).size !== scopes.length) fail('INVALID_RESPONSE');
  return scopes;
}

export function checkFreshness(observation, now = Date.now()) {
  const value = observation.collectedAt;
  if (typeof value !== 'string' || !/^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d{1,9})?Z$/.test(value)) {
    fail('INVALID_RESPONSE');
  }
  const stamp = Date.parse(value);
  if (!Number.isFinite(stamp) || new Date(stamp).toISOString().slice(0, 19) !== value.slice(0, 19)) {
    fail('INVALID_RESPONSE');
  }
  if (now - stamp > PROFILE.limits.maxEvidenceAgeMs || stamp - now > PROFILE.limits.maxFutureSkewMs) {
    fail('STALE_EVIDENCE');
  }
}

export function decodeObservation(raw, scope) {
  const result = resultEnvelope(raw);
  if (result.content.length > 1) fail('INVALID_RESPONSE');
  const textual = result.content.length === 1 ? parseJson(result.content[0].text) : undefined;
  const observation = Object.hasOwn(result, 'structuredContent') ? result.structuredContent : textual;
  if (textual !== undefined && Object.hasOwn(result, 'structuredContent')
      && !isDeepStrictEqual(textual, observation)) fail('INVALID_RESPONSE');
  if (!exactKeys(observation, ['schemaVersion', 'observationId', 'scope', 'collectedAt',
    'source', 'productVersion', 'status', 'facts', 'missingFields'])
      || observation.schemaVersion !== PROFILE.observationSchemaVersion
      || typeof observation.observationId !== 'string'
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(observation.observationId)
      || !isDeepStrictEqual(observation.scope, scope)
      || observation.source !== 'KEYCLOAK_ADMIN_API' || observation.productVersion !== 'UNKNOWN'
      || !exactKeys(observation.facts, scope.fields)
      || !Object.values(observation.facts).every(value => value === null || typeof value === 'boolean')) {
    fail('INVALID_RESPONSE');
  }
  const missing = scope.fields.filter(field => observation.facts[field] === null);
  if (!isDeepStrictEqual(observation.missingFields, missing)
      || observation.status !== (missing.length ? 'PARTIAL' : 'COMPLETE')) fail('INVALID_RESPONSE');
  checkFreshness(observation);
  return observation;
}
