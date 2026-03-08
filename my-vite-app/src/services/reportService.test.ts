import { beforeEach, describe, expect, it, vi } from 'vitest';
import { reportComment, reportPost, reportProfile } from './reportService';
import { mockFetch, mockFetchJsonOnce, mockFetchResponseOnce } from '../testUtils/mockFetch';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('reportService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('reportPost posts payload with csrf header and returns response', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({
      ok: true,
      json: async () => ({ reportId: 1, queueId: 2 }),
    });

    const res = await reportPost(10, { reasonCode: 'SPAM', reasonText: 'x' });
    expect(res).toEqual({ reportId: 1, queueId: 2 });

    const call = (fetchMock as any).mock.calls[0];
    expect(String(call?.[0] || '')).toContain('/api/posts/10/report');
    expect(call?.[1]?.method).toBe('POST');
    expect(call?.[1]?.credentials).toBe('include');
    expect(call?.[1]?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(call?.[1]?.body).toBe(JSON.stringify({ reasonCode: 'SPAM', reasonText: 'x' }));
  });

  it('reportPost throws login message on 401', async () => {
    mockFetchResponseOnce({ ok: false, status: 401, json: {} });
    await expect(reportPost(10, { reasonCode: 'SPAM' })).rejects.toThrow('请先登录后再举报');
  });

  it('reportComment throws login message on 401', async () => {
    mockFetchResponseOnce({ ok: false, status: 401, json: {} });
    await expect(reportComment(1, { reasonCode: 'SPAM' })).rejects.toThrow('请先登录后再举报');
  });

  it('reportProfile throws login message on 401', async () => {
    mockFetchResponseOnce({ ok: false, status: 401, json: {} });
    await expect(reportProfile(1, { reasonCode: 'SPAM' })).rejects.toThrow('请先登录后再举报');
  });

  it('reportComment throws backend message on non-401 failure', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'bad' } });
    await expect(reportComment(1, { reasonCode: 'SPAM' })).rejects.toThrow('bad');
  });

  it('reportPost falls back to default message when backend message is not string', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 1 } });
    await expect(reportPost(10, { reasonCode: 'SPAM' })).rejects.toThrow('举报失败');
  });

  it('reportProfile falls back to default message when response json fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(reportProfile(1, { reasonCode: 'SPAM' })).rejects.toThrow('举报失败');
  });
});
