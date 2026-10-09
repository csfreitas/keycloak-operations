import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import { MemoryRouter, Route, Routes, Outlet } from 'react-router-dom';
import { TargetOverviewPage } from '../pages/TargetOverviewPage';
import * as targetsApi from '../api/targets';
import { targetOverview, targetOverviewPrd } from './fixtures';

vi.mock('../api/targets');
vi.mock('../api/me', () => ({ fetchMe: vi.fn().mockResolvedValue({ authenticated: false, authMode: 'OPEN_LAB', subject: null, displayName: null }) }));

function renderOverviewWithTarget(targetId: string, overview: typeof targetOverview) {
  const Parent = () => <Outlet context={{ targetId, overview }} />;
  return render(
    <MemoryRouter initialEntries={[`/targets/${targetId}`]}>
      <Routes>
        <Route path="/targets/:targetId" element={<Parent />}>
          <Route index element={<TargetOverviewPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('Target isolation', () => {
  it('shows unavailable infrastructure counts explicitly', () => {
    renderOverviewWithTarget('keycloak-dev-01', { ...targetOverview,
      desiredReplicas: null, readyReplicas: null, podCount: null, zoneCount: null });
    expect(screen.getAllByText('Unknown / not collected')).toHaveLength(4);
  });

  it('does not display legacy negative or invalid count sentinels', () => {
    renderOverviewWithTarget('keycloak-dev-01', { ...targetOverview,
      desiredReplicas: -1, readyReplicas: -1, podCount: NaN, zoneCount: 1.5 });
    expect(screen.getAllByText('Unknown / not collected')).toHaveLength(4);
    expect(screen.queryByText('-1')).not.toBeInTheDocument();
  });

  it('preserves valid observed zero counts', () => {
    renderOverviewWithTarget('keycloak-dev-01', { ...targetOverview,
      desiredReplicas: 0, readyReplicas: 0, podCount: 0, zoneCount: 0 });
    expect(screen.queryByText('Unknown / not collected')).not.toBeInTheDocument();
    for (const label of ['Desired replicas', 'Ready replicas', 'Pod count', 'Zone count']) {
      expect(screen.getByText(label).parentElement).toHaveTextContent('0');
    }
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders correct target name for first target', () => {
    vi.mocked(targetsApi.fetchTargetOverview).mockResolvedValue(targetOverview);
    renderOverviewWithTarget('keycloak-dev-01', targetOverview);
    expect(screen.getByText('Keycloak Dev 01')).toBeInTheDocument();
  });

  it('renders correct target name for PRD target', () => {
    vi.mocked(targetsApi.fetchTargetOverview).mockResolvedValue(targetOverviewPrd);
    renderOverviewWithTarget('keycloak-prd-01', targetOverviewPrd);
    expect(screen.getByText('Keycloak PRD 01')).toBeInTheDocument();
  });

  it('does not show Target A data when viewing Target B', async () => {
    vi.mocked(targetsApi.fetchTargetOverview).mockResolvedValue(targetOverview);
    const { unmount } = renderOverviewWithTarget('keycloak-dev-01', targetOverview);
    expect(screen.getByText('Keycloak Dev 01')).toBeInTheDocument();
    unmount();

    // Now render a different target — should not contain Target A's name
    vi.mocked(targetsApi.fetchTargetOverview).mockResolvedValue(targetOverviewPrd);
    renderOverviewWithTarget('keycloak-prd-01', targetOverviewPrd);
    await waitFor(() => {
      expect(screen.queryByText('Keycloak Dev 01')).not.toBeInTheDocument();
      expect(screen.getByText('Keycloak PRD 01')).toBeInTheDocument();
    });
  });

  it('useTargetOverview fetches new data when targetId changes', async () => {
    vi.mocked(targetsApi.fetchTargetOverview).mockResolvedValue(targetOverview);

    // Simulate re-render with new targetId (isolation via key in router)
    let rerender: ReturnType<typeof render>['rerender'];
    const ui = (tid: string, ov: typeof targetOverview) => {
      const Parent = () => <Outlet context={{ targetId: tid, overview: ov }} />;
      return (
        <MemoryRouter initialEntries={[`/targets/${tid}`]}>
          <Routes>
            <Route path="/targets/:targetId" element={<Parent />}>
              <Route index element={<TargetOverviewPage />} />
            </Route>
          </Routes>
        </MemoryRouter>
      );
    };

    ({ rerender } = render(ui('keycloak-dev-01', targetOverview)));
    expect(screen.getByText('Keycloak Dev 01')).toBeInTheDocument();

    vi.mocked(targetsApi.fetchTargetOverview).mockResolvedValue(targetOverviewPrd);
    await act(async () => {
      rerender(ui('keycloak-prd-01', targetOverviewPrd));
    });
    await waitFor(() => {
      expect(screen.getByText('Keycloak PRD 01')).toBeInTheDocument();
      expect(screen.queryByText('Keycloak Dev 01')).not.toBeInTheDocument();
    });
  });
});
