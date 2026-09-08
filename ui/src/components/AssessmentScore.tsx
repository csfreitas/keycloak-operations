import { ScoreBar } from './ScoreBar';

/** Completeness is the backend percentage (0–100), never a probability. */
export function isAssessmentScoreAvailable(status: string | null, completeness: number | null, scoreAvailable?: boolean | null): boolean {
  return scoreAvailable === true && status === 'COMPLETE' && completeness === 100;
}

export function AssessmentScore({ score, status, completeness, scoreAvailable }: {
  score: number | null;
  status: string | null;
  completeness: number | null;
  scoreAvailable?: boolean | null;
}) {
  if (!isAssessmentScoreAvailable(status, completeness, scoreAvailable) || score == null) {
    return <span className="text-muted text-sm" data-testid="assessment-inconclusive">
      {status == null ? 'Not assessed' : 'Inconclusive — incomplete evidence'}
    </span>;
  }
  return <ScoreBar score={score} />;
}
