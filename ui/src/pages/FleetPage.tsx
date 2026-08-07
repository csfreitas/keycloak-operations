import { useNavigate } from 'react-router-dom';
import { useFleet } from '../hooks/useFleet';
import { StatusBadge } from '../components/StatusBadge';
import { EnvBadge } from '../components/EnvBadge';
import { ScoreBar } from '../components/ScoreBar';
import { LoadingState } from '../components/LoadingState';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';
import type { FleetItem, Environment } from '../api/types';

const ENV_ORDER: Environment[] = ['PRD', 'STG', 'TEST', 'DEV', 'UNKNOWN'];

function groupByEnv(items: FleetItem[]): Map<string, FleetItem[]> {
  const map = new Map<string, FleetItem[]>();
  for (const item of items) {
    const env = item.environment ?? 'UNKNOWN';
    if (!map.has(env)) map.set(env, []);
    map.get(env)!.push(item);
  }
  return map;
}

function FindingCounts({ critical, high }: { critical: number; high: number }) {
  if (critical === 0 && high === 0) return <span className="text-muted text-xs">—</span>;
  return (
    <span style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
      {critical > 0 && (
        <span style={{ color: 'var(--color-severity-critical)', fontSize: 'var(--text-xs)', fontWeight: 'var(--weight-semibold)' }}>
          {critical} CRIT
        </span>
      )}
      {high > 0 && (
        <span style={{ color: 'var(--color-severity-high)', fontSize: 'var(--text-xs)', fontWeight: 'var(--weight-semibold)' }}>
          {high} HIGH
        </span>
      )}
    </span>
  );
}

function FleetTable({ items }: { items: FleetItem[] }) {
  const navigate = useNavigate();

  return (
    <div className="data-table-wrapper">
      <table className="data-table data-table--clickable" aria-label="Fleet targets">
        <thead>
          <tr>
            <th>Name</th>
            <th>Product</th>
            <th>Version</th>
            <th>Health</th>
            <th>Score</th>
            <th>Findings</th>
            <th>Metrics</th>
            <th>Last Health</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr
              key={item.targetId}
              onClick={() => navigate(`/targets/${encodeURIComponent(item.targetId)}`)}
              data-testid="fleet-row"
              aria-label={`Target: ${item.displayName}`}
            >
              <td>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <span className="font-medium">{item.displayName}</span>
                  {item.runtime && (
                    <span className="text-xs text-muted">{item.runtime}</span>
                  )}
                </div>
              </td>
              <td>
                <span className="text-sm">{item.productType}</span>
              </td>
              <td>
                <span className="font-mono text-sm">{item.productVersion ?? '—'}</span>
              </td>
              <td>
                <StatusBadge status={item.healthStatus} size="sm" />
              </td>
              <td style={{ minWidth: 120 }}>
                <ScoreBar score={item.latestAssessmentScore} showLabel />
              </td>
              <td>
                <FindingCounts critical={item.criticalFindings} high={item.highFindings} />
              </td>
              <td>
                {item.metricsConfigured ? (
                  <span style={{ color: 'var(--color-healthy)', fontSize: 'var(--text-xs)' }}>✓</span>
                ) : (
                  <span className="text-muted text-xs">—</span>
                )}
              </td>
              <td>
                <span className="text-xs text-muted">
                  {item.latestHealthAt
                    ? new Date(item.latestHealthAt).toLocaleDateString()
                    : '—'}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function FleetPage() {
  const { items, loading, error, refresh } = useFleet();

  if (loading) return <LoadingState message="Loading fleet…" />;
  if (error) return <ErrorState error={error} onRetry={refresh} title="Failed to load fleet" />;
  if (items.length === 0) {
    return (
      <div className="page-content">
        <EmptyState
          title="No targets registered"
          description="Register targets via the API or MCP tools to see them here."
        />
      </div>
    );
  }

  const grouped = groupByEnv(items);
  const orderedEnvs = [...ENV_ORDER.filter((e) => grouped.has(e)), ...([...grouped.keys()].filter((e) => !ENV_ORDER.includes(e as Environment)))];

  const totalCritical = items.reduce((s, i) => s + i.criticalFindings, 0);
  const totalHigh = items.reduce((s, i) => s + i.highFindings, 0);
  const healthy = items.filter((i) => i.healthStatus === 'HEALTHY').length;

  return (
    <div className="page-content">
      <div className="page-header">
        <div>
          <h1 className="page-header__title">Fleet</h1>
          <p className="page-header__subtitle">
            {items.length} target{items.length !== 1 ? 's' : ''} ·{' '}
            {healthy} healthy
            {totalCritical > 0 && <> · <span style={{ color: 'var(--color-severity-critical)' }}>{totalCritical} critical finding{totalCritical !== 1 ? 's' : ''}</span></>}
            {totalHigh > 0 && <> · <span style={{ color: 'var(--color-severity-high)' }}>{totalHigh} high finding{totalHigh !== 1 ? 's' : ''}</span></>}
          </p>
        </div>
        <div className="page-header__actions">
          <button className="btn btn--secondary btn--sm" onClick={refresh}>
            Refresh
          </button>
        </div>
      </div>

      {orderedEnvs.map((env) => (
        <section key={env} style={{ marginBottom: 'var(--space-8)' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', marginBottom: 'var(--space-3)' }}>
            <EnvBadge env={env} />
            <span className="text-secondary text-sm">{grouped.get(env)?.length} target{(grouped.get(env)?.length ?? 0) !== 1 ? 's' : ''}</span>
          </div>
          <FleetTable items={grouped.get(env)!} />
        </section>
      ))}
    </div>
  );
}
