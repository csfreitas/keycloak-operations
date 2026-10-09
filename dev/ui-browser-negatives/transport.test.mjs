import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign, webcrypto } from 'node:crypto';
import { ME_URL, EXPECTED_ISSUER, JWKS_URL, scenarioConfiguration, classifyToken,
  expirationDelay, abortableDelay, verifySignature, observedFetch } from './transport.mjs';

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'synthetic-key', alg: 'RS256', use: 'sig' };
const now = 2_000_000_000_000;
function jwt(claims = {}, header = {}) {
  const input = [JSON.stringify({ alg: 'RS256', kid: jwk.kid, ...header }),
    JSON.stringify({ iss: EXPECTED_ISSUER, aud: 'keycloak-operations', exp: now / 1000 + 2,
      private_canary: 'DO_NOT_DISPLAY_THIS_CLAIM', ...claims })].map(s => Buffer.from(s).toString('base64url')).join('.');
  return `${input}.${sign('RSA-SHA256', Buffer.from(input), privateKey).toString('base64url')}`;
}
function harness({ scenario = 'valid', token = jwt(), response = new Response('{}', { status: 401 }), sleep,
  fetchFailure = false, jwksFailure = false, onObservation } = {}) {
  let clock = now;
  const calls = [];
  const observations = [];
  const nativeFetch = async (input, init) => {
    calls.push({ input, init });
    if (input === JWKS_URL) {
      if (jwksFailure) throw new Error('sensitive internal diagnostics');
      return new Response(JSON.stringify({ keys: [jwk] }));
    }
    if (fetchFailure) throw new Error('DO_NOT_DISPLAY_THIS_NATIVE_ERROR');
    return response;
  };
  const wrapped = observedFetch({ scenario, nativeFetch, crypto: webcrypto, now: () => clock,
    sleep: sleep ?? (async ms => { clock += ms; }),
    onObservation: value => { observations.push(value); onObservation?.(value); },
  });
  const init = { method: 'GET', headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' } };
  return { wrapped, calls, observations, init, response };
}

test('only exact known scenario names are accepted and configuration copies cannot mutate the registry', () => {
  for (const name of ['valid', 'wrong-issuer', 'wrong-audience', 'expired']) assert.ok(scenarioConfiguration(name).clientId);
  for (const name of [undefined, '', 'constructor', '__proto__', 'https://attacker.invalid']) assert.throws(() => scenarioConfiguration(name), /explicit supported/);
  const copy = scenarioConfiguration('valid'); copy.authority = 'changed';
  assert.equal(scenarioConfiguration('valid').authority, EXPECTED_ISSUER);
});

test('classification contains only booleans and isolates issuer, audience and elapsed expiry', () => {
  assert.deepEqual(classifyToken(jwt(), now), { issuerMatches: true, audienceMatches: true, notExpiredAtDispatch: true });
  assert.equal(classifyToken(jwt({ iss: 'http://127.0.0.1:18280/realms/operations' }), now).issuerMatches, false);
  assert.equal(classifyToken(jwt({ aud: ['other', 'keycloak-operations'] }), now).audienceMatches, true);
  assert.equal(classifyToken(jwt({ aud: 'other' }), now).audienceMatches, false);
  assert.equal(classifyToken(jwt({ exp: now / 1000 }), now).notExpiredAtDispatch, false);
  for (const token of ['', 'bad.token.value', 'x'.repeat(65_537), jwt({ exp: '2000000002' })]) {
    assert.equal(classifyToken(token, now).notExpiredAtDispatch, false);
  }
});

test('expiration delay is bounded and invalid tokens cannot appear in errors', () => {
  assert.equal(expirationDelay(jwt(), now), 4_000);
  assert.equal(expirationDelay(jwt({ exp: now / 1000 - 5 }), now), 0);
  for (const token of ['sensitive-canary', jwt({ exp: now / 1000 + 45 }), jwt({ exp: -1 }), jwt({ exp: 1e100 })]) {
    assert.throws(() => expirationDelay(token, now), /^Error: Expiry fixture requires a bounded short-lived credential\.$/);
  }
});

test('Web Crypto verifies against fixed JWKS, never token-supplied endpoints, with credential-free bounded fetch', async () => {
  const calls = [];
  const nativeFetch = async (url, options) => {
    calls.push({ url, options }); return new Response(JSON.stringify({ keys: [jwk] }));
  };
  assert.equal(await verifySignature(jwt({}, { jku: 'https://attacker.invalid/keys' }), nativeFetch, webcrypto), true);
  assert.equal(calls[0].url, JWKS_URL);
  assert.equal(calls[0].options.credentials, 'omit');
  assert.equal(calls[0].options.redirect, 'error');
  assert.equal(calls[0].options.headers, undefined);
  assert.ok(calls[0].options.signal instanceof AbortSignal);
  assert.equal(await verifySignature(jwt({}, { alg: 'HS256' }), nativeFetch, webcrypto), false);
  const valid = jwt();
  const changed = valid.split('.'); changed[1] = Buffer.from(JSON.stringify({ exp: 1 })).toString('base64url');
  assert.equal(await verifySignature(changed.join('.'), nativeFetch, webcrypto), false);
});

test('signature unavailability, malformed JWT, wrong key and oversized JWKS never count as valid', async () => {
  assert.equal(await verifySignature(jwt(), async () => { throw new Error('private'); }, webcrypto), null);
  assert.equal(await verifySignature('malformed', async () => { throw new Error('unused'); }, webcrypto), null);
  assert.equal(await verifySignature(jwt(), async () => new Response(JSON.stringify({ keys: [] })), webcrypto), false);
  assert.equal(await verifySignature(jwt(), async () => new Response('x'.repeat(65_537)), webcrypto), null);
});

test('only the exact fixed identity GET is instrumented; all other native calls are untouched', async () => {
  const h = harness();
  for (const [input, init] of [[`${ME_URL}?token=synthetic`, h.init], [ME_URL, { ...h.init, method: 'POST' }],
    ['http://evil.invalid/api/v1/me', h.init], ['http://localhost:18081/api/v1/targets', h.init]]) {
    const result = await h.wrapped(input, init);
    assert.equal(result, h.response);
    assert.equal(h.calls.at(-1).input, input); assert.equal(h.calls.at(-1).init, init);
  }
  assert.equal(h.observations.length, 0);
  assert.equal(h.calls.length, 4);
});

test('ordinary requests preserve exact argument objects, signals, headers and native 401 response', async () => {
  const h = harness(); const controller = new AbortController(); h.init.signal = controller.signal;
  assert.equal(await h.wrapped(ME_URL, h.init), h.response);
  assert.equal(h.calls.at(-1).input, ME_URL); assert.equal(h.calls.at(-1).init, h.init);
  assert.deepEqual(h.observations.at(-1), { id: 1, stage: 'completed', delayed: false,
    issuerMatches: true, audienceMatches: true, notExpiredAtDispatch: true, signatureValid: true, status: 401 });
  assert.ok(!JSON.stringify(h.observations).includes('DO_NOT_DISPLAY'));
  assert.ok(!JSON.stringify(h.observations).includes(h.init.headers.Authorization));
});

test('expiry fault delays only first request until elapsed expiry, preserving original token and native response', async () => {
  const h = harness({ scenario: 'expired' });
  assert.equal(await h.wrapped(ME_URL, h.init), h.response);
  const evidence = h.observations.at(-1);
  assert.equal(evidence.delayed, true); assert.equal(evidence.notExpiredAtDispatch, false);
  assert.equal(evidence.signatureValid, true); assert.equal(h.calls.at(-1).init, h.init);
  await h.wrapped(ME_URL, h.init);
  assert.equal(h.observations.at(-1).delayed, false);
});

test('abort before send never dispatches API and frees first-expiry selection for StrictMode replacement', async () => {
  const controller = new AbortController();
  let sleeps = 0;
  const h = harness({ scenario: 'expired', sleep: async () => { if (++sleeps === 1) controller.abort(); } });
  await assert.rejects(h.wrapped(ME_URL, { ...h.init, signal: controller.signal }), { name: 'AbortError' });
  assert.equal(h.calls.filter(call => call.input === ME_URL).length, 0);
  assert.deepEqual(h.observations.at(-1), { id: 1, stage: 'aborted', delayed: true, sent: false });
  await h.wrapped(ME_URL, h.init);
  assert.equal(sleeps, 2); assert.equal(h.observations.at(-1).delayed, true);
});

test('already aborted request performs neither JWKS nor backend request', async () => {
  const h = harness(); const controller = new AbortController(); controller.abort();
  await assert.rejects(h.wrapped(ME_URL, { ...h.init, signal: controller.signal }), { name: 'AbortError' });
  assert.equal(h.calls.length, 0);
});

test('abortable delay cancels promptly and rejects unsafe waits', async () => {
  const controller = new AbortController(); const waiting = abortableDelay(8_000, controller.signal); controller.abort();
  await assert.rejects(waiting, { name: 'AbortError' });
  for (const ms of [-1, NaN, 8_001]) await assert.rejects(abortableDelay(ms), /Invalid fixture delay/);
  await abortableDelay(0);
});

test('JWKS failure remains explicit unknown while actual backend response still determines identity result', async () => {
  const h = harness({ jwksFailure: true });
  assert.equal(await h.wrapped(ME_URL, h.init), h.response);
  assert.equal(h.observations.at(-1).signatureValid, null);
  assert.equal(h.observations.at(-1).status, 401);
});

test('native error text and claims never escape the bounded evidence or fixed errors', async () => {
  const h = harness({ fetchFailure: true });
  await assert.rejects(h.wrapped(ME_URL, h.init), /^Error: Browser fixture request failed\.$/);
  assert.deepEqual(h.observations.at(-1), { id: 1, stage: 'failed', delayed: false, sent: true });
  assert.ok(!JSON.stringify(h.observations).includes('DO_NOT_DISPLAY'));
});

test('observer exceptions do not replace native success/failure and invalid expiry never reaches backend', async () => {
  const h = harness({ onObservation: () => { throw new Error('observer-only'); } });
  assert.equal(await h.wrapped(ME_URL, h.init), h.response);
  const invalid = harness({ scenario: 'expired', token: 'private-malformed-fixture-token' });
  await assert.rejects(invalid.wrapped(ME_URL, invalid.init), /^Error: Browser fixture request failed\.$/);
  assert.equal(invalid.calls.filter(call => call.input === ME_URL).length, 0);
});

test('Vite fixture binds the exact loopback, disables env-file loading/CORS and defines scenario-only auth configuration', async () => {
  const previous = process.env.KCOPS_BROWSER_SCENARIO;
  process.env.KCOPS_BROWSER_SCENARIO = 'wrong-audience';
  try {
    const { default: config } = await import('./vite.config.mjs');
    assert.equal(config.envDir, false);
    assert.equal(config.server.host, '127.0.0.1'); assert.equal(config.server.port, 18300);
    assert.equal(config.server.strictPort, true); assert.equal(config.server.cors, false);
    assert.equal(config.server.fs.strict, true);
    assert.equal(config.define['import.meta.env.VITE_OIDC_CLIENT_ID'], '"keycloak-ops-ui-wrong-audience"');
    assert.equal(config.define['import.meta.env.VITE_OIDC_AUTHORITY'], JSON.stringify(EXPECTED_ISSUER));
    assert.equal(config.define['import.meta.env.VITE_API_BASE_URL'], '"http://localhost:18081"');
  } finally {
    if (previous === undefined) delete process.env.KCOPS_BROWSER_SCENARIO;
    else process.env.KCOPS_BROWSER_SCENARIO = previous;
  }
});
