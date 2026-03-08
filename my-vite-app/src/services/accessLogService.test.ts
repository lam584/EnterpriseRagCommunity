import { beforeEach, describe, expect, it, vi } from 'vitest';
import { adminExportAccessLogsCsv, adminGetAccessLogDetail, adminListAccessLogs } from './accessLogService';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('accessLogService', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    (globalThis as any).fetch = vi.fn();
  });

  it('adminListAccessLogs builds query with defaults and skips empty fields', async () => {
    const fetchSpy = fetch as any;
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      json: async () => ({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 }),
    } as any);
    await expect(adminListAccessLogs({ page: 2, pageSize: 10, keyword: 'k', username: '' as any })).resolves.toMatchObject({ totalElements: 0 });
    const url = String(fetchSpy.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/access-logs?');
    expect(url).toContain('page=2');
    expect(url).toContain('pageSize=10');
    expect(url).toContain('keyword=k');
    expect(url).toContain('sort=createdAt%2Cdesc');
    expect(url).not.toContain('username=');
  });

  it('adminListAccessLogs throws fallback message when backend message missing and json parse fails', async () => {
    const fetchSpy = fetch as any;
    fetchSpy.mockResolvedValueOnce({
      ok: false,
      json: async () => {
        throw new Error('bad');
      },
    } as any);
    await expect(adminListAccessLogs()).rejects.toThrow('获取访问日志失败');
  });

  it('adminGetAccessLogDetail returns dto and throws backend message', async () => {
    const fetchSpy = fetch as any;
    fetchSpy
      .mockResolvedValueOnce({ ok: true, json: async () => ({ id: 1, createdAt: 't' }) } as any)
      .mockResolvedValueOnce({ ok: false, json: async () => ({ message: 'bad' }) } as any);

    await expect(adminGetAccessLogDetail(1)).resolves.toMatchObject({ id: 1 });
    await expect(adminGetAccessLogDetail(2)).rejects.toThrow('bad');
  });

  it('adminGetAccessLogDetail throws fallback message when json parsing fails and backend message missing', async () => {
    const fetchSpy = fetch as any;
    fetchSpy.mockResolvedValueOnce({
      ok: false,
      json: async () => {
        throw new Error('bad');
      },
    } as any);
    await expect(adminGetAccessLogDetail(1)).rejects.toThrow('获取日志详情失败');
  });

  it('adminExportAccessLogsCsv returns blob and throws fallback message', async () => {
    const fetchSpy = fetch as any;
    fetchSpy.mockResolvedValueOnce({
      ok: true,
      blob: async () => new Blob(['x'], { type: 'text/csv' }),
    } as any);
    const blob = await adminExportAccessLogsCsv({ keyword: 'k' });
    expect(blob.size).toBeGreaterThan(0);
    expect(fetchSpy.mock.calls[0]?.[1]).toMatchObject({ method: 'POST', credentials: 'include', headers: { 'X-XSRF-TOKEN': 'csrf' } });

    fetchSpy.mockResolvedValueOnce({
      ok: false,
      json: async () => {
        throw new Error('bad');
      },
    } as any);
    await expect(adminExportAccessLogsCsv({})).rejects.toThrow('导出失败');
  });
});
