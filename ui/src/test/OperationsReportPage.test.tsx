import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Outlet, Route, Routes, useParams } from 'react-router-dom';
import type { OperationsReport } from '../api/types';
import { OperationsReportPage } from '../pages/OperationsReportPage';
import * as reportsApi from '../api/reports';

vi.mock('../api/reports');

function TargetContextRoute() {
  const { targetId = '' } = useParams();
  return <><Link to="/targets/target-b/report">Target B</Link><Outlet context={{ targetId, overview: null }} /></>;
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

  it('discards a late report when the selected target changes', async () => {
    let resolve!: (value: OperationsReport) => void;
    vi.mocked(reportsApi.generateOperationsReport).mockReturnValue(new Promise<OperationsReport>((done) => { resolve = done; }));
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={['/targets/rhbk-prd/report']}><Routes>
      <Route path="/targets/:targetId" element={<TargetContextRoute />}>
        <Route path="report" element={<OperationsReportPage />} />
      </Route>
    </Routes></MemoryRouter>);
    await user.click(screen.getByRole('button', { name: 'Generate Report' }));
    await user.click(screen.getByRole('link', { name: 'Target B' }));
    await act(async () => resolve(report as unknown as OperationsReport));
    expect(screen.queryByText('# Keycloak / RHBK Operations Report')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Download Markdown' })).not.toBeInTheDocument();
  });

  it('rejects a report returned for another target', async () => {
    vi.mocked(reportsApi.generateOperationsReport).mockResolvedValue({ ...report, targetId: 'wrong-target' } as unknown as OperationsReport);
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={['/targets/rhbk-prd/report']}><Routes>
      <Route path="/targets/:targetId" element={<TargetContextRoute />}><Route path="report" element={<OperationsReportPage />} /></Route>
    </Routes></MemoryRouter>);
    await user.click(screen.getByRole('button', { name: 'Generate Report' }));
    expect(await screen.findByText('Report target does not match the selected target')).toBeInTheDocument();
    expect(screen.queryByText('# Keycloak / RHBK Operations Report')).not.toBeInTheDocument();
  });
});
