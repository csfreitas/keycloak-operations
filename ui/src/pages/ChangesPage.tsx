import { useCallback, useEffect, useState } from 'react';
import { Link, useOutletContext, useParams, useSearchParams } from 'react-router-dom';
import type { ChangeRecord, TargetOverview } from '../api/types';
import { fetchChanges } from '../api/changes';
import { StatusBadge } from '../components/StatusBadge';
import { LoadingState } from '../components/LoadingState';
import { EmptyState } from '../components/EmptyState';
import { ErrorState } from '../components/ErrorState';
import { ApiResponseError } from '../api/client';

const STATUS_FILTERS = [
  '',
  'WAITING_APPROVAL',
  'APPROVED',
  'APPLIED',
  'VERIFIED',
  'FAILED',
  'REJECTED',
] as const;

interface OutletCtx {
  targetId: string;
  overview: TargetOverview | null;
}

export function ChangesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = searchParams.get('status') ?? '';
  const params = useParams<{ targetId?: string }>();
  const outlet = useOutletContext<OutletCtx | undefined>();
  const targetId = params.targetId ?? outlet?.targetId ?? searchParams.get('targetId') ?? undefined;

  const [items, setItems] = useState<ChangeRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiResponseError | Error | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await fetchChanges({
        targetId,
        status: status || undefined,
        page: 0,
        size: 50,
      });
      setItems(page.items);
    } catch (err) {
      setError(err as Error);
    } finally {
      setLoading(false);
    }
  }, [status, targetId]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="page" style={{ padding: 'var(--space-4) var(--space-6)' }}>
      <header className="page-header">
        <div>
          <h1 className="page-header__title">Changes</h1>
          <p className="page-header__subtitle">
            Controlled administration lifecycle — plan, approve, apply, verify
          </p>
        </div>
      </header>

      <div className="toolbar" style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '1rem' }}>
        {STATUS_FILTERS.map((s) => (
          <button
            key={s || 'all'}
            type="button"
            className={'btn btn--sm' + (status === s ? ' btn--primary' : '')}
            onClick={() => {
              const next = new URLSearchParams(searchParams);
              if (s) next.set('status', s);
              else next.delete('status');
              setSearchParams(next);
            }}
          >
            {s || 'All'}
          </button>
        ))}
      </div>

      {loading && <LoadingState message="Loading changes…" />}
      {error && !loading && <ErrorState error={error} onRetry={load} title="Failed to load changes" />}
      {!loading && !error && items.length === 0 && (
        <EmptyState title="No changes" description="No change records match the current filter." />
      )}
      {!loading && !error && items.length > 0 && (
        <div className="data-table-wrapper" data-testid="changes-table">
          <table className="data-table data-table--clickable">
            <thead>
              <tr>
                <th>Created</th>
                <th>Target</th>
                <th>Resource</th>
                <th>Risk</th>
                <th>Status</th>
                <th>Verification</th>
              </tr>
            </thead>
            <tbody>
              {items.map((c) => (
                <tr key={c.changeId}>
                  <td className="text-xs text-muted">
                    <Link to={`/changes/${encodeURIComponent(c.changeId)}`}>
                      {new Date(c.createdAt).toLocaleString()}
                    </Link>
                  </td>
                  <td className="text-sm">
                    <Link to={`/targets/${encodeURIComponent(c.targetId)}`}>{c.targetId}</Link>
                    <div className="text-xs text-muted">{c.environment}</div>
                  </td>
                  <td className="text-sm">
                    {c.resourceType} / {c.resourceId}
                    <div className="text-xs text-muted">{c.realm}</div>
                  </td>
                  <td>
                    <StatusBadge status={c.risk ?? 'UNKNOWN'} size="sm" />
                  </td>
                  <td>
                    <StatusBadge status={c.status} size="sm" />
                  </td>
                  <td className="text-xs text-muted">{c.verificationStatus ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
