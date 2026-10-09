import { afterEach, describe, expect, it, vi } from 'vitest';
import type Keycloak from 'keycloak-js';
import { oidcConfiguration, refreshedToken } from '../auth/oidc';
import { apiClient, setAuthToken, setAuthTokenProvider } from '../api/client';

afterEach(() => { setAuthToken(null); vi.unstubAllGlobals(); });

describe('OIDC session boundary', () => {
  it('accepts HTTPS realm issuers and loopback HTTP labs', () => {
    expect(oidcConfiguration('https://identity.example/auth/realms/operations', 'console')).toEqual({
      url: 'https://identity.example/auth', realm: 'operations', clientId: 'console',
    });
    expect(oidcConfiguration('http://localhost:18280/realms/operations', 'console').realm).toBe('operations');
  });
  it.each([
    'http://identity.example/realms/operations',
    'https://user:password@identity.example/realms/operations',
    'https://identity.example/realms/operations?token=secret',
    'https://identity.example/realms/operations#fragment',
    'https://identity.example/not-an-issuer',
  ])('rejects an unsafe or malformed issuer: %s', (issuer) => {
    expect(() => oidcConfiguration(issuer, 'console')).toThrow();
  });
  it('requires explicit OIDC configuration', () => {
    expect(() => oidcConfiguration(undefined, 'console')).toThrow();
    expect(() => oidcConfiguration('https://identity.example/realms/operations', '')).toThrow();
  });
  it('refreshes before returning the access token', async () => {
    const client = { authenticated: true, token: 'fresh', updateToken: vi.fn().mockResolvedValue(true) };
    expect(await refreshedToken(client as unknown as Keycloak)).toBe('fresh');
    expect(client.updateToken).toHaveBeenCalledWith(30);
  });
  it('clears the session when refresh fails', async () => {
    const client = { authenticated: true, updateToken: vi.fn().mockRejectedValue(new Error()), clearToken: vi.fn() };
    await expect(refreshedToken(client as unknown as Keycloak)).rejects.toThrow('Session expired');
    expect(client.clearToken).toHaveBeenCalledOnce();
  });
  it('obtains a fresh token for each API request', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response('{}'));
    vi.stubGlobal('fetch', fetchMock);
    setAuthTokenProvider(vi.fn().mockResolvedValueOnce('first').mockResolvedValueOnce('second'));
    await apiClient.get('/me'); await apiClient.get('/me');
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer first');
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer second');
  });
  it('never falls back to an anonymous request after refresh failure', async () => {
    const fetchMock = vi.fn(); vi.stubGlobal('fetch', fetchMock);
    setAuthTokenProvider(async () => { throw new Error('Expired'); });
    await expect(apiClient.get('/me')).rejects.toThrow('Expired');
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it('rejects foreign API URLs before resolving credentials', async () => {
    const provider = vi.fn(); setAuthTokenProvider(provider);
    await expect(apiClient.get('https://foreign.example/')).rejects.toThrow('platform-relative');
    await expect(apiClient.get('//foreign.example/')).rejects.toThrow('platform-relative');
    expect(provider).not.toHaveBeenCalled();
  });
});
