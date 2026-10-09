import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearReturnPath, rememberReturnPath, restoreReturnPath, safeReturnPath } from '../auth/returnPath';

const key = 'kcops.oidc.return-path';
beforeEach(() => { sessionStorage.clear(); history.replaceState(null, '', '/'); });
afterEach(() => { vi.restoreAllMocks(); sessionStorage.clear(); history.replaceState(null, '', '/'); });

describe('safe OIDC navigation hint', () => {
  it.each(['/targets', '/targets/lab-keycloak-a', '/targets/lab-keycloak-a/report',
    '/targets/lab-keycloak-a/installation', '/changes', '/changes/record-123', '/configuration'])('allows a known internal route: %s', path => {
    expect(safeReturnPath(path)).toBe(path);
  });
  it.each(['https://evil.example/', '//evil.example', '/\\evil.example', '/targets/a?token=secret',
    '/targets/a#access_token=secret', '/targets/../changes', '/targets/./report', '/targets/%2e%2e',
    '/targets/%252f%252fevil.example', '/api/v1/targets', '/unknown', '/targets/a/delete',
    '/configuration/other', '/configuration?scope=other', '/configuration#token=secret',
    '/targets/a\n', '/targets/' + 'a'.repeat(513), null])('rejects unsafe or unsupported paths: %s', path => {
    expect(safeReturnPath(path)).toBeNull();
  });
  it('retains pathname only and consumes once after the root callback', () => {
    history.replaceState(null, '', '/targets/a/report?secret=never-store#token=never-store');
    rememberReturnPath();
    expect(sessionStorage.getItem(key)).not.toContain('never-store');
    history.replaceState(null, '', '/#state=test&code=test');
    rememberReturnPath();
    restoreReturnPath();
    expect(location.pathname).toBe('/targets/a/report');
    expect(location.search + location.hash).toBe('');
    expect(sessionStorage.getItem(key)).toBeNull();
    history.replaceState(null, '', '/');
    restoreReturnPath();
    expect(location.pathname).toBe('/');
  });
  it.each([Date.now() - 11 * 60 * 1000, Date.now() + 60000, 'invalid'])('rejects expired/future/malformed timestamps: %s', savedAt => {
    sessionStorage.setItem(key, JSON.stringify({ path: '/targets/a', savedAt }));
    restoreReturnPath();
    expect(location.pathname).toBe('/');
    expect(sessionStorage.getItem(key)).toBeNull();
  });
  it.each(['broken-json', 'null', JSON.stringify({ path: '//evil.example', savedAt: Date.now() })])('discards malformed or tampered storage: %s', raw => {
    sessionStorage.setItem(key, raw);
    restoreReturnPath();
    expect(location.pathname).toBe('/');
    expect(sessionStorage.getItem(key)).toBeNull();
  });
  it('a fresh root visit clears an abandoned navigation hint', () => {
    sessionStorage.setItem(key, JSON.stringify({ path: '/targets/a', savedAt: Date.now() }));
    rememberReturnPath();
    expect(sessionStorage.getItem(key)).toBeNull();
  });
  it('storage failure does not break login navigation', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
    history.replaceState(null, '', '/targets/a');
    expect(rememberReturnPath).not.toThrow();
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    expect(restoreReturnPath).not.toThrow();
    expect(clearReturnPath).not.toThrow();
  });
});
