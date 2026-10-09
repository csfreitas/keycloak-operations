import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => ({ init: vi.fn() }));
vi.mock('keycloak-js', () => ({ default: vi.fn().mockImplementation(function () { return { init: mocks.init }; }) }));

beforeEach(() => {
  vi.resetModules(); mocks.init.mockReset(); sessionStorage.clear();
  history.replaceState(null, '', '/targets/lab-keycloak-a/report');
  vi.stubEnv('VITE_OIDC_AUTHORITY', 'http://localhost:18280/realms/operations');
  vi.stubEnv('VITE_OIDC_CLIENT_ID', 'keycloak-ops-ui');
});
afterEach(() => { vi.unstubAllEnvs(); sessionStorage.clear(); history.replaceState(null, '', '/'); });

it('keeps a fixed callback and restores only after successful authentication', async () => {
  let resolve!: (value: boolean) => void;
  mocks.init.mockImplementation(() => {
    history.replaceState(null, '', '/');
    return new Promise(done => { resolve = done; });
  });
  const { getOidcSession } = await import('../auth/oidc');
  const first = getOidcSession();
  expect(getOidcSession()).toBe(first);
  expect(location.pathname).toBe('/');
  expect(mocks.init).toHaveBeenCalledWith(expect.objectContaining({
    redirectUri: location.origin + '/', pkceMethod: 'S256', flow: 'standard',
  }));
  resolve(true);
  await first;
  expect(location.pathname).toBe('/targets/lab-keycloak-a/report');
  expect(mocks.init).toHaveBeenCalledOnce();
});

it.each([false, 'reject'])('clears the hint and does not restore on authentication failure: %s', async failure => {
  mocks.init.mockImplementation(() => {
    history.replaceState(null, '', '/');
    return failure === false ? Promise.resolve(false) : Promise.reject(new Error('provider failure'));
  });
  const { getOidcSession } = await import('../auth/oidc');
  await expect(getOidcSession()).rejects.toThrow('Platform sign-in failed');
  expect(location.pathname).toBe('/');
  expect(sessionStorage.getItem('kcops.oidc.return-path')).toBeNull();
});
