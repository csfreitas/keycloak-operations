import React, { createContext, useCallback, useEffect, useRef, useState } from 'react';
import type { AuthMode, MeResponse } from '../api/types';
import { fetchMe } from '../api/me';
import { invalidateSession, onSessionInvalidated, setAuthToken, setAuthTokenProvider } from '../api/client';
import { getOidcSession, refreshedToken } from './oidc';
import { clearReturnPath } from './returnPath';

export interface AuthContextValue {
  authenticated: boolean;
  authMode: AuthMode;
  subject: string | null;
  displayName: string | null;
  loading: boolean;
  error: string | null;
  refresh: () => void;
  logout?: () => void;
}

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthContextValue | null>(null);

const CONFIGURED_AUTH_MODE = (import.meta.env.VITE_AUTH_MODE ?? 'OPEN_LAB') as AuthMode;

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [me, setMe] = useState<MeResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const generation = useRef(0);

  const load = useCallback(() => {
    const current = ++generation.current;
    setAuthTokenProvider(async () => { throw new Error('Authentication pending.'); });
    setLoading(true);
    setError(null);

    (async () => {
      if (CONFIGURED_AUTH_MODE === 'OIDC') {
        const client = await getOidcSession();
        if (generation.current !== current) return null;
        client.onAuthLogout = () => {
          if (generation.current !== current) return;
          invalidateSession();
        };
        setAuthTokenProvider(() => refreshedToken(client));
      } else if (CONFIGURED_AUTH_MODE === 'OPEN_LAB') {
        setAuthToken(null);
      } else {
        throw new Error('Unknown authentication mode.');
      }
      const identity = await fetchMe();
      if (!identity.authenticated || identity.authMode !== CONFIGURED_AUTH_MODE) {
        throw new Error('Platform and UI authentication modes do not match.');
      }
      return identity;
    })()
      .then((data) => {
        if (generation.current !== current || !data) return;
        setMe(data);
        setLoading(false);
      })
      .catch((err) => {
        if (generation.current !== current) return;
        invalidateSession();
        setMe({
          authenticated: false,
          authMode: CONFIGURED_AUTH_MODE,
          subject: null,
          displayName: null,
        });
        setError((err as Error).message);
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    const unsubscribe = onSessionInvalidated(() => {
      generation.current++;
      setMe(null);
      setLoading(false);
      setError('Session expired. Sign in again.');
    });
    load();
    return () => {
      generation.current++;
      unsubscribe();
      invalidateSession();
    };
  }, [load]);

  const value: AuthContextValue = {
    authenticated: me?.authenticated ?? false,
    authMode: me?.authMode ?? CONFIGURED_AUTH_MODE,
    subject: me?.subject ?? null,
    displayName: me?.displayName ?? null,
    loading,
    error,
    refresh: load,
    logout: () => {
      clearReturnPath();
      invalidateSession();
      const current = generation.current;
      setMe(null);
      setLoading(false);
      setError('Signed out.');
      void getOidcSession().then(client => {
        if (generation.current === current) return client.logout({ redirectUri: window.location.origin + '/' });
      }).catch(() => {
        if (generation.current === current) {
          setError('Unable to finish sign-out. Close this tab and sign out at the identity provider.');
        }
      });
    },
  };

  return <AuthContext.Provider value={value}>
    {loading ? <p role="status">Signing in…</p> : !value.authenticated ? <div role="alert">
      <p>{error ?? 'Authentication required.'}</p>
      <button onClick={() => window.location.reload()}>Sign in again</button>
    </div> : children}
  </AuthContext.Provider>;
}
