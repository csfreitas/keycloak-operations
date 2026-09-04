import { apiClient } from './client';
import type { OperationsReport } from './types';

export function generateOperationsReport(
  targetId: string,
  profile?: string,
  metricsWindow?: string,
): Promise<OperationsReport> {
  const params = new URLSearchParams();
  if (profile?.trim()) params.set('profile', profile.trim());
  if (metricsWindow?.trim()) params.set('metricsWindow', metricsWindow.trim());
  const query = params.toString();
  return apiClient.post<OperationsReport>(
    `/targets/${encodeURIComponent(targetId)}/operations-reports${query ? `?${query}` : ''}`,
  );
}
