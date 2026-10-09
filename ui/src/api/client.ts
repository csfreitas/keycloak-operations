import type { ApiError } from './types';
import { assertCurrentSession, authHeaders, captureSession, invalidateSession, type SessionScope } from './session';
export { authHeaders, invalidateSession, onSessionInvalidated, setAuthToken, setAuthTokenProvider } from './session';

const DEFAULT_TIMEOUT_MS = 30_000;
const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081') + '/api/v1';

export class ApiResponseError extends Error {
  constructor(
    public readonly apiError: ApiError,
    public readonly response: Response,
  ) {
    super(apiError.message);
    this.name = 'ApiResponseError';
  }
}

async function buildHeaders(scope: SessionScope): Promise<Record<string, string>> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  Object.assign(headers, await authHeaders(scope));
  return headers;
}

async function request<T>(
  path: string,
  options?: RequestInit,
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<T> {
  if (!path.startsWith('/') || path.startsWith('//')) {
    throw new Error('Only platform-relative API paths are allowed.');
  }
  const scope = captureSession();
  assertCurrentSession(scope);
  const controller = new AbortController();
  const abort = () => controller.abort();
  scope.signal.addEventListener('abort', abort, { once: true });
  const timer = setTimeout(abort, timeoutMs);
  const url = `${API_BASE}${path}`;

  try {
    const headers = await buildHeaders(scope);
    assertCurrentSession(scope);
    if (controller.signal.aborted) throw new DOMException('Request aborted', 'AbortError');
    const response = await fetch(url, {
      ...options,
      headers: {
        ...headers,
        ...(options?.headers as Record<string, string> | undefined),
      },
      signal: controller.signal,
    });
    assertCurrentSession(scope);
    if (response.status === 401) {
      invalidateSession(scope);
      throw new Error('Session expired. Sign in again.');
    }

    if (!response.ok) {
      let apiError: ApiError;
      try {
        const body = await response.json();
        assertCurrentSession(scope);
        apiError = {
          code: body.code ?? 'UNKNOWN',
          message: body.message ?? response.statusText,
          status: response.status,
        };
      } catch {
        assertCurrentSession(scope);
        apiError = {
          code: 'PARSE_ERROR',
          message: response.statusText || `HTTP ${response.status}`,
          status: response.status,
        };
      }
      throw new ApiResponseError(apiError, response);
    }

    if (response.status === 204) return undefined as T;
    const body = await response.json() as T;
    assertCurrentSession(scope);
    return body;
  } catch (err) {
    assertCurrentSession(scope);
    if ((err as Error).name === 'AbortError') {
      throw new Error(`Request timed out after ${timeoutMs}ms: ${path}`);
    }
    throw err;
  } finally {
    clearTimeout(timer);
    scope.signal.removeEventListener('abort', abort);
  }
}

export const apiClient = {
  get<T>(path: string, params?: Record<string, string | number | boolean | undefined>) {
    let url = path;
    if (params) {
      const q = Object.entries(params)
        .filter(([, v]) => v !== undefined)
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
        .join('&');
      if (q) url += `?${q}`;
    }
    return request<T>(url, { method: 'GET' });
  },

  post<T>(path: string, body?: unknown) {
    return request<T>(path, {
      method: 'POST',
      body: body != null ? JSON.stringify(body) : undefined,
    });
  },
};

export { API_BASE };
