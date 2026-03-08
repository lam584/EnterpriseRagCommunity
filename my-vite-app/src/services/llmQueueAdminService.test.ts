import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

import { adminGetLlmQueueConfig, adminGetLlmQueueStatus, adminGetLlmQueueTaskDetail, adminUpdateLlmQueueConfig } from './llmQueueAdminService';

describe('llmQueueAdminService', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  it('adminGetLlmQueueStatus builds query and sends GET with credentials', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        maxConcurrent: 1,
        runningCount: 0,
        pendingCount: 0,
        running: [],
        pending: [],
        recentCompleted: [],
        samples: [],
      },
    });

    const res = await adminGetLlmQueueStatus({ windowSec: 10 });

    expect(res.maxConcurrent).toBe(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/metrics/llm-queue');
    expect(url).toContain('windowSec=10');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('adminGetLlmQueueStatus omits empty query params', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        maxConcurrent: 1,
        runningCount: 0,
        pendingCount: 0,
        running: [],
        pending: [],
        recentCompleted: [],
        samples: [],
      },
    });

    await adminGetLlmQueueStatus({});

    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/metrics/llm-queue');
    expect(url).not.toContain('?');
  });

  it('adminGetLlmQueueStatus throws backend message and falls back when missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetLlmQueueStatus({ windowSec: 1 })).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminGetLlmQueueStatus({ windowSec: 1 })).rejects.toThrow('获取 LLM 调用队列状态失败');
  });

  it('adminGetLlmQueueConfig sends GET with credentials and handles fallback error', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: { maxConcurrent: 1, maxQueueSize: 2, keepCompleted: 3 },
    });

    const res = await adminGetLlmQueueConfig();

    expect(res.maxQueueSize).toBe(2);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/admin/metrics/llm-queue/config');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminGetLlmQueueConfig()).rejects.toThrow('获取 LLM 调用队列配置失败');
  });

  it('adminUpdateLlmQueueConfig sends PUT with csrf header and body', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: { maxConcurrent: 5, maxQueueSize: 2, keepCompleted: 3 },
    });

    const res = await adminUpdateLlmQueueConfig({ maxConcurrent: 5 });

    expect(res.maxConcurrent).toBe(5);
    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/admin/metrics/llm-queue/config');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
      body: JSON.stringify({ maxConcurrent: 5 }),
    });
  });

  it('adminUpdateLlmQueueConfig stringifies empty payload when passed undefined', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: { maxConcurrent: 1, maxQueueSize: 2, keepCompleted: 3 },
    });

    await adminUpdateLlmQueueConfig(undefined as any);

    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ body: JSON.stringify({}) });
  });

  it('adminGetLlmQueueTaskDetail encodes task id and passes signal when timeout is set', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        id: 'x',
        seq: 1,
        priority: 1,
        type: 'UNKNOWN',
        status: 'DONE',
        createdAt: 't',
      },
    });

    await adminGetLlmQueueTaskDetail('a/b c', { timeoutMs: 10 });

    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/metrics/llm-queue/tasks/');
    expect(url).toContain(encodeURIComponent('a/b c'));
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
    expect((fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.signal).toBeInstanceOf(AbortSignal);
  });

  it('adminGetLlmQueueStatus removes abort listener in cleanup when outer signal is provided', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        maxConcurrent: 1,
        runningCount: 0,
        pendingCount: 0,
        running: [],
        pending: [],
        recentCompleted: [],
        samples: [],
      },
    });

    const outer = new AbortController();
    const addSpy = vi.spyOn(outer.signal, 'addEventListener');
    const removeSpy = vi.spyOn(outer.signal, 'removeEventListener');

    await adminGetLlmQueueStatus({ windowSec: 1, signal: outer.signal });

    expect(addSpy).toHaveBeenCalled();
    expect(removeSpy).toHaveBeenCalled();
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('adminGetLlmQueueStatus passes an already-aborted signal to fetch when outer signal is aborted', async () => {
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        maxConcurrent: 1,
        runningCount: 0,
        pendingCount: 0,
        running: [],
        pending: [],
        recentCompleted: [],
        samples: [],
      },
    });

    const outer = new AbortController();
    outer.abort();

    await adminGetLlmQueueStatus({ signal: outer.signal });

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit | undefined;
    expect(init?.signal).toBeInstanceOf(AbortSignal);
    expect((init?.signal as AbortSignal | undefined)?.aborted).toBe(true);
  });

  it('adminUpdateLlmQueueConfig throws fallback message when backend message is missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminUpdateLlmQueueConfig({ maxConcurrent: 1 })).rejects.toThrow('更新 LLM 调用队列配置失败');
  });

  it('adminGetLlmQueueTaskDetail throws backend message and falls back on json parse failure', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetLlmQueueTaskDetail('x')).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminGetLlmQueueTaskDetail('x')).rejects.toThrow('获取任务详情失败');
  });
});
