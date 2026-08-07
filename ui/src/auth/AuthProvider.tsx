import React, { createContext, useCallback, useEffect, useState } from 'react';
import type { AuthMode, MeResponse } from '../api/types';
import { fetchMe } from '../api/me';
import { setAuthToken } from '../api/client';

export interface AuthContextValue {
  authenticated: boolean;
  authMode: AuthMode;
  subject: string | null;
  displayName: string | null;
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthContextValue | null>(null);

const CONFIGURED_AUTH_MODE = (import.meta.env.VITE_AUTH_MODE ?? 'OPEN_LAB') as AuthMode;

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [me, setMe] = useState<MeResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);

    // In OIDC mode a real implementation would exchange a code/token here
    // and call setAuthToken(token). For OPEN_LAB there is no Bearer token.
    if (CONFIGURED_AUTH_MODE === 'OPEN_LAB') {
      setAuthToken(null);
    }

    fetchMe()
      .then((data) => {
        setMe(data);
        setLoading(false);
      })
      .catch((err) => {
        // Degrade gracefully: assume OPEN_LAB unauthenticated
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
    load();
  }, [load]);

  const value: AuthContextValue = {
    authenticated: me?.authenticated ?? false,
    authMode: me?.authMode ?? CONFIGURED_AUTH_MODE,
    subject: me?.subject ?? null,
    displayName: me?.displayName ?? null,
    loading,
    error,
    refresh: load,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
