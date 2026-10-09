// Development-only fault injection. Never import this module into the product build.
export const ME_URL = 'http://localhost:18081/api/v1/me';
export const EXPECTED_ISSUER = 'http://localhost:18280/realms/operations';
export const JWKS_URL = `${EXPECTED_ISSUER}/protocol/openid-connect/certs`;
const AUDIENCE = 'keycloak-operations';
const MAX_BODY = 65_536;
const MAX_DELAY = 8_000;
const scenarios = Object.freeze({
  valid: { authority: EXPECTED_ISSUER, clientId: 'keycloak-ops-ui' },
  'wrong-issuer': { authority: 'http://127.0.0.1:18280/realms/operations', clientId: 'keycloak-ops-ui' },
  'wrong-audience': { authority: EXPECTED_ISSUER, clientId: 'keycloak-ops-ui-wrong-audience' },
  expired: { authority: EXPECTED_ISSUER, clientId: 'keycloak-ops-ui-expired' },
});

export function scenarioConfiguration(name) {
  if (!Object.hasOwn(scenarios, name)) throw new Error('Select an explicit supported browser fixture scenario.');
  return { ...scenarios[name] };
}

function decode(part) {
  if (!/^[A-Za-z0-9_-]+$/.test(part)) throw new Error('Invalid fixture credential.');
  const bytes = Uint8Array.from(atob(part.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
  return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
}

function parse(token) {
  if (typeof token !== 'string' || token.length > MAX_BODY) throw new Error('Invalid fixture credential.');
  const parts = token.split('.');
  if (parts.length !== 3 || !parts.every(p => /^[A-Za-z0-9_-]+$/.test(p))) throw new Error('Invalid fixture credential.');
  const header = decode(parts[0]);
  const claims = decode(parts[1]);
  if (!header || !claims || Array.isArray(header) || Array.isArray(claims)
      || typeof header !== 'object' || typeof claims !== 'object') throw new Error('Invalid fixture credential.');
  return { parts, header, claims };
}

export function classifyToken(token, now = Date.now()) {
  try {
    const { claims } = parse(token);
    return {
      issuerMatches: claims.iss === EXPECTED_ISSUER,
      audienceMatches: claims.aud === AUDIENCE || (Array.isArray(claims.aud) && claims.aud.includes(AUDIENCE)),
      notExpiredAtDispatch: Number.isSafeInteger(claims.exp) && claims.exp * 1000 > now,
    };
  } catch {
    return { issuerMatches: false, audienceMatches: false, notExpiredAtDispatch: false };
  }
}

export function expirationDelay(token, now = Date.now()) {
  try {
    const { claims } = parse(token);
    if (!Number.isSafeInteger(claims.exp) || claims.exp <= 0) throw new Error();
    const delay = Math.max(0, claims.exp * 1000 + 2_000 - now);
    if (delay > MAX_DELAY) throw new Error();
    return delay;
  } catch { throw new Error('Expiry fixture requires a bounded short-lived credential.'); }
}

export function abortableDelay(ms, signal) {
  if (!Number.isFinite(ms) || ms < 0 || ms > MAX_DELAY) return Promise.reject(new Error('Invalid fixture delay.'));
  return new Promise((resolve, reject) => {
    let timer;
    const cleanup = () => { clearTimeout(timer); signal?.removeEventListener('abort', abort); };
    const abort = () => { cleanup(); reject(new DOMException('Fixture request aborted.', 'AbortError')); };
    if (signal?.aborted) { abort(); return; }
    signal?.addEventListener('abort', abort, { once: true });
    timer = setTimeout(() => { cleanup(); resolve(); }, ms);
  });
}

async function boundedJson(response) {
  if (!response.ok || !response.body) throw new Error();
  const reader = response.body.getReader();
  const chunks = [];
  let size = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > MAX_BODY) throw new Error();
      chunks.push(value);
    }
    const bytes = new Uint8Array(size);
    let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
    return JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
  } finally { await reader.cancel().catch(() => undefined); reader.releaseLock(); }
}

/** null means unavailable, not a successful signature check. No token-controlled URL is used. */
export async function verifySignature(token, nativeFetch, crypto = globalThis.crypto, signal) {
  try {
    const { parts, header } = parse(token);
    if (header.alg !== 'RS256' || typeof header.kid !== 'string' || !header.kid || header.kid.length > 128) return false;
    const timeout = AbortSignal.timeout(3_000);
    const combined = signal ? AbortSignal.any([timeout, signal]) : timeout;
    const response = await nativeFetch(JWKS_URL, {
      method: 'GET', credentials: 'omit', redirect: 'error', cache: 'no-store', signal: combined,
    });
    const jwks = await boundedJson(response);
    if (!Array.isArray(jwks.keys) || jwks.keys.length > 32) return null;
    const keys = jwks.keys.filter(key => key?.kid === header.kid && key.kty === 'RSA'
      && (!key.alg || key.alg === 'RS256') && (!key.use || key.use === 'sig'));
    if (keys.length !== 1) return false;
    const key = await crypto.subtle.importKey('jwk', keys[0], { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    const signature = Uint8Array.from(atob(parts[2].replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
    return await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, signature, new TextEncoder().encode(parts.slice(0, 2).join('.')));
  } catch { return null; }
}

function matches(input, init) {
  const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input?.url;
  const method = init?.method ?? (input instanceof Request ? input.method : 'GET');
  return url === ME_URL && method.toUpperCase() === 'GET';
}

/** The returned wrapper preserves native responses; metadata contains only fixed labels, booleans and status. */
export function observedFetch({ scenario, nativeFetch, onObservation, now = Date.now, sleep = abortableDelay, crypto = globalThis.crypto }) {
  scenarioConfiguration(scenario);
  let expiryClaimed = false;
  let sequence = 0;
  const observe = value => { try { onObservation(value); } catch { /* Evidence rendering cannot alter transport. */ } };
  return async (input, init) => {
    if (!matches(input, init)) return nativeFetch(input, init);
    const id = ++sequence;
    const signal = init?.signal ?? (input instanceof Request ? input.signal : undefined);
    const headers = new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined));
    const authorization = headers.get('Authorization') ?? '';
    const token = authorization.startsWith('Bearer ') ? authorization.slice(7) : '';
    const delayed = scenario === 'expired' && !expiryClaimed;
    if (delayed) expiryClaimed = true;
    let sent = false;
    observe({ id, stage: 'checking', delayed });
    try {
      if (signal?.aborted) throw new DOMException('Fixture request aborted.', 'AbortError');
      const signatureValid = await verifySignature(token, nativeFetch, crypto, signal);
      if (signal?.aborted) throw new DOMException('Fixture request aborted.', 'AbortError');
      if (delayed) await sleep(expirationDelay(token, now()), signal);
      if (signal?.aborted) throw new DOMException('Fixture request aborted.', 'AbortError');
      const checks = { ...classifyToken(token, now()), signatureValid };
      observe({ id, stage: 'dispatching', delayed, ...checks });
      sent = true;
      const response = await nativeFetch(input, init);
      observe({ id, stage: 'completed', delayed, ...checks, status: response.status });
      return response;
    } catch (error) {
      const aborted = signal?.aborted || error?.name === 'AbortError';
      if (delayed && !sent) expiryClaimed = false;
      observe({ id, stage: aborted ? 'aborted' : 'failed', delayed, sent });
      if (aborted) throw new DOMException('Fixture request aborted.', 'AbortError');
      throw new Error('Browser fixture request failed.');
    }
  };
}
