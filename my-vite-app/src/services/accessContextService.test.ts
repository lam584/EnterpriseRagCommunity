import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { fetchAccessContext } from './accessContextService';

describe('accessContextService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('fetchAccessContext normalizes nulls and arrays', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { email: null, roles: ['ROLE_ADMIN'], permissions: ['PERM_POST'] } });

    const res = await fetchAccessContext();

    expect(res).toEqual({ email: null, roles: ['ROLE_ADMIN'], permissions: ['PERM_POST'] });
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/auth/access-context');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('fetchAccessContext maps missing fields to defaults', async () => {
    mockFetchResponseOnce({ ok: true, json: { email: undefined, roles: 'bad', permissions: null } });

    const res = await fetchAccessContext();

    expect(res).toEqual({ email: null, roles: [], permissions: [] });
  });

  it('fetchAccessContext throws default message when not ok', async () => {
    mockFetchResponseOnce({ ok: false, status: 403, json: {} });
    await expect(fetchAccessContext()).rejects.toThrow('Failed to fetch access context');
  });
});
