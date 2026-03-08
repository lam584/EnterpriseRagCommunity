import { render, screen, within, cleanup, act } from '@testing-library/react';
import { describe, it, vi, beforeEach, afterEach } from 'vitest';
import LlmQueueForm from './llm-queue';

vi.mock('./llm-loadtest', () => ({
  default: () => null,
}));

const adminGetLlmQueueStatus = vi.fn();
const adminGetLlmQueueTaskDetail = vi.fn();

vi.mock('../../../../services/llmQueueAdminService', () => ({
  adminGetLlmQueueStatus: (...args: unknown[]) => adminGetLlmQueueStatus(...args),
  adminGetLlmQueueTaskDetail: (...args: unknown[]) => adminGetLlmQueueTaskDetail(...args),
  adminUpdateLlmQueueConfig: () => Promise.resolve({ maxConcurrent: 1, maxQueueSize: 0, keepCompleted: 0 }),
}));

vi.mock('../../../../services/llmRoutingAdminService', () => ({
  adminGetLlmRoutingConfig: () => Promise.resolve({ scenarios: [] }),
}));

vi.mock('../../../../services/aiProvidersAdminService', () => ({
  adminGetAiProvidersConfig: () => Promise.resolve({ providers: [] }),
}));

async function flush(times = 10) {
  await act(async () => {
    for (let i = 0; i < times; i++) await Promise.resolve();
  });
}

async function eventually<T>(getValue: () => T | null | undefined, tries = 50) {
  for (let i = 0; i < tries; i++) {
    const v = getValue();
    if (v) return v;
    await flush(2);
  }
  throw new Error('eventually() failed');
}

describe('LlmQueueForm detail auto refresh', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
  });

  it(
    'polls task detail while status is RUNNING and updates to DONE',
    async () => {
    const nowIso = new Date('2026-01-01T00:00:00.000Z').toISOString();

    adminGetLlmQueueStatus.mockResolvedValue({
      maxConcurrent: 1,
      runningCount: 0,
      pendingCount: 0,
      running: [],
      pending: [],
      recentCompleted: [
        {
          id: 't-1',
          seq: 1,
          priority: 0,
          type: 'MODERATION',
          status: 'FAILED',
          providerId: null,
          model: null,
          createdAt: nowIso,
          startedAt: nowIso,
          finishedAt: nowIso,
          waitMs: 0,
          durationMs: 0,
          tokensIn: null,
          tokensOut: null,
          totalTokens: null,
          tokensPerSec: null,
          error: null,
        },
      ],
      samples: [],
    });

    adminGetLlmQueueTaskDetail
      .mockResolvedValueOnce({
        id: 't-1',
        seq: 1,
        priority: 0,
        type: 'MODERATION',
        status: 'RUNNING',
        providerId: null,
        model: null,
        createdAt: nowIso,
        startedAt: nowIso,
        finishedAt: null,
        waitMs: 0,
        durationMs: null,
        tokensIn: null,
        tokensOut: null,
        totalTokens: null,
        tokensPerSec: null,
        error: null,
        input: '',
        output: 'progress 24/27',
      })
      .mockResolvedValueOnce({
        id: 't-1',
        seq: 1,
        priority: 0,
        type: 'MODERATION',
        status: 'DONE',
        providerId: null,
        model: null,
        createdAt: nowIso,
        startedAt: nowIso,
        finishedAt: nowIso,
        waitMs: 0,
        durationMs: 1000,
        tokensIn: 10,
        tokensOut: 20,
        totalTokens: 30,
        tokensPerSec: 10,
        error: null,
        input: '',
        output: 'progress 27/27',
      });

    render(<LlmQueueForm />);

    await flush();
    const detailBtn = await eventually(() => screen.queryByRole('button', { name: '详情' }));
    detailBtn.click();

    const dialog = await eventually(() => screen.queryByRole('dialog'));
    await eventually(() => within(dialog).queryByText('RUNNING'));

    await act(async () => {
      vi.advanceTimersByTime(5000);
    });
    await flush();

    await eventually(() => within(dialog).queryByText('DONE'));
    },
    20000
  );
});
