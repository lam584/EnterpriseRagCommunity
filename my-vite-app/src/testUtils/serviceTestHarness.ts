import { vi } from 'vitest';
import type { MockFetchResponseInit } from './mockFetch';
import { createMockResponse } from './mockFetch';

export function resetServiceTest() {
  vi.resetAllMocks();
  vi.resetModules();
}

export function setApiBaseUrlForTests(apiBaseUrl: string) {
  (globalThis as unknown as { __VITE_API_BASE_URL__?: string }).__VITE_API_BASE_URL__ = apiBaseUrl;
}

export function clearApiBaseUrlForTests() {
  delete (globalThis as unknown as { __VITE_API_BASE_URL__?: string }).__VITE_API_BASE_URL__;
}

export function installFetchMock() {
  const fetchMock = vi.fn();
  (globalThis as unknown as { fetch: typeof fetch }).fetch = fetchMock as unknown as typeof fetch;

  const replyOnce = (init: MockFetchResponseInit) => {
    fetchMock.mockResolvedValueOnce(createMockResponse(init));
    return fetchMock;
  };

  const replyJsonOnce = (opts: { ok: boolean; json: unknown; status?: number; headers?: Record<string, string | undefined> }) => {
    replyOnce({ ok: opts.ok, status: opts.status, json: opts.json, headers: opts.headers });
    return fetchMock;
  };

  const rejectOnce = (error: unknown) => {
    fetchMock.mockRejectedValueOnce(error);
    return fetchMock;
  };

  const lastCall = () => fetchMock.mock.calls.at(-1) as [RequestInfo, RequestInit?] | undefined;

  return { fetchMock, replyOnce, replyJsonOnce, rejectOnce, lastCall };
}

export function getFetchCallInfo(call?: [RequestInfo, RequestInit?]) {
  if (!call) return undefined;
  const [input, init] = call;
  const url =
    typeof input === 'string'
      ? input
      : input instanceof URL
        ? input.toString()
        : (input as any)?.url
          ? String((input as any).url)
          : String(input);
  const method = (init?.method || (input as any)?.method || 'GET') as string;
  const headers = (init?.headers || (input as any)?.headers) as HeadersInit | undefined;
  const body = (init?.body || (input as any)?.body) as unknown;
  return { url, method, headers, body, init };
}
