import { apiClient } from './client';
import type { InventoryResult } from './types';

export function fetchInventory(targetId: string): Promise<InventoryResult> {
  return apiClient.get<InventoryResult>(
    `/targets/${encodeURIComponent(targetId)}/inventory`,
  );
}
