import type { MetricAvailability } from '../api/types';

interface MetricValueProps {
  value: number | null | undefined;
  availability: MetricAvailability | null | undefined;
  unit?: string | null;
  precision?: number;
}

/**
 * Renders a metric value respecting availability.
 * NEVER shows a missing or unavailable metric as "0".
 */
export function MetricValue({ value, availability, unit, precision = 2 }: MetricValueProps) {
  const avail = availability ?? 'UNKNOWN';

  if (avail === 'AVAILABLE' && value != null) {
    const formatted = Number.isInteger(value)
      ? value.toString()
      : value.toFixed(precision);
    return (
      <span className="metric-value metric-value--available" data-testid="metric-value">
        {formatted}
        {unit && <span className="metric-value__unit">{unit}</span>}
      </span>
    );
  }

  const labels: Record<MetricAvailability, string> = {
    AVAILABLE: 'N/A',
    NOT_AVAILABLE: 'N/A',
    STALE: 'Stale',
    UNKNOWN: 'Unknown',
    PARTIAL: 'Partial',
    DEGRADED: 'Degraded',
  };

  const label = labels[avail as MetricAvailability] ?? avail;
  const cls =
    avail === 'STALE' ? 'metric-value metric-value--stale' : 'metric-value metric-value--unavailable';

  return (
    <span className={cls} data-testid="metric-value" aria-label={`Metric unavailable: ${label}`}>
      {label}
    </span>
  );
}
