/** One in-memory credential generation shared by REST and event transports. */
export interface SessionScope {
  readonly signal: AbortSignal;
}

interface SessionState extends SessionScope {
  readonly controller: AbortController;
  readonly active: boolean;
}

function newState(active: boolean): SessionState {
  const controller = new AbortController();
  return { controller, signal: controller.signal, active };
}

let current = newState(true);
let bearerToken: string | null = null;
let tokenProvider: (() => Promise<string>) | null = null;
const listeners = new Set<() => void>();

function replaceSession(active: boolean) {
  const previous = current;
  current = newState(active);
  previous.controller.abort();
}

export function captureSession(): SessionScope { return current; }

export function isCurrentSession(scope: SessionScope): boolean {
  return scope === current && current.active && !scope.signal.aborted;
}

export function assertCurrentSession(scope: SessionScope): void {
  if (!isCurrentSession(scope)) throw new Error('Session expired. Sign in again.');
}

/** Explicit credential installation starts a new generation, including OPEN_LAB. */
export function setAuthTokenProvider(provider: (() => Promise<string>) | null) {
  tokenProvider = provider;
  bearerToken = null;
  replaceSession(true);
}

export function setAuthToken(token: string | null) {
  tokenProvider = null;
  bearerToken = token;
  replaceSession(true);
}

/** Expected scopes prevent late 401s from revoking a replacement identity. */
export function invalidateSession(expected?: SessionScope): void {
  if (!current.active || (expected && expected !== current)) return;
  tokenProvider = null;
  bearerToken = null;
  replaceSession(false);
  for (const listener of [...listeners]) listener();
}

export function onSessionInvalidated(listener: () => void): () => void {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

export async function authHeaders(scope = captureSession()): Promise<Record<string, string>> {
  assertCurrentSession(scope);
  const token = tokenProvider ? await tokenProvider() : bearerToken;
  assertCurrentSession(scope);
  return token ? { Authorization: `Bearer ${token}` } : {};
}
