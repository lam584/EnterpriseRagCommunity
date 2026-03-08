import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockConsole } from '../testUtils/mockConsole';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';

const { getCsrfTokenMock } = vi.hoisted(() => ({
  getCsrfTokenMock: vi.fn<() => Promise<string>>(),
}));

vi.mock('../utils/csrfUtils', () => ({
  getCsrfToken: getCsrfTokenMock,
}));

import { adminDeleteProviderModel, adminGetAiProvidersConfig, adminUpdateAiProvidersConfig } from './aiProvidersAdminService';
import { adminAddProviderModel, adminFetchUpstreamModels, adminListProviderModels, adminPreviewUpstreamModels } from './aiProvidersAdminService';

describe('aiProvidersAdminService', () => {
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

  it('adminGetAiProvidersConfig sends GET with credentials', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { activeProviderId: 'p', providers: [] } });

    const res = await adminGetAiProvidersConfig();

    expect(res.activeProviderId).toBe('p');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/admin/ai/providers/config');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });

  it('adminGetAiProvidersConfig throws backend message and falls back when missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminGetAiProvidersConfig()).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminGetAiProvidersConfig()).rejects.toThrow('获取模型提供商配置失败');
  });

  it('adminUpdateAiProvidersConfig sends PUT with csrf header and body', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { activeProviderId: 'p', providers: [] } });

    const res = await adminUpdateAiProvidersConfig({ activeProviderId: 'p', providers: [] });

    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/admin/ai/providers/config');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
      body: JSON.stringify({ activeProviderId: 'p', providers: [] }),
    });
    expect(res.activeProviderId).toBe('p');
  });

  it('adminUpdateAiProvidersConfig throws backend message and falls back when missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminUpdateAiProvidersConfig({})).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminUpdateAiProvidersConfig({})).rejects.toThrow('保存模型提供商配置失败');
  });

  it('adminDeleteProviderModel builds query and sends DELETE with csrf header', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { providerId: 'p', models: [] } });

    const res = await adminDeleteProviderModel('p', 'chat', 'gpt-4o');

    expect(res.providerId).toBe('p');
    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/ai/providers/p/models?');
    expect(url).toContain('purpose=chat');
    expect(url).toContain('modelName=gpt-4o');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'DELETE',
      credentials: 'include',
      headers: { 'X-XSRF-TOKEN': 'csrf-token' },
    });
  });

  it('adminDeleteProviderModel throws backend message and falls back when missing', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminDeleteProviderModel('p', 'chat', 'gpt')).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminDeleteProviderModel('p', 'chat', 'gpt')).rejects.toThrow('删除模型失败');
  });

  it('adminListProviderModels sends GET and returns models', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { providerId: 'p', models: [{ purpose: 'chat', modelName: 'm', enabled: true }] } });
    const res = await adminListProviderModels('p');
    expect(res.providerId).toBe('p');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/admin/ai/providers/p/models');
  });

  it('adminListProviderModels throws backend message and falls back on json parse failure', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminListProviderModels('p')).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminListProviderModels('p')).rejects.toThrow('获取模型列表失败');
  });

  it('adminAddProviderModel sends POST with csrf header and body', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { providerId: 'p', models: [] } });
    await expect(adminAddProviderModel('p', 'chat', 'gpt-4o')).resolves.toMatchObject({ providerId: 'p' });
    expect(getCsrfTokenMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
      body: JSON.stringify({ purpose: 'chat', modelName: 'gpt-4o' }),
    });
  });

  it('adminAddProviderModel throws backend message and falls back on json parse failure', async () => {
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminAddProviderModel('p', 'chat', 'm')).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminAddProviderModel('p', 'chat', 'm')).rejects.toThrow('添加模型失败');
  });

  it('adminFetchUpstreamModels and adminPreviewUpstreamModels cover ok and error fallbacks', async () => {
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { providerId: 'p', models: ['m1'] } });
    await expect(adminFetchUpstreamModels('p')).resolves.toMatchObject({ models: ['m1'] });
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/upstream-models');

    const fetchMock2 = mockFetchResponseOnce({ ok: true, json: { providerId: 'p', models: ['m2'] } });
    await expect(adminPreviewUpstreamModels({ providerId: 'p', baseUrl: 'u' })).resolves.toMatchObject({ models: ['m2'] });
    expect(getCsrfTokenMock).toHaveBeenCalled();
    expect(fetchMock2.mock.calls[0]?.[1]).toMatchObject({ method: 'POST', credentials: 'include' });

    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(adminFetchUpstreamModels('p')).rejects.toThrow('bad');

    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminPreviewUpstreamModels({ providerId: 'p' })).rejects.toThrow('获取 /v1/models 失败');
  });

  it('adminFetchUpstreamModels falls back when backend message is missing and on json parse failure', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, json: {} });
    await expect(adminFetchUpstreamModels('p')).rejects.toThrow('获取 /v1/models 失败');

    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(adminFetchUpstreamModels('p')).rejects.toThrow('获取 /v1/models 失败');
  });
});
