import { apiClient } from './client';
import type { MetricsStatus, PerformanceSummary, MetricCategorySummary } from './types';

export type MetricCategory =
  | 'http'
  | 'database'
  | 'jvm'
  | 'cache'
  | 'authentication'
  | 'runtime'
  | 'cluster';

export function fetchMetricsStatus(targetId: string): Promise<MetricsStatus> {
  return apiClient.get<MetricsStatus>(
    `/targets/${encodeURIComponent(targetId)}/metrics/status`,
  );
}

export function fetchMetricsSummary(targetId: string, window?: string): Promise<PerformanceSummary> {
  return apiClient.get<PerformanceSummary>(
    `/targets/${encodeURIComponent(targetId)}/metrics/summary`,
    window ? { window } : undefined,
  );
}

export function fetchMetricCategory(
  targetId: string,
  category: MetricCategory,
  window?: string,
): Promise<MetricCategorySummary> {
  return apiClient.get<MetricCategorySummary>(
    `/targets/${encodeURIComponent(targetId)}/metrics/${category}`,
    window ? { window } : undefined,
  );
}
