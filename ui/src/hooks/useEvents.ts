import { useEffect, useRef, useState } from 'react';
import { connectEventStream } from '../api/events';
import type { OperationalEvent } from '../api/types';

interface UseEventsResult {
  events: OperationalEvent[];
  connected: boolean;
}

const MAX_EVENTS = 50;

export function useEvents(enabled = true): UseEventsResult {
  const [events, setEvents] = useState<OperationalEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const sourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!enabled) return;

    const source = connectEventStream(
      (event) => {
        setConnected(true);
        setEvents((prev) => [event, ...prev].slice(0, MAX_EVENTS));
      },
      () => {
        setConnected(false);
      },
    );

    source.onopen = () => setConnected(true);
    sourceRef.current = source;

    return () => {
      source.close();
      sourceRef.current = null;
      setConnected(false);
    };
  }, [enabled]);

  return { events, connected };
}
