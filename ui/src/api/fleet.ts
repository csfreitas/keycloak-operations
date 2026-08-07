import { apiClient } from './client';
import type { FleetItem } from './types';

export function fetchFleet(): Promise<FleetItem[]> {
  return apiClient.get<FleetItem[]>('/fleet');
}
