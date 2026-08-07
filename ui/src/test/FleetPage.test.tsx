import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { FleetPage } from '../pages/FleetPage';
import * as fleetApi from '../api/fleet';
import { fleetItems, fleetItemHealthy, fleetItemCritical } from './fixtures';

vi.mock('../api/fleet');
vi.mock('../api/me', () => ({ fetchMe: vi.fn().mockResolvedValue({ authenticated: false, authMode: 'OPEN_LAB', subject: null, displayName: null }) }));

function renderFleetPage() {
  return render(
    <MemoryRouter>
      <FleetPage />
    </MemoryRouter>,
  );
}

describe('FleetPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows loading state initially', () => {
    vi.mocked(fleetApi.fetchFleet).mockReturnValue(new Promise(() => {}));
    renderFleetPage();
    expect(screen.getByTestId('loading-state')).toBeInTheDocument();
  });

  it('renders fleet rows after loading', async () => {
    vi.mocked(fleetApi.fetchFleet).mockResolvedValue(fleetItems);
    renderFleetPage();
    await waitFor(() => {
      expect(screen.getAllByTestId('fleet-row').length).toBe(fleetItems.length);
    });
  });

  it('displays target display names', async () => {
    vi.mocked(fleetApi.fetchFleet).mockResolvedValue(fleetItems);
    renderFleetPage();
    await waitFor(() => {
      expect(screen.getByText(fleetItemHealthy.displayName)).toBeInTheDocument();
      expect(screen.getByText(fleetItemCritical.displayName)).toBeInTheDocument();
    });
  });

  it('shows health status badges', async () => {
    vi.mocked(fleetApi.fetchFleet).mockResolvedValue([fleetItemHealthy]);
    renderFleetPage();
    await waitFor(() => {
      const badges = screen.getAllByTestId('status-badge');
      expect(badges.length).toBeGreaterThan(0);
    });
  });

  it('groups targets by environment', async () => {
    vi.mocked(fleetApi.fetchFleet).mockResolvedValue(fleetItems);
    renderFleetPage();
    await waitFor(() => {
      expect(screen.getAllByTestId('env-badge').length).toBeGreaterThan(0);
    });
  });

  it('shows error state when API fails', async () => {
    vi.mocked(fleetApi.fetchFleet).mockRejectedValue(new Error('Network error'));
    renderFleetPage();
    await waitFor(() => {
      expect(screen.getByTestId('error-state')).toBeInTheDocument();
    });
  });

  it('shows empty state when no targets', async () => {
    vi.mocked(fleetApi.fetchFleet).mockResolvedValue([]);
    renderFleetPage();
    await waitFor(() => {
      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    });
  });

  it('shows critical finding counts for critical targets', async () => {
    vi.mocked(fleetApi.fetchFleet).mockResolvedValue([fleetItemCritical]);
    renderFleetPage();
    await waitFor(() => {
      expect(screen.getByText(/CRIT/)).toBeInTheDocument();
    });
  });
});
