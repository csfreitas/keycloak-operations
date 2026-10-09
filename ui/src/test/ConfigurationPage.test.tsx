import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { RouterProvider } from 'react-router-dom';
import { ConfigurationPage } from '../pages/ConfigurationPage';
import { createAppRouter } from '../routes';
import { invalidateSession, setAuthToken } from '../api/client';
import type { ConfigurationObservation, ConfigurationScope } from '../api/configurationReads';

vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ displayName: 'Alice', authMode: 'OIDC', logout: vi.fn() }) }));
vi.mock('../hooks/useEvents', () => ({ useEvents: () => ({ connected: false }) }));

const scopeA: ConfigurationScope = {
  scopeId: 'realm-a', targetId: 'target-a', realm: 'realm-a', kind: 'REALM',
  fields: ['enabled', 'registrationAllowed', 'resetPasswordAllowed'],
};
const scopeB: ConfigurationScope = {
  scopeId: 'client-b', targetId: 'target-b', realm: 'realm-b', kind: 'CLIENT',
  fields: ['enabled', 'publicClient'],
};
const fetchMock = vi.fn();
const ids: Record<string, string> = {
  'observation-a': '11111111-1111-4111-8111-111111111111',
  'observation-b': '22222222-2222-4222-8222-222222222222',
  'current-observation': '33333333-3333-4333-8333-333333333333',
  'late-observation': '44444444-4444-4444-8444-444444444444',
};

function observation(scope = scopeA, id = 'observation-a'): ConfigurationObservation {
  return {
    schemaVersion: '1.0', observationId: ids[id], scope, collectedAt: '2026-09-19T12:00:00Z',
    source: 'KEYCLOAK_ADMIN_API', productVersion: 'UNKNOWN', status: 'COMPLETE',
    facts: Object.fromEntries(scope.fields.map(field => [field, true])), missingFields: [],
  };
}

function response(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

async function selectScope(scopeId = scopeA.scopeId) {
  const select = await screen.findByRole('combobox', { name: 'Configuration scope' });
  await userEvent.selectOptions(select, scopeId);
}

beforeEach(() => {
  vi.resetAllMocks();
  setAuthToken(null);
  history.replaceState(null, '', '/');
  fetchMock.mockResolvedValueOnce(response([scopeA, scopeB]));
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  setAuthToken(null);
  vi.unstubAllGlobals();
  history.replaceState(null, '', '/');
});

describe('ConfigurationPage', () => {
  it('loads only authorized descriptors and requires an explicit inspection', async () => {
    render(<ConfigurationPage />);
    await selectScope();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/v1\/configuration-reads$/);
    expect(screen.queryByRole('region', { name: 'Inspection results' })).not.toBeInTheDocument();
    fetchMock.mockResolvedValueOnce(response(observation()));
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    expect(await screen.findByRole('region', { name: 'Inspection results' })).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][0]).toMatch(/\/api\/v1\/configuration-reads\/realm-a$/);
    expect(fetchMock.mock.calls[1][1].method).toBe('GET');
  });

  it('opens the standalone route without requesting a target overview or fleet', async () => {
    history.replaceState(null, '', '/configuration');
    const router = createAppRouter();
    const view = render(<RouterProvider router={router} />);
    expect(await screen.findByRole('combobox', { name: 'Configuration scope' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Configuration' })).toHaveAttribute('aria-current', 'page');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toMatch(/\/configuration-reads$/);
    view.unmount();
    router.dispose();
  });

  it('shows loading and an empty authorized list without inspecting a target', async () => {
    const pending = deferred<Response>();
    fetchMock.mockReset().mockReturnValueOnce(pending.promise);
    render(<ConfigurationPage />);
    expect(screen.getByRole('status')).toHaveTextContent('Loading authorized scopes');
    await act(async () => pending.resolve(response([])));
    expect(screen.getByText('No authorized scopes')).toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Run inspection' })).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('reports descriptor loading errors and retries only the descriptor list', async () => {
    fetchMock.mockReset().mockRejectedValueOnce(new Error('Network unavailable')).mockResolvedValueOnce(response([scopeA]));
    render(<ConfigurationPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Network unavailable');
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('combobox')).toBeInTheDocument();
    expect(fetchMock.mock.calls.every(([url]) => String(url).endsWith('/configuration-reads'))).toBe(true);
  });

  it('distinguishes false and unavailable values, and shows observation time and unknown version', async () => {
    fetchMock.mockResolvedValueOnce(response({ ...observation(), status: 'PARTIAL',
      facts: { enabled: true, registrationAllowed: false, resetPasswordAllowed: null }, missingFields: ['resetPasswordAllowed'] }));
    render(<ConfigurationPage />);
    await selectScope();
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    const results = await screen.findByRole('region', { name: 'Inspection results' });
    expect(within(results).getByRole('row', { name: 'Enabled True' })).toBeInTheDocument();
    expect(within(results).getByRole('row', { name: 'User registration False' })).toBeInTheDocument();
    expect(within(results).getByRole('row', { name: 'Password reset Unknown' })).toBeInTheDocument();
    expect(within(results).getByText('Source: Keycloak Admin API · Product version: Unknown')).toBeInTheDocument();
    expect(results.querySelector('time')).toHaveAttribute('datetime', '2026-09-19T12:00:00Z');
    expect(within(results).getByRole('status')).toHaveTextContent('Partial observation');
  });

  it('clears facts as soon as selection changes and does not inspect the new scope automatically', async () => {
    fetchMock.mockResolvedValueOnce(response(observation()));
    render(<ConfigurationPage />);
    await selectScope();
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    await screen.findByText(ids['observation-a']);
    fireEvent.change(screen.getByRole('combobox'), { target: { value: scopeB.scopeId } });
    expect(screen.queryByText(ids['observation-a'])).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Inspection results' })).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it.each(['success', 'failure'] as const)('ignores an old scope %s after another inspection has completed', async outcome => {
    const old = deferred<Response>();
    fetchMock.mockReturnValueOnce(old.promise).mockResolvedValueOnce(response(observation(scopeB, 'observation-b')));
    render(<ConfigurationPage />);
    await selectScope();
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    await selectScope(scopeB.scopeId);
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    await screen.findByText(ids['observation-b']);
    await act(async () => {
      if (outcome === 'success') old.resolve(response(observation()));
      else old.reject(new Error('Old scope failure'));
    });
    expect(screen.getByText(ids['observation-b'])).toBeInTheDocument();
    expect(screen.queryByText(ids['observation-a'])).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('does not reuse an earlier response when selection returns to the same scope', async () => {
    const old = deferred<Response>();
    fetchMock.mockReturnValueOnce(old.promise).mockResolvedValueOnce(response(observation(scopeA, 'current-observation')));
    render(<ConfigurationPage />);
    await selectScope();
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    await selectScope(scopeB.scopeId);
    await selectScope();
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    await screen.findByText(ids['current-observation']);
    await act(async () => old.resolve(response(observation())));
    expect(screen.getByText(ids['current-observation'])).toBeInTheDocument();
    expect(screen.queryByText(ids['observation-a'])).not.toBeInTheDocument();
  });

  it.each(['scopeId', 'targetId', 'realm', 'kind', 'fields'] as const)('rejects an observation with a mismatched %s', async key => {
    const foreign = { ...scopeA, [key]: key === 'fields' ? ['enabled'] : key === 'kind' ? 'CLIENT' : 'foreign' };
    fetchMock.mockResolvedValueOnce(response({ ...observation(), scope: foreign }));
    render(<ConfigurationPage />);
    await selectScope();
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Observation scope does not match');
    expect(screen.queryByRole('region', { name: 'Inspection results' })).not.toBeInTheDocument();
  });

  it.each([401, 403])('clears previous facts on HTTP %s and never retries the inspection automatically', async status => {
    fetchMock.mockResolvedValueOnce(response(observation())).mockResolvedValueOnce(response({ message: 'Access denied' }, status));
    render(<ConfigurationPage />);
    await selectScope();
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    await screen.findByText(ids['observation-a']);
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    await screen.findByRole('alert');
    expect(screen.queryByText(ids['observation-a'])).not.toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Inspection results' })).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(3);
    if (status === 401) expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });

  it.each([
    ['complete with null', { facts: { enabled: true, registrationAllowed: false, resetPasswordAllowed: null }, missingFields: ['resetPasswordAllowed'] }],
    ['complete with a missing key', { facts: { enabled: true, registrationAllowed: false } }],
    ['partial without null', { status: 'PARTIAL' }],
    ['null without a missing field', { status: 'PARTIAL', facts: { enabled: true, registrationAllowed: false, resetPasswordAllowed: null } }],
    ['a missing field with a known value', { missingFields: ['enabled'] }],
    ['an extra fact', { facts: { enabled: true, registrationAllowed: false, resetPasswordAllowed: true, verifyEmail: true } }],
    ['invalid observation identity', { observationId: 'not-a-uuid' }],
  ])('rejects inconsistent observation: %s', async (_, override) => {
    fetchMock.mockResolvedValueOnce(response({ ...observation(), ...override }));
    render(<ConfigurationPage />);
    await selectScope();
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('configuration observation');
    expect(screen.queryByRole('region', { name: 'Inspection results' })).not.toBeInTheDocument();
  });

  it.each([
    ['unsafe scope', [{ ...scopeA, scopeId: '../realm' }]],
    ['long scope', [{ ...scopeA, scopeId: 'a'.repeat(65) }]],
    ['unsupported target', [{ ...scopeA, targetId: 'target/a' }]],
    ['unsupported realm', [{ ...scopeA, realm: 'realm/a' }]],
    ['empty fields', [{ ...scopeA, fields: [] }]],
    ['unknown field', [{ ...scopeA, fields: ['secret'] }]],
    ['too many scopes', Array.from({ length: 101 }, (_, index) => ({ ...scopeA, scopeId: `scope-${index}` }))],
  ])('rejects invalid descriptors without offering an inspection: %s', async (_, scopes) => {
    fetchMock.mockReset().mockResolvedValueOnce(response(scopes));
    render(<ConfigurationPage />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid configuration scopes');
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('clears displayed facts immediately on logout', async () => {
    fetchMock.mockResolvedValueOnce(response(observation()));
    render(<ConfigurationPage />);
    await selectScope();
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    await screen.findByText(ids['observation-a']);
    act(() => invalidateSession());
    expect(screen.queryByRole('region', { name: 'Inspection results' })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Session expired');
  });

  it('discards an inspection that returns after logout', async () => {
    const pending = deferred<Response>();
    fetchMock.mockResolvedValueOnce(response(observation())).mockReturnValueOnce(pending.promise);
    render(<ConfigurationPage />);
    await selectScope();
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    await screen.findByText(ids['observation-a']);
    await userEvent.click(screen.getByRole('button', { name: 'Run inspection' }));
    act(() => invalidateSession());
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    await act(async () => pending.resolve(response(observation(scopeA, 'late-observation'))));
    expect(screen.queryByRole('region', { name: 'Inspection results' })).not.toBeInTheDocument();
    expect(screen.queryByText(ids['late-observation'])).not.toBeInTheDocument();
  });

  it('disables duplicate inspection submissions while one is pending', async () => {
    const pending = deferred<Response>();
    fetchMock.mockReturnValueOnce(pending.promise);
    render(<ConfigurationPage />);
    await selectScope();
    const button = screen.getByRole('button', { name: 'Run inspection' });
    fireEvent.click(button);
    fireEvent.click(button);
    await act(async () => { await Promise.resolve(); });
    expect(button).toBeDisabled();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    await act(async () => pending.resolve(response(observation())));
    expect(button).toBeEnabled();
  });
});
