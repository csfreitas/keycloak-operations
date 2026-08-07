import type { ApiError } from './types';

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

let _bearerToken: string | null = null;

export function setAuthToken(token: string | null) {
  _bearerToken = token;
}

function buildHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };
  if (_bearerToken) {
    headers['Authorization'] = `Bearer ${_bearerToken}`;
  }
  return headers;
}

async function request<T>(
  path: string,
  options?: RequestInit,
  timeoutMs = DEFAULT_TIMEOUT_MS,
): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  const url = path.startsWith('http') ? path : `${API_BASE}${path}`;

  let response: Response;
  try {
    response = await fetch(url, {
      ...options,
      headers: {
        ...buildHeaders(),
        ...(options?.headers as Record<string, string> | undefined),
      },
      signal: controller.signal,
    });
  } catch (err) {
    clearTimeout(timer);
    if ((err as Error).name === 'AbortError') {
      throw new Error(`Request timed out after ${timeoutMs}ms: ${path}`);
    }
    throw err;
  }
  clearTimeout(timer);

  if (!response.ok) {
    let apiError: ApiError;
    try {
      const body = await response.json();
      apiError = {
        code: body.code ?? 'UNKNOWN',
        message: body.message ?? response.statusText,
        status: response.status,
      };
    } catch {
      apiError = {
        code: 'PARSE_ERROR',
        message: response.statusText || `HTTP ${response.status}`,
        status: response.status,
      };
    }
    throw new ApiResponseError(apiError, response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
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
