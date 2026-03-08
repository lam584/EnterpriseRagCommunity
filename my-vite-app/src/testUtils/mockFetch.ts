import { vi } from 'vitest';

type JsonValue = unknown;

export type MockFetchResponseInit = {
  ok?: boolean;
  status?: number;
  statusText?: string;
  headers?: Record<string, string | undefined> | undefined;
  json?: JsonValue | (() => JsonValue);
  text?: string | (() => string);
  body?: unknown;
  jsonError?: unknown;
  textError?: unknown;
};

export type MockFetchStep = MockFetchResponseInit | { reject: unknown };

function createHeaders(init?: Record<string, string | undefined>) {
  const headers = new Headers();
  if (init) {
    for (const [k, v] of Object.entries(init)) {
      if (typeof v === 'string') {
        headers.set(k, v);
      }
    }
  }
  return headers;
}

export function createMockResponse(init: MockFetchResponseInit = {}) {
  const status = typeof init.status === 'number' ? init.status : init.ok === false ? 400 : 200;
  const ok = typeof init.ok === 'boolean' ? init.ok : status >= 200 && status < 300;
  const statusText = typeof init.statusText === 'string' ? init.statusText : ok ? 'OK' : 'Error';

  const headersRecord: Record<string, string | undefined> = { ...(init.headers || {}) };
  if (init.json !== undefined && headersRecord['content-type'] === undefined && headersRecord['Content-Type'] === undefined) {
    headersRecord['content-type'] = 'application/json';
  }
  if (init.text !== undefined && headersRecord['content-type'] === undefined && headersRecord['Content-Type'] === undefined) {
    headersRecord['content-type'] = 'text/plain; charset=utf-8';
  }
  const headers = createHeaders(headersRecord);

  const textFactory =
    typeof init.text === 'function'
      ? init.text
      : init.text !== undefined
        ? () => String(init.text)
        : init.json !== undefined
          ? () => JSON.stringify(typeof init.json === 'function' ? init.json() : init.json)
          : () => '';

  const jsonFactory =
    typeof init.json === 'function'
      ? init.json
      : init.json !== undefined
        ? () => init.json
        : () => {
            const t = textFactory();
            return t ? JSON.parse(t) : {};
          };

  const res = {
    ok,
    status,
    statusText,
    headers,
    body: init.body,
    async text() {
      if (init.textError !== undefined) throw init.textError;
      return textFactory();
    },
    async json() {
      if (init.jsonError !== undefined) throw init.jsonError;
      return jsonFactory();
    },
  };

  return res as unknown as Response;
}

export function mockFetch() {
  const fn = vi.fn();
  (globalThis as unknown as { fetch: typeof fetch }).fetch = fn as unknown as typeof fetch;
  return fn;
}

export function mockFetchJsonOnce(opts: { ok: boolean; json: JsonValue }) {
  const fn = mockFetch();
  fn.mockResolvedValueOnce(
    createMockResponse({
      ok: opts.ok,
      json: opts.json,
    }),
  );
  return fn;
}

export function mockFetchRejectOnce(error: unknown) {
  const fn = mockFetch();
  fn.mockRejectedValueOnce(error);
  return fn;
}

export function mockFetchResponseOnce(init: MockFetchResponseInit) {
  const fn = mockFetch();
  fn.mockResolvedValueOnce(createMockResponse(init));
  return fn;
}

export function mockFetchTextOnce(opts: { ok?: boolean; status?: number; statusText?: string; text: string; headers?: Record<string, string | undefined> }) {
  return mockFetchResponseOnce({
    ok: opts.ok,
    status: opts.status,
    statusText: opts.statusText,
    text: opts.text,
    headers: opts.headers,
  });
}

export function mockFetchQueue(steps: MockFetchStep[]) {
  const fn = mockFetch();
  const queue = [...steps];
  fn.mockImplementation(async () => {
    if (!queue.length) {
      throw new Error('No more mock fetch responses');
    }
    const step = queue.shift();
    if (!step) throw new Error('No more mock fetch responses');
    if ('reject' in step) {
      throw step.reject;
    }
    return createMockResponse(step);
  });
  return fn;
}
