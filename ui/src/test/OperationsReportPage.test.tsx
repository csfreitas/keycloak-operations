import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Outlet, Route, Routes, useParams } from 'react-router-dom';
import { OperationsReportPage } from '../pages/OperationsReportPage';
import * as reportsApi from '../api/reports';

vi.mock('../api/reports');

function TargetContextRoute() {
  const { targetId = '' } = useParams();
  return <Outlet context={{ targetId, overview: null }} />;
}

const report = {
  schemaVersion: '1.0',
  reportId: 'report-1',
  targetId: 'rhbk-prd',
  targetDisplayName: 'RHBK Production',
  productType: 'RHBK',
  environment: 'PRD',
  configuredInfrastructureType: 'OPENSHIFT',
  generatedAt: '2026-09-04T10:00:00Z',
  status: 'PARTIAL' as const,
  sections: [
    { name: 'health', status: 'COMPLETE' as const, message: 'Health checks completed' },
    { name: 'performance', status: 'SKIPPED' as const, message: 'Metrics not configured' },
  ],
  environmentSnapshot: null,
  healthCheck: { overallStatus: 'WARNING' as const },
  assessment: { overallScore: 72 },
  performance: null,
  markdown: '# Keycloak / RHBK Operations Report',
};

describe('OperationsReportPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('generates and renders explicit report completeness separately from health', async () => {
    vi.mocked(reportsApi.generateOperationsReport).mockResolvedValue(report as never);
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/targets/rhbk-prd/report']}>
        <Routes>
          <Route path="/targets/:targetId" element={<TargetContextRoute />}>
            <Route path="report" element={<OperationsReportPage />} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    await user.click(screen.getByRole('button', { name: 'Generate Report' }));

    await waitFor(() => expect(screen.getByText('# Keycloak / RHBK Operations Report')).toBeInTheDocument());
    expect(screen.getByText('WARNING')).toBeInTheDocument();
    expect(screen.getByLabelText('Status: Partial')).toBeInTheDocument();
    expect(reportsApi.generateOperationsReport).toHaveBeenCalledWith('rhbk-prd', '', '15m');
  });
});
