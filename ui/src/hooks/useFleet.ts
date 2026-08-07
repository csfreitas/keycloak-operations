import { useCallback, useEffect, useState } from 'react';
import { fetchFleet } from '../api/fleet';
import type { FleetItem } from '../api/types';

interface UseFleetResult {
  items: FleetItem[];
  loading: boolean;
  error: string | null;
  refresh: () => void;
}

export function useFleet(): UseFleetResult {
  const [items, setItems] = useState<FleetItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    fetchFleet()
      .then((data) => {
        setItems(data);
        setLoading(false);
      })
      .catch((err: unknown) => {
        setError((err as Error).message ?? 'Failed to load fleet');
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return { items, loading, error, refresh: load };
}
