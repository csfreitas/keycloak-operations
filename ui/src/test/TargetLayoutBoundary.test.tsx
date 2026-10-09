import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { TargetLayout } from '../layouts/TargetLayout';
import { useTargetOverview } from '../hooks/useTargetOverview';
import { targetOverview } from './fixtures';
vi.mock('../hooks/useTargetOverview');
beforeEach(() => vi.clearAllMocks());
function show() {
  return render(<MemoryRouter initialEntries={['/targets/lab-keycloak-b/report']}>
    <Routes><Route path="/targets/:targetId" element={<TargetLayout />}>
      <Route path="report" element={<p>Protected report content</p>} />
    </Route></Routes>
  </MemoryRouter>);
}
it('does not mount a target section while access is being resolved', () => {
  vi.mocked(useTargetOverview).mockReturnValue({ overview: null, loading: true, error: null, refresh: vi.fn() });
  show(); expect(screen.queryByText('Protected report content')).not.toBeInTheDocument();
});
it('does not mount a target section after access is denied', () => {
  vi.mocked(useTargetOverview).mockReturnValue({ overview: null, loading: false, error: 'Access denied', refresh: vi.fn() });
  show(); expect(screen.getByText('Failed to load target')).toBeInTheDocument();
  expect(screen.queryByText('Protected report content')).not.toBeInTheDocument();
});
it('mounts a target section after its overview is authorized', () => {
  vi.mocked(useTargetOverview).mockReturnValue({ overview: targetOverview, loading: false, error: null, refresh: vi.fn() });
  show(); expect(screen.getByText('Protected report content')).toBeInTheDocument();
});
