import type { Finding, Severity } from '../api/types';
import { EmptyState } from './EmptyState';

interface FindingListProps {
  findings: Finding[];
  maxItems?: number;
}

const severityOrder: Record<Severity, number> = {
  CRITICAL: 0,
  HIGH: 1,
  MEDIUM: 2,
  LOW: 3,
  INFO: 4,
};

const severityColors: Record<Severity, string> = {
  CRITICAL: 'var(--color-severity-critical)',
  HIGH: 'var(--color-severity-high)',
  MEDIUM: 'var(--color-severity-medium)',
  LOW: 'var(--color-severity-low)',
  INFO: 'var(--color-severity-info)',
};

function SeverityBadge({ severity }: { severity: Severity }) {
  return (
    <span
      className="severity-badge"
      style={{ borderColor: severityColors[severity], color: severityColors[severity] }}
      aria-label={`Severity: ${severity}`}
    >
      {severity}
    </span>
  );
}

export function FindingList({ findings, maxItems }: FindingListProps) {
  if (findings.length === 0) {
    return <EmptyState title="No findings" description="No findings match the current filter." />;
  }

  const sorted = [...findings].sort(
    (a, b) => (severityOrder[a.severity] ?? 5) - (severityOrder[b.severity] ?? 5),
  );
  const visible = maxItems != null ? sorted.slice(0, maxItems) : sorted;
  const hidden = sorted.length - visible.length;

  return (
    <div data-testid="finding-list">
      {visible.map((f) => (
        <div key={f.id} className="finding-item" data-testid="finding-item">
          <div className="finding-header">
            <SeverityBadge severity={f.severity} />
            <span className="finding-title">{f.title}</span>
            <span className="tag">{f.category}</span>
            {f.status !== 'OPEN' && <span className="tag">{f.status}</span>}
          </div>
          <div className="finding-meta">
            <span>ID: {f.id}</span>
            {f.subject && <span>Subject: {f.subject}</span>}
          </div>
          {f.description && (
            <p className="finding-description">{f.description}</p>
          )}
          {f.impact && (
            <p className="finding-description">
              <strong>Impact:</strong> {f.impact}
            </p>
          )}
          {f.recommendation && (
            <div className="finding-recommendation">{f.recommendation}</div>
          )}
        </div>
      ))}
      {hidden > 0 && (
        <p className="text-muted text-sm" style={{ marginTop: 'var(--space-3)' }}>
          + {hidden} more finding{hidden !== 1 ? 's' : ''}
        </p>
      )}
    </div>
  );
}
