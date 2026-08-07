import { useCallback, useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import type { PerformanceSummary, MetricsStatus, TargetOverview } from '../api/types';
import { fetchMetricsSummary, fetchMetricsStatus } from '../api/metrics';
import { MetricValue } from '../components/MetricValue';
import { LoadingState } from '../components/LoadingState';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';

interface OutletCtx {
  targetId: string;
  overview: TargetOverview | null;
}

function availabilityLabel(a: string): string {
  const map: Record<string, string> = {
    AVAILABLE: 'Available',
    NOT_AVAILABLE: 'Not Available',
    STALE: 'Stale',
    UNKNOWN: 'Unknown',
    PARTIAL: 'Partial',
    DEGRADED: 'Degraded',
  };
  return map[a] ?? a;
}

export function PerformancePage() {
  const { targetId } = useOutletContext<OutletCtx>();
  const [status, setStatus] = useState<MetricsStatus | null>(null);
  const [summary, setSummary] = useState<PerformanceSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.all([
      fetchMetricsStatus(targetId),
      fetchMetricsSummary(targetId),
    ])
      .then(([s, sum]) => {
        setStatus(s);
        setSummary(sum);
        setLoading(false);
      })
      .catch((err: unknown) => {
        setError((err as Error).message);
        setLoading(false);
      });
  }, [targetId]);

  useEffect(() => { load(); }, [load]);

  if (loading) return <LoadingState message="Loading metrics…" />;
  if (error) return <ErrorState error={error} onRetry={load} />;

  if (!status?.configured) {
    return (
      <EmptyState
        title="Metrics not configured"
        description="Prometheus metrics are not configured for this target. Configure a metrics endpoint to enable performance monitoring."
      />
    );
  }

  if (!summary) {
    return (
      <EmptyState
        title="No metrics available"
        description="Metrics are configured but no data is available yet."
        action={<button className="btn btn--secondary btn--sm" onClick={load}>Retry</button>}
      />
    );
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-header__title">Performance</h2>
          <p className="page-header__subtitle">
            Semantic metrics · {summary.window ?? 'default'} window ·{' '}
            <span style={{ fontFamily: 'var(--font-mono)', fontSize: 'var(--text-xs)' }}>
              {availabilityLabel(summary.overallAvailability)}
            </span>
          </p>
        </div>
        <div className="page-header__actions">
          <button className="btn btn--secondary btn--sm" onClick={load}>Refresh</button>
        </div>
      </div>

      {status.message && (
        <p className="text-sm text-muted" style={{ marginBottom: 'var(--space-4)' }}>{status.message}</p>
      )}

      {summary.categories.map((cat) => (
        <div key={cat.category} className="card" style={{ marginBottom: 'var(--space-4)' }}>
          <div className="card__header">
            <h3 className="card__title">{cat.category}</h3>
            <span className="text-xs text-muted">{availabilityLabel(cat.availability)}</span>
          </div>
          {cat.metrics.length === 0 ? (
            <p className="text-sm text-muted">No metrics in this category.</p>
          ) : (
            cat.metrics.map((m) => (
              <div key={m.name} className="metric-row">
                <div>
                  <div className="metric-row__label">{m.label ?? m.name}</div>
                  {m.description && (
                    <div className="text-xs text-muted" style={{ marginTop: 2 }}>{m.description}</div>
                  )}
                </div>
                <MetricValue
                  value={m.value}
                  availability={m.availability}
                  unit={m.unit}
                />
              </div>
            ))
          )}
        </div>
      ))}

      {summary.categories.length === 0 && (
        <EmptyState title="No metric categories" description="No metric categories returned from the backend." />
      )}
    </div>
  );
}
