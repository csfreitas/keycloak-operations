import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { apiClient, authHeaders, invalidateSession, onSessionInvalidated, setAuthToken, setAuthTokenProvider } from '../api/client';
import { connectEventStream, type EventStreamConnection } from '../api/events';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

function outcome<T>(promise: Promise<T>) {
  return promise.then(value => ({ ok: true, value }), error => ({ ok: false, error }));
}

let connection: EventStreamConnection | undefined;
beforeEach(() => { setAuthToken('synthetic-session-a'); });
afterEach(() => {
  connection?.close(); connection = undefined;
  setAuthToken(null); vi.unstubAllGlobals(); vi.useRealTimers();
});

it('does not send a POST whose token refresh completed after sign-out', async () => {
  const token = deferred<string>();
  const provider = vi.fn(() => token.promise);
  const fetchMock = vi.fn().mockResolvedValue(new Response('{}'));
  vi.stubGlobal('fetch', fetchMock); setAuthTokenProvider(provider);
  const result = outcome(apiClient.post('/targets/a/health-checks', {}));
  await vi.waitFor(() => expect(provider).toHaveBeenCalledOnce());
  setAuthTokenProvider(async () => { throw new Error('Signed out.'); });
  token.resolve('synthetic-session-a');
  expect((await result).ok).toBe(false);
  expect(fetchMock).not.toHaveBeenCalled();
});

it('aborts an old request and rejects its late response after the session changes', async () => {
  const response = deferred<Response>();
  const fetchMock = vi.fn().mockImplementation(() => response.promise);
  vi.stubGlobal('fetch', fetchMock);
  const result = outcome(apiClient.get('/targets/a'));
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
  setAuthToken('synthetic-session-b');
  const aborted = fetchMock.mock.calls[0][1]?.signal?.aborted;
  response.resolve(new Response('{"targetId":"a"}'));
  expect((await result).ok).toBe(false);
  expect(aborted).toBe(true);
});

it('rejects an old success body which finishes decoding after the session changes', async () => {
  const body = deferred<object>();
  const json = vi.fn(() => body.promise);
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, status: 200, json }));
  const result = outcome(apiClient.get('/targets/a'));
  await vi.waitFor(() => expect(json).toHaveBeenCalledOnce());
  setAuthToken('synthetic-session-b'); body.resolve({ targetId: 'a' });
  expect((await result).ok).toBe(false);
});

it('does not expose an old error body which finishes decoding after the session changes', async () => {
  const body = deferred<object>();
  const json = vi.fn(() => body.promise);
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 403, statusText: 'Forbidden', json }));
  const result = outcome(apiClient.get('/targets/a'));
  await vi.waitFor(() => expect(json).toHaveBeenCalledOnce());
  setAuthToken('synthetic-session-b'); body.resolve({ code: 'OLD_SESSION', message: 'old-session-body-canary' });
  const finished = await result;
  expect(finished.ok).toBe(false);
  expect('error' in finished && finished.error.message).not.toContain('old-session-body-canary');
});

it('blocks further authenticated or anonymous sends after a current REST 401', async () => {
  const fetchMock = vi.fn().mockImplementation(async () => new Response('{}', { status: 401 }));
  vi.stubGlobal('fetch', fetchMock);
  expect((await outcome(apiClient.get('/me'))).ok).toBe(false);
  expect((await outcome(apiClient.get('/targets'))).ok).toBe(false);
  expect(fetchMock).toHaveBeenCalledOnce();
});

it('does not revoke the session for a target-scoped REST 403', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response('{}', { status: 403 }))
    .mockResolvedValueOnce(new Response('{"targetId":"a"}'));
  vi.stubGlobal('fetch', fetchMock);
  expect((await outcome(apiClient.get('/targets/b'))).ok).toBe(false);
  expect(await apiClient.get('/targets/a')).toEqual({ targetId: 'a' });
  expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer synthetic-session-a');
});

it('does not let an old REST 401 revoke a replacement session', async () => {
  const response = deferred<Response>();
  const fetchMock = vi.fn().mockImplementationOnce(() => response.promise)
    .mockResolvedValueOnce(new Response('{"targetId":"b"}'));
  vi.stubGlobal('fetch', fetchMock);
  const result = outcome(apiClient.get('/targets/a'));
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
  setAuthToken('synthetic-session-b'); response.resolve(new Response('{}', { status: 401 }));
  expect((await result).ok).toBe(false);
  expect(await apiClient.get('/targets/b')).toEqual({ targetId: 'b' });
  expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer synthetic-session-b');
});

it('does not open an SSE connection whose credential wait belongs to an old session', async () => {
  const token = deferred<string>(); const provider = vi.fn(() => token.promise);
  const fetchMock = vi.fn().mockResolvedValue(new Response('', { headers: { 'Content-Type': 'text/event-stream' } }));
  vi.stubGlobal('fetch', fetchMock); setAuthTokenProvider(provider);
  connection = connectEventStream(vi.fn());
  await vi.waitFor(() => expect(provider).toHaveBeenCalledOnce());
  setAuthToken('synthetic-session-b'); token.resolve('synthetic-session-a');
  await new Promise(resolve => setTimeout(resolve, 0));
  expect(fetchMock).not.toHaveBeenCalled();
});

it('does not announce an SSE response arriving after explicit close', async () => {
  const response = deferred<Response>(); const cancel = vi.fn().mockResolvedValue(undefined);
  const fetchMock = vi.fn().mockImplementation(() => response.promise); vi.stubGlobal('fetch', fetchMock);
  connection = connectEventStream(vi.fn()); const open = vi.fn(); connection.onopen = open;
  await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
  connection.close();
  response.resolve(new Response(new ReadableStream({ cancel }), { headers: { 'Content-Type': 'text/event-stream' } }));
  await vi.waitFor(() => expect(cancel).toHaveBeenCalledOnce());
  expect(open).not.toHaveBeenCalled();
});

it('closes a current SSE stream and drops queued frames when the session changes', async () => {
  let streamController!: ReadableStreamDefaultController<Uint8Array>;
  const body = new ReadableStream<Uint8Array>({ start(controller) { streamController = controller; } });
  const fetchMock = vi.fn().mockResolvedValue(new Response(body, { headers: { 'Content-Type': 'text/event-stream' } }));
  vi.stubGlobal('fetch', fetchMock);
  const event = vi.fn(); const open = vi.fn(); connection = connectEventStream(event); connection.onopen = open;
  await vi.waitFor(() => expect(open).toHaveBeenCalledOnce());
  setAuthToken('synthetic-session-b');
  const aborted = fetchMock.mock.calls[0][1].signal.aborted;
  streamController.enqueue(new TextEncoder().encode('data: {"type":"health","targetId":"a"}\n\n'));
  await new Promise(resolve => setTimeout(resolve, 0));
  expect(aborted).toBe(true); expect(event).not.toHaveBeenCalled();
});

it('does not retry an old SSE subscription with a replacement session', async () => {
  vi.useFakeTimers();
  const fetchMock = vi.fn().mockResolvedValue(new Response('temporarily unavailable', { status: 503 }));
  vi.stubGlobal('fetch', fetchMock);
  const error = vi.fn(); connection = connectEventStream(vi.fn(), error);
  await vi.advanceTimersByTimeAsync(0);
  expect(error).toHaveBeenCalledOnce();
  setAuthToken('synthetic-session-b');
  await vi.advanceTimersByTimeAsync(3_001);
  expect(fetchMock).toHaveBeenCalledOnce();
});

it('revokes future REST sends after a current SSE 401', async () => {
  const fetchMock = vi.fn().mockImplementation(async () => new Response('{}', { status: 401 }));
  vi.stubGlobal('fetch', fetchMock);
  const error = vi.fn(); connection = connectEventStream(vi.fn(), error);
  await vi.waitFor(() => expect(error).toHaveBeenCalledOnce());
  expect((await outcome(apiClient.get('/me'))).ok).toBe(false);
  expect(fetchMock).toHaveBeenCalledOnce();
});

it('notifies explicit invalidation only once, including reentrant subscribers', async () => {
  const listener = vi.fn(() => invalidateSession());
  const unsubscribe = onSessionInvalidated(listener);
  try {
    invalidateSession(); invalidateSession();
    expect(listener).toHaveBeenCalledOnce();
    await expect(authHeaders()).rejects.toThrow('Session expired');
  } finally { unsubscribe(); }
});

it('allows unsubscribing and replacing credentials without invalidation notifications', async () => {
  const listener = vi.fn(); const unsubscribe = onSessionInvalidated(listener);
  setAuthToken('synthetic-session-b');
  setAuthTokenProvider(async () => 'synthetic-session-c');
  expect(listener).not.toHaveBeenCalled();
  expect(await authHeaders()).toEqual({ Authorization: 'Bearer synthetic-session-c' });
  unsubscribe(); invalidateSession(); expect(listener).not.toHaveBeenCalled();
});

it('revokes a provider without consulting it again, then accepts an explicit new session', async () => {
  const provider = vi.fn().mockResolvedValue('synthetic-session-a');
  setAuthTokenProvider(provider); invalidateSession();
  await expect(authHeaders()).rejects.toThrow('Session expired');
  expect(provider).not.toHaveBeenCalled();
  setAuthToken('synthetic-session-b');
  expect(await authHeaders()).toEqual({ Authorization: 'Bearer synthetic-session-b' });
});

it('retains the explicitly selected anonymous OPEN_LAB mode', async () => {
  setAuthToken(null);
  expect(await authHeaders()).toEqual({});
  const fetchMock = vi.fn().mockResolvedValue(new Response('{"authMode":"OPEN_LAB"}'));
  vi.stubGlobal('fetch', fetchMock);
  expect(await apiClient.get('/me')).toEqual({ authMode: 'OPEN_LAB' });
  expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
});

it('does not let an old SSE 401 invalidate or report errors into a replacement session', async () => {
  const response = deferred<Response>();
  const fetchMock = vi.fn().mockImplementationOnce(() => response.promise)
    .mockResolvedValueOnce(new Response('{"targetId":"b"}'));
  vi.stubGlobal('fetch', fetchMock);
  const error = vi.fn(); const invalidated = vi.fn(); const unsubscribe = onSessionInvalidated(invalidated);
  try {
    connection = connectEventStream(vi.fn(), error);
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    setAuthToken('synthetic-session-b'); response.resolve(new Response('{}', { status: 401 }));
    await new Promise(resolve => setTimeout(resolve, 0));
    expect(invalidated).not.toHaveBeenCalled(); expect(error).not.toHaveBeenCalled();
    expect(await apiClient.get('/targets/b')).toEqual({ targetId: 'b' });
  } finally { unsubscribe(); }
});

it('does not revoke the session for a target-scoped SSE 403', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response('{}', { status: 403 }))
    .mockResolvedValueOnce(new Response('{"targetId":"a"}'));
  vi.stubGlobal('fetch', fetchMock);
  const error = vi.fn(); connection = connectEventStream(vi.fn(), error);
  await vi.waitFor(() => expect(error).toHaveBeenCalledOnce());
  expect(await apiClient.get('/targets/a')).toEqual({ targetId: 'a' });
  expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe('Bearer synthetic-session-a');
});
