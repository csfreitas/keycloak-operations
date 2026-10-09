import type { OperationalEvent } from './types';
import { API_BASE } from './client';
import { assertCurrentSession, authHeaders, captureSession, invalidateSession, isCurrentSession } from './session';

export type EventsCallback = (event: OperationalEvent) => void;
export type EventsErrorCallback = (err: Event) => void;

export interface EventStreamConnection {
  close(): void;
  onopen: (() => void) | null;
}

/** Fetch supports bearer headers; never place credentials in the event URL. */
export function connectEventStream(onEvent: EventsCallback, onError?: EventsErrorCallback): EventStreamConnection {
  const scope = captureSession();
  let closed = false;
  let retry: ReturnType<typeof setTimeout> | undefined;
  let controller = new AbortController();
  const connection: EventStreamConnection = {
    onopen: null,
    close() {
      closed = true;
      clearTimeout(retry);
      scope.signal.removeEventListener('abort', close);
      controller.abort();
    },
  };
  function close() { connection.close(); }
  scope.signal.addEventListener('abort', close, { once: true });
  async function connect() {
    if (closed) return;
    controller = new AbortController();
    let reader: ReadableStreamDefaultReader<Uint8Array> | undefined;
    try {
      let headers: Record<string, string>;
      try { headers = await authHeaders(scope); }
      catch { closed = true; throw new Error('Authentication unavailable'); }
      if (closed) return;
      assertCurrentSession(scope);
      const response = await fetch(`${API_BASE}/events`, {
        headers: { ...headers, Accept: 'text/event-stream' },
        signal: controller.signal, redirect: 'error', credentials: 'omit',
      });
      if (closed || !isCurrentSession(scope)) {
        await response.body?.cancel().catch(() => undefined);
        return;
      }
      if (response.status === 401) {
        onError?.(new Event('error'));
        invalidateSession(scope);
        return;
      }
      if (response.status === 403) closed = true;
      if (!response.ok || !response.body || !response.headers.get('content-type')?.includes('text/event-stream')) {
        throw new Error('Event stream unavailable');
      }
      connection.onopen?.();
      reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let data: string[] = [];
      let frameSize = 0;
      while (!closed) {
        const chunk = await reader.read();
        if (closed) break;
        assertCurrentSession(scope);
        if (chunk.done) break;
        buffer += decoder.decode(chunk.value, { stream: true });
        if (buffer.length > 1_048_576) throw new Error('Event frame too large');
        let newline: number;
        while ((newline = buffer.indexOf('\n')) >= 0) {
          const line = buffer.slice(0, newline).replace(/\r$/, '');
          buffer = buffer.slice(newline + 1);
          frameSize += line.length;
          if (frameSize > 1_048_576) throw new Error('Event frame too large');
          if (line === '') {
            if (data.length) {
              let event: OperationalEvent | undefined;
              try { event = JSON.parse(data.join('\n')) as OperationalEvent; } catch { /* Ignore malformed frames. */ }
              if (event && !closed && isCurrentSession(scope)) onEvent(event);
            }
            data = []; frameSize = 0;
          } else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
        }
      }
      if (!closed) onError?.(new Event('error'));
    } catch {
      if (!controller.signal.aborted) onError?.(new Event('error'));
    } finally {
      await reader?.cancel().catch(() => undefined);
      controller.abort();
      if (closed || !isCurrentSession(scope)) connection.close();
      else retry = setTimeout(() => { void connect(); }, 3_000);
    }
  }
  void connect();
  return connection;
}
