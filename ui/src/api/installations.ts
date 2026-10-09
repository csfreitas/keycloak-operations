import { apiClient } from './client';

export interface InstallationBinding { apiVersion: string; kind: string; name: string; uid: string }
export interface InstallationState {
  targetId: string; clusterId: string | null; namespace: string | null;
  binding: InstallationBinding | null; revision: number; managed: boolean;
  canDiscover: boolean; canConfirm: boolean;
}
export interface InstallationDiscovery {
  runId: string; targetId: string; namespace: string; expiresAt: string;
  candidates: { id: string; installation: InstallationBinding }[];
}
const path = (targetId: string) => `/targets/${encodeURIComponent(targetId)}/installation`;
export const fetchInstallation = (targetId: string) => apiClient.get<InstallationState>(path(targetId));
export const discoverInstallations = (targetId: string) => apiClient.post<InstallationDiscovery>(`${path(targetId)}/discover`);
export const confirmInstallation = (targetId: string, runId: string, candidateId: string) =>
  apiClient.post<InstallationState>(`${path(targetId)}/confirm`, { runId, candidateId });
