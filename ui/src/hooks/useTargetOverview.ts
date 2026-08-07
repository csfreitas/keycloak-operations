import { useCallback, useEffect, useState } from 'react';
import { fetchTargetOverview } from '../api/targets';
import type { TargetOverview } from '../api/types';

interface UseTargetOverviewResult {
  overview: TargetOverview | null;
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

export function useTargetOverview(targetId: string): UseTargetOverviewResult {
  const [overview, setOverview] = useState<TargetOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    setOverview(null);
    fetchTargetOverview(targetId)
      .then((data) => {
        setOverview(data);
        setLoading(false);
      })
      .catch((err: unknown) => {
        setError((err as Error).message ?? 'Failed to load target');
        setLoading(false);
      });
  }, [targetId]);

  useEffect(() => {
    load();
  }, [load]);

  return { overview, loading, error, refresh: load };
}
