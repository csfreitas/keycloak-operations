import Keycloak from 'keycloak-js';
import { clearReturnPath, rememberReturnPath, restoreReturnPath } from './returnPath';

let session: Promise<Keycloak> | undefined;

export function oidcConfiguration(authority?: string, clientId?: string) {
  if (!authority || !clientId?.trim()) throw new Error('Configure the platform OIDC authority and public client ID.');
  const issuer = new URL(authority);
  const realm = issuer.pathname.match(/^(.*)\/realms\/([a-zA-Z0-9._-]+)\/?$/);
  const loopback = ['localhost', '127.0.0.1', '[::1]'].includes(issuer.hostname);
  if (!realm || issuer.username || issuer.password || issuer.search || issuer.hash
      || (issuer.protocol !== 'https:' && !(issuer.protocol === 'http:' && loopback))) {
    throw new Error('Use a Keycloak HTTPS issuer; HTTP is allowed only for a loopback laboratory.');
  }
  return { url: issuer.origin + realm[1], realm: realm[2], clientId: clientId.trim() };
}

/** Single initialization, including React StrictMode. Tokens stay in adapter memory. */
export function getOidcSession(): Promise<Keycloak> {
  if (!session) {
    session = (async () => {
      rememberReturnPath();
      const client = new Keycloak(oidcConfiguration(
        import.meta.env.VITE_OIDC_AUTHORITY, import.meta.env.VITE_OIDC_CLIENT_ID));
      const authenticated = await client.init({
        onLoad: 'login-required', flow: 'standard', pkceMethod: 'S256',
        checkLoginIframe: false, redirectUri: window.location.origin + '/',
      });
      if (!authenticated) throw new Error('Platform authentication is required.');
      restoreReturnPath();
      return client;
    })().catch(() => {
      clearReturnPath();
      session = undefined;
      throw new Error('Platform sign-in failed. Check the OIDC configuration and try again.');
    });
  }
  return session;
}

export async function refreshedToken(client: Keycloak): Promise<string> {
  try {
    if (!client.authenticated) throw new Error();
    await client.updateToken(30);
    if (!client.token) throw new Error();
    return client.token;
  } catch {
    client.clearToken();
    throw new Error('Session expired. Sign in again.');
  }
}
