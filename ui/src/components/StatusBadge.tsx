import type { HealthStatus, AssessmentStatus } from '../api/types';

type AnyStatus = HealthStatus | AssessmentStatus | string;

interface StatusBadgeProps {
  status: AnyStatus;
  size?: 'sm' | 'md';
}

const statusConfig: Record<string, { label: string; className: string }> = {
  HEALTHY: { label: 'Healthy', className: 'badge badge--healthy' },
  WARNING: { label: 'Warning', className: 'badge badge--warning' },
  CRITICAL: { label: 'Critical', className: 'badge badge--critical' },
  UNKNOWN: { label: 'Unknown', className: 'badge badge--unknown' },
  PASSED: { label: 'Passed', className: 'badge badge--healthy' },
  FAILED: { label: 'Failed', className: 'badge badge--critical' },
  PARTIAL: { label: 'Partial', className: 'badge badge--warning' },
  PENDING: { label: 'Pending', className: 'badge badge--unknown' },
  RUNNING: { label: 'Running', className: 'badge badge--info' },
  ERROR: { label: 'Error', className: 'badge badge--critical' },
};

export function StatusBadge({ status, size = 'md' }: StatusBadgeProps) {
  const cfg = statusConfig[status] ?? { label: status, className: 'badge badge--unknown' };
  const sizeClass = size === 'sm' ? ' badge--sm' : '';
  return (
    <span
      className={cfg.className + sizeClass}
      data-testid="status-badge"
      aria-label={`Status: ${cfg.label}`}
    >
      {cfg.label}
    </span>
  );
}
