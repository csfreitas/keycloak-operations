import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, Outlet } from 'react-router-dom';
import { AssessmentPage } from '../pages/AssessmentPage';
import * as assessmentsApi from '../api/assessments';
import * as findingsApi from '../api/findings';
import {
  assessmentRunSummary,
  partialAssessmentSummary,
  findings,
} from './fixtures';

vi.mock('../api/assessments');
vi.mock('../api/findings');
vi.mock('../api/me', () => ({ fetchMe: vi.fn().mockResolvedValue({ authenticated: false, authMode: 'OPEN_LAB', subject: null, displayName: null }) }));

function renderWithOutlet(targetId = 'keycloak-dev-01') {
  const Parent = () => <Outlet context={{ targetId, overview: null }} />;
  return render(
    <MemoryRouter initialEntries={[`/targets/${targetId}/assessment`]}>
      <Routes>
        <Route path="/targets/:targetId" element={<Parent />}>
          <Route path="assessment" element={<AssessmentPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe('AssessmentPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(findingsApi.fetchFindings).mockResolvedValue({ items: findings, page: 0, size: 50, total: findings.length });
  });

  it('shows loading state', () => {
    vi.mocked(assessmentsApi.fetchAssessments).mockReturnValue(new Promise(() => {}));
    renderWithOutlet();
    expect(screen.getByTestId('loading-state')).toBeInTheDocument();
  });

  it('renders assessment card with score', async () => {
    vi.mocked(assessmentsApi.fetchAssessments).mockResolvedValue({
      items: [assessmentRunSummary],
      page: 0, size: 20, total: 1,
    });
    renderWithOutlet();
    await waitFor(() => {
      expect(screen.getByTestId('assessment-card')).toBeInTheDocument();
    });
    // Score 87 appears in the header label and in the ScoreBar — use getAllByText
    expect(screen.getAllByText('87').length).toBeGreaterThanOrEqual(1);
  });

  it('shows findings in latest assessment', async () => {
    vi.mocked(assessmentsApi.fetchAssessments).mockResolvedValue({
      items: [assessmentRunSummary],
      page: 0, size: 20, total: 1,
    });
    renderWithOutlet();
    await waitFor(() => {
      expect(screen.getByTestId('finding-list')).toBeInTheDocument();
    });
  });

  it('renders partial assessment with PARTIAL badge', async () => {
    vi.mocked(assessmentsApi.fetchAssessments).mockResolvedValue({
      items: [partialAssessmentSummary],
      page: 0, size: 20, total: 1,
    });
    renderWithOutlet();
    await waitFor(() => {
      const partial = screen.getAllByText('Partial');
      expect(partial.length).toBeGreaterThan(0);
    });
  });

  it('shows empty state when no assessments', async () => {
    vi.mocked(assessmentsApi.fetchAssessments).mockResolvedValue({
      items: [],
      page: 0, size: 20, total: 0,
    });
    renderWithOutlet();
    await waitFor(() => {
      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    });
  });

  it('shows error state when API fails', async () => {
    vi.mocked(assessmentsApi.fetchAssessments).mockRejectedValue(new Error('API down'));
    renderWithOutlet();
    await waitFor(() => {
      expect(screen.getByTestId('error-state')).toBeInTheDocument();
    });
  });
});
