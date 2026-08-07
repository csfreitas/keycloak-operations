import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, Outlet } from 'react-router-dom';
import { HistoryPage } from '../pages/HistoryPage';
import * as assessmentsApi from '../api/assessments';
import * as healthApi from '../api/health';
import * as snapshotsApi from '../api/snapshots';
import * as auditApi from '../api/audit';

vi.mock('../api/assessments');
vi.mock('../api/health');
vi.mock('../api/snapshots');
vi.mock('../api/audit');
vi.mock('../api/me', () => ({ fetchMe: vi.fn().mockResolvedValue({ authenticated: false, authMode: 'OPEN_LAB', subject: null, displayName: null }) }));

const emptyPage = { items: [], page: 0, size: 30, total: 0 };

function renderHistoryPage(targetId = 'keycloak-dev-01') {
  const Parent = () => <Outlet context={{ targetId, overview: null }} />;
  return render(
    <MemoryRouter initialEntries={[`/targets/${targetId}/history`]}>
      <Routes>
        <Route path="/targets/:targetId" element={<Parent />}>
          <Route path="history" element={<HistoryPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('HistoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(assessmentsApi.fetchAssessments).mockResolvedValue(emptyPage);
    vi.mocked(healthApi.fetchHealthChecks).mockResolvedValue(emptyPage);
    vi.mocked(snapshotsApi.fetchSnapshots).mockResolvedValue(emptyPage);
    vi.mocked(auditApi.fetchAudit).mockResolvedValue(emptyPage);
  });

  it('renders history page with tabs', async () => {
    renderHistoryPage();
    await waitFor(() => {
      expect(screen.getByText(/Assessments/)).toBeInTheDocument();
      expect(screen.getByText(/Health/)).toBeInTheDocument();
      expect(screen.getByText(/Snapshots/)).toBeInTheDocument();
      expect(screen.getByText(/Audit/)).toBeInTheDocument();
    });
  });

  it('shows empty assessments state', async () => {
    renderHistoryPage();
    await waitFor(() => {
      expect(screen.getByText('No assessments')).toBeInTheDocument();
    });
  });

  it('shows loading initially', () => {
    vi.mocked(assessmentsApi.fetchAssessments).mockReturnValue(new Promise(() => {}));
    vi.mocked(healthApi.fetchHealthChecks).mockReturnValue(new Promise(() => {}));
    vi.mocked(snapshotsApi.fetchSnapshots).mockReturnValue(new Promise(() => {}));
    vi.mocked(auditApi.fetchAudit).mockReturnValue(new Promise(() => {}));
    renderHistoryPage();
    expect(screen.getByTestId('loading-state')).toBeInTheDocument();
  });
});
