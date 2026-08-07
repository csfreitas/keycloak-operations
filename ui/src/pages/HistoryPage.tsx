import { useCallback, useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import type { AssessmentRunSummary, HealthCheckDetail, SnapshotDetail, AuditEntry, TargetOverview } from '../api/types';
import { fetchAssessments } from '../api/assessments';
import { fetchHealthChecks } from '../api/health';
import { fetchSnapshots } from '../api/snapshots';
import { fetchAudit } from '../api/audit';
import { StatusBadge } from '../components/StatusBadge';
import { ScoreBar } from '../components/ScoreBar';
import { LoadingState } from '../components/LoadingState';
import { EmptyState } from '../components/EmptyState';

interface OutletCtx {
  targetId: string;
  overview: TargetOverview | null;
}

type Tab = 'assessments' | 'health' | 'snapshots' | 'audit';

function AssessmentHistory({ items }: { items: AssessmentRunSummary[] }) {
  if (items.length === 0) return <EmptyState title="No assessments" description="No assessment history for this target." />;
  return (
    <div className="data-table-wrapper" data-testid="history-table">
      <table className="data-table">
        <thead>
          <tr><th>Date</th><th>Profile</th><th>Status</th><th>Score</th><th>Trigger</th></tr>
        </thead>
        <tbody>
          {items.map((a) => (
            <tr key={a.id}>
              <td className="text-xs text-muted">{new Date(a.startedAt).toLocaleString()}</td>
              <td className="text-sm">{a.profile}</td>
              <td><StatusBadge status={a.status} size="sm" /></td>
              <td style={{ minWidth: 120 }}><ScoreBar score={a.score} /></td>
              <td className="text-xs text-muted">{a.triggerType}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function HealthHistory({ items }: { items: HealthCheckDetail[] }) {
  if (items.length === 0) return <EmptyState title="No health checks" description="No health check history for this target." />;
  return (
    <div className="data-table-wrapper" data-testid="history-table">
      <table className="data-table">
        <thead>
          <tr><th>Date</th><th>Status</th><th>Components</th><th>Trigger</th><th>Duration</th></tr>
        </thead>
        <tbody>
          {items.map((h) => {
            const durationMs = h.completedAt && h.startedAt
              ? new Date(h.completedAt).getTime() - new Date(h.startedAt).getTime()
              : null;
            return (
              <tr key={h.id}>
                <td className="text-xs text-muted">{new Date(h.startedAt).toLocaleString()}</td>
                <td><StatusBadge status={h.overallStatus} size="sm" /></td>
                <td className="text-xs">{h.components.length} component{h.components.length !== 1 ? 's' : ''}</td>
                <td className="text-xs text-muted">{h.triggerType}</td>
                <td className="font-mono text-xs text-muted">{durationMs != null ? `${durationMs}ms` : '—'}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function SnapshotHistory({ items }: { items: SnapshotDetail[] }) {
  if (items.length === 0) return <EmptyState title="No snapshots" description="No snapshot history for this target." />;
  return (
    <div className="data-table-wrapper" data-testid="history-table">
      <table className="data-table">
        <thead>
          <tr><th>Date</th><th>Hash</th></tr>
        </thead>
        <tbody>
          {items.map((s) => (
            <tr key={s.id}>
              <td className="text-xs text-muted">{new Date(s.createdAt).toLocaleString()}</td>
              <td className="font-mono text-xs">{s.snapshotHash.substring(0, 16)}…</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AuditHistory({ items }: { items: AuditEntry[] }) {
  if (items.length === 0) return <EmptyState title="No audit entries" description="No audit trail for this target." />;
  return (
    <div className="data-table-wrapper" data-testid="history-table">
      <table className="data-table">
        <thead>
          <tr><th>Date</th><th>Source</th><th>Action</th><th>Subject</th></tr>
        </thead>
        <tbody>
          {items.map((e) => (
            <tr key={e.id}>
              <td className="text-xs text-muted">{new Date(e.at).toLocaleString()}</td>
              <td className="text-xs">{e.source}</td>
              <td className="text-sm font-medium">{e.action}</td>
              <td className="text-xs text-muted">{e.subject ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function HistoryPage() {
  const { targetId } = useOutletContext<OutletCtx>();
  const [tab, setTab] = useState<Tab>('assessments');
  const [assessments, setAssessments] = useState<AssessmentRunSummary[]>([]);
  const [healthChecks, setHealthChecks] = useState<HealthCheckDetail[]>([]);
  const [snapshots, setSnapshots] = useState<SnapshotDetail[]>([]);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.allSettled([
      fetchAssessments(targetId, 0, 30),
      fetchHealthChecks(targetId, 0, 30),
      fetchSnapshots(targetId, 0, 30),
      fetchAudit({ targetId, size: 30 }),
    ]).then(([aRes, hRes, sRes, auRes]) => {
      if (aRes.status === 'fulfilled') setAssessments(aRes.value.items);
      if (hRes.status === 'fulfilled') setHealthChecks(hRes.value.items);
      if (sRes.status === 'fulfilled') setSnapshots(sRes.value.items);
      if (auRes.status === 'fulfilled') setAudit(auRes.value.items);
      const errs = [aRes, hRes, sRes, auRes]
        .filter((r) => r.status === 'rejected')
        .map((r) => (r as PromiseRejectedResult).reason?.message ?? 'Error')
        .join('; ');
      if (errs) setError(errs);
      setLoading(false);
    });
  }, [targetId]);

  useEffect(() => { load(); }, [load]);

  const tabClass = (t: Tab) =>
    'btn btn--ghost' + (tab === t ? ' btn--secondary' : '');

  return (
    <div>
      <div className="page-header">
        <div>
          <h2 className="page-header__title">History</h2>
          <p className="page-header__subtitle">Assessment, health, snapshot, and audit history</p>
        </div>
        <div className="page-header__actions">
          <button className="btn btn--secondary btn--sm" onClick={load} disabled={loading}>Refresh</button>
        </div>
      </div>

      {loading && <LoadingState message="Loading history…" />}
      {!loading && error && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <p className="text-sm" style={{ color: 'var(--color-warning)' }}>Some data may be incomplete: {error}</p>
        </div>
      )}

      {!loading && (
        <>
          <div style={{ display: 'flex', gap: 'var(--space-2)', marginBottom: 'var(--space-4)' }}>
            <button className={tabClass('assessments')} onClick={() => setTab('assessments')}>
              Assessments ({assessments.length})
            </button>
            <button className={tabClass('health')} onClick={() => setTab('health')}>
              Health ({healthChecks.length})
            </button>
            <button className={tabClass('snapshots')} onClick={() => setTab('snapshots')}>
              Snapshots ({snapshots.length})
            </button>
            <button className={tabClass('audit')} onClick={() => setTab('audit')}>
              Audit ({audit.length})
            </button>
          </div>

          {tab === 'assessments' && <AssessmentHistory items={assessments} />}
          {tab === 'health' && <HealthHistory items={healthChecks} />}
          {tab === 'snapshots' && <SnapshotHistory items={snapshots} />}
          {tab === 'audit' && <AuditHistory items={audit} />}
        </>
      )}
    </div>
  );
}
