import { useCallback, useEffect, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import type { AssessmentRunSummary, Finding, TargetOverview } from '../api/types';
import { fetchAssessments, runAssessment } from '../api/assessments';
import { fetchFindings } from '../api/findings';
import { StatusBadge } from '../components/StatusBadge';
import { ScoreBar } from '../components/ScoreBar';
import { FindingList } from '../components/FindingList';
import { LoadingState } from '../components/LoadingState';
import { ErrorState } from '../components/ErrorState';
import { EmptyState } from '../components/EmptyState';

interface OutletCtx {
  targetId: string;
  overview: TargetOverview | null;
}

function CategoryScoreList({ scores }: { scores: AssessmentRunSummary['categoryScores'] }) {
  if (!scores || scores.length === 0) return null;
  return (
    <div style={{ marginTop: 'var(--space-4)' }}>
      <h4 className="text-xs text-muted" style={{ textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 'var(--space-3)' }}>
        Category Scores
      </h4>
      {scores.map((cs) => (
        <div key={cs.category} style={{ marginBottom: 'var(--space-2)' }}>
          <ScoreBar score={cs.score} label={cs.category} />
        </div>
      ))}
    </div>
  );
}

function AssessmentCard({
  assessment,
  isLatest,
  findings,
  findingsLoading,
}: {
  assessment: AssessmentRunSummary;
  isLatest: boolean;
  findings: Finding[];
  findingsLoading: boolean;
}) {
  const [expanded, setExpanded] = useState(isLatest);
  const fc = assessment.findingCounts;

  return (
    <div className="card" style={{ marginBottom: 'var(--space-4)' }} data-testid="assessment-card">
      <div className="card__header" style={{ cursor: 'pointer' }} onClick={() => setExpanded((v) => !v)}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flex: 1, flexWrap: 'wrap' }}>
          <StatusBadge status={assessment.status} />
          {assessment.score != null && (
            <span className="font-semibold" style={{ color: assessment.score >= 80 ? 'var(--color-healthy)' : assessment.score >= 60 ? 'var(--color-warning)' : 'var(--color-critical)' }}>
              {Math.round(assessment.score)}
            </span>
          )}
          <span className="text-sm font-medium">{new Date(assessment.startedAt).toLocaleString()}</span>
          <span className="text-xs text-muted">{assessment.profile}</span>
          <span className="text-xs text-muted">{assessment.triggerType}</span>
          {isLatest && <span className="tag">Latest</span>}
          {assessment.status === 'PARTIAL' && <span className="tag" style={{ color: 'var(--color-warning)' }}>Partial</span>}
          {(fc.critical > 0 || fc.high > 0) && (
            <span style={{ marginLeft: 'auto', display: 'flex', gap: 4 }}>
              {fc.critical > 0 && <span style={{ color: 'var(--color-severity-critical)', fontSize: 'var(--text-xs)' }}>{fc.critical}C</span>}
              {fc.high > 0 && <span style={{ color: 'var(--color-severity-high)', fontSize: 'var(--text-xs)' }}>{fc.high}H</span>}
            </span>
          )}
        </div>
        <span className="text-muted text-sm">{expanded ? '▲' : '▼'}</span>
      </div>

      {expanded && (
        <div style={{ marginTop: 'var(--space-4)' }}>
          <div style={{ display: 'flex', gap: 'var(--space-6)', marginBottom: 'var(--space-4)', flexWrap: 'wrap' }}>
            {assessment.score != null && (
              <div>
                <div className="stat-card__label">Score</div>
                <ScoreBar score={assessment.score} />
              </div>
            )}
            {assessment.evidenceCompleteness != null && (
              <div>
                <div className="stat-card__label">Evidence Completeness</div>
                <span className="font-semibold">{Math.round(assessment.evidenceCompleteness * 100)}%</span>
              </div>
            )}
            {assessment.confidence != null && (
              <div>
                <div className="stat-card__label">Confidence</div>
                <span className="font-semibold">{Math.round(assessment.confidence * 100)}%</span>
              </div>
            )}
          </div>

          <CategoryScoreList scores={assessment.categoryScores} />

          <hr className="divider" />

          <h4 className="text-xs text-muted" style={{ textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 'var(--space-3)' }}>
            Findings
          </h4>
          {isLatest && findingsLoading && <LoadingState inline message="Loading findings…" />}
          {isLatest && !findingsLoading && <FindingList findings={findings} />}
          {!isLatest && <p className="text-sm text-muted">Findings shown for latest assessment only.</p>}
        </div>
      )}
    </div>
  );
}

export function AssessmentPage() {
  const { targetId } = useOutletContext<OutletCtx>();
  const [assessments, setAssessments] = useState<AssessmentRunSummary[]>([]);
  const [findings, setFindings] = useState<Finding[]>([]);
  const [loading, setLoading] = useState(true);
  const [findingsLoading, setFindingsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const [runError, setRunError] = useState<string | null>(null);

  const loadFindings = useCallback(() => {
    setFindingsLoading(true);
    fetchFindings(targetId)
      .then((p) => { setFindings(p.items); setFindingsLoading(false); })
      .catch(() => { setFindingsLoading(false); });
  }, [targetId]);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    fetchAssessments(targetId)
      .then((p) => {
        setAssessments(p.items);
        setLoading(false);
        if (p.items.length > 0) loadFindings();
      })
      .catch((err: unknown) => {
        setError((err as Error).message);
        setLoading(false);
      });
  }, [targetId, loadFindings]);

  useEffect(() => { load(); }, [load]);

  const handleRun = () => {
    setRunning(true);
    setRunError(null);
    runAssessment(targetId)
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
          <h2 className="page-header__title">Assessment</h2>
          <p className="page-header__subtitle">Security and configuration assessment results</p>
        </div>
        <div className="page-header__actions">
          <button className="btn btn--secondary btn--sm" onClick={load} disabled={loading}>Refresh</button>
          <button className="btn btn--primary btn--sm" onClick={handleRun} disabled={running}>
            {running ? 'Running…' : 'Run Assessment'}
          </button>
        </div>
      </div>

      {runError && (
        <p className="text-sm" style={{ color: 'var(--color-critical)', marginBottom: 'var(--space-4)' }}>
          Failed to run assessment: {runError}
        </p>
      )}

      {loading && <LoadingState message="Loading assessments…" />}
      {!loading && error && <ErrorState error={error} onRetry={load} />}
      {!loading && !error && assessments.length === 0 && (
        <EmptyState
          title="No assessments yet"
          description="Run an assessment to evaluate this target."
          action={
            <button className="btn btn--primary" onClick={handleRun} disabled={running}>
              {running ? 'Running…' : 'Run Assessment'}
            </button>
          }
        />
      )}
      {!loading && assessments.map((a, i) => (
        <AssessmentCard
          key={a.id}
          assessment={a}
          isLatest={i === 0}
          findings={findings}
          findingsLoading={findingsLoading}
        />
      ))}
    </div>
  );
}
