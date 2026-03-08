import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchQueue, mockFetchRejectOnce, mockFetchResponseOnce } from '../testUtils/mockFetch';
import { mockConsole } from '../testUtils/mockConsole';

describe('csrfUtils', () => {
  let restoreConsole: (() => void) | null = null;

  beforeEach(() => {
    vi.resetAllMocks();
    vi.resetModules();
    restoreConsole = null;
  });

  afterEach(() => {
    if (restoreConsole) restoreConsole();
  });

  it('getCsrfToken caches token by default', async () => {
    restoreConsole = mockConsole(['log', 'error']).restore;
    const fetchMock = mockFetchQueue([{ ok: true, json: { token: 'a' } }]);

    const { getCsrfToken } = await import('./csrfUtils');
    await expect(getCsrfToken()).resolves.toBe('a');
    await expect(getCsrfToken()).resolves.toBe('a');

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/auth/csrf-token');
    const init = fetchMock.mock.calls[0]?.[1] as { credentials?: unknown } | undefined;
    expect(init?.credentials).toBe('include');
  });

  it('getCsrfToken(forceRefresh=true) bypasses cache', async () => {
    restoreConsole = mockConsole(['log', 'error']).restore;
    const fetchMock = mockFetchQueue([
      { ok: true, json: { token: 'a' } },
      { ok: true, json: { token: 'b' } },
    ]);

    const { getCsrfToken } = await import('./csrfUtils');
    await expect(getCsrfToken()).resolves.toBe('a');
    await expect(getCsrfToken(true)).resolves.toBe('b');

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('clearCsrfToken clears cache and forces next call to refetch', async () => {
    restoreConsole = mockConsole(['log', 'error']).restore;
    const fetchMock = mockFetchQueue([
      { ok: true, json: { token: 'a' } },
      { ok: true, json: { token: 'b' } },
    ]);

    const { getCsrfToken, clearCsrfToken } = await import('./csrfUtils');
    await expect(getCsrfToken()).resolves.toBe('a');
    clearCsrfToken();
    await expect(getCsrfToken()).resolves.toBe('b');

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('getCsrfToken throws a generic error when response is not ok and does not cache', async () => {
    restoreConsole = mockConsole(['log', 'error']).restore;
    const fetchMock = mockFetchQueue([
      { ok: false, status: 500, json: { token: 'ignored' } },
      { ok: true, json: { token: 'a' } },
    ]);

    const { getCsrfToken } = await import('./csrfUtils');
    await expect(getCsrfToken()).rejects.toThrow('无法获取安全令牌');
    await expect(getCsrfToken()).resolves.toBe('a');

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('getCsrfToken throws a generic error when token is missing', async () => {
    restoreConsole = mockConsole(['log', 'error']).restore;
    mockFetchResponseOnce({ ok: true, json: {} });
    const { getCsrfToken } = await import('./csrfUtils');
    await expect(getCsrfToken()).rejects.toThrow('无法获取安全令牌');
  });

  it('getCsrfToken throws a generic error when fetch rejects', async () => {
    restoreConsole = mockConsole(['log', 'error']).restore;
    mockFetchRejectOnce(new Error('net'));
    const { getCsrfToken } = await import('./csrfUtils');
    await expect(getCsrfToken()).rejects.toThrow('无法获取安全令牌');
  });
});
