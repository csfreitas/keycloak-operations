import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ChangesPage } from '../pages/ChangesPage';
import * as changesApi from '../api/changes';

vi.mock('../api/changes');

describe('ChangesPage', () => {
  beforeEach(() => {
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
      <MemoryRouter initialEntries={['/changes']}>
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
});
