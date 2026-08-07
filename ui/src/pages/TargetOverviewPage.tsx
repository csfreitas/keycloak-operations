import { Link, useOutletContext } from 'react-router-dom';
import type { TargetOverview } from '../api/types';
import { StatusBadge } from '../components/StatusBadge';
import { ScoreBar } from '../components/ScoreBar';
import { LoadingState } from '../components/LoadingState';

interface OutletCtx {
  targetId: string;
  overview: TargetOverview | null;
}

function InfoRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: 'var(--space-2) 0', borderBottom: '1px solid var(--color-border-subtle)' }}>
      <span className="text-secondary text-sm">{label}</span>
      <span className="text-sm font-medium" style={{ textAlign: 'right' }}>{value ?? '—'}</span>
    </div>
  );
}

export function TargetOverviewPage() {
  const { targetId, overview } = useOutletContext<OutletCtx>();

  if (!overview) return <LoadingState message="Loading overview…" />;

  const latestAssessment = overview.latestAssessment;
  const latestHealth = overview.latestHealthCheck;

  return (
    <div>
      <div className="grid grid-2" style={{ gap: 'var(--space-6)', marginBottom: 'var(--space-6)' }}>
        {/* Identity */}
        <div className="card">
          <div className="card__header"><h2 className="card__title">Identity</h2></div>
          <InfoRow label="Target ID" value={<span className="font-mono text-xs">{overview.targetId}</span>} />
          <InfoRow label="Display name" value={overview.displayName} />
          <InfoRow label="Product" value={overview.productType} />
          <InfoRow label="Environment" value={overview.environment} />
          <InfoRow label="Version" value={overview.productVersion} />
          <InfoRow label="Runtime" value={overview.runtime} />
          <InfoRow label="Namespace" value={overview.namespace} />
          <InfoRow label="Enabled" value={overview.enabled ? 'Yes' : 'No'} />
          <InfoRow label="Metrics" value={overview.metricsConfigured ? 'Configured' : 'Not configured'} />
        </div>

        {/* Infrastructure */}
        <div className="card">
          <div className="card__header">
            <h2 className="card__title">Infrastructure</h2>
            <Link to={`/targets/${encodeURIComponent(targetId)}/infrastructure`} className="btn btn--ghost btn--sm">
              Details →
            </Link>
          </div>
          <InfoRow label="Desired replicas" value={overview.desiredReplicas} />
          <InfoRow label="Ready replicas" value={overview.readyReplicas} />
          <InfoRow label="Pod count" value={overview.podCount} />
          <InfoRow label="Zone count" value={overview.zoneCount} />
        </div>
      </div>

      <div className="grid grid-2" style={{ gap: 'var(--space-6)' }}>
        {/* Health */}
        <div className="card">
          <div className="card__header">
            <h2 className="card__title">Health</h2>
            <Link to={`/targets/${encodeURIComponent(targetId)}/health`} className="btn btn--ghost btn--sm">
              Details →
            </Link>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', marginBottom: 'var(--space-4)' }}>
            <StatusBadge status={overview.healthStatus} />
          </div>
          {latestHealth ? (
            <>
              <InfoRow label="Last check" value={new Date(latestHealth.startedAt).toLocaleString()} />
              <InfoRow label="Trigger" value={latestHealth.triggerType} />
              {latestHealth.summary && (
                <p className="text-sm text-secondary" style={{ marginTop: 'var(--space-3)' }}>{latestHealth.summary}</p>
              )}
            </>
          ) : (
            <p className="text-sm text-muted">No health checks recorded yet.</p>
          )}
        </div>

        {/* Assessment */}
        <div className="card">
          <div className="card__header">
            <h2 className="card__title">Assessment</h2>
            <Link to={`/targets/${encodeURIComponent(targetId)}/assessment`} className="btn btn--ghost btn--sm">
              Details →
            </Link>
          </div>
          {latestAssessment ? (
            <>
              <div style={{ marginBottom: 'var(--space-4)' }}>
                <ScoreBar score={latestAssessment.score} />
              </div>
              <StatusBadge status={latestAssessment.status} />
              <div style={{ marginTop: 'var(--space-3)' }}>
                <InfoRow label="Profile" value={latestAssessment.profile} />
                <InfoRow label="Run at" value={new Date(latestAssessment.startedAt).toLocaleString()} />
                <InfoRow
                  label="Findings"
                  value={
                    <span>
                      {latestAssessment.findingCounts.critical > 0 && (
                        <span style={{ color: 'var(--color-severity-critical)', marginRight: 4 }}>
                          {latestAssessment.findingCounts.critical}C
                        </span>
                      )}
                      {latestAssessment.findingCounts.high > 0 && (
                        <span style={{ color: 'var(--color-severity-high)', marginRight: 4 }}>
                          {latestAssessment.findingCounts.high}H
                        </span>
                      )}
                      {latestAssessment.findingCounts.critical === 0 && latestAssessment.findingCounts.high === 0 && '—'}
                    </span>
                  }
                />
              </div>
            </>
          ) : (
            <p className="text-sm text-muted">No assessments recorded yet.</p>
          )}
        </div>
      </div>
    </div>
  );
}
