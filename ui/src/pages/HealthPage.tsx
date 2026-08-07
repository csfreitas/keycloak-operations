import { useCallback, useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import type { HealthCheckDetail, TargetOverview } from '../api/types';
import { fetchHealthChecks, runHealthCheck } from '../api/health';
import { StatusBadge } from '../components/StatusBadge';
import { LoadingState } from '../components/LoadingState';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';

interface OutletCtx {
  targetId: string;
  overview: TargetOverview | null;
}

function HealthComponentList({ components }: { components: HealthCheckDetail['components'] }) {
  return (
    <div>
      {components.map((c) => (
        <div key={c.name} className="health-component-row">
          <StatusBadge status={c.status} size="sm" />
          <span className="health-component-name">{c.name}</span>
          {c.message && <span className="health-component-message">{c.message}</span>}
          {c.durationMs != null && (
            <span className="health-component-duration">{c.durationMs}ms</span>
          )}
        </div>
      ))}
    </div>
  );
}

function HealthCheckCard({ check, isLatest }: { check: HealthCheckDetail; isLatest: boolean }) {
  const [expanded, setExpanded] = useState(isLatest);
  return (
    <div className="card" style={{ marginBottom: 'var(--space-3)' }}>
      <div className="card__header" style={{ cursor: 'pointer' }} onClick={() => setExpanded((v) => !v)}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
          <StatusBadge status={check.overallStatus} />
          <span className="text-sm font-medium">{new Date(check.startedAt).toLocaleString()}</span>
          <span className="text-xs text-muted">{check.triggerType}</span>
          {isLatest && <span className="tag">Latest</span>}
        </div>
        <span className="text-muted text-sm">{expanded ? '▲' : '▼'}</span>
      </div>
      {expanded && (
        <div style={{ marginTop: 'var(--space-3)' }}>
          {check.summary && (
            <p className="text-sm text-secondary" style={{ marginBottom: 'var(--space-3)' }}>{check.summary}</p>
          )}
          {check.components.length > 0 ? (
            <HealthComponentList components={check.components} />
          ) : (
            <p className="text-sm text-muted">No component details available.</p>
          )}
        </div>
      )}
    </div>
  );
}

export function HealthPage() {
  const { targetId } = useOutletContext<OutletCtx>();
  const [checks, setChecks] = useState<HealthCheckDetail[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const [runError, setRunError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    fetchHealthChecks(targetId)
      .then((p) => {
        setChecks(p.items);
        setLoading(false);
      })
      .catch((err: unknown) => {
        setError((err as Error).message);
        setLoading(false);
      });
  }, [targetId]);

  useEffect(() => { load(); }, [load]);

  const handleRun = () => {
    setRunning(true);
    setRunError(null);
    runHealthCheck(targetId)
      .then(() => { setRunning(false); load(); })
      .catch((err: unknown) => {
        setRunError((err as Error).message);
        setRunning(false);
      });
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-header__title">Health</h2>
          <p className="page-header__subtitle">Component health checks for this target</p>
        </div>
        <div className="page-header__actions">
          <button className="btn btn--secondary btn--sm" onClick={load} disabled={loading}>
            Refresh
          </button>
          <button className="btn btn--primary btn--sm" onClick={handleRun} disabled={running}>
            {running ? 'Running…' : 'Run Health Check'}
          </button>
        </div>
      </div>

      {runError && (
        <div className="error-state" style={{ padding: 'var(--space-3)', marginBottom: 'var(--space-4)' }}>
          <p className="text-sm" style={{ color: 'var(--color-critical)' }}>Failed to run: {runError}</p>
        </div>
      )}

      {loading && <LoadingState message="Loading health checks…" />}
      {!loading && error && <ErrorState error={error} onRetry={load} />}
      {!loading && !error && checks.length === 0 && (
        <EmptyState
          title="No health checks yet"
          description="Run a health check to see component status."
          action={
            <button className="btn btn--primary" onClick={handleRun} disabled={running}>
              {running ? 'Running…' : 'Run Health Check'}
            </button>
          }
        />
      )}
      {!loading && checks.map((check, i) => (
        <HealthCheckCard key={check.id} check={check} isLatest={i === 0} />
      ))}
    </div>
  );
}
