import { describe, expect, it, vi, beforeEach } from 'vitest';
import { useLayoutEffect } from 'react';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { ChangesPage } from '../pages/ChangesPage';
import * as changesApi from '../api/changes';
import type { ChangeRecord, ChangeStatus, Page } from '../api/types';

vi.mock('../api/changes');

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

function page(targetId = 'lab-keycloak-a', resourceId = 'account', status: ChangeStatus = 'WAITING_APPROVAL'): Page<ChangeRecord> {
  return {
    items: [{
      changeId: `change-${resourceId}`, targetId, environment: 'DEV', resourceType: 'CLIENT',
      resourceId, realm: 'master', operation: 'UPDATE', status, risk: 'LOW',
      policyDecision: 'APPROVAL_REQUIRED', policyReason: 'test', requiresApproval: true,
      planFingerprint: 'abc', baselineFingerprint: 'def', approvalFingerprint: null,
      diff: [], verificationStatus: null, verificationMessage: null, resultMessage: null,
      approvedBy: null, approvedAt: null, appliedAt: null,
      createdAt: '2026-08-07T12:00:00Z', updatedAt: '2026-08-07T12:00:00Z',
    }], page: 0, size: 50, total: 1,
  };
}

function Harness({ observe }: { observe?: (search: string, tableText: string | null) => void }) {
  const location = useLocation();
  // Observe the committed DOM before the page's passive loading effect can hide old rows.
  useLayoutEffect(() => {
    observe?.(location.search, screen.queryByTestId('changes-table')?.textContent ?? null);
  }, [location, observe]);
  return <>
    <Link to="/changes?targetId=lab-keycloak-a">Target A</Link>
    <Link to="/changes?targetId=lab-keycloak-b">Target B</Link>
    <Link to="/changes">No target</Link>
    <Routes><Route path="/changes" element={<ChangesPage />} /></Routes>
  </>;
}

function showList(observe?: (search: string, tableText: string | null) => void) {
  return render(<MemoryRouter initialEntries={['/changes?targetId=lab-keycloak-a']}>
    <Harness observe={observe} />
  </MemoryRouter>);
}

describe('ChangesPage', () => {
  it('exposes filter selection and uses the existing readable unselected style', async () => {
    render(<MemoryRouter initialEntries={['/changes?targetId=lab-keycloak-a&status=APPROVED']}><ChangesPage /></MemoryRouter>);
    await screen.findByTestId('changes-table');
    expect(screen.getByRole('button', { name: 'APPROVED' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'All' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getByRole('button', { name: 'All' })).toHaveClass('btn--secondary');
  });
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(changesApi.fetchChanges).mockResolvedValue({
      items: [
        {
          changeId: 'chg-1',
          targetId: 'lab-keycloak-a',
          environment: 'DEV',
          resourceType: 'CLIENT',
          resourceId: 'account',
          realm: 'master',
          operation: 'UPDATE',
          status: 'WAITING_APPROVAL',
          risk: 'LOW',
          policyDecision: 'APPROVAL_REQUIRED',
          policyReason: 'test',
          requiresApproval: true,
          planFingerprint: 'abc',
          baselineFingerprint: 'def',
          approvalFingerprint: null,
          diff: [{ property: 'name', kind: 'CHANGED', before: 'a', after: 'b' }],
          verificationStatus: null,
          verificationMessage: null,
          resultMessage: null,
          approvedBy: null,
          approvedAt: null,
          appliedAt: null,
          createdAt: '2026-08-07T12:00:00Z',
          updatedAt: '2026-08-07T12:00:00Z',
        },
      ],
      page: 0,
      size: 50,
      total: 1,
    });
  });

  it('renders pending changes table', async () => {
    render(
      <MemoryRouter initialEntries={['/changes?targetId=lab-keycloak-a']}>
        <Routes>
          <Route path="/changes" element={<ChangesPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('changes-table')).toBeInTheDocument();
    });
    expect(screen.getByText('lab-keycloak-a')).toBeInTheDocument();
    expect(screen.getByText(/CLIENT/)).toBeInTheDocument();
  });

  it('never requests unscoped changes', async () => {
    render(<MemoryRouter initialEntries={['/changes']}><ChangesPage /></MemoryRouter>);
    expect(await screen.findByText('Select a target')).toBeInTheDocument();
    expect(changesApi.fetchChanges).not.toHaveBeenCalled();
  });

  it.each(['target', 'status'] as const)('hides old rows in the first committed render after changing %s', async (scope) => {
    const pending = deferred<Page<ChangeRecord>>();
    vi.mocked(changesApi.fetchChanges).mockResolvedValueOnce(page()).mockReturnValueOnce(pending.promise);
    const observe = vi.fn();
    const user = userEvent.setup();
    showList(observe);
    await screen.findByTestId('changes-table');
    await user.click(scope === 'target'
      ? screen.getByRole('link', { name: 'Target B' })
      : screen.getByRole('button', { name: 'APPROVED' }));
    const expectedSearch = scope === 'target' ? '?targetId=lab-keycloak-b' : '?targetId=lab-keycloak-a&status=APPROVED';
    expect(observe).toHaveBeenCalledWith(expectedSearch, null);
    expect(screen.queryByTestId('changes-table')).not.toBeInTheDocument();
    expect(screen.getByText('Loading changes…')).toBeInTheDocument();
  });

  it.each(['success', 'failure'] as const)('ignores old target %s after the new target loaded', async (outcome) => {
    const old = deferred<Page<ChangeRecord>>();
    vi.mocked(changesApi.fetchChanges).mockReturnValueOnce(old.promise).mockResolvedValueOnce(page('lab-keycloak-b', 'new-resource'));
    const user = userEvent.setup();
    showList();
    await user.click(screen.getByRole('link', { name: 'Target B' }));
    await screen.findByText('CLIENT / new-resource');
    await act(async () => {
      if (outcome === 'success') old.resolve(page('lab-keycloak-a', 'old-resource'));
      else old.reject(new Error('Old target failure'));
    });
    expect(screen.getByText('CLIENT / new-resource')).toBeInTheDocument();
    expect(screen.queryByText('CLIENT / old-resource')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it.each(['success', 'failure'] as const)('does not let old target %s finish the new target loading state', async (outcome) => {
    const old = deferred<Page<ChangeRecord>>();
    const current = deferred<Page<ChangeRecord>>();
    vi.mocked(changesApi.fetchChanges).mockReturnValueOnce(old.promise).mockReturnValueOnce(current.promise);
    const user = userEvent.setup();
    showList();
    await user.click(screen.getByRole('link', { name: 'Target B' }));
    await act(async () => {
      if (outcome === 'success') old.resolve(page());
      else old.reject(new Error('Old target failure'));
    });
    expect(screen.getByText('Loading changes…')).toBeInTheDocument();
    expect(screen.queryByTestId('changes-table')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await act(async () => current.resolve(page('lab-keycloak-b', 'current-resource')));
    expect(screen.getByText('CLIENT / current-resource')).toBeInTheDocument();
  });

  it.each(['success', 'failure'] as const)('ignores pending %s after removing the selected target', async (outcome) => {
    const old = deferred<Page<ChangeRecord>>();
    vi.mocked(changesApi.fetchChanges).mockReturnValueOnce(old.promise);
    const user = userEvent.setup();
    showList();
    await user.click(screen.getByRole('link', { name: 'No target' }));
    await act(async () => {
      if (outcome === 'success') old.resolve(page());
      else old.reject(new Error('Old target failure'));
    });
    expect(screen.getByText('Select a target')).toBeInTheDocument();
    expect(screen.queryByTestId('changes-table')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByText('Loading changes…')).not.toBeInTheDocument();
    expect(changesApi.fetchChanges).toHaveBeenCalledTimes(1);
  });

  it.each(['success', 'failure'] as const)('ignores old filter %s after the new filter loaded', async (outcome) => {
    const old = deferred<Page<ChangeRecord>>();
    vi.mocked(changesApi.fetchChanges).mockReturnValueOnce(old.promise)
      .mockResolvedValueOnce(page('lab-keycloak-a', 'approved-resource', 'APPROVED'));
    const user = userEvent.setup();
    showList();
    await user.click(screen.getByRole('button', { name: 'APPROVED' }));
    await screen.findByText('CLIENT / approved-resource');
    await act(async () => {
      if (outcome === 'success') old.resolve(page('lab-keycloak-a', 'unfiltered-resource'));
      else old.reject(new Error('Old filter failure'));
    });
    expect(screen.getByText('CLIENT / approved-resource')).toBeInTheDocument();
    expect(screen.queryByText('CLIENT / unfiltered-resource')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(changesApi.fetchChanges).toHaveBeenLastCalledWith({ targetId: 'lab-keycloak-a', status: 'APPROVED', page: 0, size: 50 });
  });

  it('does not accept the first A response after A to B to A navigation', async () => {
    const firstA = deferred<Page<ChangeRecord>>();
    const middleB = deferred<Page<ChangeRecord>>();
    const lastA = deferred<Page<ChangeRecord>>();
    vi.mocked(changesApi.fetchChanges).mockReturnValueOnce(firstA.promise)
      .mockReturnValueOnce(middleB.promise).mockReturnValueOnce(lastA.promise);
    const user = userEvent.setup();
    showList();
    await user.click(screen.getByRole('link', { name: 'Target B' }));
    await user.click(screen.getByRole('link', { name: 'Target A' }));
    await act(async () => lastA.resolve(page('lab-keycloak-a', 'latest-a')));
    await act(async () => firstA.resolve(page('lab-keycloak-a', 'first-a')));
    await act(async () => middleB.reject(new Error('Middle B failure')));
    expect(screen.getByText('CLIENT / latest-a')).toBeInTheDocument();
    expect(screen.queryByText('CLIENT / first-a')).not.toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('rejects a mixed-target response without rendering even its valid rows, then retries', async () => {
    const mixed = page('lab-keycloak-a', 'valid-row');
    mixed.items.push(...page('lab-keycloak-b', 'foreign-row').items);
    vi.mocked(changesApi.fetchChanges).mockResolvedValueOnce(mixed).mockResolvedValueOnce(page('lab-keycloak-a', 'retried-row'));
    const user = userEvent.setup();
    showList();
    expect(await screen.findByRole('alert')).toHaveTextContent('Change target does not match the selected target');
    expect(screen.queryByTestId('changes-table')).not.toBeInTheDocument();
    expect(screen.queryByText('CLIENT / valid-row')).not.toBeInTheDocument();
    expect(screen.queryByText('CLIENT / foreign-row')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('CLIENT / retried-row')).toBeInTheDocument();
  });

  it('preserves an empty successful result for the current target', async () => {
    vi.mocked(changesApi.fetchChanges).mockResolvedValue({ items: [], total: 0, page: 0, size: 50 });
    showList();
    expect(await screen.findByText('No changes')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByTestId('changes-table')).not.toBeInTheDocument();
  });
});
