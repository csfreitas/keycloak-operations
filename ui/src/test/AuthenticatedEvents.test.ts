import { afterEach, expect, it, vi } from 'vitest';
import { connectEventStream, type EventStreamConnection } from '../api/events';
import { setAuthToken, setAuthTokenProvider } from '../api/client';

let connection: EventStreamConnection | undefined;
afterEach(() => { connection?.close(); setAuthToken(null); vi.unstubAllGlobals(); });

it('authenticates SSE through a header and parses split multiline frames', async () => {
  const encoder = new TextEncoder();
  const body = new ReadableStream({ start(controller) {
    controller.enqueue(encoder.encode(': heartbeat\r\ndata: {"type":\r\n'));
    controller.enqueue(encoder.encode('data: "health"}\r\n\r\ndata: invalid\n\n'));
  } });
  const fetchMock = vi.fn().mockResolvedValue(new Response(body, { headers: { 'Content-Type': 'text/event-stream' } }));
  vi.stubGlobal('fetch', fetchMock); setAuthToken('fixture-token');
  const event = vi.fn(); connection = connectEventStream(event);
  await vi.waitFor(() => expect(event).toHaveBeenCalledWith({ type: 'health' }));
  expect(event).toHaveBeenCalledOnce();
  expect(fetchMock.mock.calls[0][0]).not.toContain('fixture-token');
  expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer fixture-token');
  connection.close();
  expect(fetchMock.mock.calls[0][1].signal.aborted).toBe(true);
});

it('does not connect when credentials cannot be refreshed', async () => {
  const fetchMock = vi.fn(); vi.stubGlobal('fetch', fetchMock);
  setAuthTokenProvider(async () => { throw new Error('Expired'); });
  const error = vi.fn(); connection = connectEventStream(vi.fn(), error);
  await vi.waitFor(() => expect(error).toHaveBeenCalledOnce());
  expect(fetchMock).not.toHaveBeenCalled();
});

it.each([401, 403])('does not dispatch events from a denied stream (%s)', async (status) => {
  const fetchMock = vi.fn().mockResolvedValue(new Response('denied', { status }));
  vi.stubGlobal('fetch', fetchMock);
  const event = vi.fn(); const error = vi.fn(); connection = connectEventStream(event, error);
  await vi.waitFor(() => expect(error).toHaveBeenCalledOnce());
  expect(event).not.toHaveBeenCalled();
});
