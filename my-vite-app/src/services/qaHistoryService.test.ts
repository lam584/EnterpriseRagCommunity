import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('qaHistoryService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('listQaSessions uses apiFetch with csrf header and parses json', async () => {
    const { listQaSessions } = await import('./qaHistoryService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { content: [], totalElements: 0, number: 0, size: 20 } });

    const res = await listQaSessions(0, 20);
    expect(res).toMatchObject({ content: [], totalElements: 0, number: 0, size: 20 });

    expect(String(lastCall()?.[0] || '')).toContain('/api/ai/qa/sessions?page=0&size=20');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
  });

  it('deleteQaSession returns undefined on 204 No Content', async () => {
    const { deleteQaSession } = await import('./qaHistoryService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: true, status: 204, text: '' });
    await expect(deleteQaSession(1)).resolves.toBeUndefined();
  });

  it('apiFetch throws response text on failure', async () => {
    const { getQaSessionMessages } = await import('./qaHistoryService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 400, text: 'bad' });
    await expect(getQaSessionMessages(1)).rejects.toThrow('bad');
  });

  it('apiFetch falls back to status message when response text is empty', async () => {
    const { getQaSessionMessages } = await import('./qaHistoryService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 400, text: '' });
    await expect(getQaSessionMessages(1)).rejects.toThrow('请求失败: 400');
  });

  it('apiFetch falls back to status message when response text throws', async () => {
    const { getQaSessionMessages } = await import('./qaHistoryService');
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 500, textError: new Error('bad') });
    await expect(getQaSessionMessages(1)).rejects.toThrow('请求失败: 500');
  });

  it('listFavoriteQaMessages uses default page/size', async () => {
    const { listFavoriteQaMessages } = await import('./qaHistoryService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { content: [], totalElements: 0, number: 0, size: 20 } });

    await listFavoriteQaMessages();
    expect(String(lastCall()?.[0] || '')).toContain('/api/ai/qa/favorites?page=0&size=20');
  });

  it('searchQaHistory and toggleQaMessageFavorite issue requests without crashing', async () => {
    const { searchQaHistory, toggleQaMessageFavorite } = await import('./qaHistoryService');
    const { replyOnce } = installFetchMock();

    replyOnce({ ok: true, json: { content: [], totalElements: 0, number: 0, size: 20 } });
    await searchQaHistory('q', 0, 20);

    replyOnce({ ok: true, json: true });
    await toggleQaMessageFavorite(1);
  });

  it('apiUrl 默认使用相对路径', async () => {
    const { listQaSessions } = await import('./qaHistoryService');
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({ ok: true, json: { content: [], totalElements: 0, number: 0, size: 20 } });

    await expect(listQaSessions(0, 20)).resolves.toMatchObject({ totalElements: 0 });
    expect(String(lastCall()?.[0] || '')).toContain('/api/ai/qa/sessions?page=0&size=20');
  });
});
