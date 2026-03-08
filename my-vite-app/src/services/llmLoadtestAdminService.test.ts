import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockConsole } from '../testUtils/mockConsole';
import { mockFetchRejectOnce, mockFetchResponseOnce } from '../testUtils/mockFetch';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

import {
  adminDeleteLlmLoadTestHistory,
  adminGetLlmLoadTestExportUrl,
  adminGetLlmLoadTestStatus,
  adminListLlmLoadTestHistory,
  adminStartLlmLoadTest,
  adminStopLlmLoadTest,
  adminUpsertLlmLoadTestHistory,
} from './llmLoadtestAdminService';

describe('llmLoadtestAdminService', () => {
  let consoleMock: ReturnType<typeof mockConsole>;

  beforeEach(() => {
    vi.restoreAllMocks();
    consoleMock = mockConsole();
    getCsrfTokenMock.mockReset();
    getCsrfTokenMock.mockResolvedValue('csrf-token');
  });

  afterEach(() => {
    consoleMock.restore();
  });

  it('adminStartLlmLoadTest sends POST with csrf header and body', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { runId: 'r1' } });

    const res = await adminStartLlmLoadTest({
      concurrency: 1,
      totalRequests: 2,
      ratioChatStream: 1,
      ratioModerationTest: 0,
      stream: true,
      timeoutMs: 1000,
      retries: 0,
      retryDelayMs: 0,
      chatMessage: 'hi',
      moderationText: 't',
    });

    expect(res.runId).toBe('r1');
    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/admin/metrics/llm-loadtest/run');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
    });
    expect(String((fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.body)).toBe(
      JSON.stringify({
        concurrency: 1,
        totalRequests: 2,
        ratioChatStream: 1,
        ratioModerationTest: 0,
        stream: true,
        timeoutMs: 1000,
        retries: 0,
        retryDelayMs: 0,
        chatMessage: 'hi',
        moderationText: 't',
      }),
    );
  });

  it('adminStartLlmLoadTest throws backend message and falls back when missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(
      adminStartLlmLoadTest({
        concurrency: 1,
        totalRequests: 1,
        ratioChatStream: 1,
        ratioModerationTest: 0,
        stream: true,
        timeoutMs: 1000,
        retries: 0,
        retryDelayMs: 0,
        chatMessage: 'hi',
        moderationText: 't',
      }),
    ).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(
      adminStartLlmLoadTest({
        concurrency: 1,
        totalRequests: 1,
        ratioChatStream: 1,
        ratioModerationTest: 0,
        stream: true,
        timeoutMs: 1000,
        retries: 0,
        retryDelayMs: 0,
        chatMessage: 'hi',
        moderationText: 't',
      }),
    ).rejects.toThrow('启动压测失败');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(
      adminStartLlmLoadTest({
        concurrency: 1,
        totalRequests: 1,
        ratioChatStream: 1,
        ratioModerationTest: 0,
        stream: true,
        timeoutMs: 1000,
        retries: 0,
        retryDelayMs: 0,
        chatMessage: 'hi',
        moderationText: 't',
      }),
    ).rejects.toThrow('启动压测失败');
  });

  it('adminListLlmLoadTestHistory builds query and sends GET with credentials', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: [] });

    const res = await adminListLlmLoadTestHistory({ limit: 10 });

    expect(res).toEqual([]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/metrics/llm-loadtest/history');
    expect(url).toContain('limit=10');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('adminListLlmLoadTestHistory omits empty query params', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: [] });

    await adminListLlmLoadTestHistory({});

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/metrics/llm-loadtest/history');
    expect(url).not.toContain('limit=');
  });

  it('adminStopLlmLoadTest sends POST with csrf header and body and handles error fallback', async () => {
    const fetchMock1 = mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminStopLlmLoadTest('r1')).rejects.toThrow('bad');
    expect(fetchMock1).toHaveBeenCalledTimes(1);
    expect(String(fetchMock1.mock.calls[0]?.[0])).toContain('/api/admin/metrics/llm-loadtest/r1/stop');
    expect(fetchMock1.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
      body: JSON.stringify({}),
    });

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminStopLlmLoadTest('r1')).rejects.toThrow('停止压测失败');
  });

  it('adminGetLlmLoadTestStatus uses encoded runId, passes signal, and always cleans up timeout', async () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    const clearTimeoutSpy = vi.spyOn(window, 'clearTimeout');

    const fetchMock1 = mockFetchResponseOnce({ ok: true, json: { runId: 'x', running: true, cancelled: false, done: 0, total: 1, success: 0, failed: 0, queuePeak: { maxPending: 0, maxRunning: 0, maxTotal: 0, tokensPerSecMax: 0, tokensPerSecAvg: 0 }, recentResults: [], createdAtMs: 0 } });
    const controller = new AbortController();
    const status = await adminGetLlmLoadTestStatus('a b', { signal: controller.signal, timeoutMs: 10 });
    expect(status.runId).toBe('x');
    expect(fetchMock1).toHaveBeenCalledTimes(1);
    expect(String(fetchMock1.mock.calls[0]?.[0])).toContain('/api/admin/metrics/llm-loadtest/a%20b');
    expect(fetchMock1.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
    expect((fetchMock1.mock.calls[0]?.[1] as RequestInit | undefined)?.signal).toBeInstanceOf(AbortSignal);
    expect(setTimeoutSpy).toHaveBeenCalledTimes(1);
    expect(clearTimeoutSpy).toHaveBeenCalledTimes(1);

    clearTimeoutSpy.mockClear();
    mockFetchRejectOnce(new Error('boom'));
    await expect(adminGetLlmLoadTestStatus('r1', { timeoutMs: 10 })).rejects.toThrow('boom');
    expect(clearTimeoutSpy).toHaveBeenCalledTimes(1);
  });

  it('adminGetLlmLoadTestStatus throws backend message and falls back when missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetLlmLoadTestStatus('r1')).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminGetLlmLoadTestStatus('r1')).rejects.toThrow('获取压测状态失败');
  });

  it('adminUpsertLlmLoadTestHistory sends csrf header and returns record', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { runId: 'r1', createdAt: '2020-01-01', summary: { a: 1 } } });

    const res = await adminUpsertLlmLoadTestHistory({ runId: 'r1', summary: { a: 1 } });

    expect(res.runId).toBe('r1');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
    });
  });

  it('adminDeleteLlmLoadTestHistory sends DELETE with csrf header and handles error fallback', async () => {
    const fetchMock1 = mockFetchResponseOnce({ ok: true, json: {} });
    await expect(adminDeleteLlmLoadTestHistory('r1')).resolves.toBeUndefined();
    expect(fetchMock1).toHaveBeenCalledTimes(1);
    expect(String(fetchMock1.mock.calls[0]?.[0])).toContain('/api/admin/metrics/llm-loadtest/history/r1');
    expect(fetchMock1.mock.calls[0]?.[1]).toMatchObject({
      method: 'DELETE',
      credentials: 'include',
      headers: { 'X-XSRF-TOKEN': 'csrf-token' },
    });

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminDeleteLlmLoadTestHistory('r1')).rejects.toThrow('删除压测历史失败');
  });

  it('adminGetLlmLoadTestExportUrl builds query and encodes runId', () => {
    expect(adminGetLlmLoadTestExportUrl('a b', 'csv')).toContain('/api/admin/metrics/llm-loadtest/a%20b/export?format=csv');
    expect(adminGetLlmLoadTestExportUrl('r1')).toContain('/api/admin/metrics/llm-loadtest/r1/export?format=json');
  });

  it('adminGetLlmLoadTestStatus covers signal-aborted and no-timeout branches', async () => {
    const setTimeoutSpy = vi.spyOn(window, 'setTimeout');
    const clearTimeoutSpy = vi.spyOn(window, 'clearTimeout');

    const fetchMock = mockFetchResponseOnce({
      ok: true,
      json: {
        runId: 'x',
        running: true,
        cancelled: false,
        done: 0,
        total: 1,
        success: 0,
        failed: 0,
        queuePeak: { maxPending: 0, maxRunning: 0, maxTotal: 0, tokensPerSecMax: 0, tokensPerSecAvg: 0 },
        recentResults: [],
        createdAtMs: 0,
      },
    });

    const controller = new AbortController();
    controller.abort();
    await expect(adminGetLlmLoadTestStatus('r1', { signal: controller.signal })).resolves.toMatchObject({ runId: 'x' });

    const passedSignal = (fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.signal as AbortSignal | undefined;
    expect(passedSignal).toBeInstanceOf(AbortSignal);
    expect(passedSignal?.aborted).toBe(true);
    expect(setTimeoutSpy).not.toHaveBeenCalled();
    expect(clearTimeoutSpy).not.toHaveBeenCalled();
  });

  it('adminStartLlmLoadTest falls back payload to {}', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { runId: 'r1' } });
    await expect((adminStartLlmLoadTest as any)(undefined)).resolves.toMatchObject({ runId: 'r1' });
    expect(String((fetchMock.mock.calls[0]?.[1] as RequestInit | undefined)?.body)).toBe(JSON.stringify({}));
  });

  it('adminListLlmLoadTestHistory throws fallback message on failure without backend message', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, json: { message: 1 } });
    await expect(adminListLlmLoadTestHistory({ limit: 1 })).rejects.toThrow('获取压测历史失败');
  });

  it('adminUpsertLlmLoadTestHistory throws fallback message on failure without backend message', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, json: { message: 1 } });
    await expect(adminUpsertLlmLoadTestHistory({ runId: 'r1', summary: {} })).rejects.toThrow('保存压测历史失败');
  });

  it('apiUrl 默认使用相对路径', async () => {
    const svc = await import('./llmLoadtestAdminService');
    const fetchMock = mockFetchResponseOnce({ ok: true, json: [] });
    await expect(svc.adminListLlmLoadTestHistory({ limit: 1 })).resolves.toEqual([]);
    expect(String(fetchMock.mock.calls[0]?.[0] || '')).toContain('/api/admin/metrics/llm-loadtest/history');
  });
});
