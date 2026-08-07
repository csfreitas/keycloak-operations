import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { FleetPage } from '../pages/FleetPage';
import * as fleetApi from '../api/fleet';
import * as targetsApi from '../api/targets';
import { fleetItems, targetOverview } from './fixtures';

vi.mock('../api/fleet');
vi.mock('../api/targets');
vi.mock('../api/me', () => ({ fetchMe: vi.fn().mockResolvedValue({ authenticated: false, authMode: 'OPEN_LAB', subject: null, displayName: null }) }));
vi.mock('../api/health', () => ({ fetchHealthChecks: vi.fn().mockResolvedValue({ items: [], page: 0, size: 20, total: 0 }) }));
vi.mock('../api/assessments', () => ({ fetchAssessments: vi.fn().mockResolvedValue({ items: [], page: 0, size: 20, total: 0 }) }));

function TargetPageStub() {
  return <div data-testid="target-page">Target Page</div>;
}

describe('Target navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(fleetApi.fetchFleet).mockResolvedValue(fleetItems);
    vi.mocked(targetsApi.fetchTargetOverview).mockResolvedValue(targetOverview);
  });

  it('navigates to target page on row click', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/targets']}>
        <Routes>
          <Route path="/targets" element={<FleetPage />} />
          <Route path="/targets/:targetId" element={<TargetPageStub />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getAllByTestId('fleet-row').length).toBeGreaterThan(0);
    });

    await user.click(screen.getAllByTestId('fleet-row')[0]);
    await waitFor(() => {
      expect(screen.getByTestId('target-page')).toBeInTheDocument();
    });
  });

  it('renders fleet page with correct target count', async () => {
    render(
      <MemoryRouter>
        <FleetPage />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(screen.getAllByTestId('fleet-row').length).toBe(fleetItems.length);
    });
  });
});
