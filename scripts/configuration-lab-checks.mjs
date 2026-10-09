// Disposable loopback fixtures only. Tokens, cookies, login HTML and raw errors never leave memory.
import { createHash, randomBytes } from 'node:crypto';
import { lstat, open, realpath } from 'node:fs/promises';
import { basename, dirname, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { runReferenceChecks } from './configuration-client-lab.mjs';

export const LAB = Object.freeze({
  issuer: 'http://localhost:18280/realms/operations',
  api: 'http://localhost:18081',
  target: 'http://localhost:18080',
  callback: 'http://127.0.0.1:18300/',
  targetId: 'lab-keycloak-a', realm: 'target-a',
});
const PASSWORD = 'Local-fixture-only-2026!'; // Public disposable fixture, never a production credential.
const MAX_JSON = 1_048_576, MAX_HTML = 262_144;
const ERROR_CODES = new Set(['INPUT_REJECTED', 'DESTINATION_REJECTED', 'COOKIE_REJECTED', 'BODY_REJECTED',
  'DEADLINE_EXCEEDED', 'TRANSPORT_FAILED', 'LOGIN_FAILED', 'TOKEN_REJECTED', 'CONTRACT_FAILED', 'OUTPUT_FAILED']);
const LOGIN_PHASES = new Set(['AUTHORIZE_GET', 'LOGIN_COOKIES', 'LOGIN_FORM', 'LOGIN_ACTION',
  'LOGIN_POST', 'CALLBACK', 'TOKEN_EXCHANGE', 'TOKEN_CLAIMS']);
const COOKIE_NAMES = new Set(['AUTH_SESSION_ID', 'AUTH_SESSION_ID_LEGACY', 'KC_RESTART',
  'KEYCLOAK_IDENTITY', 'KEYCLOAK_IDENTITY_LEGACY', 'KEYCLOAK_SESSION', 'KEYCLOAK_SESSION_LEGACY', 'KC_AUTH_SESSION_HASH']);
const LOGIN_FAILURE_KINDS = new Set(['COOKIE_MISSING', 'INVALID_CREDENTIALS', 'EXPIRED_ACTION',
  'HTTPS_REQUIRED', 'INVALID_REQUEST', 'INVALID_CODE', 'INVALID_REDIRECT', 'SESSION_INVALID', 'UNCLASSIFIED']);
const OPERATORS = Object.freeze([
  { name: 'rhea', username: 'rhea-realm', role: 'config-realm', scopeId: 'realm-settings', staleId: 'realm-stale',
    foreignId: 'portal-settings', kind: 'REALM', facts: { registrationAllowed: false,
      resetPasswordAllowed: true, bruteForceProtected: true, verifyEmail: true } },
  { name: 'chris', username: 'chris-client', role: 'config-client', scopeId: 'portal-settings', staleId: 'client-stale',
    foreignId: 'realm-settings', kind: 'CLIENT', facts: { enabled: true, publicClient: true,
      standardFlowEnabled: true, directAccessGrantsEnabled: false, serviceAccountsEnabled: false } },
]);

export class LabFailure extends Error {
  constructor(code = 'CONTRACT_FAILED') {
    super('Configuration lab validation failed');
    this.name = 'LabFailure';
    this.code = ERROR_CODES.has(code) ? code : 'CONTRACT_FAILED';
  }
}
function requireCondition(condition, code = 'CONTRACT_FAILED') { if (!condition) throw new LabFailure(code); }
export function safeFailure(error) {
  return { status: 'FAILED', reason: error instanceof LabFailure ? error.code : 'CONTRACT_FAILED',
    ...(error instanceof LabFailure && LOGIN_PHASES.has(error.loginPhase) ? { loginPhase: error.loginPhase } : {}),
    ...(error instanceof LabFailure && Number.isInteger(error.httpStatus) && error.httpStatus >= 100
      && error.httpStatus <= 599 ? { httpStatus: error.httpStatus } : {}),
    ...(error instanceof LabFailure && LOGIN_FAILURE_KINDS.has(error.loginFailureKind)
      ? { loginFailureKind: error.loginFailureKind } : {}),
    ...(error instanceof LabFailure && error.loginCookies ? { loginCookies: safeCookieSummary(error.loginCookies) } : {}) };
}
function safeCookieSummary(summary) {
  const safe = {};
  for (const key of ['retained', 'sent', 'secureSuppressed']) {
    if (Number.isInteger(summary[key]) && summary[key] >= 0 && summary[key] <= 32) safe[key] = summary[key];
  }
  safe.knownCookies = Array.isArray(summary.knownCookies) ? summary.knownCookies.slice(0, 32)
    .filter(item => item && COOKIE_NAMES.has(item.name) && typeof item.secure === 'boolean')
    .map(item => ({ name: item.name, secure: item.secure })) : [];
  return safe;
}
export function classifyLoginFailure(html) {
  if (typeof html !== 'string' || Buffer.byteLength(html) > MAX_HTML) return 'UNCLASSIFIED';
  const visible = html.replace(/<script\b[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style\b[\s\S]*?<\/style>/gi, ' ').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ');
  for (const [kind, pattern] of [
    ['COOKIE_MISSING', /cookie[ _]not[ _]found|cookie[^.]{0,40}(?:missing|não encontrad)/i],
    ['INVALID_CREDENTIALS', /invalid[ _]user[ _]credentials|invalid username or password|usuário ou senha inválid/i],
    ['EXPIRED_ACTION', /expired[ _]code|page has expired|action expired|login attempt timed out/i],
    ['HTTPS_REQUIRED', /https required|ssl[ _]required/i],
    ['INVALID_REDIRECT', /invalid[ _]redirect[ _]uri/i],
    ['INVALID_CODE', /invalid[ _]code|invalid authorization code/i],
    ['SESSION_INVALID', /session[ _]not[ _]found|session not active|authentication session expired/i],
    ['INVALID_REQUEST', /invalid[ _]request|invalid parameter:/i],
  ]) if (pattern.test(visible)) return kind;
  return 'UNCLASSIFIED';
}
function parseJson(text) {
  try { return JSON.parse(text); } catch { throw new LabFailure('BODY_REJECTED'); }
}
function plainObject(value) { return value != null && typeof value === 'object' && !Array.isArray(value); }
function exactKeys(value, expected) {
  return plainObject(value) && JSON.stringify(Object.keys(value).sort()) === JSON.stringify([...expected].sort());
}
function sameStrings(actual, expected) {
  return Array.isArray(actual) && actual.every(item => typeof item === 'string')
    && JSON.stringify([...actual].sort()) === JSON.stringify([...expected].sort());
}
function parsedUrl(value, base) {
  requireCondition(typeof value === 'string' && value.length <= 8192 && !/[\\\x00-\x20\x7f]/.test(value), 'DESTINATION_REJECTED');
  try {
    const url = new URL(value, base);
    requireCondition(!url.username && !url.password && !url.hash && url.protocol === 'http:', 'DESTINATION_REJECTED');
    return url;
  } catch { throw new LabFailure('DESTINATION_REJECTED'); }
}
function onlyQuery(url, allowed) {
  const names = [...url.searchParams.keys()];
  requireCondition(names.every(name => allowed.includes(name)) && new Set(names).size === names.length, 'DESTINATION_REJECTED');
}

/** No response redirect can turn the checker into a general HTTP client. */
export function validateRequestUrl(value) {
  const url = parsedUrl(value);
  const idp = new URL(LAB.issuer);
  const idpPaths = [idp.pathname + '/protocol/openid-connect/auth', idp.pathname + '/protocol/openid-connect/token',
    idp.pathname + '/login-actions/authenticate'];
  const apiPaths = /^\/api\/v1\/(?:configuration-reads(?:\/[a-z][a-z0-9-]{0,63})?|targets(?:\/lab-keycloak-a(?:\/(?:overview|assessments|snapshots|health-checks|operations-reports))?)?|fleet|audit)$/;
  const targetPaths = ['/realms/target-a/protocol/openid-connect/token', '/admin/realms/target-a',
    '/admin/realms/target-a/clients/agt2-portal-a-v1', '/admin/realms/target-a/users/configuration-negative-unallocated'];
  const allowed = url.origin === idp.origin && idpPaths.includes(url.pathname)
    || url.origin === LAB.api && (apiPaths.test(url.pathname) || url.pathname === '/mcp')
    || url.origin === LAB.target && targetPaths.includes(url.pathname);
  requireCondition(allowed, 'DESTINATION_REJECTED');
  return url;
}

function htmlAttribute(value) {
  return value.replace(/&(?:amp|quot|apos|lt|gt|#\d+|#x[0-9a-f]+);/gi, entity => {
    const known = { '&amp;': '&', '&quot;': '"', '&apos;': "'", '&lt;': '<', '&gt;': '>' };
    if (known[entity.toLowerCase()]) return known[entity.toLowerCase()];
    const code = entity.toLowerCase().startsWith('&#x') ? parseInt(entity.slice(3, -1), 16) : Number(entity.slice(2, -1));
    requireCondition(Number.isInteger(code) && code > 0 && code <= 0x10ffff, 'LOGIN_FAILED');
    return String.fromCodePoint(code);
  });
}
export function loginAction(html, clientId, onStage = () => {}) {
  onStage('LOGIN_FORM');
  requireCondition(typeof html === 'string' && Buffer.byteLength(html) <= MAX_HTML, 'LOGIN_FAILED');
  const candidates = [...html.matchAll(/<form\b([^>]*)>/gi)].map(match => {
    const attrs = new Map();
    for (const attribute of match[1].matchAll(/([\w:-]+)\s*=\s*(["'])(.*?)\2/gs)) {
      const name = attribute[1].toLowerCase();
      requireCondition(!attrs.has(name), 'LOGIN_FAILED');
      attrs.set(name, htmlAttribute(attribute[3]));
    }
    return attrs;
  }).filter(attrs => attrs.get('id') === 'kc-form-login');
  requireCondition(candidates.length === 1 && candidates[0].get('method')?.toLowerCase() === 'post', 'LOGIN_FAILED');
  onStage('LOGIN_ACTION');
  const action = parsedUrl(candidates[0].get('action'), LAB.issuer + '/');
  const issuer = new URL(LAB.issuer);
  requireCondition(action.origin === issuer.origin && action.pathname === issuer.pathname + '/login-actions/authenticate', 'DESTINATION_REJECTED');
  onlyQuery(action, ['session_code', 'execution', 'client_id', 'tab_id', 'client_data', 'auth_session_id']);
  requireCondition(action.searchParams.get('client_id') === clientId
    && ['session_code', 'execution', 'tab_id'].every(key => action.searchParams.get(key)), 'LOGIN_FAILED');
  return action.href;
}

export function authorizationCode(location, state) {
  const callback = parsedUrl(location);
  const expected = new URL(LAB.callback);
  requireCondition(callback.origin === expected.origin && callback.pathname === expected.pathname, 'DESTINATION_REJECTED');
  onlyQuery(callback, ['code', 'state', 'session_state', 'iss']);
  requireCondition(callback.searchParams.get('state') === state
    && callback.searchParams.get('iss') === LAB.issuer, 'LOGIN_FAILED');
  const code = callback.searchParams.get('code');
  requireCondition(typeof code === 'string' && /^[A-Za-z0-9._~-]{1,4096}$/.test(code), 'LOGIN_FAILED');
  return code;
}

/** Host-only operational jar: only this IdP origin and realm paths can receive cookies. */
export class LoginCookies {
  #cookies = new Map();
  #lastSent = 0;
  #lastSuppressed = 0;
  accept(headers, sourceUrl) {
    const source = parsedUrl(sourceUrl);
    requireCondition(source.origin === new URL(LAB.issuer).origin, 'COOKIE_REJECTED');
    requireCondition(typeof headers.getSetCookie === 'function', 'COOKIE_REJECTED');
    const cookies = headers.getSetCookie();
    requireCondition(Array.isArray(cookies) && cookies.length <= 32, 'COOKIE_REJECTED');
    for (const cookie of cookies) {
      requireCondition(typeof cookie === 'string' && cookie.length <= 8192 && !/[\r\n\x00]/.test(cookie), 'COOKIE_REJECTED');
      const [pair, ...attributes] = cookie.split(';');
      const equal = pair.indexOf('=');
      const name = pair.slice(0, equal).trim(), value = pair.slice(equal + 1).trim();
      requireCondition(equal > 0 && /^[!#$%&'*+.^_`|~A-Za-z0-9-]{1,128}$/.test(name)
        && /^[\x21-\x3a\x3c-\x7e]*$/.test(value), 'COOKIE_REJECTED');
      const attrs = new Map();
      for (const attribute of attributes) {
        const at = attribute.indexOf('=');
        const key = (at < 0 ? attribute : attribute.slice(0, at)).trim().toLowerCase();
        requireCondition(!attrs.has(key), 'COOKIE_REJECTED');
        attrs.set(key, at < 0 ? true : attribute.slice(at + 1).trim());
      }
      requireCondition(!attrs.has('domain') || attrs.get('domain') === 'localhost', 'COOKIE_REJECTED');
      const path = attrs.get('path') ?? '/realms/operations/';
      requireCondition(typeof path === 'string' && (path === '/' || path === '/realms/operations'
        || path.startsWith('/realms/operations/')) && path.length <= 256 && !/[\x00-\x20\x7f;?\\]/.test(path), 'COOKIE_REJECTED');
      const key = name + '\n' + path;
      if (attrs.get('max-age') === '0' || value === '') this.#cookies.delete(key);
      else this.#cookies.set(key, { name, value, path, secure: attrs.has('secure') });
      requireCondition(this.#cookies.size <= 32, 'COOKIE_REJECTED');
    }
  }
  header(destination) {
    const url = parsedUrl(destination);
    requireCondition(url.origin === new URL(LAB.issuer).origin && url.pathname.startsWith('/realms/operations/'), 'COOKIE_REJECTED');
    const matching = [...this.#cookies.values()].filter(cookie => url.pathname === cookie.path
      || url.pathname.startsWith(cookie.path.endsWith('/') ? cookie.path : cookie.path + '/'));
    // Browsers permit Secure cookies for localhost. Emulate only this explicitly
    // approved issuer; the exact origin/realm guard above still rejects every other URL.
    const approvedLocalhostException = url.origin === new URL(LAB.issuer).origin;
    const sendable = cookie => !cookie.secure || approvedLocalhostException;
    this.#lastSuppressed = matching.filter(cookie => !sendable(cookie)).length;
    this.#lastSent = matching.filter(sendable).length;
    const header = matching.filter(sendable)
      .sort((a, b) => b.path.length - a.path.length).map(cookie => cookie.name + '=' + cookie.value).join('; ');
    requireCondition(header.length <= 16_384, 'COOKIE_REJECTED');
    return header;
  }
  summary() {
    return safeCookieSummary({ retained: this.#cookies.size, sent: this.#lastSent,
      secureSuppressed: this.#lastSuppressed, knownCookies: [...this.#cookies.values()]
        .map(cookie => ({ name: cookie.name, secure: cookie.secure })) });
  }
}

export async function readBoundedBody(response, limit, signal) {
  requireCondition(Number.isInteger(limit) && limit > 0 && limit <= MAX_JSON, 'INPUT_REJECTED');
  const length = response.headers.get('content-length');
  if (!(length == null || /^\d+$/.test(length) && Number(length) <= limit)) {
    void response.body?.cancel().catch(() => {});
    throw new LabFailure('BODY_REJECTED');
  }
  if (!response.body) return '';
  const reader = response.body.getReader();
  const chunks = []; let count = 0, ended = false;
  let onAbort;
  const aborted = new Promise((_, reject) => {
    onAbort = () => reject(new LabFailure('DEADLINE_EXCEEDED'));
    signal.addEventListener('abort', onAbort, { once: true });
  });
  try {
    requireCondition(!signal.aborted, 'DEADLINE_EXCEEDED');
    while (true) {
      const next = await Promise.race([reader.read(), aborted]);
      if (next.done) { ended = true; break; }
      count += next.value.byteLength;
      requireCondition(count <= limit, 'BODY_REJECTED');
      chunks.push(next.value);
    }
    try { return new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks)); }
    catch { throw new LabFailure('BODY_REJECTED'); }
  } finally {
    signal.removeEventListener('abort', onAbort);
    if (!ended) void reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

export function createTransport({ fetchImpl = fetch, budgetMs = 480_000, now = () => performance.now() } = {}) {
  requireCondition(Number.isFinite(budgetMs) && budgetMs > 0 && budgetMs <= 480_000, 'INPUT_REJECTED');
  const deadline = now() + budgetMs;
  return async function request(urlValue, options = {}) {
    const url = validateRequestUrl(urlValue);
    const method = (options.method ?? 'GET').toUpperCase();
    const writableProtocolEndpoint = url.pathname.endsWith('/protocol/openid-connect/token')
      || url.pathname.endsWith('/login-actions/authenticate');
    requireCondition(method === 'GET' && !writableProtocolEndpoint
      || method === 'POST' && (writableProtocolEndpoint || url.origin === LAB.api
        && (url.pathname === '/mcp' || url.pathname.endsWith('/operations-reports')))
      || method === 'DELETE' && url.origin === LAB.api && url.pathname === '/mcp', 'DESTINATION_REJECTED');
    const remaining = deadline - now();
    requireCondition(remaining > 0, 'DEADLINE_EXCEEDED');
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), Math.min(10_000, remaining));
    try {
      const { limit = MAX_JSON, signal: callerSignal, ...requestOptions } = options;
      requireCondition(callerSignal === undefined || callerSignal instanceof AbortSignal, 'INPUT_REJECTED');
      const signal = callerSignal ? AbortSignal.any([controller.signal, callerSignal]) : controller.signal;
      requireCondition(!signal.aborted, 'DEADLINE_EXCEEDED');
      const response = await fetchImpl(url, { ...requestOptions, redirect: 'manual', signal });
      const text = await readBoundedBody(response, limit, signal);
      requireCondition(now() <= deadline && !signal.aborted, 'DEADLINE_EXCEEDED');
      return { status: response.status, headers: response.headers, text };
    } catch (error) {
      if (error instanceof LabFailure) throw error;
      throw new LabFailure(controller.signal.aborted || options.signal?.aborted ? 'DEADLINE_EXCEEDED' : 'TRANSPORT_FAILED');
    } finally { clearTimeout(timer); }
  };
}

export function tokenClaims(token, expectedClient, role) {
  requireCondition(typeof token === 'string' && token.length <= 65_536
    && /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(token), 'TOKEN_REJECTED');
  let claims;
  try { claims = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8')); }
  catch { throw new LabFailure('TOKEN_REJECTED'); }
  const roles = claims.realm_access?.roles;
  requireCondition(claims.iss === LAB.issuer && claims.azp === expectedClient
    && typeof claims.sub === 'string' && claims.sub.length > 0 && claims.sub.length <= 256
    && (claims.aud === 'keycloak-operations' || Array.isArray(claims.aud) && claims.aud.includes('keycloak-operations'))
    && Array.isArray(roles) && roles.includes(role)
    && !roles.some(value => ['ops-a', 'ops-b', 'setup-a', 'setup-b', 'setup-both', 'bind-only',
      role === 'config-realm' ? 'config-client' : 'config-realm'].includes(value))
    && Number.isFinite(claims.exp) && claims.exp * 1000 > Date.now(), 'TOKEN_REJECTED');
  return claims; // Inspection is not signature verification; the real backend validates the signed token.
}

export async function login(request, operator, clientId) {
  let loginPhase = 'AUTHORIZE_GET', httpStatus, loginFailureKind, loginCookies;
  try {
  const verifier = randomBytes(32).toString('base64url');
  const state = randomBytes(24).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const authorize = new URL(LAB.issuer + '/protocol/openid-connect/auth');
  authorize.search = new URLSearchParams({ client_id: clientId, redirect_uri: LAB.callback,
    response_type: 'code', scope: 'openid', state, nonce: randomBytes(24).toString('base64url'),
    code_challenge: challenge, code_challenge_method: 'S256', prompt: 'login' }).toString();
  const cookies = new LoginCookies();
  const page = await request(authorize.href, { limit: MAX_HTML });
  httpStatus = page.status;
  requireCondition(page.status === 200, 'LOGIN_FAILED');
  loginPhase = 'LOGIN_COOKIES';
  cookies.accept(page.headers, authorize.href);
  const action = loginAction(page.text, clientId, phase => { loginPhase = phase; });
  loginPhase = 'LOGIN_POST'; httpStatus = undefined;
  const cookieHeader = cookies.header(action);
  loginCookies = cookies.summary();
  const authenticated = await request(action, { method: 'POST', limit: MAX_HTML,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', Cookie: cookieHeader },
    body: new URLSearchParams({ username: operator.username, password: PASSWORD, credentialId: '' }) });
  httpStatus = authenticated.status;
  if (![302, 303].includes(authenticated.status)) loginFailureKind = classifyLoginFailure(authenticated.text);
  requireCondition([302, 303].includes(authenticated.status), 'LOGIN_FAILED');
  loginPhase = 'LOGIN_COOKIES';
  cookies.accept(authenticated.headers, action);
  loginPhase = 'CALLBACK';
  const code = authorizationCode(authenticated.headers.get('location'), state);
  loginPhase = 'TOKEN_EXCHANGE'; httpStatus = undefined;
  const tokenResponse = await request(LAB.issuer + '/protocol/openid-connect/token', { method: 'POST', limit: 65_536,
    body: new URLSearchParams({ grant_type: 'authorization_code', client_id: clientId,
      redirect_uri: LAB.callback, code, code_verifier: verifier }) });
  httpStatus = tokenResponse.status;
  requireCondition(tokenResponse.status === 200, 'LOGIN_FAILED');
  loginPhase = 'TOKEN_CLAIMS';
  const result = parseJson(tokenResponse.text);
  requireCondition(result.token_type?.toLowerCase() === 'bearer', 'TOKEN_REJECTED');
  const claims = tokenClaims(result.access_token, clientId, operator.role);
  return { token: result.access_token, subject: claims.sub };
  } catch (error) {
    const failure = error instanceof LabFailure ? error : new LabFailure('LOGIN_FAILED');
    failure.loginPhase = loginPhase;
    failure.httpStatus = httpStatus;
    failure.loginFailureKind = loginFailureKind;
    failure.loginCookies = loginCookies;
    throw failure;
  }
}

export function validateCatalogue(value, operator) {
  requireCondition(Array.isArray(value) && value.length === 2);
  const expectedHandles = [operator.scopeId, operator.staleId];
  requireCondition(sameStrings(value.map(scope => scope.scopeId), expectedHandles));
  for (const scope of value) validateScope(scope, operator, scope.scopeId);
  return value;
}
function validateScope(scope, operator, handle) {
  requireCondition(exactKeys(scope, ['scopeId', 'targetId', 'realm', 'kind', 'fields'])
    && scope.scopeId === handle && scope.targetId === LAB.targetId && scope.realm === LAB.realm
    && scope.kind === operator.kind && sameStrings(scope.fields, Object.keys(operator.facts)));
}
export function validateObservation(value, operator) {
  requireCondition(exactKeys(value, ['schemaVersion', 'observationId', 'scope', 'collectedAt', 'source',
    'productVersion', 'status', 'facts', 'missingFields']));
  requireCondition(value.schemaVersion === '1.0' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value.observationId)
    && typeof value.collectedAt === 'string' && Number.isFinite(Date.parse(value.collectedAt))
    && Math.abs(Date.now() - Date.parse(value.collectedAt)) < 120_000
    && value.source === 'KEYCLOAK_ADMIN_API' && value.productVersion === 'UNKNOWN' && value.status === 'COMPLETE'
    && Array.isArray(value.missingFields) && value.missingFields.length === 0);
  validateScope(value.scope, operator, operator.scopeId);
  requireCondition(exactKeys(value.facts, Object.keys(operator.facts))
    && Object.entries(operator.facts).every(([key, expected]) => value.facts[key] === expected));
  return value;
}
export function rpcResult(text) {
  const pieces = text.trim().startsWith('{') ? [text] : text.split('\n')
    .filter(line => line.startsWith('data:')).map(line => line.slice(5).trim());
  const candidates = pieces.map(parseJson).filter(value => value?.result || value?.error);
  requireCondition(candidates.length === 1 && plainObject(candidates[0].result) && !candidates[0].error);
  return candidates[0].result;
}
function toolValue(result) {
  requireCondition(result.isError !== true);
  if (result.structuredContent != null) return result.structuredContent;
  requireCondition(Array.isArray(result.content) && result.content.length === 1
    && result.content[0].type === 'text' && typeof result.content[0].text === 'string');
  return parseJson(result.content[0].text);
}
export function catalogueToolValue(result) {
  requireCondition(result.isError !== true);
  if (result.structuredContent != null || result.content?.length !== 2) return toolValue(result);
  // Quarkus MCP encodes List<Scope> as one JSON TextContent per scope.
  // This lab expects exactly two entries; validateCatalogue still checks every field and owner.
  requireCondition(Array.isArray(result.content) && result.content.every(item => item?.type === 'text'
    && typeof item.text === 'string'));
  const scopes = result.content.map(item => parseJson(item.text));
  requireCondition(scopes.every(plainObject));
  return scopes;
}
export function mcpDenied(result, code, allowTransport = false) {
  if (allowTransport && result.transportDenied === true) return result.isError === true;
  const expected = code === 'AUTHORIZATION_FAILED'
    ? 'AUTHORIZATION_FAILED: Configuration scope is not authorized'
    : code === 'TARGET_NOT_AUTHORIZED' ? 'TARGET_NOT_AUTHORIZED: not authorized for target: ' + LAB.targetId : null;
  return expected != null && result.isError === true && result.structuredContent == null
    && Array.isArray(result.content) && result.content.length === 1
    && result.content[0].type === 'text' && result.content[0].text === expected;
}
function noStore(response) {
  return response.headers.get('cache-control') === 'no-store'
    && response.headers.get('vary')?.toLowerCase().split(/\s*,\s*/).includes('authorization');
}
function genericDenied(response) {
  return response.status === 403 && noStore(response) && exactKeys(parseJson(response.text), ['code', 'message'])
    && parseJson(response.text).code === 'AUTHORIZATION_FAILED'
    && parseJson(response.text).message === 'Configuration scope is not authorized';
}
async function mcp(request, bearer, clientName = 'configuration-lab') {
  const headers = { Authorization: 'Bearer ' + bearer, 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' };
  const send = (method, params, id, token = bearer) => request(LAB.api + '/mcp', { method: 'POST',
    headers: { ...headers, Authorization: 'Bearer ' + token }, body: JSON.stringify({ jsonrpc: '2.0', ...(id ? { id } : {}), method, params }) });
  const initialized = await send('initialize', { protocolVersion: '2025-11-25', capabilities: {},
    clientInfo: { name: clientName, version: '1' } }, 1);
  const id = initialized.headers.get('mcp-session-id');
  requireCondition(initialized.status === 200 && typeof id === 'string' && /^[A-Za-z0-9._-]{1,128}$/.test(id));
  headers['Mcp-Session-Id'] = id;
  const notified = await send('notifications/initialized', {});
  requireCondition([200, 202, 204].includes(notified.status));
  let requestId = 2;
  return {
    async call(name, args = {}, token = bearer) {
      const response = await send('tools/call', { name, arguments: args }, requestId++, token);
      if ([401, 403].includes(response.status)) return { isError: true, transportDenied: true };
      requireCondition(response.status === 200);
      return rpcResult(response.text);
    },
    async close() { await request(LAB.api + '/mcp', { method: 'DELETE', headers }); },
  };
}

export async function runChecks({ request = createTransport(), log = console.log } = {}) {
  const checks = [], facts = {};
  let currentCheck = 'Start bounded configuration validation';
  function phase(name) { currentCheck = name; }
  function check(name, condition = true) {
    phase(name); requireCondition(condition);
    checks.push({ name, status: 'PASSED' }); log('PASS ' + checks.length + ': ' + name);
  }
  const api = (path, token, options = {}) => request(LAB.api + path, { ...options,
    headers: { ...(token ? { Authorization: 'Bearer ' + token } : {}), ...(options.headers ?? {}) } });
  try {
    for (const [label, token] of [['Anonymous', null], ['Malformed bearer', 'not-a-jwt']]) {
      phase(label + ' authentication boundary');
      for (const path of ['/api/v1/configuration-reads', '/api/v1/configuration-reads/realm-settings']) {
        const response = await api(path, token);
        check(label + ' cannot access ' + (path.endsWith('realm-settings') ? 'observation' : 'catalogue'), response.status === 401);
      }
    }
    const identities = {};
    for (const operator of OPERATORS) {
      phase('PKCE authentication: ' + operator.name);
      identities[operator.name] = { rest: await login(request, operator, 'keycloak-ops-ui'),
        mcp: await login(request, operator, 'keycloak-config-mcp') };
      check('Real human PKCE identity and separate client claims: ' + operator.name,
        identities[operator.name].rest.subject === identities[operator.name].mcp.subject);
    }
    check('Operators have distinct signed subjects', identities.rhea.rest.subject !== identities.chris.rest.subject);
    for (const operator of OPERATORS) {
      const identity = identities[operator.name];
      phase('REST configuration validation: ' + operator.name);
      const catalogue = await api('/api/v1/configuration-reads', identity.rest.token);
      requireCondition(catalogue.status === 200); validateCatalogue(parseJson(catalogue.text), operator);
      check('REST catalogue exposes only own configured valid and stale handles: ' + operator.name, noStore(catalogue));
      const response = await api('/api/v1/configuration-reads/' + operator.scopeId, identity.rest.token);
      requireCondition(response.status === 200); const observed = validateObservation(parseJson(response.text), operator);
      check('REST exact Boolean projection excludes internal IDs, secrets and PII: ' + operator.name, noStore(response));
      facts[operator.scopeId] = { ...observed.facts };
      const foreign = await api('/api/v1/configuration-reads/' + operator.foreignId, identity.rest.token);
      const missing = await api('/api/v1/configuration-reads/unallocated-handle', identity.rest.token);
      check('REST foreign and unknown handles share safe denial: ' + operator.name,
        genericDenied(foreign) && genericDenied(missing) && foreign.text === missing.text);
      const stale = await api('/api/v1/configuration-reads/' + operator.staleId, identity.rest.token);
      const staleBody = parseJson(stale.text);
      check('Pinned resource identity mismatch is unavailable without facts: ' + operator.name,
        stale.status === 503 && noStore(stale) && exactKeys(staleBody, ['code', 'message'])
        && staleBody.code === 'KEYCLOAK_UNAVAILABLE' && staleBody.message === 'Configuration evidence is unavailable');
      const wrongChannel = await api('/api/v1/configuration-reads/' + operator.scopeId, identity.mcp.token);
      check('MCP-approved token cannot read REST configuration: ' + operator.name, genericDenied(wrongChannel));
      for (const path of ['/api/v1/targets', '/api/v1/fleet']) {
        const listed = await api(path, identity.rest.token);
        check('Restricted identity has empty legacy ' + path.split('/').at(-1) + ': ' + operator.name,
          listed.status === 200 && Array.isArray(parseJson(listed.text)) && parseJson(listed.text).length === 0);
      }
      for (const suffix of ['overview', 'assessments', 'snapshots', 'health-checks']) {
        const denied = await api('/api/v1/targets/' + LAB.targetId + '/' + suffix, identity.rest.token);
        check('Restricted identity cannot read legacy ' + suffix + ': ' + operator.name, denied.status === 403);
      }
      const report = await api('/api/v1/targets/' + LAB.targetId + '/operations-reports', identity.rest.token, { method: 'POST' });
      check('Restricted identity cannot collect a full report: ' + operator.name, report.status === 403);
      const audit = await api('/api/v1/audit?targetId=' + LAB.targetId, identity.rest.token);
      check('Restricted identity cannot inspect target audit: ' + operator.name, audit.status === 403);

      phase('MCP client segregation: ' + operator.name);
      const unapproved = await mcp(request, identity.rest.token, 'keycloak-config-mcp');
      try {
        const denied = await unapproved.call('keycloak_read_configuration', { scopeId: operator.scopeId });
        check('REST token plus spoofed MCP clientInfo cannot gain MCP access: ' + operator.name, mcpDenied(denied, 'AUTHORIZATION_FAILED'));
      } finally { await unapproved.close(); }

      phase('MCP configuration validation: ' + operator.name);
      const session = await mcp(request, identity.mcp.token);
      try {
        validateCatalogue(catalogueToolValue(await session.call('keycloak_list_configuration_scopes')), operator);
        check('MCP catalogue has the same authorized handles: ' + operator.name);
        const mcpObserved = validateObservation(toolValue(await session.call('keycloak_read_configuration', { scopeId: operator.scopeId })), operator);
        check('REST and MCP return identical permitted canonical facts: ' + operator.name,
          Object.keys(observed.facts).every(key => mcpObserved.facts[key] === observed.facts[key]));
        const denied = await session.call('keycloak_read_configuration', { scopeId: operator.foreignId });
        const unknown = await session.call('keycloak_read_configuration', { scopeId: 'unallocated-handle' });
        check('MCP foreign and unknown scope reads are denied: ' + operator.name,
          mcpDenied(denied, 'AUTHORIZATION_FAILED') && mcpDenied(unknown, 'AUTHORIZATION_FAILED'));
        const wrongIdentity = identities[operator.name === 'rhea' ? 'chris' : 'rhea'].mcp.token;
        const reused = await session.call('keycloak_read_configuration', { scopeId: operator.scopeId }, wrongIdentity);
        check('Other operator cannot reuse MCP session for original scope: ' + operator.name, mcpDenied(reused, 'AUTHORIZATION_FAILED', true));
        for (const [tool, args] of [['keycloak_list_realms', { targetId: LAB.targetId }],
          ['keycloak_get_realm', { targetId: LAB.targetId, realm: LAB.realm }],
          ['keycloak_list_assessments', { targetId: LAB.targetId, page: 0, size: 1 }],
          ['keycloak_generate_operations_report', { targetId: LAB.targetId, metricsWindow: '5m' }]]) {
          const deniedTool = await session.call(tool, args);
          check('Restricted MCP principal cannot use legacy ' + tool + ': ' + operator.name, mcpDenied(deniedTool, 'TARGET_NOT_AUTHORIZED'));
        }
      } finally { await session.close(); }
    }
    await runReferenceChecks({ request, identities, operators: OPERATORS, check, phase });
    phase('Independent least-privilege provider credential');
    const provider = await request(LAB.target + '/realms/target-a/protocol/openid-connect/token', { method: 'POST', limit: 65_536,
      body: new URLSearchParams({ grant_type: 'client_credentials', client_id: 'configuration-reader', client_secret: 'local-configuration-reader' }) });
    requireCondition(provider.status === 200); const providerToken = parseJson(provider.text).access_token;
    requireCondition(typeof providerToken === 'string' && providerToken.length <= 65_536, 'TOKEN_REJECTED');
    const providerHeaders = { Authorization: 'Bearer ' + providerToken };
    const realm = await request(LAB.target + '/admin/realms/target-a', { headers: providerHeaders });
    check('Identity B can read exact pinned realm', realm.status === 200 && parseJson(realm.text).id === 'agt2-realm-a-v1');
    const client = await request(LAB.target + '/admin/realms/target-a/clients/agt2-portal-a-v1', { headers: providerHeaders });
    check('Identity B can read exact pinned client', client.status === 200 && parseJson(client.text).id === 'agt2-portal-a-v1');
    const user = await request(LAB.target + '/admin/realms/target-a/users/configuration-negative-unallocated', { headers: providerHeaders });
    check('Identity B fixed user-read authorization probe is forbidden', user.status === 403);
    const confused = await api('/api/v1/configuration-reads', providerToken);
    check('Target service credential cannot authenticate as platform operator', confused.status === 401);
    return { schemaVersion: '1.0', status: 'PASSED', checks, facts,
      boundaries: { login: 'AUTHORIZATION_CODE_PKCE', signatureValidation: 'BACKEND_OIDC',
        provider: 'LOCAL_COMMUNITY_KEYCLOAK', actualResourceRecreation: false, modelEvaluated: false,
        referenceClientProfile: '0.1.0', referenceClientTransport: 'FIXED_LOOPBACK_LAB',
        signout: 'LOCAL_HOST_AND_MCP_SESSION_NOT_IDP_REVOCATION', delayedReplies: 'HOST_INJECTED_AFTER_REAL_RESPONSE' } };
  } catch (error) {
    checks.push({ name: currentCheck, ...safeFailure(error) });
    return { schemaVersion: '1.0', status: 'FAILED', checks, facts };
  }
}

export async function validateOutputPath(output) {
  try {
    requireCondition(typeof output === 'string' && resolve(output) === output
      && basename(output) === 'configuration-checks.json', 'OUTPUT_FAILED');
    const parent = await realpath(dirname(output));
    requireCondition(/^\/(?:private\/)?tmp\/kcops-(?:identity|configuration)\.[A-Za-z0-9_-]+$/.test(parent), 'OUTPUT_FAILED');
    const path = parent + '/configuration-checks.json';
    try { await lstat(path); throw new LabFailure('OUTPUT_FAILED'); }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
    return path;
  } catch { throw new LabFailure('OUTPUT_FAILED'); }
}

export async function writeSafeResult(output, result) {
  try {
    const path = await validateOutputPath(output);
    const handle = await open(path, 'wx', 0o600);
    try { await handle.writeFile(JSON.stringify(result, null, 2) + '\n', 'utf8'); }
    finally { await handle.close(); }
  } catch { throw new LabFailure('OUTPUT_FAILED'); }
}

async function main() {
  let result;
  try {
    const args = process.argv.slice(2);
    requireCondition(args.length === 0 || args.length === 2 && args[0] === '--output', 'INPUT_REJECTED');
    if (args.length) await validateOutputPath(args[1]);
    result = await runChecks();
    if (args.length) await writeSafeResult(args[1], result);
    console.log(JSON.stringify(result));
    process.exitCode = result.status === 'PASSED' ? 0 : 1;
  } catch (error) {
    console.log(JSON.stringify({ schemaVersion: '1.0', ...safeFailure(error) }));
    process.exitCode = 1;
  }
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) await main();
