import { apiClient } from './client';
import type { Finding, Page, Severity, FindingStatus } from './types';

export function fetchFindings(
  targetId: string,
  opts?: { severity?: Severity; lifecycleStatus?: FindingStatus; page?: number; size?: number },
): Promise<Page<Finding>> {
  return apiClient.get<Page<Finding>>(
    `/targets/${encodeURIComponent(targetId)}/findings`,
    {
      severity: opts?.severity,
      lifecycleStatus: opts?.lifecycleStatus,
      page: opts?.page ?? 0,
      size: opts?.size ?? 50,
    },
  );
}
