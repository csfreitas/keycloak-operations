import type { OperationalEvent } from './types';
import { API_BASE } from './client';

export type EventsCallback = (event: OperationalEvent) => void;
export type EventsErrorCallback = (err: Event) => void;

export function connectEventStream(
  onEvent: EventsCallback,
  onError?: EventsErrorCallback,
): EventSource {
  const url = `${API_BASE}/events`;
  const source = new EventSource(url);

  source.onmessage = (ev) => {
    try {
      const data = JSON.parse(ev.data) as OperationalEvent;
      onEvent(data);
    } catch {
      // ignore malformed frames
    }
  };

  if (onError) {
    source.onerror = onError;
  }

  return source;
}
