import { describe, expect, it, vi, beforeEach } from 'vitest';
import { adminExportAuditLogsCsv, adminGetAuditLogDetail, adminListAuditLogs, portalExportMyAuditLogsCsv, portalListMyAuditLogs } from './auditLogService';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

// 轻量单测：确保 query 序列化、错误消息解析不崩

describe('auditLogService', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    (globalThis as any).fetch = vi.fn();
  });

  it('adminListAuditLogs builds query and returns page', async () => {
    const mockFetch = fetch as any;
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ content: [{ id: 1, createdAt: '2025-01-01T00:00:00Z', action: 'X', entityType: 'SYSTEM' }], totalElements: 1, totalPages: 1, size: 20, number: 0 }),
    } as any);

    const res = await adminListAuditLogs({ page: 2, pageSize: 10, keyword: 'k' });

    expect(res.totalElements).toBe(1);
    expect(mockFetch).toHaveBeenCalledTimes(1);
    const url = String(mockFetch.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/audit-logs');
    expect(url).toContain('page=2');
    expect(url).toContain('pageSize=10');
    expect(url).toContain('keyword=k');
  });

  it('adminListAuditLogs skips empty query fields', async () => {
    const mockFetch = fetch as any;
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 }),
    } as any);

    await adminListAuditLogs({ keyword: '', actorName: '' as any, traceId: undefined });
    const url = String(mockFetch.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/audit-logs');
    expect(url).not.toContain('keyword=');
    expect(url).not.toContain('actorName=');
    expect(url).not.toContain('traceId=');
  });

  it('adminListAuditLogs throws backend message', async () => {
    const mockFetch = fetch as any;
    mockFetch.mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'bad' }),
    } as any);

    await expect(adminListAuditLogs({})).rejects.toThrow('bad');
  });

  it('adminListAuditLogs uses defaults and throws fallback when json parsing fails', async () => {
    const mockFetch = fetch as any;
    mockFetch
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 }),
      } as any)
      .mockResolvedValueOnce({
        ok: false,
        json: async () => {
          throw new Error('bad json');
        },
      } as any);

    await expect(adminListAuditLogs()).resolves.toMatchObject({ totalElements: 0 });
    const url = String(mockFetch.mock.calls[0]?.[0]);
    expect(url).toContain('page=1');
    expect(url).toContain('pageSize=20');
    expect(url).toContain('sort=createdAt%2Cdesc');

    await expect(adminListAuditLogs()).rejects.toThrow('获取审核日志失败');
  });

  it('adminGetAuditLogDetail returns dto', async () => {
    const mockFetch = fetch as any;
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ id: 1, createdAt: 't', action: 'X', entityType: 'SYSTEM' }),
    } as any);
    await expect(adminGetAuditLogDetail(1)).resolves.toMatchObject({ id: 1 });
  });

  it('adminGetAuditLogDetail throws fallback when json parsing fails', async () => {
    const mockFetch = fetch as any;
    mockFetch.mockResolvedValue({
      ok: false,
      json: async () => {
        throw new Error('bad json');
      },
    } as any);
    await expect(adminGetAuditLogDetail(1)).rejects.toThrow('获取日志详情失败');
  });

  it('adminExportAuditLogsCsv returns blob and sends csrf header', async () => {
    const fetchSpy = fetch as any;
    fetchSpy.mockResolvedValue({
      ok: true,
      blob: async () => new Blob(['x'], { type: 'text/csv' }),
    } as any);
    const blob = await adminExportAuditLogsCsv({ keyword: 'k' });
    expect(blob.size).toBeGreaterThan(0);
    expect(fetchSpy.mock.calls[0]?.[1]).toMatchObject({ method: 'POST', credentials: 'include', headers: { 'X-XSRF-TOKEN': 'csrf' } });
  });

  it('adminExportAuditLogsCsv throws fallback message when json parsing fails', async () => {
    const mockFetch = fetch as any;
    mockFetch.mockResolvedValue({
      ok: false,
      json: async () => {
        throw new Error('bad');
      },
    } as any);
    await expect(adminExportAuditLogsCsv({})).rejects.toThrow('导出失败');
  });

  it('adminExportAuditLogsCsv throws backend message when provided', async () => {
    const mockFetch = fetch as any;
    mockFetch.mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'bad' }),
    } as any);
    await expect(adminExportAuditLogsCsv({})).rejects.toThrow('bad');
  });

  it('portalListMyAuditLogs and portalExportMyAuditLogsCsv cover ok and error', async () => {
    const mockFetch = fetch as any;
    mockFetch
      .mockResolvedValueOnce({
      ok: true,
      json: async () => ({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0 }),
      } as any)
      .mockResolvedValueOnce({
        ok: false,
        json: async () => ({ message: 'bad' }),
      } as any);
    await expect(portalListMyAuditLogs({ page: 1, pageSize: 10 })).resolves.toMatchObject({ totalElements: 0 });
    await expect(portalExportMyAuditLogsCsv({ keyword: 'k' })).rejects.toThrow('bad');
  });

  it('portalListMyAuditLogs throws fallback and portalExportMyAuditLogsCsv returns blob', async () => {
    const mockFetch = fetch as any;
    mockFetch
      .mockResolvedValueOnce({
        ok: false,
        json: async () => {
          throw new Error('bad json');
        },
      } as any)
      .mockResolvedValueOnce({
        ok: true,
        blob: async () => new Blob(['x'], { type: 'text/csv' }),
      } as any);

    await expect(portalListMyAuditLogs()).rejects.toThrow('获取治理记录失败');
    const blob = await portalExportMyAuditLogsCsv({});
    expect(blob.size).toBeGreaterThan(0);
  });
});

