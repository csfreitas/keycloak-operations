import { apiClient } from './client';
import type { HealthCheckDetail, Page } from './types';

export function fetchHealthChecks(
  targetId: string,
  page = 0,
  size = 20,
): Promise<Page<HealthCheckDetail>> {
  return apiClient.get<Page<HealthCheckDetail>>(
    `/targets/${encodeURIComponent(targetId)}/health-checks`,
    { page, size },
  );
}

export function runHealthCheck(targetId: string): Promise<HealthCheckDetail> {
  return apiClient.post<HealthCheckDetail>(
    `/targets/${encodeURIComponent(targetId)}/health-checks`,
  );
}
