import { useContext } from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => {
  vi.stubEnv('VITE_AUTH_MODE', 'OIDC');
  return { session: vi.fn(), token: vi.fn() };
});
vi.mock('../auth/oidc', () => ({ getOidcSession: mocks.session, refreshedToken: mocks.token }));
import { AuthContext, AuthProvider } from '../auth/AuthProvider';
import { apiClient, setAuthToken } from '../api/client';

function Protected() {
  const auth = useContext(AuthContext)!;
  return <><p>Private report</p>
    <button onClick={() => { void apiClient.get('/targets/a').catch(() => undefined); }}>Read</button>
    <button onClick={auth.logout}>Sign out</button></>;
}

beforeEach(() => {
  mocks.session.mockResolvedValue({ logout: vi.fn().mockResolvedValue(undefined) });
  mocks.token.mockResolvedValue('synthetic-token');
});
afterEach(() => { setAuthToken(null); vi.unstubAllGlobals(); });

it.each([401, 403])('treats real transport status %s separately from target permission denial', async status => {
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({
    authenticated: true, authMode: 'OIDC', subject: 'alice', displayName: 'Alice',
  }))).mockResolvedValueOnce(new Response('{}', { status }));
  vi.stubGlobal('fetch', fetchMock);
  render(<AuthProvider><Protected /></AuthProvider>);
  fireEvent.click(await screen.findByRole('button', { name: 'Read' }));
  await act(async () => { await Promise.resolve(); });
  if (status === 401) {
    expect(await screen.findByRole('alert')).toHaveTextContent('Session expired');
    expect(screen.queryByText('Private report')).toBeNull();
  } else {
    expect(screen.getByText('Private report')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  }
});

it('does not send a protected read when its token resolves after actual logout invalidation', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
    authenticated: true, authMode: 'OIDC', subject: 'alice',
  })));
  vi.stubGlobal('fetch', fetchMock);
  render(<AuthProvider><Protected /></AuthProvider>);
  await screen.findByText('Private report');
  let resolve!: (token: string) => void;
  mocks.token.mockReturnValueOnce(new Promise(done => { resolve = done; }));
  fireEvent.click(screen.getByRole('button', { name: 'Read' }));
  fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
  await act(async () => resolve('old-synthetic-token'));
  expect(screen.queryByText('Private report')).toBeNull();
  expect(fetchMock).toHaveBeenCalledOnce();
});
