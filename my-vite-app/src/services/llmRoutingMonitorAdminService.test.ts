import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

function parseUrl(url: string) {
  return new URL(url, 'http://localhost');
}

describe('llmRoutingMonitorAdminService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('adminGetLlmRoutingDecisions builds query and normalizes response', async () => {
    const { adminGetLlmRoutingDecisions } = await import('./llmRoutingMonitorAdminService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: { checkedAtMs: 1, items: [] } });

    const res = await adminGetLlmRoutingDecisions({ taskType: '  chat  ', limit: 10.9 });
    expect(res.checkedAtMs).toBe(1);
    expect(res.items).toEqual([]);

    const info = getFetchCallInfo(lastCall())!;
    const url = parseUrl(info.url);
    expect(url.pathname).toBe('/api/admin/metrics/llm-routing/decisions');
    expect(url.searchParams.get('taskType')).toBe('CHAT');
    expect(url.searchParams.get('limit')).toBe('10');
  });

  it('adminGetLlmRoutingDecisions omits taskType when normalized empty, clamps limit, and falls back fields', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-01T00:00:00.000Z'));

    const { adminGetLlmRoutingDecisions } = await import('./llmRoutingMonitorAdminService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: { checkedAtMs: 'bad', items: 'bad' } });

    const res = await adminGetLlmRoutingDecisions({ taskType: '   ', limit: 0 });
    expect(res.checkedAtMs).toBe(Date.now());
    expect(res.items).toEqual([]);

    const info = getFetchCallInfo(lastCall())!;
    const url = parseUrl(info.url);
    expect(url.searchParams.get('taskType')).toBeNull();
    expect(url.searchParams.get('limit')).toBe('1');
    vi.useRealTimers();
  });

  it('adminGetLlmRoutingDecisions omits query string when params absent', async () => {
    const { adminGetLlmRoutingDecisions } = await import('./llmRoutingMonitorAdminService');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: { checkedAtMs: 1, items: [] } });

    await adminGetLlmRoutingDecisions();
    const info = getFetchCallInfo(lastCall())!;
    const url = parseUrl(info.url);
    expect(url.pathname).toBe('/api/admin/metrics/llm-routing/decisions');
    expect(url.search).toBe('');
  });

  it('adminGetLlmRoutingDecisions throws backend message and falls back when missing/parse fails', async () => {
    const { adminGetLlmRoutingDecisions } = await import('./llmRoutingMonitorAdminService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetLlmRoutingDecisions()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(adminGetLlmRoutingDecisions()).rejects.toThrow('获取路由事件失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(adminGetLlmRoutingDecisions()).rejects.toThrow('获取路由事件失败');
  });

  it('adminOpenLlmRoutingEventSource uses window.EventSource and falls back on constructor error', async () => {
    const { adminOpenLlmRoutingEventSource } = await import('./llmRoutingMonitorAdminService');

    const es2 = { id: 'es2' } as unknown as EventSource;
    const fallbackCtorCalls: any[][] = [];

    function EventSourceFallbackCtor(...args: any[]) {
      fallbackCtorCalls.push(args);
      return es2;
    }

    Object.defineProperty(globalThis, 'EventSource', {
      value: EventSourceFallbackCtor,
      configurable: true,
      writable: true,
    });

    const r1 = adminOpenLlmRoutingEventSource({ taskType: 'chat' });
    expect(r1).toBe(es2);
    expect(fallbackCtorCalls.length).toBe(1);
    expect(String(fallbackCtorCalls[0]?.[0] || '')).toContain('/api/admin/metrics/llm-routing/stream');
    expect(String(fallbackCtorCalls[0]?.[0] || '')).toContain('taskType=CHAT');
  });
});
