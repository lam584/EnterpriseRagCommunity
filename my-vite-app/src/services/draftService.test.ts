import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

describe('draftService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    getCsrfTokenMock.mockResolvedValue('csrf');
  });

  it('createEmptyDraft uses defaults and applies init overrides', async () => {
    const { createEmptyDraft } = await import('./draftService');
    const d1 = createEmptyDraft();
    expect(d1).toMatchObject({ id: '0', boardId: 1, title: '', content: '', contentFormat: 'MARKDOWN', tags: [], attachments: [] });
    expect(typeof d1.createdAt).toBe('string');
    expect(typeof d1.updatedAt).toBe('string');

    const d2 = createEmptyDraft({ boardId: 2, title: 't', content: 'c', contentFormat: 'HTML', tags: ['a'] });
    expect(d2).toMatchObject({ id: '0', boardId: 2, title: 't', content: 'c', contentFormat: 'HTML', tags: ['a'] });
  });

  it('listDrafts maps paged content array and metadata fields', async () => {
    const { listDrafts } = await import('./draftService');
    mockFetchResponseOnce({
      ok: true,
      json: {
        content: [
          {
            id: 1,
            boardId: 2,
            title: 't',
            content: 'c',
            contentFormat: 'MARKDOWN',
            metadata: {
              tags: ['x'],
              attachments: [{ id: 10, fileName: 'f', fileUrl: 'u', fileSize: 1, mimeType: 'm' }],
            },
            createdAt: '2020-01-01T00:00:00.000Z',
            updatedAt: '2020-01-02T00:00:00.000Z',
          },
        ],
      },
    });

    const res = await listDrafts();
    expect(res).toHaveLength(1);
    expect(res[0]).toMatchObject({
      id: '1',
      boardId: 2,
      title: 't',
      content: 'c',
      contentFormat: 'MARKDOWN',
      tags: ['x'],
      attachments: [{ id: 10, fileName: 'f', fileUrl: 'u', fileSize: 1, mimeType: 'm' }],
      createdAt: '2020-01-01T00:00:00.000Z',
      updatedAt: '2020-01-02T00:00:00.000Z',
    });
  });

  it('listDrafts supports backend returning array directly', async () => {
    const { listDrafts } = await import('./draftService');
    mockFetchResponseOnce({ ok: true, json: [{ id: 2, boardId: 1, title: 't', content: 'c', contentFormat: 'MARKDOWN', metadata: {} }] });
    const res = await listDrafts();
    expect(res).toHaveLength(1);
    expect(res[0]?.id).toBe('2');
  });

  it('listDrafts returns empty array when response shape is unexpected', async () => {
    const { listDrafts } = await import('./draftService');
    mockFetchResponseOnce({ ok: true, json: {} });
    await expect(listDrafts()).resolves.toEqual([]);
  });

  it('listDrafts throws backend message or fallback on failure', async () => {
    const { listDrafts } = await import('./draftService');
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(listDrafts()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(listDrafts()).rejects.toThrow('加载草稿失败');
  });

  it('getDraft returns null on 404 and throws on other errors', async () => {
    const { getDraft } = await import('./draftService');
    mockFetchResponseOnce({ ok: false, status: 404, json: {} });
    await expect(getDraft('1')).resolves.toBeNull();

    mockFetchResponseOnce({ ok: false, status: 500, json: { message: 'bad' } });
    await expect(getDraft('1')).rejects.toThrow('bad');
  });

  it('getDraft falls back createdAt/updatedAt when backend fields missing', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2020-01-01T00:00:00.000Z'));
    const { getDraft } = await import('./draftService');
    mockFetchResponseOnce({ ok: true, json: { id: 1, boardId: 1, title: 't', content: 'c', metadata: {} } });
    const res = await getDraft('1');
    expect(res).toMatchObject({ createdAt: '2020-01-01T00:00:00.000Z', updatedAt: '2020-01-01T00:00:00.000Z' });
    vi.useRealTimers();
  });

  it('upsertDraft uses POST for id=0 and PUT for valid id', async () => {
    const { upsertDraft, createEmptyDraft } = await import('./draftService');

    const fetchMock1 = mockFetchResponseOnce({ ok: true, json: { id: 3, boardId: 1, title: 't', content: 'c', metadata: {} } });
    const d1 = createEmptyDraft({ boardId: 1, title: 't', content: 'c' });
    const res1 = await upsertDraft(d1);
    expect(res1.id).toBe('3');
    expect(fetchMock1.mock.calls[0]?.[0]).toBe('/api/post-drafts');
    expect(fetchMock1.mock.calls[0]?.[1]).toMatchObject({ method: 'POST', headers: { 'X-XSRF-TOKEN': 'csrf' } });

    const fetchMock2 = mockFetchResponseOnce({ ok: true, json: { id: 4, boardId: 1, title: 't', content: 'c', metadata: {} } });
    const d2 = { ...d1, id: '4' };
    const res2 = await upsertDraft(d2);
    expect(res2.id).toBe('4');
    expect(fetchMock2.mock.calls[0]?.[0]).toBe('/api/post-drafts/4');
    expect(fetchMock2.mock.calls[0]?.[1]).toMatchObject({ method: 'PUT', headers: { 'X-XSRF-TOKEN': 'csrf' } });
  });

  it('upsertDraft throws Validation failed with fieldErrors when backend returns field map', async () => {
    const { upsertDraft, createEmptyDraft } = await import('./draftService');
    mockFetchResponseOnce({ ok: false, status: 400, json: { title: 'required' } });
    const d = createEmptyDraft({ title: '' });
    await expect(upsertDraft(d)).rejects.toMatchObject({
      message: 'Validation failed',
      fieldErrors: { title: 'required' },
    });
  });

  it('upsertDraft throws fallback when response json fails', async () => {
    const { upsertDraft, createEmptyDraft } = await import('./draftService');
    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('boom') });
    const d = createEmptyDraft({ title: 't' });
    await expect(upsertDraft(d)).rejects.toThrow('保存草稿失败');
  });

  it('deleteDraft throws backend message or fallback on failure', async () => {
    const { deleteDraft } = await import('./draftService');
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(deleteDraft('1')).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 400, jsonError: new Error('boom') });
    await expect(deleteDraft('1')).rejects.toThrow('删除草稿失败');
  });

  it('deleteDraft resolves on success', async () => {
    const { deleteDraft } = await import('./draftService');
    mockFetchResponseOnce({ ok: true, status: 200 });
    await expect(deleteDraft('1')).resolves.toBeUndefined();
  });

  it('draftToPostCreateDTO maps attachment ids', async () => {
    const { draftToPostCreateDTO, createEmptyDraft } = await import('./draftService');
    const d = createEmptyDraft({
      boardId: 2,
      title: 't',
      content: 'c',
      attachments: [{ id: 1, fileName: 'f', fileUrl: 'u', fileSize: 1, mimeType: 'm' } as any],
    });
    expect(draftToPostCreateDTO(d)).toEqual({
      boardId: 2,
      title: 't',
      content: 'c',
      contentFormat: 'MARKDOWN',
      tags: [],
      attachmentIds: [1],
    });
  });
});

