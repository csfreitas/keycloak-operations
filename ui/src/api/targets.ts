import { apiClient } from './client';
import type { TargetOverview } from './types';

export function fetchTargetOverview(targetId: string): Promise<TargetOverview> {
  return apiClient.get<TargetOverview>(`/targets/${encodeURIComponent(targetId)}/overview`);
}
