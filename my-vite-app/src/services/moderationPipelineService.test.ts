import { describe, expect, it, vi, beforeEach } from 'vitest';
import { adminGetLatestPipelineByQueueId } from './moderationPipelineService';

describe('moderationPipelineService', () => {
  beforeEach(() => {
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
});
