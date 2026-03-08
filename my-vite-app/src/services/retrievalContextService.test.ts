import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
import {
  adminGetContextClipConfig,
  adminGetContextWindow,
  adminListContextWindows,
  adminTestContextClip,
  adminUpdateContextClipConfig,
} from './retrievalContextService';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('retrievalContextService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('adminGetContextClipConfig returns dto and throws backend message', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true, policy: 'FIXED' } });
    await expect(adminGetContextClipConfig()).resolves.toMatchObject({ policy: 'FIXED' });
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetContextClipConfig()).rejects.toThrow('bad');
  });

  it('adminUpdateContextClipConfig sends PUT with csrf header and throws fallback', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: false } });
    await expect(adminUpdateContextClipConfig({ enabled: false })).resolves.toMatchObject({ enabled: false });
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' },
    });

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminUpdateContextClipConfig({})).rejects.toThrow('保存动态上下文裁剪配置失败');
  });

  it('adminTestContextClip posts payload and throws fallback', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { usedTokens: 1 } });
    await expect(adminTestContextClip({ queryText: 'q' })).resolves.toMatchObject({ usedTokens: 1 });
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'POST', credentials: 'include' });

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminTestContextClip({ queryText: 'q' })).rejects.toThrow('动态上下文裁剪测试失败');
  });

  it('covers json parse fallback for config endpoints', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(adminGetContextClipConfig()).rejects.toThrow('获取动态上下文裁剪配置失败');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(adminUpdateContextClipConfig({ enabled: true })).rejects.toThrow('保存动态上下文裁剪配置失败');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(adminTestContextClip({ queryText: 'q' })).rejects.toThrow('动态上下文裁剪测试失败');
  });

  it('adminListContextWindows builds url params and adminGetContextWindow returns detail', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { content: [], totalElements: 0 } });
    await expect(adminListContextWindows({ from: 'a', to: 'b' })).resolves.toMatchObject({ totalElements: 0 });
    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/retrieval/context/logs/windows?');
    expect(url).toContain('page=0');
    expect(url).toContain('size=20');
    expect(url).toContain('from=a');
    expect(url).toContain('to=b');

    mockFetchResponseOnce({ ok: true, json: { id: 1 } });
    await expect(adminGetContextWindow(1)).resolves.toMatchObject({ id: 1 });
  });

  it('covers list/get error branches and json parse fallback', async () => {
    const fetchMock1 = mockFetchResponseOnce({ ok: true, json: { content: [], totalElements: 0 } });
    await expect(adminListContextWindows()).resolves.toMatchObject({ totalElements: 0 });
    const url = String(fetchMock1.mock.calls[0]?.[0]);
    expect(url).toContain('page=0');
    expect(url).toContain('size=20');
    expect(url).not.toContain('from=');
    expect(url).not.toContain('to=');

    mockFetchResponseOnce({ ok: false, status: 500, json: { message: 'bad' } });
    await expect(adminListContextWindows()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(adminGetContextWindow(1)).rejects.toThrow('获取上下文窗口详情失败');
  });
});
