import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetch, mockFetchResponseOnce } from '../testUtils/mockFetch';
import {
  adminCreateBoard,
  adminDeleteBoard,
  adminListBoards,
  adminSearchBoards,
  adminUpdateBoard,
  createBoard,
  deleteBoard,
  getBoardAccessControl,
  listBoards,
  searchBoards,
  updateBoard,
  updateBoardAccessControl,
} from './boardService';
 
vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf-token'),
  };
});
 
describe('boardService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });
 
  it('createBoard sends POST with csrf header and json body', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { id: 1, name: 'n' } });
 
    const payload = { name: 'n', description: 'd', visible: true };
    const res = await createBoard(payload);
 
    expect(res).toMatchObject({ id: 1, name: 'n' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/boards');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
      body: JSON.stringify(payload),
    });
  });
 
  it('createBoard throws message and attaches fieldErrors when not ok', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad', name: 'required' } });
 
    try {
      await createBoard({ name: '' });
      expect.unreachable();
    } catch (e) {
      expect(e).toBeInstanceOf(Error);
      expect((e as Error).message).toBe('bad');
      expect((e as { fieldErrors?: unknown }).fieldErrors).toEqual({ message: 'bad', name: 'required' });
    }
  });
 
  it('searchBoards builds query string, passes signal, and normalizes dto fields', async () => {
    const ac = new AbortController();
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        content: [
          { id: '1', tenantId: '2', parentId: null, name: 'x', visible: 'true', sortOrder: '3' },
        ],
      },
    });
 
    const res = await searchBoards({ nameLike: 'k', page: 2, pageSize: 10 }, { signal: ac.signal });
 
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/boards?');
    expect(url).toContain('nameLike=k');
    expect(url).toContain('page=2');
    expect(url).toContain('pageSize=10');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include', signal: ac.signal });
    expect(res[0]).toEqual({
      id: 1,
      tenantId: 2,
      parentId: undefined,
      name: 'x',
      description: undefined,
      visible: true,
      sortOrder: 3,
      createdAt: undefined,
      updatedAt: undefined,
    });
  });

  it('searchBoards buildQueryString filters empty but keeps false/0', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { content: [] } });
    await expect(searchBoards({ nameLike: '', visible: false, sortOrder: 0, page: 1, pageSize: 25 })).resolves.toEqual([]);
    const url = String(fetchMock.mock.calls[0]?.[0] || '');
    expect(url).toContain('visible=false');
    expect(url).toContain('sortOrder=0');
    expect(url).not.toContain('nameLike=');
  });

  it('searchBoards filters out empty string query params', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { content: [] } });
    await expect(searchBoards({ nameLike: '', page: 1, pageSize: 25 })).resolves.toEqual([]);
    const url = String(fetchMock.mock.calls[0]?.[0] || '');
    expect(url).toContain('/api/boards?');
    expect(url).not.toContain('nameLike=');
  });
 
  it('updateBoardAccessControl normalizes id lists in request and response', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        boardId: '9',
        viewRoleIds: ['1', '1', 2],
        postRoleIds: [-1, '3', 3],
        moderatorUserIds: ['x', 5, '5'],
      },
    });
 
    const res = await updateBoardAccessControl(9, {
      viewRoleIds: [1, 1, 2, 0, -1],
      postRoleIds: [3, 3, 0, -9],
      moderatorUserIds: [5, 5, 0],
    });
 
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/admin/boards/9/access-control');
    const init = fetchMock.mock.calls[0]?.[1] as { body?: unknown; headers?: Record<string, unknown> };
    const body = JSON.parse(String(init.body));
    expect(body).toEqual({ boardId: 9, viewRoleIds: [1, 2], postRoleIds: [3], moderatorUserIds: [5] });
    expect(init.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' });
 
    expect(res).toEqual({ boardId: 9, viewRoleIds: [1, 2], postRoleIds: [3], moderatorUserIds: [5] });
  });
 
  it('getBoardAccessControl throws backend message and falls back when missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 403, json: {} });
    await expect(getBoardAccessControl(9)).rejects.toThrow('加载权限失败');
  });
 
  it('searchBoards throws when http not ok', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(searchBoards({ page: 1, pageSize: 25 })).rejects.toThrow('查询失败');
  });
 
  it('createBoard includes credentials include', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({ ok: true, json: async () => ({ id: 1, name: 'n' }) });
    await createBoard({ name: 'n' });
    expect((fetchMock as any).mock.calls[0]?.[1]?.credentials).toBe('include');
  });

  it('searchBoards returns [] when backend content is not an array and supports array payloads', async () => {
    mockFetchResponseOnce({ ok: true, json: { content: { bad: true } } });
    await expect(searchBoards({ page: 1, pageSize: 25 })).resolves.toEqual([]);

    mockFetchResponseOnce({ ok: true, json: [{ id: '1', name: 'x' }] });
    await expect(searchBoards({ page: 1, pageSize: 25 })).resolves.toMatchObject([{ id: 1, name: 'x' }]);
  });

  it('searchBoards buildQueryString filters undefined/null/empty string but keeps 0/false', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { content: [] } });
    await expect(
      searchBoards({
        nameLike: '',
        description: undefined,
        createdFrom: null as any,
        page: 0,
        visible: false,
        sortOrderFrom: 0,
      } as any),
    ).resolves.toEqual([]);

    const url = String(fetchMock.mock.calls[0]?.[0] || '');
    expect(url).toContain('/api/boards?');
    expect(url).not.toContain('nameLike=');
    expect(url).not.toContain('description=');
    expect(url).not.toContain('createdFrom=');
    expect(url).toContain('page=0');
    expect(url).toContain('visible=false');
    expect(url).toContain('sortOrderFrom=0');
  });

  it('searchBoards normalizes boolean/number fields with multiple input shapes', async () => {
    mockFetchResponseOnce({
      ok: true,
      json: {
        content: [
          { id: '1', name: 'a', visible: 'yes', sortOrder: '3', tenantId: '2', parentId: '' },
          { id: 2, name: 'b', visible: 'no', sortOrder: 'x', tenantId: null, parentId: 9 },
          { id: 3, name: 'c', visible: 0, sortOrder: 0, tenantId: 0, parentId: 0 },
          { id: 4, name: 'd', visible: 'maybe' },
        ],
      },
    });
    const res = await searchBoards({ page: 1, pageSize: 25 });
    expect(res[0]).toMatchObject({ id: 1, visible: true, sortOrder: 3, tenantId: 2, parentId: undefined });
    expect(res[1]).toMatchObject({ id: 2, visible: false, sortOrder: undefined, tenantId: undefined, parentId: 9 });
    expect(res[2]).toMatchObject({ id: 3, visible: false, sortOrder: 0, tenantId: 0, parentId: 0 });
    expect(res[3]).toMatchObject({ id: 4, visible: undefined });
  });

  it('listBoards uses default paging and calls boards endpoint', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { content: [] } });
    await expect(listBoards()).resolves.toEqual([]);
    const url = String(fetchMock.mock.calls[0]?.[0] || '');
    expect(url).toContain('/api/boards?');
    expect(url).toContain('page=1');
    expect(url).toContain('pageSize=25');
  });

  it('updateBoard and deleteBoard throw fallback messages when response json fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(updateBoard({ id: 1, name: 'n' })).rejects.toThrow('更新失败');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(deleteBoard(1)).rejects.toThrow('删除失败');
  });

  it('adminSearchBoards uses backend message when present and falls back otherwise', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminSearchBoards({ page: 1, pageSize: 25 })).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 123 } });
    await expect(adminSearchBoards({ page: 1, pageSize: 25 })).rejects.toThrow('查询失败');
  });

  it('adminSearchBoards falls back when error json parsing fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('boom') });
    await expect(adminSearchBoards({ page: 1, pageSize: 25 })).rejects.toThrow('查询失败');
  });

  it('adminListBoards uses default sort and paging', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { content: [] } });
    await expect(adminListBoards()).resolves.toEqual([]);
    const url = String(fetchMock.mock.calls[0]?.[0] || '');
    expect(url).toContain('/api/admin/boards?');
    expect(url).toContain('page=1');
    expect(url).toContain('pageSize=50');
    expect(url).toContain('sortBy=sortOrder');
    expect(url).toContain('sortOrderDirection=asc');
  });

  it('getBoardAccessControl normalizes id lists and falls back to param boardId', async () => {
    mockFetchResponseOnce({ ok: true, json: { viewRoleIds: 'bad', postRoleIds: [0, '2', 2], moderatorUserIds: undefined } });
    await expect(getBoardAccessControl(9)).resolves.toEqual({ boardId: 9, viewRoleIds: [], postRoleIds: [2], moderatorUserIds: [] });
  });

  it('adminCreate/update/delete cover success and error branches', async () => {
    mockFetchResponseOnce({ ok: true, json: { id: 1, name: 'n' } });
    await expect(adminCreateBoard({ name: 'n' } as any)).resolves.toMatchObject({ id: 1 });

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad', name: 'required' } });
    await adminCreateBoard({ name: '' } as any).catch((e: any) => {
      expect(e?.message).toBe('bad');
      expect(e?.fieldErrors).toMatchObject({ message: 'bad', name: 'required' });
    });

    mockFetchResponseOnce({ ok: true, json: { id: 2, name: 'n2' } });
    await expect(adminUpdateBoard({ id: 2, name: 'n2' } as any)).resolves.toMatchObject({ id: 2 });

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminUpdateBoard({ id: 2, name: 'n2' } as any)).rejects.toThrow('更新失败');

    mockFetchResponseOnce({ ok: true, status: 200 });
    await expect(adminDeleteBoard(2)).resolves.toBeUndefined();

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'no' } });
    await expect(adminDeleteBoard(2)).rejects.toThrow('no');
  });

  it('updateBoard resolves on ok and deleteBoard resolves on ok', async () => {
    mockFetchResponseOnce({ ok: true, json: { id: 1, name: 'n' } });
    await expect(updateBoard({ id: 1, name: 'n' } as any)).resolves.toMatchObject({ id: 1 });

    mockFetchResponseOnce({ ok: true, status: 200 });
    await expect(deleteBoard(1)).resolves.toBeUndefined();
  });

  it('createBoard and adminCreateBoard fall back when error json parsing fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(createBoard({ name: 'n' } as any)).rejects.toMatchObject({ message: '创建失败', fieldErrors: {} });

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminCreateBoard({ name: 'n' } as any)).rejects.toMatchObject({ message: '创建失败', fieldErrors: {} });
  });

  it('adminDeleteBoard falls back when error json parsing fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminDeleteBoard(1)).rejects.toThrow('删除失败');
  });

  it('searchBoards returns [] when backend returns empty object (content undefined)', async () => {
    mockFetchResponseOnce({ ok: true, json: {} });
    await expect(searchBoards({ page: 1, pageSize: 25 })).resolves.toEqual([]);
  });

  it('searchBoards normalizes boolean branch and handles non-string non-number visible', async () => {
    mockFetchResponseOnce({
      ok: true,
      json: {
        content: [
          { id: '1', name: 'a', visible: true, sortOrder: '1' },
          { id: '2', name: 'b', visible: {}, sortOrder: '2' },
          null,
        ],
      },
    });
    const res = await searchBoards({ page: 1, pageSize: 25 });
    expect(res[0]).toMatchObject({ id: 1, visible: true, sortOrder: 1 });
    expect(res[1]).toMatchObject({ id: 2, visible: undefined, sortOrder: 2 });
    expect(Number.isNaN(res[2]?.id)).toBe(true);
  });

  it('getBoardAccessControl normalizes id lists including null/undefined items', async () => {
    mockFetchResponseOnce({ ok: true, json: { boardId: 9, viewRoleIds: [null, undefined, '1', 2], postRoleIds: [undefined, '3'], moderatorUserIds: [null, '5'] } });
    await expect(getBoardAccessControl(9)).resolves.toEqual({ boardId: 9, viewRoleIds: [1, 2], postRoleIds: [3], moderatorUserIds: [5] });
  });

  it('adminSearchBoards supports array payloads and non-array content payloads', async () => {
    mockFetchResponseOnce({ ok: true, json: [{ id: '1', name: 'x' }] });
    await expect(adminSearchBoards({ page: 1, pageSize: 25 })).resolves.toMatchObject([{ id: 1, name: 'x' }]);

    mockFetchResponseOnce({ ok: true, json: { content: { bad: true } } });
    await expect(adminSearchBoards({ page: 1, pageSize: 25 })).resolves.toEqual([]);
  });

  it('updateBoardAccessControl throws backend message and falls back on json parse failure', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(updateBoardAccessControl(9, { viewRoleIds: [], postRoleIds: [], moderatorUserIds: [] })).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(updateBoardAccessControl(9, { viewRoleIds: [], postRoleIds: [], moderatorUserIds: [] })).rejects.toThrow('保存权限失败');
  });
});
