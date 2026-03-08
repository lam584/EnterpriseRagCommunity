import { beforeEach, describe, expect, it, vi } from 'vitest';
import { clearDraftUploadSessions, loadDraftUploadSessions, saveDraftUploadSessions, type DraftUploadSession } from './draftUploadProgressStore';

describe('draftUploadProgressStore', () => {
  const KEY = 'portal.posts.compose.draftUploadSessions.v1';

  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('saves and loads sessions by draftId', () => {
    const sessions: DraftUploadSession[] = [
      {
        uploadId: 'abc123',
        kind: 'attachment',
        fileName: 'a.bin',
        fileSize: 10,
        loaded: 3,
        total: 10,
        status: 'paused',
        updatedAt: new Date('2020-01-01T00:00:00.000Z').toISOString(),
      },
    ];
    saveDraftUploadSessions('d1', sessions);
    const loaded = loadDraftUploadSessions('d1');
    expect(loaded).toEqual(sessions);
  });

  it('dedupes sessions by uploadId', () => {
    const sessions: DraftUploadSession[] = [
      {
        uploadId: 'u1',
        kind: 'image',
        fileName: 'a.png',
        fileSize: 10,
        loaded: 1,
        total: 10,
        status: 'paused',
        updatedAt: new Date('2020-01-01T00:00:00.000Z').toISOString(),
      },
      {
        uploadId: 'u1',
        kind: 'image',
        fileName: 'a.png',
        fileSize: 10,
        loaded: 2,
        total: 10,
        status: 'paused',
        updatedAt: new Date('2020-01-01T00:00:00.000Z').toISOString(),
      },
    ];
    saveDraftUploadSessions('d1', sessions);
    const loaded = loadDraftUploadSessions('d1');
    expect(loaded.length).toBe(1);
    expect(loaded[0].uploadId).toBe('u1');
  });

  it('clears sessions', () => {
    const sessions: DraftUploadSession[] = [
      {
        uploadId: 'abc123',
        kind: 'attachment',
        fileName: 'a.bin',
        fileSize: 10,
        loaded: 3,
        total: 10,
        status: 'paused',
        updatedAt: new Date('2020-01-01T00:00:00.000Z').toISOString(),
      },
    ];
    saveDraftUploadSessions('d1', sessions);
    clearDraftUploadSessions('d1');
    expect(loadDraftUploadSessions('d1')).toEqual([]);
  });

  it('tolerates invalid JSON in storage', () => {
    localStorage.setItem(KEY, '{bad json');
    expect(loadDraftUploadSessions('d1')).toEqual([]);
  });

  it('returns empty for blank draftId', () => {
    expect(loadDraftUploadSessions('')).toEqual([]);
    expect(loadDraftUploadSessions('   ')).toEqual([]);
  });

  it('ignores saving when draftId is blank', () => {
    const sessions: DraftUploadSession[] = [
      {
        uploadId: 'u1',
        kind: 'image',
        fileName: 'a.png',
        fileSize: 10,
        loaded: 1,
        total: 10,
        status: 'paused',
        updatedAt: new Date('2020-01-01T00:00:00.000Z').toISOString(),
      },
    ];
    saveDraftUploadSessions('   ', sessions);
    expect(localStorage.getItem(KEY)).toBeNull();
  });

  it('filters invalid sessions when loading from storage', () => {
    const valid: DraftUploadSession = {
      uploadId: 'u1',
      kind: 'image',
      fileName: 'a.png',
      fileSize: 10,
      loaded: 1,
      total: 10,
      status: 'paused',
      updatedAt: new Date('2020-01-01T00:00:00.000Z').toISOString(),
    };
    localStorage.setItem(
      KEY,
      JSON.stringify({
        v: 1,
        drafts: {
          d1: {
            updatedAt: '',
            sessions: [
              null,
              {},
              { uploadId: '   ', kind: 'image', fileName: 'a.png', fileSize: 1, loaded: 0, total: 1, status: 'paused', updatedAt: valid.updatedAt },
              { uploadId: 'u2', kind: 'nope', fileName: 'a.png', fileSize: 1, loaded: 0, total: 1, status: 'paused', updatedAt: valid.updatedAt },
              { uploadId: 'u3', kind: 'image', fileName: '', fileSize: 1, loaded: 0, total: 1, status: 'paused', updatedAt: valid.updatedAt },
              { uploadId: 'u4', kind: 'image', fileName: 'a.png', fileSize: -1, loaded: 0, total: 1, status: 'paused', updatedAt: valid.updatedAt },
              { uploadId: 'u5', kind: 'image', fileName: 'a.png', fileSize: 1, loaded: -1, total: 1, status: 'paused', updatedAt: valid.updatedAt },
              { uploadId: 'u6', kind: 'image', fileName: 'a.png', fileSize: 1, loaded: 0, total: -1, status: 'paused', updatedAt: valid.updatedAt },
              { uploadId: 'u7', kind: 'image', fileName: 'a.png', fileSize: 1, loaded: 0, total: 1, status: 'nope', updatedAt: valid.updatedAt },
              { uploadId: 'u8', kind: 'image', fileName: 'a.png', fileSize: 1, loaded: 0, total: 1, status: 'paused', updatedAt: '' },
              { uploadId: 123, kind: 'image', fileName: 'a.png', fileSize: 1, loaded: 0, total: 1, status: 'paused', updatedAt: valid.updatedAt },
              { uploadId: 'u9', kind: 'image', fileName: 'a.png', fileSize: 'x', loaded: 0, total: 1, status: 'paused', updatedAt: valid.updatedAt },
              valid,
            ],
          },
        },
      }),
    );
    expect(loadDraftUploadSessions('d1')).toEqual([valid]);
  });

  it('returns empty store for incompatible or malformed store structures', () => {
    localStorage.setItem(KEY, JSON.stringify({ v: 2, drafts: { d1: { sessions: [] } } }));
    expect(loadDraftUploadSessions('d1')).toEqual([]);

    localStorage.setItem(KEY, JSON.stringify({ v: 1, drafts: null }));
    expect(loadDraftUploadSessions('d1')).toEqual([]);

    localStorage.setItem(KEY, JSON.stringify({ v: 1, drafts: { '': { sessions: [] }, d1: null, d2: 'x' } }));
    expect(loadDraftUploadSessions('d1')).toEqual([]);
  });

  it('ignores draft entries with no valid sessions', () => {
    localStorage.setItem(KEY, JSON.stringify({ v: 1, drafts: { d1: { updatedAt: 'x', sessions: [{ uploadId: '' }] } } }));
    expect(loadDraftUploadSessions('d1')).toEqual([]);
  });

  it('trims sessions to MAX_SESSIONS_PER_DRAFT', () => {
    const base: Omit<DraftUploadSession, 'uploadId'> = {
      kind: 'attachment',
      fileName: 'a.bin',
      fileSize: 10,
      loaded: 0,
      total: 10,
      status: 'paused',
      updatedAt: new Date('2020-01-01T00:00:00.000Z').toISOString(),
    };
    const many: DraftUploadSession[] = Array.from({ length: 100 }, (_, i) => ({ ...base, uploadId: `u${i}` }));
    saveDraftUploadSessions('d1', many);
    expect(loadDraftUploadSessions('d1').length).toBe(64);
  });

  it('tolerates non-array sessions input', () => {
    saveDraftUploadSessions('d1', null as any);
    expect(loadDraftUploadSessions('d1')).toEqual([]);
  });

  it('clearDraftUploadSessions is a no-op when draft does not exist', () => {
    clearDraftUploadSessions('d1');
    expect(localStorage.getItem(KEY)).toBeNull();
  });

  it('tolerates storage write errors', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('boom');
    });
    const sessions: DraftUploadSession[] = [
      {
        uploadId: 'u1',
        kind: 'image',
        fileName: 'a.png',
        fileSize: 10,
        loaded: 1,
        total: 10,
        status: 'paused',
        updatedAt: new Date('2020-01-01T00:00:00.000Z').toISOString(),
      },
    ];
    expect(() => saveDraftUploadSessions('d1', sessions)).not.toThrow();
  });

  it('deletes draft entry when saving empty or invalid sessions', () => {
    const sessions: DraftUploadSession[] = [
      {
        uploadId: 'abc123',
        kind: 'attachment',
        fileName: 'a.bin',
        fileSize: 10,
        loaded: 3,
        total: 10,
        status: 'paused',
        updatedAt: new Date('2020-01-01T00:00:00.000Z').toISOString(),
      },
    ];
    saveDraftUploadSessions('d1', sessions);
    expect(loadDraftUploadSessions('d1').length).toBe(1);
    saveDraftUploadSessions('d1', []);
    expect(loadDraftUploadSessions('d1')).toEqual([]);
  });

  it('tolerates storage read errors', () => {
    const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('boom');
    });
    expect(loadDraftUploadSessions('d1')).toEqual([]);
    spy.mockRestore();
  });
});
