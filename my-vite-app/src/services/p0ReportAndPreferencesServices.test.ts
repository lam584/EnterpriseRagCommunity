import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

describe('p0ReportAndPreferencesServices', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('reportService reportPost sends csrf header and omits undefined reasonText', async () => {
    const { reportPost } = await import('./reportService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { reportId: 1, queueId: 2 } });
    await expect(reportPost(9, { reasonCode: 'SPAM' })).resolves.toEqual({ reportId: 1, queueId: 2 });

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toContain('/api/posts/9/report');
    expect(info?.method).toBe('POST');
    expect(info?.init?.credentials).toBe('include');
    expect(info?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(info?.body).toBe(JSON.stringify({ reasonCode: 'SPAM' }));
  });

  it('reportService reportComment throws login message on 401', async () => {
    const { reportComment } = await import('./reportService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 401, json: { message: 'ignored' } });
    await expect(reportComment(1, { reasonCode: 'X' })).rejects.toThrow('请先登录后再举报');
  });

  it('reportService reportProfile throws backend message and fallback message', async () => {
    const { reportProfile } = await import('./reportService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(reportProfile(1, { reasonCode: 'X' })).rejects.toThrow('bad');

    replyJsonOnce({ ok: false, status: 500, json: {} });
    await expect(reportProfile(1, { reasonCode: 'X' })).rejects.toThrow('举报失败');
  });

  it('reportService bubbles up network exceptions', async () => {
    const { reportPost } = await import('./reportService');
    const { rejectOnce } = installFetchMock();

    rejectOnce(new Error('net'));
    await expect(reportPost(1, { reasonCode: 'X' })).rejects.toThrow('net');
  });

  it('accountPreferencesService get/update handle message and error fields', async () => {
    const { getMyTranslatePreferences, updateMyTranslatePreferences } = await import('./accountPreferencesService');
    const { replyJsonOnce, lastCall } = installFetchMock();

    replyJsonOnce({ ok: true, json: { targetLanguage: 'zh', autoTranslatePosts: true, autoTranslateComments: false } });
    await expect(getMyTranslatePreferences()).resolves.toMatchObject({ targetLanguage: 'zh' });
    expect(String(lastCall()?.[0] || '')).toContain('/api/account/preferences');
    expect(lastCall()?.[1]?.method).toBe('GET');

    replyJsonOnce({ ok: false, status: 400, json: { error: 'no' } });
    await expect(getMyTranslatePreferences()).rejects.toThrow('no');

    replyJsonOnce({ ok: true, json: { targetLanguage: 'en', autoTranslatePosts: false, autoTranslateComments: true } });
    await expect(updateMyTranslatePreferences({ targetLanguage: 'en' })).resolves.toMatchObject({ targetLanguage: 'en' });
    expect(lastCall()?.[1]?.method).toBe('PUT');
    expect(lastCall()?.[1]?.headers).toMatchObject({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    expect(lastCall()?.[1]?.body).toBe(JSON.stringify({ targetLanguage: 'en' }));

    replyJsonOnce({ ok: false, status: 400, json: {} });
    await expect(updateMyTranslatePreferences({ targetLanguage: 'en' })).rejects.toThrow('保存偏好失败');
  });

  it('accountPreferencesService covers fallback message and json parse fallback', async () => {
    const { getMyTranslatePreferences, updateMyTranslatePreferences } = await import('./accountPreferencesService');
    const csrfUtils = await import('../utils/csrfUtils');
    const { replyOnce, replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: null });
    await expect(getMyTranslatePreferences()).rejects.toThrow('获取偏好失败');

    replyOnce({ ok: true, status: 200, jsonError: new Error('bad json') });
    await expect(getMyTranslatePreferences()).resolves.toEqual({});

    replyOnce({ ok: true, status: 200, jsonError: new Error('bad json') });
    await expect(updateMyTranslatePreferences({ targetLanguage: 'en' })).resolves.toEqual({});

    (csrfUtils.getCsrfToken as any).mockRejectedValueOnce(new Error('csrf fail'));
    await expect(updateMyTranslatePreferences({ targetLanguage: 'en' })).rejects.toThrow('csrf fail');
  });

  it('accountPreferencesService bubbles up network exceptions', async () => {
    const { getMyTranslatePreferences } = await import('./accountPreferencesService');
    const { rejectOnce } = installFetchMock();

    rejectOnce(new Error('net'));
    await expect(getMyTranslatePreferences()).rejects.toThrow('net');
  });

  it('assistantPreferencesService get/update handle parse failures', async () => {
    const { getMyAssistantPreferences, updateMyAssistantPreferences } = await import('./assistantPreferencesService');
    const { replyOnce, replyJsonOnce } = installFetchMock();

    replyOnce({ ok: true, status: 200, jsonError: new Error('bad json') });
    await expect(getMyAssistantPreferences()).resolves.toEqual({});

    replyJsonOnce({
      ok: true,
      json: {
        defaultProviderId: null,
        defaultModel: null,
        defaultDeepThink: false,
        autoLoadLastSession: true,
        defaultUseRag: false,
        ragTopK: 5,
        stream: true,
      },
    });
    await expect(getMyAssistantPreferences()).resolves.toMatchObject({ ragTopK: 5 });

    replyOnce({ ok: false, status: 400, jsonError: new Error('bad json') });
    await expect(updateMyAssistantPreferences({ stream: false })).rejects.toThrow('保存助手偏好失败');
  });

  it('assistantPreferencesService falls back when backend body is not an object', async () => {
    const { getMyAssistantPreferences, updateMyAssistantPreferences } = await import('./assistantPreferencesService');
    const { replyJsonOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: 'bad' as any });
    await expect(getMyAssistantPreferences()).rejects.toThrow('获取助手偏好失败');

    replyJsonOnce({ ok: false, status: 400, json: 'bad' as any });
    await expect(updateMyAssistantPreferences({ stream: false })).rejects.toThrow('保存助手偏好失败');
  });

  it('assistantPreferencesService bubbles up network exceptions', async () => {
    const { getMyAssistantPreferences } = await import('./assistantPreferencesService');
    const { rejectOnce } = installFetchMock();

    rejectOnce(new Error('net'));
    await expect(getMyAssistantPreferences()).rejects.toThrow('net');
  });
});
