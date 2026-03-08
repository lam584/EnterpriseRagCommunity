import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import { clearApiBaseUrlForTests, setApiBaseUrlForTests } from '../testUtils/serviceTestHarness';
import {
  adminGetPostSummaryConfig,
  adminListPostSummaryHistory,
  adminRegeneratePostSummary,
  adminUpsertPostSummaryConfig,
} from './postSummaryAdminService';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('postSummaryAdminService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    clearApiBaseUrlForTests();
  });

  it('adminGetPostSummaryConfig returns dto and throws backend message', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true, maxContentChars: 1, promptCode: 'p' } });
    await expect(adminGetPostSummaryConfig()).resolves.toMatchObject({ promptCode: 'p' });
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetPostSummaryConfig()).rejects.toThrow('bad');
  });

  it('adminGetPostSummaryConfig uses api base url and falls back on json parse failure', async () => {
    setApiBaseUrlForTests('https://api.example');
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true, maxContentChars: 1, promptCode: 'p' } });
    await expect(adminGetPostSummaryConfig()).resolves.toMatchObject({ enabled: true });
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('https://api.example/api/admin/semantic/summary/config');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetPostSummaryConfig()).rejects.toThrow('获取帖子摘要配置失败');
  });

  it('adminUpsertPostSummaryConfig sends PUT with csrf header and throws fallback', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true, maxContentChars: 1, promptCode: 'p' } });
    await expect(
      adminUpsertPostSummaryConfig({ enabled: true, maxContentChars: 1, promptCode: 'p' }),
    ).resolves.toMatchObject({ enabled: true });
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' },
    });

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminUpsertPostSummaryConfig({ enabled: true, maxContentChars: 1, promptCode: 'p' })).rejects.toThrow('保存帖子摘要配置失败');
  });

  it('adminListPostSummaryHistory builds query and throws fallback', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { content: [], totalElements: 0, number: 0, size: 20 } });
    await expect(adminListPostSummaryHistory({ page: 1, size: 10, postId: 9 })).resolves.toMatchObject({ totalElements: 0 });
    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/semantic/summary/history?');
    expect(url).toContain('page=1');
    expect(url).toContain('size=10');
    expect(url).toContain('postId=9');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminListPostSummaryHistory({})).rejects.toThrow('获取帖子摘要生成日志失败');
  });

  it('adminRegeneratePostSummary sends POST with body and throws backend message', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: {} });
    await expect(adminRegeneratePostSummary(9)).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' },
      body: JSON.stringify({ postId: 9 }),
    });

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminRegeneratePostSummary(9)).rejects.toThrow('bad');
  });
});
