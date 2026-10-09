import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Outlet, Route, Routes, useParams } from 'react-router-dom';
import { InstallationPage } from '../pages/InstallationPage';
import * as api from '../api/installations';

vi.mock('../api/installations');
const binding = { apiVersion: 'apps/v1', kind: 'Deployment', name: 'rhbk', uid: 'uid-a' };
const state: api.InstallationState = { targetId: 'a', clusterId: 'approved', namespace: 'iam', binding: null,
  revision: 0, managed: false, canDiscover: true, canConfirm: true };
const run = (): api.InstallationDiscovery => ({ runId: 'run-a', targetId: 'a', namespace: 'iam',
  expiresAt: new Date(Date.now() + 600000).toISOString(), candidates: [{ id: 'candidate-a', installation: binding }] });
function Context() {
  const { id } = useParams();
  return <><Link to="/b">Other target</Link><Outlet context={{ targetId: id }} /></>;
}
function show() {
  render(<MemoryRouter initialEntries={['/a']}><Routes><Route path="/:id" element={<Context />}>
    <Route index element={<InstallationPage />} /></Route></Routes></MemoryRouter>);
}
describe('Installation confirmation', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(api.fetchInstallation).mockImplementation(async id => ({ ...state, targetId: id }));
    vi.mocked(api.discoverInstallations).mockResolvedValue(run());
    vi.mocked(api.confirmInstallation).mockResolvedValue({ ...state, binding, revision: 1, managed: true });
  });
  it('requires explicit selection and acknowledgment and sends only opaque IDs', async () => {
    const user = userEvent.setup(); show();
    await user.click(await screen.findByRole('button', { name: 'Discover candidates' }));
    expect(await screen.findByRole('button', { name: 'Confirm installation' })).toBeDisabled();
    await user.click(screen.getByRole('radio'));
    expect(screen.getByRole('button', { name: 'Confirm installation' })).toBeDisabled();
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: 'Confirm installation' }));
    await screen.findByText(/saved and audited/);
    expect(api.confirmInstallation).toHaveBeenCalledTimes(1);
    expect(api.confirmInstallation).toHaveBeenCalledWith('a', 'run-a', 'candidate-a');
  });
  it('honors backend capability denial', async () => {
    vi.mocked(api.fetchInstallation).mockResolvedValue({ ...state, canDiscover: false, canConfirm: false });
    show();
    expect(await screen.findByRole('button', { name: 'Discover candidates' })).toBeDisabled();
    expect(api.discoverInstallations).not.toHaveBeenCalled();
  });
  it('blocks expired discovery', async () => {
    vi.mocked(api.discoverInstallations).mockResolvedValue({ ...run(), expiresAt: new Date(Date.now() - 1000).toISOString() });
    const user = userEvent.setup(); show();
    await user.click(await screen.findByRole('button', { name: 'Discover candidates' }));
    await screen.findByText('Discovery expired. Discover again.');
    expect(screen.getByRole('button', { name: 'Confirm installation' })).toBeDisabled();
  });
  it('does not auto-confirm a single candidate and surfaces denied discovery', async () => {
    vi.mocked(api.discoverInstallations).mockRejectedValue(new Error('Permission denied'));
    const user = userEvent.setup(); show();
    await user.click(await screen.findByRole('button', { name: 'Discover candidates' }));
    await screen.findByRole('alert');
    expect(screen.getByRole('alert')).toHaveTextContent('Permission denied');
    expect(api.confirmInstallation).not.toHaveBeenCalled();
  });
  it('discards a late discovery after switching targets', async () => {
    let resolve!: (value: api.InstallationDiscovery) => void;
    vi.mocked(api.discoverInstallations).mockReturnValue(new Promise(done => { resolve = done; }));
    const user = userEvent.setup(); show();
    await user.click(await screen.findByRole('button', { name: 'Discover candidates' }));
    await user.click(screen.getByText('Other target'));
    await waitFor(() => expect(api.fetchInstallation).toHaveBeenCalledWith('b'));
    await act(async () => resolve(run()));
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
    expect(screen.queryByText('uid-a')).not.toBeInTheDocument();
  });
});
