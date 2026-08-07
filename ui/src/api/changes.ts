import { apiClient } from './client';
import type { ChangeRecord, Page } from './types';

export function fetchChanges(params?: {
  targetId?: string;
  status?: string;
  page?: number;
  size?: number;
}) {
  return apiClient.get<Page<ChangeRecord>>('/changes', params);
}

export function fetchChange(changeId: string) {
  return apiClient.get<ChangeRecord>(`/changes/${encodeURIComponent(changeId)}`);
}

export function approveChange(changeId: string, approver?: string) {
  return apiClient.post<ChangeRecord>(`/changes/${encodeURIComponent(changeId)}/approve`, {
    approver,
  });
}

export function rejectChange(changeId: string, rejector?: string, reason?: string) {
  return apiClient.post<ChangeRecord>(`/changes/${encodeURIComponent(changeId)}/reject`, {
    rejector,
    reason,
  });
}

export function applyChange(changeId: string, actor?: string) {
  return apiClient.post<ChangeRecord>(`/changes/${encodeURIComponent(changeId)}/apply`, { actor });
}

export function verifyChange(changeId: string) {
  return apiClient.post<ChangeRecord>(`/changes/${encodeURIComponent(changeId)}/verify`);
}
