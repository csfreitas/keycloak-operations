import { apiClient } from './client';
import type { AssessmentRunSummary, Page } from './types';

export function fetchAssessments(
  targetId: string,
  page = 0,
  size = 20,
): Promise<Page<AssessmentRunSummary>> {
  return apiClient.get<Page<AssessmentRunSummary>>(
    `/targets/${encodeURIComponent(targetId)}/assessments`,
    { page, size },
  );
}

export function runAssessment(
  targetId: string,
  profile?: string,
): Promise<AssessmentRunSummary> {
  return apiClient.post<AssessmentRunSummary>(
    `/targets/${encodeURIComponent(targetId)}/assessments`,
    profile ? { profile } : undefined,
  );
}
