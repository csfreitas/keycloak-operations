interface ScoreBarProps {
  score: number | null;
  label?: string;
  showLabel?: boolean;
}

function scoreColor(score: number): string {
  if (score >= 80) return 'var(--color-healthy)';
  if (score >= 60) return 'var(--color-warning)';
  return 'var(--color-critical)';
}

export function ScoreBar({ score, label, showLabel = true }: ScoreBarProps) {
  if (score == null) {
    return (
      <span className="text-muted text-sm" data-testid="score-bar">
        {label ?? 'No score'}
      </span>
    );
  }

  const pct = Math.max(0, Math.min(100, score));
  const color = scoreColor(pct);

  return (
    <div className="score-bar-container" data-testid="score-bar">
      {label && <span className="text-xs text-muted" style={{ minWidth: 80 }}>{label}</span>}
      <div className="score-bar-track" aria-hidden="true">
        <div
          className="score-bar-fill"
          style={{ width: `${pct}%`, background: color }}
        />
      </div>
      {showLabel && (
        <span className="score-bar-label" style={{ color }}>
          {Math.round(pct)}
        </span>
      )}
    </div>
  );
}
