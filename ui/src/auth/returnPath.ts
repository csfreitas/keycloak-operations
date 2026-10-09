// Navigation hint only, never authorization state. No query/hash/token is retained.
const KEY = 'kcops.oidc.return-path';
const MAX_AGE_MS = 10 * 60 * 1000;

export function safeReturnPath(path: unknown): string | null {
  if (typeof path !== 'string' || path.length > 512
      || path.split('/').some(part => part === '.' || part === '..')) return null;
  return /^\/(?:targets(?:\/[A-Za-z0-9._-]+(?:\/(?:health|assessment|report|performance|infrastructure|installation|history|changes))?)?|changes(?:\/[A-Za-z0-9._-]+)?|configuration)$/.test(path)
    ? path : null;
}

export function clearReturnPath() {
  try { window.sessionStorage.removeItem(KEY); } catch { /* Storage may be unavailable. */ }
}

export function rememberReturnPath() {
  // The adapter still validates OIDC state/nonce/PKCE; this only avoids overwriting
  // the navigation hint on the fixed root callback before that validation.
  const fragment = new URLSearchParams(window.location.hash.slice(1));
  if (window.location.pathname === '/' && fragment.has('state')
      && (fragment.has('code') || fragment.has('error'))) return;
  clearReturnPath();
  const path = safeReturnPath(window.location.pathname);
  if (!path) return;
  try { window.sessionStorage.setItem(KEY, JSON.stringify({ path, savedAt: Date.now() })); }
  catch { /* Login can proceed without retaining a navigation hint. */ }
}

/** Consume once, only after the adapter has authenticated, before mounting the router. */
export function restoreReturnPath() {
  try {
    const raw = window.sessionStorage.getItem(KEY);
    clearReturnPath();
    if (!raw) return;
    const value = JSON.parse(raw);
    const path = safeReturnPath(value?.path);
    const age = Date.now() - value?.savedAt;
    if (!path || typeof value.savedAt !== 'number' || !Number.isFinite(age)
        || age < 0 || age > MAX_AGE_MS) return;
    window.history.replaceState(null, '', path);
  } catch { clearReturnPath(); }
}
