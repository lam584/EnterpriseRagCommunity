import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { listMyModeratedBoards } from './moderatorBoardsService';

describe('moderatorBoardsService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('listMyModeratedBoards returns array on success', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: [{ id: 1, name: 'b' }] });

    const res = await listMyModeratedBoards();

    expect(res).toEqual([{ id: 1, name: 'b' }]);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/moderator/boards');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('listMyModeratedBoards falls back to empty array when data is not array', async () => {
    mockFetchResponseOnce({ ok: true, json: { id: 1 } });

    const res = await listMyModeratedBoards();

    expect(res).toEqual([]);
  });

  it('listMyModeratedBoards throws backend message or fallback', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(listMyModeratedBoards()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(listMyModeratedBoards()).rejects.toThrow('获取版主版块失败');
  });

  it('listMyModeratedBoards covers json parse fallback', async () => {
    mockFetchResponseOnce({ ok: true, status: 200, jsonError: new Error('bad json') });
    await expect(listMyModeratedBoards()).resolves.toEqual([]);

    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(listMyModeratedBoards()).rejects.toThrow('获取版主版块失败');
  });
});
