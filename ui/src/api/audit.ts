import { apiClient } from './client';
import type { AuditEntry, Page } from './types';

export function fetchAudit(opts?: {
  targetId?: string;
  source?: string;
  page?: number;
  size?: number;
}): Promise<Page<AuditEntry>> {
  return apiClient.get<Page<AuditEntry>>('/audit', {
    targetId: opts?.targetId,
    source: opts?.source,
    page: opts?.page ?? 0,
    size: opts?.size ?? 30,
  });
}
