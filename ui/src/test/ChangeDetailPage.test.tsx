import { act, fireEvent, render, screen } from '@testing-library/react';
import { StrictMode } from 'react';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as changesApi from '../api/changes';
import type { ChangeRecord } from '../api/types';
import { ChangeDetailPage } from '../pages/ChangeDetailPage';

vi.mock('../api/changes');

function record(changeId: string, status: ChangeRecord['status'] = 'WAITING_APPROVAL'): ChangeRecord {
  return {
    changeId, targetId: `target-${changeId}`, environment: 'DEV', resourceType: 'CLIENT',
    resourceId: `resource-${changeId}`, realm: 'demo', operation: 'UPDATE', status,
    risk: 'LOW', policyDecision: 'APPROVAL_REQUIRED', policyReason: 'Synthetic test',
    requiresApproval: true, planFingerprint: 'plan', baselineFingerprint: 'baseline',
    approvalFingerprint: null, diff: [], verificationStatus: null, verificationMessage: null,
    resultMessage: null, approvedBy: null, approvedAt: null, appliedAt: null,
    createdAt: '2026-09-19T12:00:00Z', updatedAt: '2026-09-19T12:00:00Z',
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<T>((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

function mount() {
  return render(
    <MemoryRouter initialEntries={['/changes/a']}>
      <nav><Link to="/changes/a">Open A</Link><Link to="/changes/b">Open B</Link></nav>
      <Routes><Route path="/changes/:changeId" element={<ChangeDetailPage />} /></Routes>
    </MemoryRouter>,
  );
}
const navigate = (id: 'A' | 'B') => fireEvent.click(screen.getByRole('link', { name: `Open ${id}` }));
const title = (id: string) => screen.findByRole('heading', { name: `CLIENT · resource-${id}` });

describe('ChangeDetailPage route and action ownership', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(changesApi.fetchChange).mockImplementation(async (id) => record(id));
  });

  it('uses the existing readable secondary treatment for non-primary actions', async () => {
    mount();
    await title('a');
    expect(screen.getByRole('button', { name: 'Reject' })).toHaveClass('btn--secondary');
    expect(screen.getByRole('button', { name: 'Verify' })).toHaveClass('btn--secondary');
  });

  it.each(['Forbidden', 'Not found'])('hides A immediately while B loads and remains hidden after %s', async (message) => {
    const pending = deferred<ChangeRecord>();
    vi.mocked(changesApi.fetchChange).mockImplementation((id) => id === 'a' ? Promise.resolve(record(id)) : pending.promise);
    mount();
    await title('a');
    navigate('B');
    expect(screen.queryByTestId('change-detail')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    expect(screen.getByText('Loading change…')).toBeInTheDocument();
    await act(async () => pending.reject(new Error(message)));
    expect(screen.getByRole('alert')).toHaveTextContent(message);
    expect(screen.queryByTestId('change-detail')).not.toBeInTheDocument();
  });

  it.each(['success', 'failure'])('ignores late A GET %s after B is loaded', async (outcome) => {
    const pending = deferred<ChangeRecord>();
    vi.mocked(changesApi.fetchChange).mockImplementation((id) => id === 'a' ? pending.promise : Promise.resolve(record(id)));
    mount();
    navigate('B');
    await title('b');
    await act(async () => outcome === 'success' ? pending.resolve(record('a')) : pending.reject(new Error('old A error')));
    expect(await title('b')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByText('resource-a')).not.toBeInTheDocument();
  });

  it('does not let an old A GET finish the new A loading state after A → B → A', async () => {
    const oldA = deferred<ChangeRecord>();
    const newA = deferred<ChangeRecord>();
    vi.mocked(changesApi.fetchChange).mockReturnValueOnce(oldA.promise).mockResolvedValueOnce(record('b')).mockReturnValueOnce(newA.promise);
    mount();
    navigate('B');
    await title('b');
    navigate('A');
    await act(async () => oldA.resolve({ ...record('a'), resourceId: 'obsolete' }));
    expect(screen.getByText('Loading change…')).toBeInTheDocument();
    expect(screen.queryByTestId('change-detail')).not.toBeInTheDocument();
    await act(async () => newA.resolve(record('a')));
    await title('a');
  });

  it('rejects a GET response whose change ID is not the requested route and supports a fresh retry', async () => {
    vi.mocked(changesApi.fetchChange).mockResolvedValueOnce(record('b')).mockResolvedValueOnce(record('a'));
    mount();
    expect(await screen.findByRole('alert')).toHaveTextContent('does not match');
    expect(screen.queryByTestId('change-detail')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await title('a');
    expect(changesApi.fetchChange).toHaveBeenNthCalledWith(2, 'a');
  });

  it('invalidates the first effect request during StrictMode setup/cleanup replay', async () => {
    const oldRequest = deferred<ChangeRecord>();
    vi.mocked(changesApi.fetchChange).mockReturnValueOnce(oldRequest.promise).mockResolvedValueOnce(record('a'));
    render(<StrictMode><MemoryRouter initialEntries={['/changes/a']}>
      <Routes><Route path="/changes/:changeId" element={<ChangeDetailPage />} /></Routes>
    </MemoryRouter></StrictMode>);
    await title('a');
    expect(changesApi.fetchChange).toHaveBeenCalledTimes(2);
    await act(async () => oldRequest.resolve({ ...record('a'), resourceId: 'obsolete' }));
    expect(await title('a')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /obsolete/ })).not.toBeInTheDocument();
  });

  it('ignores an old A action after returning to a fresh A view', async () => {
    const pending = deferred<ChangeRecord>();
    vi.mocked(changesApi.approveChange).mockReturnValue(pending.promise);
    mount();
    await title('a');
    fireEvent.click(screen.getByRole('button', { name: 'Approve' }));
    navigate('B');
    await title('b');
    navigate('A');
    await title('a');
    await act(async () => pending.resolve({ ...record('a', 'APPROVED'), resultMessage: 'obsolete A' }));
    expect(screen.queryByText('obsolete A')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Approve' })).toBeEnabled();
  });

  const actions = [
    ['Approve', 'approveChange', 'WAITING_APPROVAL'],
    ['Reject', 'rejectChange', 'WAITING_APPROVAL'],
    ['Apply', 'applyChange', 'APPROVED'],
    ['Verify', 'verifyChange', 'APPLIED'],
  ] as const;

  it.each(actions)('ignores a late %s result from A while B has its own pending action', async (label, api, status) => {
    const oldAction = deferred<ChangeRecord>();
    const newAction = deferred<ChangeRecord>();
    vi.mocked(changesApi.fetchChange).mockImplementation(async (id) => record(id, status));
    vi.mocked(changesApi[api]).mockReturnValueOnce(oldAction.promise).mockReturnValueOnce(newAction.promise);
    mount();
    await title('a');
    fireEvent.click(screen.getByRole('button', { name: label }));
    navigate('B');
    await title('b');
    fireEvent.click(screen.getByRole('button', { name: label }));
    await act(async () => oldAction.resolve({ ...record('a', status), resultMessage: 'old A action' }));
    expect(await title('b')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Verify' })).toBeDisabled();
    expect(screen.queryByText('old A action')).not.toBeInTheDocument();
    await act(async () => newAction.resolve({ ...record('b', status), resultMessage: 'new B action' }));
    expect(screen.getByText('new B action')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Verify' })).toBeEnabled();
    expect(vi.mocked(changesApi[api]).mock.calls.map(([id]) => id)).toEqual(['a', 'b']);
  });

  it.each(actions)('ignores a late %s error/finally from A while B has a pending action', async (label, api, status) => {
    const oldAction = deferred<ChangeRecord>();
    const newAction = deferred<ChangeRecord>();
    vi.mocked(changesApi.fetchChange).mockImplementation(async (id) => record(id, status));
    vi.mocked(changesApi[api]).mockReturnValueOnce(oldAction.promise).mockReturnValueOnce(newAction.promise);
    mount();
    await title('a');
    fireEvent.click(screen.getByRole('button', { name: label }));
    navigate('B');
    await title('b');
    fireEvent.click(screen.getByRole('button', { name: label }));
    await act(async () => oldAction.reject(new Error('old A action failure')));
    expect(await title('b')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Verify' })).toBeDisabled();
    await act(async () => newAction.resolve(record('b', status)));
    expect(screen.getByRole('button', { name: 'Verify' })).toBeEnabled();
  });

  it.each(['changeId', 'targetId'] as const)('does not adopt an action response with an incompatible %s', async (field) => {
    vi.mocked(changesApi.approveChange).mockResolvedValue({ ...record('a'), [field]: 'foreign', resourceId: 'foreign-resource' });
    mount();
    await title('a');
    fireEvent.click(screen.getByRole('button', { name: 'Approve' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('does not match');
    expect(screen.queryByRole('heading', { name: /foreign-resource/ })).not.toBeInTheDocument();
  });

  it('prevents synchronous duplicate submissions before React commits the disabled state', async () => {
    const pending = deferred<ChangeRecord>();
    vi.mocked(changesApi.approveChange).mockReturnValue(pending.promise);
    mount();
    await title('a');
    const button = screen.getByRole('button', { name: 'Approve' });
    act(() => { button.click(); button.click(); });
    expect(changesApi.approveChange).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Verify' })).toBeDisabled();
    await act(async () => pending.resolve(record('a', 'APPROVED')));
    expect(screen.getByRole('button', { name: 'Apply' })).toBeEnabled();
  });

  it('reloads after an action failure without leaving old controls enabled during the reload', async () => {
    const pending = deferred<ChangeRecord>();
    vi.mocked(changesApi.fetchChange).mockResolvedValueOnce(record('a')).mockReturnValueOnce(pending.promise);
    vi.mocked(changesApi.approveChange).mockRejectedValue(new Error('Action rejected'));
    mount();
    await title('a');
    fireEvent.click(screen.getByRole('button', { name: 'Approve' }));
    await screen.findByRole('alert');
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    await act(async () => pending.resolve(record('a', 'APPROVED')));
    expect(screen.getByRole('button', { name: 'Apply' })).toBeEnabled();
    expect(changesApi.approveChange).toHaveBeenCalledTimes(1);
  });
});
