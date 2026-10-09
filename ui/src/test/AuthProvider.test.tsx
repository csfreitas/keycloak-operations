import { StrictMode, useContext } from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
const mocks = vi.hoisted(() => {
  vi.stubEnv('VITE_AUTH_MODE', 'OIDC');
  return { session: vi.fn(), me: vi.fn(), provider: vi.fn(), token: vi.fn(),
    invalidate: vi.fn(), subscribe: vi.fn(), listener: undefined as (() => void) | undefined };
});
vi.mock('../auth/oidc', () => ({ getOidcSession: mocks.session, refreshedToken: mocks.token }));
vi.mock('../api/me', () => ({ fetchMe: mocks.me }));
vi.mock('../api/client', () => ({ setAuthTokenProvider: mocks.provider, setAuthToken: vi.fn(),
  invalidateSession: mocks.invalidate, onSessionInvalidated: mocks.subscribe }));
import { AuthContext, AuthProvider } from '../auth/AuthProvider';

beforeEach(() => {
  vi.clearAllMocks();
  mocks.listener = undefined;
  mocks.subscribe.mockImplementation((listener: () => void) => {
    mocks.listener = listener;
    return () => { mocks.listener = undefined; };
  });
  mocks.invalidate.mockImplementation(() => { mocks.listener?.(); });
  mocks.session.mockResolvedValue({});
  mocks.me.mockResolvedValue({ authenticated: true, authMode: 'OIDC', subject: 'alice', displayName: 'Alice' });
});
afterEach(() => vi.unstubAllEnvs());

it('does not mount target routes until OIDC and the backend identity succeed', async () => {
  let resolve!: (value: object) => void;
  mocks.session.mockReturnValue(new Promise(done => { resolve = done; }));
  render(<AuthProvider><p>Protected routes</p></AuthProvider>);
  expect(screen.queryByText('Protected routes')).toBeNull();
  expect(mocks.me).not.toHaveBeenCalled();
  resolve({});
  expect(await screen.findByText('Protected routes')).toBeInTheDocument();
  expect(mocks.provider).toHaveBeenCalled();
});

it('fails closed when the IdP fails, without probing anonymous APIs', async () => {
  mocks.session.mockRejectedValue(new Error('Sign-in failed'));
  render(<AuthProvider><p>Protected routes</p></AuthProvider>);
  expect(await screen.findByRole('alert')).toHaveTextContent('Sign-in failed');
  expect(screen.queryByText('Protected routes')).toBeNull();
  expect(mocks.me).not.toHaveBeenCalled();
});

it('rejects an OPEN_LAB backend when OIDC is requested', async () => {
  mocks.me.mockResolvedValue({ authenticated: true, authMode: 'OPEN_LAB' });
  render(<AuthProvider><p>Protected routes</p></AuthProvider>);
  expect(await screen.findByRole('alert')).toHaveTextContent('do not match');
  expect(screen.queryByText('Protected routes')).toBeNull();
});

it('ignores the stale StrictMode initialization', async () => {
  render(<StrictMode><AuthProvider><p>Protected routes</p></AuthProvider></StrictMode>);
  await waitFor(() => expect(screen.getByText('Protected routes')).toBeInTheDocument());
  expect(mocks.me).toHaveBeenCalledOnce();
});

function SessionActions() {
  const auth = useContext(AuthContext)!;
  return <><p>Protected routes</p><button onClick={auth.logout}>Sign out</button></>;
}

it('unmounts protected content when current transport invalidates authentication', async () => {
  render(<AuthProvider><SessionActions /></AuthProvider>);
  await screen.findByText('Protected routes');
  expect(mocks.listener).toBeTypeOf('function');
  act(() => mocks.listener?.());
  expect(screen.queryByText('Protected routes')).toBeNull();
  expect(screen.getByRole('alert')).toHaveTextContent('Session expired');
});

it('does not restore protected content when identity resolves after invalidation', async () => {
  let resolve!: (identity: object) => void;
  mocks.me.mockReturnValue(new Promise(done => { resolve = done; }));
  render(<AuthProvider><SessionActions /></AuthProvider>);
  await waitFor(() => expect(mocks.me).toHaveBeenCalledOnce());
  act(() => mocks.listener?.());
  await act(async () => resolve({ authenticated: true, authMode: 'OIDC', subject: 'alice' }));
  expect(screen.queryByText('Protected routes')).toBeNull();
});

it('invalidates transport before awaiting remote sign-out', async () => {
  const logout = vi.fn().mockReturnValue(new Promise(() => undefined));
  mocks.session.mockResolvedValue({ logout });
  render(<AuthProvider><SessionActions /></AuthProvider>);
  fireEvent.click(await screen.findByRole('button', { name: 'Sign out' }));
  expect(mocks.invalidate).toHaveBeenCalledOnce();
  expect(screen.queryByText('Protected routes')).toBeNull();
  expect(screen.getByRole('alert')).toHaveTextContent('Signed out');
  await waitFor(() => expect(logout).toHaveBeenCalledOnce());
});

it('invalidates transport when the adapter reports logout', async () => {
  const client: { onAuthLogout?: () => void } = {};
  mocks.session.mockResolvedValue(client);
  render(<AuthProvider><SessionActions /></AuthProvider>);
  await screen.findByText('Protected routes');
  act(() => client.onAuthLogout?.());
  expect(mocks.invalidate).toHaveBeenCalledOnce();
  expect(screen.queryByText('Protected routes')).toBeNull();
});

it('invalidates transport on a failed platform identity check', async () => {
  mocks.me.mockRejectedValue(new Error('Identity rejected'));
  render(<AuthProvider><SessionActions /></AuthProvider>);
  await screen.findByRole('alert');
  expect(mocks.invalidate).toHaveBeenCalledOnce();
  expect(screen.queryByText('Protected routes')).toBeNull();
});

it('removes its listener and invalidates transport when unmounted', async () => {
  const view = render(<AuthProvider><SessionActions /></AuthProvider>);
  await screen.findByText('Protected routes');
  view.unmount();
  expect(mocks.listener).toBeUndefined();
  expect(mocks.invalidate).toHaveBeenCalledOnce();
});
