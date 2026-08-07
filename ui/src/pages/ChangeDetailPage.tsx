import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import type { ChangeRecord } from '../api/types';
import {
  approveChange,
  applyChange,
  fetchChange,
  rejectChange,
  verifyChange,
} from '../api/changes';
import { StatusBadge } from '../components/StatusBadge';
import { LoadingState } from '../components/LoadingState';
import { ErrorState } from '../components/ErrorState';
import { ApiResponseError } from '../api/client';

export function ChangeDetailPage() {
  const { changeId } = useParams<{ changeId: string }>();
  const [change, setChange] = useState<ChangeRecord | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiResponseError | Error | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    if (!changeId) return;
    setLoading(true);
    setError(null);
    try {
      setChange(await fetchChange(changeId));
    } catch (err) {
      setError(err as Error);
    } finally {
      setLoading(false);
    }
  }, [changeId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function run(action: () => Promise<ChangeRecord>) {
    setBusy(true);
    setError(null);
    try {
      setChange(await action());
    } catch (err) {
      setError(err as Error);
    } finally {
      setBusy(false);
    }
  }

  if (!changeId) return null;
  if (loading && !change) return <LoadingState message="Loading change…" />;
  if (error && !change) {
    return <ErrorState error={error} onRetry={load} title="Failed to load change" />;
  }
  if (!change) return null;

  return (
    <div className="page" style={{ padding: 'var(--space-4) var(--space-6)' }} data-testid="change-detail">
      <header className="page-header">
        <div>
          <p className="text-xs text-muted">
            <Link to="/changes">Changes</Link> / {change.changeId}
          </p>
          <h1 className="page-header__title">
            {change.resourceType} · {change.resourceId}
          </h1>
          <p className="page-header__subtitle">
            {change.targetId} · {change.environment} · {change.operation}
          </p>
        </div>
        <div className="page-header__actions" style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
          {change.status === 'WAITING_APPROVAL' && (
            <>
              <button
                type="button"
                className="btn btn--primary"
                disabled={busy}
                onClick={() => void run(() => approveChange(change.changeId, 'ui-operator'))}
              >
                Approve
              </button>
              <button
                type="button"
                className="btn"
                disabled={busy}
                onClick={() => void run(() => rejectChange(change.changeId, 'ui-operator', 'Rejected from UI'))}
              >
                Reject
              </button>
            </>
          )}
          {(change.status === 'APPROVED' || change.status === 'APPLIED') && (
            <button
              type="button"
              className="btn btn--primary"
              disabled={busy}
              onClick={() => void run(() => applyChange(change.changeId, 'ui-operator'))}
            >
              Apply
            </button>
          )}
          <button
            type="button"
            className="btn"
            disabled={busy}
            onClick={() => void run(() => verifyChange(change.changeId))}
          >
            Verify
          </button>
        </div>
      </header>

      {error && <ErrorState error={error} onRetry={load} title="Action failed" />}

      <div className="stat-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(140px,1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
        <div className="stat-card">
          <div className="stat-card__label">Status</div>
          <div className="stat-card__value"><StatusBadge status={change.status} /></div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">Risk</div>
          <div className="stat-card__value"><StatusBadge status={change.risk ?? 'UNKNOWN'} /></div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">Policy</div>
          <div className="stat-card__value text-sm">{change.policyDecision ?? '—'}</div>
          <div className="stat-card__sub">{change.policyReason}</div>
        </div>
        <div className="stat-card">
          <div className="stat-card__label">Verification</div>
          <div className="stat-card__value text-sm">{change.verificationStatus ?? '—'}</div>
          <div className="stat-card__sub">{change.verificationMessage}</div>
        </div>
      </div>

      <section style={{ marginBottom: '1.5rem' }}>
        <h2 className="text-sm" style={{ marginBottom: '0.5rem' }}>Diff</h2>
        <div className="data-table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>Property</th>
                <th>Kind</th>
                <th>Before</th>
                <th>After</th>
              </tr>
            </thead>
            <tbody>
              {change.diff.map((d) => (
                <tr key={d.property + d.kind}>
                  <td className="font-mono text-xs">{d.property}</td>
                  <td><StatusBadge status={d.kind} size="sm" /></td>
                  <td className="font-mono text-xs">{d.before ?? '—'}</td>
                  <td className="font-mono text-xs">{d.after ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section>
        <h2 className="text-sm" style={{ marginBottom: '0.5rem' }}>Integrity</h2>
        <dl className="text-xs text-muted" style={{ display: 'grid', gap: '0.35rem' }}>
          <div><dt style={{ display: 'inline' }}>Plan fingerprint: </dt><dd style={{ display: 'inline' }} className="font-mono">{change.planFingerprint ?? '—'}</dd></div>
          <div><dt style={{ display: 'inline' }}>Approval fingerprint: </dt><dd style={{ display: 'inline' }} className="font-mono">{change.approvalFingerprint ?? '—'}</dd></div>
          <div><dt style={{ display: 'inline' }}>Approved by: </dt><dd style={{ display: 'inline' }}>{change.approvedBy ?? '—'} {change.approvedAt ? `(${new Date(change.approvedAt).toLocaleString()})` : ''}</dd></div>
          <div><dt style={{ display: 'inline' }}>Applied at: </dt><dd style={{ display: 'inline' }}>{change.appliedAt ? new Date(change.appliedAt).toLocaleString() : '—'}</dd></div>
          <div><dt style={{ display: 'inline' }}>Result: </dt><dd style={{ display: 'inline' }}>{change.resultMessage ?? '—'}</dd></div>
        </dl>
      </section>
    </div>
  );
}
