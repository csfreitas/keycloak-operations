import { apiClient } from './client';
import type { MeResponse } from './types';

export function fetchMe(): Promise<MeResponse> {
  return apiClient.get<MeResponse>('/me');
}
