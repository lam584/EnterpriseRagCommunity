import { describe, expect, it, vi, beforeEach } from 'vitest';
import { clearApiBaseUrlForTests, setApiBaseUrlForTests } from '../testUtils/serviceTestHarness';
import { adminGetLatestPipelineByQueueId, adminGetPipelineByRunId, adminListPipelineHistory, noopCsrfPing } from './moderationPipelineService';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('moderationPipelineService', () => {
  beforeEach(() => {
    clearApiBaseUrlForTests();
    // In Vitest (jsdom/node), fetch may not be typed as writable on globalThis.
    // We cast to any to keep the test simple.
    (globalThis as any).fetch = vi.fn();
  });

  it('adminGetLatestPipelineByQueueId: ok', async () => {
    (fetch as any).mockResolvedValue({
      ok: true,
      json: async () => ({ run: { id: 1, queueId: 2 }, steps: [] }),
    });

    const res = await adminGetLatestPipelineByQueueId(2);
    expect(res.run?.id).toBe(1);
  });

  it('adminGetLatestPipelineByQueueId: error', async () => {
    (fetch as any).mockResolvedValue({
      ok: false,
      json: async () => ({ message: 'bad' }),
    });

    await expect(adminGetLatestPipelineByQueueId(2)).rejects.toThrow('bad');
  });

  it('adminGetLatestPipelineByQueueId: covers api base url and json parse fallback', async () => {
    setApiBaseUrlForTests('https://api.example');
    (fetch as any).mockResolvedValue({
      ok: false,
      status: 500,
      json: async () => {
        throw new Error('bad json');
      },
    });
    await expect(adminGetLatestPipelineByQueueId(2)).rejects.toThrow('获取流水线追溯信息失败');
    expect(String((fetch as any).mock.calls[0]?.[0])).toContain('https://api.example/api/admin/moderation/pipeline/latest');
  });

  it('adminListPipelineHistory builds query and returns page', async () => {
    (fetch as any).mockResolvedValue({
      ok: true,
      json: async () => ({ content: [], totalElements: 0 }),
    });
    const res = await adminListPipelineHistory({ queueId: 1, contentType: 'POST', contentId: 2, page: 3, pageSize: 10 });
    expect(res.totalElements).toBe(0);
    const url = String((fetch as any).mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/moderation/pipeline/history?');
    expect(url).toContain('queueId=1');
    expect(url).toContain('contentType=POST');
    expect(url).toContain('contentId=2');
    expect(url).toContain('page=3');
    expect(url).toContain('pageSize=10');
  });

  it('adminGetPipelineByRunId throws fallback when message missing', async () => {
    (fetch as any).mockResolvedValue({
      ok: false,
      json: async () => ({}),
    });
    await expect(adminGetPipelineByRunId(1)).rejects.toThrow('获取流水线详情失败');
  });

  it('noopCsrfPing calls csrf helper', async () => {
    await expect(noopCsrfPing()).resolves.toBeUndefined();
  });
});
