import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { FleetPage } from '../pages/FleetPage';
import { PerformancePage } from '../pages/PerformancePage';
import { MemoryRouter as MR, Route, Routes, Outlet } from 'react-router-dom';
import * as fleetApi from '../api/fleet';
import * as metricsApi from '../api/metrics';
import { metricsStatusConfigured, performanceSummaryUnavailable } from './fixtures';

vi.mock('../api/fleet');
vi.mock('../api/metrics');
vi.mock('../api/me', () => ({ fetchMe: vi.fn().mockResolvedValue({ authenticated: false, authMode: 'OPEN_LAB', subject: null, displayName: null }) }));

function renderPerformancePage(targetId = 'keycloak-prd-01') {
  const Parent = () => <Outlet context={{ targetId, overview: null }} />;
  return render(
    <MR initialEntries={[`/targets/${targetId}/performance`]}>
      <Routes>
        <Route path="/targets/:targetId" element={<Parent />}>
          <Route path="performance" element={<PerformancePage />} />
        </Route>
      </Routes>
    </MR>,
  );
}

describe('API error states', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fleet page shows error state on network failure', async () => {
    vi.mocked(fleetApi.fetchFleet).mockRejectedValue(new Error('Network error'));
    render(<MemoryRouter><FleetPage /></MemoryRouter>);
    await waitFor(() => {
      expect(screen.getByTestId('error-state')).toBeInTheDocument();
      expect(screen.getByText(/Network error/)).toBeInTheDocument();
    });
  });

  it('performance page shows error when metrics status fetch fails', async () => {
    vi.mocked(metricsApi.fetchMetricsStatus).mockRejectedValue(new Error('Metrics unavailable'));
    vi.mocked(metricsApi.fetchMetricsSummary).mockRejectedValue(new Error('Metrics unavailable'));
    renderPerformancePage();
    await waitFor(() => {
      expect(screen.getByTestId('error-state')).toBeInTheDocument();
    });
  });

  it('performance page shows NOT_AVAILABLE metric without rendering 0', async () => {
    vi.mocked(metricsApi.fetchMetricsStatus).mockResolvedValue(metricsStatusConfigured);
    vi.mocked(metricsApi.fetchMetricsSummary).mockResolvedValue(performanceSummaryUnavailable);
    renderPerformancePage('keycloak-prd-01');
    await waitFor(() => {
      const metricValues = screen.getAllByTestId('metric-value');
      metricValues.forEach((mv) => {
        expect(mv).not.toHaveTextContent(/^0$/);
      });
    });
  });
});
