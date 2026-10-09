import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, stat, symlink, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import test from 'node:test';
import { LAB, LabFailure, LoginCookies, authorizationCode, createTransport, loginAction, login, classifyLoginFailure,
  readBoundedBody, rpcResult, runChecks, safeFailure, tokenClaims, validateCatalogue, mcpDenied, catalogueToolValue,
  validateObservation, validateOutputPath, validateRequestUrl, writeSafeResult } from './configuration-lab-checks.mjs';

const client = 'keycloak-ops-ui';
const action = LAB.issuer + '/login-actions/authenticate?session_code=s&execution=e&client_id=' + client + '&tab_id=t';
const html = `<form id="kc-form-login" action="${action.replaceAll('&', '&amp;')}" method="post"></form>`;
const codeUrl = LAB.callback + '?state=state-value&iss=' + encodeURIComponent(LAB.issuer) + '&code=auth-code';
const canary = 'private-token-canary-should-never-leave-memory';
const operator = { name: 'rhea', scopeId: 'realm-settings', staleId: 'realm-stale', kind: 'REALM',
  facts: { registrationAllowed: false, resetPasswordAllowed: true, bruteForceProtected: true, verifyEmail: true } };
const scope = { scopeId: operator.scopeId, targetId: LAB.targetId, realm: LAB.realm,
  kind: operator.kind, fields: Object.keys(operator.facts) };
const observed = () => ({ schemaVersion: '1.0', observationId: '11111111-1111-4111-8111-111111111111',
  scope: structuredClone(scope), collectedAt: new Date().toISOString(), source: 'KEYCLOAK_ADMIN_API',
  productVersion: 'UNKNOWN', status: 'COMPLETE', facts: { ...operator.facts }, missingFields: [] });
const safeReject = (fn, code) => assert.throws(fn, error => error instanceof LabFailure
  && error.code === code && !error.message.includes(canary) && !error.cause);

test('MCP catalogue accepts the measured one-object-per-TextContent wire format', () => {
  const scopes = [scope, { ...scope, scopeId: operator.staleId }];
  const result = { isError: false, content: scopes.map(value => ({ type: 'text', text: JSON.stringify(value) })) };
  assert.deepEqual(validateCatalogue(catalogueToolValue(result), operator), scopes);
  assert.deepEqual(validateCatalogue(catalogueToolValue({ content: [{ type: 'text', text: JSON.stringify(scopes) }] }), operator), scopes);
  assert.deepEqual(validateCatalogue(catalogueToolValue({ structuredContent: scopes }), operator), scopes);
});
test('MCP catalogue keeps exact scope and field validation for every text item', () => {
  for (const scopes of [[scope, { ...scope, scopeId: 'foreign' }], [scope, { ...scope, scopeId: operator.staleId, secret: canary }],
    [scope, { ...scope, scopeId: operator.staleId, fields: [...scope.fields, 'secret'] }],
    [scope, null], [scope, []], [scope, canary], [scope, scope, scope]]) {
    const result = { content: scopes.map(value => ({ type: 'text', text: JSON.stringify(value) })) };
    assert.throws(() => validateCatalogue(catalogueToolValue(result), operator), LabFailure);
  }
  assert.throws(() => catalogueToolValue({ isError: true, content: [] }), LabFailure);
  assert.throws(() => catalogueToolValue({ content: [{ type: 'image', text: '{}' }] }), LabFailure);
});

test('fixed request destinations permit only scoped lab endpoints', () => {
  for (const url of [LAB.api + '/mcp', LAB.api + '/api/v1/configuration-reads/realm-settings',
    LAB.issuer + '/protocol/openid-connect/token', action,
    LAB.target + '/admin/realms/target-a/clients/agt2-portal-a-v1']) {
    assert.equal(validateRequestUrl(url).href, url);
  }
});
for (const url of ['https://example.com/', 'http://localhost:9999/', LAB.callback,
  'http://user:secret@localhost:18081/mcp', LAB.api + '/mcp#fragment', LAB.api + '/api/v1/targets/other',
  LAB.target + '/admin/realms/target-a/users', LAB.target + '/admin/realms/master',
  'http://127.0.0.1:18081/mcp', 'http://localhost:18081\\@example.com/mcp']) {
  test('reject foreign or broadened destination: ' + url.replace('secret', 'redacted'), () =>
    safeReject(() => validateRequestUrl(url), 'DESTINATION_REJECTED'));
}
test('PKCE login action decodes entities and binds exact realm and client', () => {
  assert.equal(loginAction(html, client), action);
});
for (const modified of [html + html, html.replace('method="post"', 'method="get"'),
  html.replace('kc-form-login', 'another-form'), html.replace('action=', 'action="bad" action='),
  '<form id="kc-form-login" method="post"></form>', 'x'.repeat(262_145)]) {
  test('malformed or ambiguous form is rejected ' + (modified.length < 100 ? modified : modified.length), () =>
    assert.throws(() => loginAction(modified, client), LabFailure));
}
for (const wrong of [action.replace('localhost', 'external.invalid'), action.replace('/operations/', '/master/'),
  action.replace(client, 'foreign-client'), action + '&redirect_uri=https://external.invalid/',
  action + '&client_id=' + client]) {
  test('untrusted action cannot receive fixture credentials: ' + wrong, () => {
    assert.throws(() => loginAction(`<form id="kc-form-login" method="post" action="${wrong}">`, client), LabFailure);
  });
}
test('authorization code accepted only from exact callback with state and issuer', () => {
  assert.equal(authorizationCode(codeUrl, 'state-value'), 'auth-code');
});
for (const wrong of [codeUrl.replace('127.0.0.1', 'localhost'), codeUrl.replace(':18300/', ':18301/'),
  codeUrl.replace('/?', '/other?'), codeUrl.replace('state-value', 'foreign-state'),
  codeUrl.replace(encodeURIComponent(LAB.issuer), encodeURIComponent('http://external.invalid')),
  codeUrl + '&state=state-value', codeUrl + '#fragment', codeUrl + '&error=access_denied']) {
  test('foreign/malformed callback is never followed: ' + wrong, () =>
    assert.throws(() => authorizationCode(wrong, 'state-value'), LabFailure));
}
test('cookies remain host/realm-bound and deletion does not replay old state', () => {
  const jar = new LoginCookies();
  jar.accept(new Headers({ 'set-cookie': 'AUTH_SESSION_ID=value; Path=/realms/operations/; HttpOnly; SameSite=Lax' }), action);
  assert.equal(jar.header(action), 'AUTH_SESSION_ID=value');
  jar.accept(new Headers({ 'set-cookie': 'AUTH_SESSION_ID=; Path=/realms/operations/; Max-Age=0' }), action);
  assert.equal(jar.header(action), '');
});
test('Secure cookie follows browser localhost exception only for the exact approved lab issuer', () => {
  const jar = new LoginCookies();
  jar.accept(new Headers({ 'set-cookie': 'session=value; Path=/; Secure' }), action);
  assert.equal(jar.header(action), 'session=value');
  assert.deepEqual(jar.summary(), { retained: 1, sent: 1, secureSuppressed: 0, knownCookies: [] });
  for (const destination of [LAB.target + '/realms/operations/', LAB.api + '/realms/operations/',
    'http://127.0.0.1:18280/realms/operations/', 'http://localhost:18280/realms/other/']) {
    safeReject(() => jar.header(destination), 'COOKIE_REJECTED');
  }
});
for (const cookie of ['session=x; Domain=external.invalid', 'session=x; Domain=.localhost',
  'session=x; Path=/realms/other/', 'session=x; Path=/; Path=/realms/operations/',
  'not a cookie', 'session=' + 'x'.repeat(8193)]) {
  test('cookie attributes cannot widen scope ' + cookie.slice(0, 55), () => {
    const jar = new LoginCookies();
    safeReject(() => jar.accept(new Headers({ 'set-cookie': cookie }), action), 'COOKIE_REJECTED');
  });
}
test('cookie origin checks deny cross-port and target/API replay', () => {
  const jar = new LoginCookies();
  for (const url of [LAB.api + '/api/v1/configuration-reads', LAB.target + '/realms/target-a/']) {
    safeReject(() => jar.header(url), 'COOKIE_REJECTED');
    safeReject(() => jar.accept(new Headers(), url), 'COOKIE_REJECTED');
  }
});
test('bounded body includes chunked bytes and cancels oversize consumption', async () => {
  let cancelled = false;
  const body = new ReadableStream({ start(controller) { controller.enqueue(new Uint8Array(40)); },
    cancel() { cancelled = true; } });
  await assert.rejects(readBoundedBody(new Response(body), 32, new AbortController().signal), LabFailure);
  assert.equal(cancelled, true);
});
test('oversized Content-Length cancels without beginning JSON consumption', async () => {
  let cancelled = false;
  const response = new Response(new ReadableStream({ cancel() { cancelled = true; } }), { headers: { 'Content-Length': '100' } });
  await assert.rejects(readBoundedBody(response, 32, new AbortController().signal), LabFailure);
  assert.equal(cancelled, true);
});
test('invalid UTF8 cannot masquerade as valid evidence text', async () => {
  await assert.rejects(readBoundedBody(new Response(new Uint8Array([0xff])), 32, new AbortController().signal), LabFailure);
});
test('request forces manual redirects and preserves one shared deadline', async () => {
  let clock = 0, calls = 0;
  const request = createTransport({ budgetMs: 100, now: () => clock,
    fetchImpl: async (url, options) => {
      calls++; assert.equal(url.href, LAB.api + '/mcp');
      assert.equal(options.redirect, 'manual'); assert.ok(options.signal instanceof AbortSignal);
      return new Response('{}');
    } });
  assert.equal((await request(LAB.api + '/mcp', { redirect: 'follow' })).text, '{}');
  clock = 100;
  await assert.rejects(request(LAB.api + '/mcp'), error => error.code === 'DEADLINE_EXCEEDED');
  assert.equal(calls, 1);
});
test('stalled body is canceled by the shared request/body deadline', async () => {
  let cancelled = false;
  const request = createTransport({ budgetMs: 20, fetchImpl: async () => new Response(new ReadableStream({
    cancel() { cancelled = true; },
  })) });
  await assert.rejects(request(LAB.api + '/mcp'), error => error.code === 'DEADLINE_EXCEEDED');
  assert.equal(cancelled, true);
});
test('transport exceptions never preserve raw provider messages or causes', async () => {
  const request = createTransport({ fetchImpl: async () => { throw new Error(canary); } });
  await assert.rejects(request(LAB.api + '/mcp'), error => error.code === 'TRANSPORT_FAILED'
    && !JSON.stringify(safeFailure(error)).includes(canary) && !error.cause);
});
test('claim inspection checks issuer audience client role and expiry without claiming signature verification', () => {
  const claims = { iss: LAB.issuer, sub: 'human-subject', azp: client, aud: ['keycloak-operations'],
    exp: Math.floor(Date.now() / 1000) + 60, realm_access: { roles: ['config-realm'] } };
  const token = value => 'header.' + Buffer.from(JSON.stringify(value)).toString('base64url') + '.signature';
  assert.equal(tokenClaims(token(claims), client, 'config-realm').sub, claims.sub);
  for (const change of [{ iss: 'foreign' }, { azp: 'foreign' }, { aud: ['other'] }, { sub: '' }, { exp: 1 },
    { realm_access: { roles: ['config-realm', 'ops-a'] } }, { realm_access: { roles: ['config-client'] } },
    { realm_access: { roles: ['config-realm', 'setup-both'] } }]) {
    safeReject(() => tokenClaims(token({ ...claims, ...change }), client, 'config-realm'), 'TOKEN_REJECTED');
  }
});
test('catalogue validates exact owned handles without trusting returned discovery names', () => {
  assert.equal(validateCatalogue([scope, { ...scope, scopeId: operator.staleId }], operator).length, 2);
  for (const entries of [[scope], [scope, scope], [scope, { ...scope, scopeId: 'foreign-scope' }],
    [scope, { ...scope, scopeId: operator.staleId, clientId: canary }]]) {
    safeReject(() => validateCatalogue(entries, operator), 'CONTRACT_FAILED');
  }
});
test('observation is a closed Boolean projection, not unrestricted provider metadata', () => {
  assert.deepEqual(validateObservation(observed(), operator).facts, operator.facts);
  for (const change of [{ password: canary }, { productVersion: '26.7.1' }, { status: 'HEALTHY' },
    { observationId: 'not-an-id' }, { collectedAt: 'invalid' }, { missingFields: ['verifyEmail'] },
    { facts: { ...operator.facts, secret: canary } }, { facts: { ...operator.facts, verifyEmail: null } },
    { facts: { ...operator.facts, verifyEmail: 'true' } }, { scope: { ...scope, targetId: 'other' } }]) {
    safeReject(() => validateObservation({ ...observed(), ...change }, operator), 'CONTRACT_FAILED');
  }
});
test('MCP parser accepts one JSON or SSE result and rejects ambiguous results', () => {
  const envelope = JSON.stringify({ jsonrpc: '2.0', id: 1, result: { isError: false, content: [] } });
  assert.equal(rpcResult(envelope).isError, false);
  assert.equal(rpcResult('event: message\ndata: ' + envelope + '\n\n').isError, false);
  for (const text of ['', '{bad', 'data: ' + envelope + '\ndata: ' + envelope,
    JSON.stringify({ error: { message: canary } })]) assert.throws(() => rpcResult(text), LabFailure);
});
test('MCP negatives require actual authorization denial, never provider/internal failure', () => {
  const denied = text => ({ isError: true, content: [{ type: 'text', text }] });
  assert.equal(mcpDenied(denied('AUTHORIZATION_FAILED: Configuration scope is not authorized'), 'AUTHORIZATION_FAILED'), true);
  assert.equal(mcpDenied(denied('TARGET_NOT_AUTHORIZED: not authorized for target: ' + LAB.targetId), 'TARGET_NOT_AUTHORIZED'), true);
  for (const text of ['KEYCLOAK_UNAVAILABLE: Configuration evidence is unavailable', 'INTERNAL_ERROR: tool operation failed', canary]) {
    assert.equal(mcpDenied(denied(text), 'AUTHORIZATION_FAILED'), false);
    assert.equal(mcpDenied(denied(text), 'TARGET_NOT_AUTHORIZED'), false);
  }
  assert.equal(mcpDenied({ isError: true, transportDenied: true }, 'AUTHORIZATION_FAILED'), false);
  assert.equal(mcpDenied({ isError: true, transportDenied: true }, 'AUTHORIZATION_FAILED', true), true);
});
test('transport cannot mutate target resources through an allowed read URL', async () => {
  let called = false;
  const request = createTransport({ fetchImpl: async () => { called = true; return new Response('{}'); } });
  for (const method of ['DELETE', 'PUT', 'PATCH', 'POST']) {
    await assert.rejects(request(LAB.target + '/admin/realms/target-a', { method }), LabFailure);
  }
  assert.equal(called, false);
});
test('failure summary and progress never serialize raw exceptions, response bodies or tokens', async () => {
  const logs = [];
  const result = await runChecks({ log: value => logs.push(value), request: async () => { throw new Error(canary); } });
  assert.equal(result.status, 'FAILED');
  assert.equal(result.checks.length, 1);
  assert.ok(!JSON.stringify({ result, logs, failure: safeFailure(new Error(canary)) }).includes(canary));
});
test('PKCE diagnostics reveal only the failing phase and HTTP status', async () => {
  const actor = { username: 'rhea-realm', role: 'config-realm' };
  for (const [responses, phase, status] of [
    [[{ status: 400, text: canary, headers: new Headers() }], 'AUTHORIZE_GET', 400],
    [[{ status: 200, text: canary, headers: new Headers() }], 'LOGIN_FORM', 200],
    [[{ status: 200, text: html, headers: new Headers() },
      { status: 200, text: canary, headers: new Headers() }], 'LOGIN_POST', 200],
    [[{ status: 200, text: html, headers: new Headers() },
      { status: 302, text: canary, headers: new Headers({ location: LAB.callback + '?code=hidden' }) }], 'CALLBACK', 302],
  ]) {
    let index = 0;
    await assert.rejects(login(async () => responses[index++], actor, client), error => {
      const safe = safeFailure(error);
      assert.equal(safe.loginPhase, phase); assert.equal(safe.httpStatus, status);
      assert.ok(!JSON.stringify(safe).includes(canary));
      return error instanceof LabFailure;
    });
  }
  const injected = new LabFailure(); injected.loginPhase = canary; injected.httpStatus = canary;
  assert.ok(!JSON.stringify(safeFailure(injected)).includes(canary));
});
test('cookie diagnostics contain known names and flags but never values or arbitrary names', () => {
  const jar = new LoginCookies();
  const headers = new Headers();
  headers.append('set-cookie', 'AUTH_SESSION_ID=' + canary + '; Path=/realms/operations/; Secure');
  headers.append('set-cookie', canary + '=private; Path=/realms/operations/');
  jar.accept(headers, action);
  jar.header(action);
  const summary = jar.summary();
  assert.deepEqual(summary, { retained: 2, sent: 2, secureSuppressed: 0,
    knownCookies: [{ name: 'AUTH_SESSION_ID', secure: true }] });
  assert.ok(!JSON.stringify(summary).includes(canary));
  const failure = new LabFailure(); failure.loginCookies = { ...summary, value: canary,
    knownCookies: [...summary.knownCookies, { name: canary, secure: true }] };
  assert.ok(!JSON.stringify(safeFailure(failure)).includes(canary));
});
for (const [message, expected] of [
  ['Cookie not found. Please make sure cookies are enabled in your browser.', 'COOKIE_MISSING'],
  ['Invalid username or password.', 'INVALID_CREDENTIALS'], ['Page has expired', 'EXPIRED_ACTION'],
  ['HTTPS required', 'HTTPS_REQUIRED'], ['Invalid parameter: client_id', 'INVALID_REQUEST'],
  ['Invalid authorization code', 'INVALID_CODE'], ['invalid_redirect_uri', 'INVALID_REDIRECT'],
  ['Authentication session expired', 'SESSION_INVALID'], [canary, 'UNCLASSIFIED'],
]) {
  test('login error page classifies only allowlisted condition ' + expected, () => {
    assert.equal(classifyLoginFailure('<p>' + message + '</p>'), expected);
  });
}
test('scripts and large arbitrary pages cannot inject a login diagnostic', () => {
  assert.equal(classifyLoginFailure('<script>Cookie not found</script>Other'), 'UNCLASSIFIED');
  assert.equal(classifyLoginFailure('Cookie not found' + 'x'.repeat(262_144)), 'UNCLASSIFIED');
});
test('safe result output is create-only in a recognizable temporary run directory', async () => {
  const directory = await mkdtemp('/tmp/kcops-configuration.');
  const output = join(directory, 'configuration-checks.json');
  try {
    const result = { schemaVersion: '1.0', status: 'PASSED', checks: [], facts: {} };
    await validateOutputPath(output);
    await writeSafeResult(output, result);
    assert.deepEqual(JSON.parse(await readFile(output, 'utf8')), result);
    assert.equal((await stat(output)).mode & 0o777, 0o600);
    await assert.rejects(writeSafeResult(output, result), LabFailure);
  } finally { await rm(directory, { recursive: true, force: true }); }
});
test('output rejects broad destinations and pre-existing symlinks without overwrite', async () => {
  const directory = await mkdtemp('/tmp/kcops-configuration.');
  const output = join(directory, 'configuration-checks.json');
  const retained = join(directory, 'retained.txt');
  try {
    for (const value of ['/tmp/configuration-checks.json', './configuration-checks.json', join(directory, 'other.json')]) {
      await assert.rejects(validateOutputPath(value), LabFailure);
    }
    await writeFile(retained, 'retained'); await symlink(retained, output);
    await assert.rejects(writeSafeResult(output, {}), LabFailure);
    assert.equal(await readFile(retained, 'utf8'), 'retained');
  } finally { await rm(directory, { recursive: true, force: true }); }
});
