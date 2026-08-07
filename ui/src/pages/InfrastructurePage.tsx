import { useCallback, useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import type { SnapshotDetail, InventoryResult, TargetOverview } from '../api/types';
import { fetchLatestSnapshot } from '../api/snapshots';
import { fetchInventory } from '../api/inventory';
import { LoadingState } from '../components/LoadingState';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';

interface OutletCtx {
  targetId: string;
  overview: TargetOverview | null;
}

function SnapshotSummaryView({ snapshot }: { snapshot: SnapshotDetail }) {
  const summary = snapshot.summary;
  return (
    <div>
      <div className="card" style={{ marginBottom: 'var(--space-4)' }}>
        <div className="card__header">
          <h3 className="card__title">Snapshot</h3>
          <span className="text-xs text-muted">{new Date(snapshot.createdAt).toLocaleString()}</span>
        </div>
        <div style={{ marginBottom: 'var(--space-3)' }}>
          <span className="text-xs text-muted">Hash: </span>
          <span className="font-mono text-xs">{snapshot.snapshotHash}</span>
        </div>
        {summary && (
          <pre className="font-mono text-xs text-secondary" style={{
            background: 'var(--color-bg-raised)',
            padding: 'var(--space-4)',
            borderRadius: 'var(--radius-md)',
            overflow: 'auto',
            maxHeight: 400,
          }}>
            {JSON.stringify(summary, null, 2)}
          </pre>
        )}
        {!summary && <p className="text-sm text-muted">No summary in this snapshot.</p>}
      </div>
    </div>
  );
}

function InventoryView({ inventory }: { inventory: InventoryResult }) {
  return (
    <div>
      {inventory.pods.length > 0 && (
        <div className="card" style={{ marginBottom: 'var(--space-4)' }}>
          <div className="card__header"><h3 className="card__title">Pods</h3></div>
          <div className="data-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Namespace</th>
                  <th>Status</th>
                  <th>Node</th>
                  <th>Zone</th>
                </tr>
              </thead>
              <tbody>
                {inventory.pods.map((pod) => (
                  <tr key={pod.name}>
                    <td className="font-mono text-xs">{pod.name}</td>
                    <td className="text-xs">{pod.namespace}</td>
                    <td>
                      <span style={{ color: pod.ready ? 'var(--color-healthy)' : 'var(--color-warning)', fontSize: 'var(--text-xs)' }}>
                        {pod.ready ? '● Ready' : '○ Not Ready'}
                      </span>
                    </td>
                    <td className="text-xs text-muted">{pod.node ?? '—'}</td>
                    <td className="text-xs text-muted">{pod.zone ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {inventory.nodes.length > 0 && (
        <div className="card" style={{ marginBottom: 'var(--space-4)' }}>
          <div className="card__header"><h3 className="card__title">Nodes</h3></div>
          <div className="data-table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Zone</th>
                  <th>Ready</th>
                  <th>Roles</th>
                </tr>
              </thead>
              <tbody>
                {inventory.nodes.map((node) => (
                  <tr key={node.name}>
                    <td className="font-mono text-xs">{node.name}</td>
                    <td className="text-xs text-muted">{node.zone ?? '—'}</td>
                    <td>
                      <span style={{ color: node.ready ? 'var(--color-healthy)' : 'var(--color-critical)', fontSize: 'var(--text-xs)' }}>
                        {node.ready ? '● Yes' : '○ No'}
                      </span>
                    </td>
                    <td className="text-xs text-muted">{node.roles.join(', ') || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {inventory.pods.length === 0 && inventory.nodes.length === 0 && (
        <EmptyState title="No inventory data" description="No pods or nodes discovered for this target." />
      )}
    </div>
  );
}

export function InfrastructurePage() {
  const { targetId } = useOutletContext<OutletCtx>();
  const [snapshot, setSnapshot] = useState<SnapshotDetail | null>(null);
  const [inventory, setInventory] = useState<InventoryResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);

    fetchLatestSnapshot(targetId)
      .then((snap) => {
        setSnapshot(snap);
        if (!snap) {
          return fetchInventory(targetId).then((inv) => setInventory(inv));
        }
      })
      .catch(() => {
        return fetchInventory(targetId)
          .then((inv) => setInventory(inv))
          .catch((err: unknown) => setError((err as Error).message));
      })
      .finally(() => setLoading(false));
  }, [targetId]);

  useEffect(() => { load(); }, [load]);

  if (loading) return <LoadingState message="Loading infrastructure…" />;
  if (error && !snapshot && !inventory) return <ErrorState error={error} onRetry={load} />;

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-header__title">Infrastructure</h2>
          <p className="page-header__subtitle">
            {snapshot ? 'Latest environment snapshot' : 'Live inventory'}
          </p>
        </div>
        <div className="page-header__actions">
          <button className="btn btn--secondary btn--sm" onClick={load}>Refresh</button>
        </div>
      </div>

      {snapshot && <SnapshotSummaryView snapshot={snapshot} />}
      {!snapshot && inventory && <InventoryView inventory={inventory} />}
      {!snapshot && !inventory && (
        <EmptyState
          title="No infrastructure data"
          description="No snapshot or inventory data is available for this target."
        />
      )}
    </div>
  );
}
