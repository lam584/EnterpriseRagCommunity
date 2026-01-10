import { describe, expect, it, vi, beforeEach } from 'vitest';
import { adminListAuditLogs } from './auditLogService';

// 轻量单测：确保 query 序列化、错误消息解析不崩

describe('auditLogService', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('adminListAuditLogs builds query and returns page', async () => {
    const mockFetch = vi.spyOn(globalThis, 'fetch' as any).mockResolvedValue({
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

  it('adminListAuditLogs throws backend message', async () => {
    vi.spyOn(globalThis, 'fetch' as any).mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'bad' }),
    } as any);

    await expect(adminListAuditLogs({})).rejects.toThrow('bad');
  });
});

