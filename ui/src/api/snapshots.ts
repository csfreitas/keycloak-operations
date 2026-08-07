import { apiClient } from './client';
import type { SnapshotDetail, Page } from './types';

export function fetchSnapshots(targetId: string, page = 0, size = 20): Promise<Page<SnapshotDetail>> {
  return apiClient.get<Page<SnapshotDetail>>(
    `/targets/${encodeURIComponent(targetId)}/snapshots`,
    { page, size },
  );
}

export function fetchLatestSnapshot(targetId: string): Promise<SnapshotDetail | null> {
  return fetchSnapshots(targetId, 0, 1).then(p => p.items[0] ?? null);
}
