import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('chatContextGovernanceService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  it('adminGetChatContextConfig prefixes missing leading slash and returns data', async () => {
    const { adminGetChatContextConfig } = await import('./chatContextGovernanceService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { enabled: true } });
    await expect(adminGetChatContextConfig()).resolves.toEqual({ enabled: true });

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toBe('/api/admin/ai/chat-context/config');
    expect(info?.method).toBe('GET');
    expect(info?.init?.credentials).toBe('include');
  });

  it('adminGetChatContextConfig error prefers backend message else fallback; json parse fail fallback', async () => {
    const { adminGetChatContextConfig } = await import('./chatContextGovernanceService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetChatContextConfig()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 400, json: { message: 1 } });
    await expect(adminGetChatContextConfig()).rejects.toThrow('获取对话上下文治理配置失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(adminGetChatContextConfig()).rejects.toThrow('获取对话上下文治理配置失败');
  });

  it('adminUpdateChatContextConfig sends csrf header and body; error fallback when message missing', async () => {
    const { adminUpdateChatContextConfig } = await import('./chatContextGovernanceService');
    const { replyJsonOnce, replyOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { enabled: false } });
    await expect(adminUpdateChatContextConfig({ enabled: false })).resolves.toEqual({ enabled: false });

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toBe('/api/admin/ai/chat-context/config');
    expect(info?.method).toBe('PUT');
    expect(info?.init?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(info?.body).toBe(JSON.stringify({ enabled: false }));

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(adminUpdateChatContextConfig({ enabled: true })).rejects.toThrow('保存对话上下文治理配置失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(adminUpdateChatContextConfig({ enabled: true })).rejects.toThrow('保存对话上下文治理配置失败');
  });

  it('adminListChatContextLogs applies default paging and from/to query when present', async () => {
    const { adminListChatContextLogs } = await import('./chatContextGovernanceService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, number: 0, size: 20 } });
    await expect(adminListChatContextLogs()).resolves.toMatchObject({ content: [] });

    let info = getFetchCallInfo(lastCall());
    expect(info?.url).toContain('/api/admin/ai/chat-context/logs?');
    expect(info?.url).toContain('page=0');
    expect(info?.url).toContain('size=20');
    expect(info?.url).not.toContain('from=');
    expect(info?.url).not.toContain('to=');

    replyJsonOnce({ ok: true, json: { content: [], totalElements: 0, number: 1, size: 10 } });
    await expect(adminListChatContextLogs({ page: 1, size: 10, from: '2020-01-01', to: '2020-01-02' })).resolves.toMatchObject({ content: [] });
    info = getFetchCallInfo(lastCall());
    expect(info?.url).toContain('page=1');
    expect(info?.url).toContain('size=10');
    expect(info?.url).toContain('from=2020-01-01');
    expect(info?.url).toContain('to=2020-01-02');
  });

  it('adminListChatContextLogs error prefers backend message else fallback; json parse fail fallback', async () => {
    const { adminListChatContextLogs } = await import('./chatContextGovernanceService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminListChatContextLogs()).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 400, json: { message: 1 } });
    await expect(adminListChatContextLogs()).rejects.toThrow('获取对话上下文治理日志失败');

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(adminListChatContextLogs()).rejects.toThrow('获取对话上下文治理日志失败');
  });

  it('adminGetChatContextLog error prefers backend message else fallback', async () => {
    const { adminGetChatContextLog } = await import('./chatContextGovernanceService');
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 404, json: { message: 'nope' } });
    await expect(adminGetChatContextLog(1)).rejects.toThrow('nope');

    replyJsonOnce({ ok: false, status: 404, json: {} });
    await expect(adminGetChatContextLog(1)).rejects.toThrow('获取对话上下文治理日志详情失败');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(adminGetChatContextLog(1)).rejects.toThrow('获取对话上下文治理日志详情失败');
  });
});
