import { useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import { generateOperationsReport } from '../api/reports';
import type { OperationsReport, TargetOverview } from '../api/types';
import { ErrorState } from '../components/ErrorState';
import { StatusBadge } from '../components/StatusBadge';

interface OutletCtx {
  targetId: string;
  overview: TargetOverview | null;
}

export function OperationsReportPage() {
  const { targetId } = useOutletContext<OutletCtx>();
  const [profile, setProfile] = useState('');
  const [metricsWindow, setMetricsWindow] = useState('15m');
  const [report, setReport] = useState<OperationsReport | null>(null);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const generate = () => {
    setGenerating(true);
    setError(null);
    generateOperationsReport(targetId, profile, metricsWindow)
      .then((next) => {
        setReport(next);
        setGenerating(false);
      })
      .catch((err: unknown) => {
        setError((err as Error).message);
        setGenerating(false);
      });
  };

  const downloadMarkdown = () => {
    if (!report?.markdown) return;
    const url = URL.createObjectURL(new Blob([report.markdown], { type: 'text/markdown;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `keycloak-operations-${report.targetId}-${report.reportId}.md`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-header__title">Operations Report</h2>
          <p className="page-header__subtitle">
            Point-in-time platform, health, assessment, findings, and performance evidence
          </p>
        </div>
        <div className="page-header__actions">
          {report && (
            <button className="btn btn--secondary btn--sm" onClick={downloadMarkdown}>
              Download Markdown
            </button>
          )}
          <button className="btn btn--primary btn--sm" onClick={generate} disabled={generating}>
            {generating ? 'Generating…' : 'Generate Report'}
          </button>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 'var(--space-6)' }}>
        <div className="grid grid-2" style={{ gap: 'var(--space-4)' }}>
          <label className="text-sm">
            <span className="text-secondary">Assessment profile (optional)</span>
            <input
              aria-label="Assessment profile"
              value={profile}
              onChange={(event) => setProfile(event.target.value)}
              placeholder="Configured default"
              style={{ display: 'block', width: '100%', marginTop: 'var(--space-2)' }}
            />
          </label>
          <label className="text-sm">
            <span className="text-secondary">Metrics window</span>
            <select
              aria-label="Metrics window"
              value={metricsWindow}
              onChange={(event) => setMetricsWindow(event.target.value)}
              style={{ display: 'block', width: '100%', marginTop: 'var(--space-2)' }}
            >
              <option value="15m">15 minutes</option>
              <option value="1h">1 hour</option>
              <option value="6h">6 hours</option>
              <option value="24h">24 hours</option>
            </select>
          </label>
        </div>
      </div>

      {error && <ErrorState error={error} onRetry={generate} title="Failed to generate report" />}

      {!error && !report && (
        <div className="card">
          <p className="text-sm text-secondary">
            Generate a fresh report. Collection may be partial when infrastructure or metrics providers are unavailable;
            missing evidence is never treated as healthy.
          </p>
        </div>
      )}

      {report && (
        <>
          <div className="grid grid-2" style={{ gap: 'var(--space-4)', marginBottom: 'var(--space-6)' }}>
            <div className="card">
              <div className="card__header"><h3 className="card__title">Report completeness</h3></div>
              <StatusBadge status={report.status} />
              <p className="text-xs text-muted" style={{ marginTop: 'var(--space-3)' }}>
                Generated {new Date(report.generatedAt).toLocaleString()}
              </p>
            </div>
            <div className="card">
              <div className="card__header"><h3 className="card__title">Target state</h3></div>
              <p className="text-sm">Health: <strong>{report.healthCheck?.overallStatus ?? 'Unavailable'}</strong></p>
              <p className="text-sm">Assessment score: <strong>{report.assessment?.overallScore ?? 'Unavailable'}</strong></p>
              <p className="text-sm">Infrastructure: <strong>{report.configuredInfrastructureType}</strong></p>
            </div>
          </div>

          <div className="card" style={{ marginBottom: 'var(--space-6)' }}>
            <div className="card__header"><h3 className="card__title">Collection sections</h3></div>
            <div className="table-wrapper">
              <table>
                <thead><tr><th>Section</th><th>Status</th><th>Message</th></tr></thead>
                <tbody>
                  {report.sections.map((section) => (
                    <tr key={section.name}>
                      <td>{section.name}</td>
                      <td><StatusBadge status={section.status} /></td>
                      <td className="text-secondary">{section.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="card">
            <div className="card__header"><h3 className="card__title">Deterministic report</h3></div>
            <pre style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', fontSize: 'var(--text-sm)' }}>
              {report.markdown}
            </pre>
          </div>
        </>
      )}
    </div>
  );
}
