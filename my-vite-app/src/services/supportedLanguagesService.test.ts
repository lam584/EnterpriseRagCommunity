import { describe, expect, it, vi, beforeEach } from 'vitest';
import {
  adminDeleteSupportedLanguage,
  adminUpdateSupportedLanguage,
  adminUpsertSupportedLanguage,
  listSupportedLanguages,
} from './supportedLanguagesService';
import { mockFetch, mockFetchJsonOnce, mockFetchResponseOnce } from '../testUtils/mockFetch';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('supportedLanguagesService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('listSupportedLanguages returns array and throws backend message on error', async () => {
    mockFetchJsonOnce({ ok: true, json: [{ languageCode: 'zh', displayName: 'Chinese' }] });
    const res = await listSupportedLanguages();
    expect(res[0]?.languageCode).toBe('zh');

    mockFetchJsonOnce({ ok: false, json: { message: 'bad' } });
    await expect(listSupportedLanguages()).rejects.toThrow('bad');
  });

  it('adminUpsertSupportedLanguage posts csrf header', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({ ok: true, json: async () => ({ languageCode: 'en', displayName: 'English' }) });
    const res = await adminUpsertSupportedLanguage({ languageCode: 'en', displayName: 'English' });
    expect(res.languageCode).toBe('en');
    const init = (fetchMock as any).mock.calls[0]?.[1];
    expect(init.headers?.['X-XSRF-TOKEN']).toBe('csrf');
  });

  it('adminUpdateSupportedLanguage encodes original language code', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({ ok: true, json: async () => ({ languageCode: 'a/b', displayName: 'X' }) });
    await adminUpdateSupportedLanguage('a/b', { languageCode: 'a/b', displayName: 'X' });
    const url = String((fetchMock as any).mock.calls[0]?.[0]);
    expect(url).toContain('/api/admin/ai/supported-languages/a%2Fb');
  });

  it('adminDeleteSupportedLanguage throws backend message on error', async () => {
    mockFetchJsonOnce({ ok: false, json: { error: 'nope' } });
    await expect(adminDeleteSupportedLanguage('zh')).rejects.toThrow('nope');
  });

  it('listSupportedLanguages returns empty array when response is not an array', async () => {
    mockFetchJsonOnce({ ok: true, json: { items: [] } });
    await expect(listSupportedLanguages()).resolves.toEqual([]);
  });

  it('listSupportedLanguages throws fallback message when json parsing fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(listSupportedLanguages()).rejects.toThrow('获取支持语言列表失败');
  });

  it('adminUpsertSupportedLanguage throws backend message on error', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'bad-upsert' } });
    await expect(adminUpsertSupportedLanguage({ languageCode: 'en', displayName: 'English' })).rejects.toThrow('bad-upsert');
  });

  it('adminUpdateSupportedLanguage throws backend message on error', async () => {
    mockFetchJsonOnce({ ok: false, json: { error: 'bad-update' } });
    await expect(adminUpdateSupportedLanguage('en', { languageCode: 'en', displayName: 'English' })).rejects.toThrow('bad-update');
  });

  it('adminDeleteSupportedLanguage throws fallback message when json parsing fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(adminDeleteSupportedLanguage('zh')).rejects.toThrow('删除语言失败');
  });

  it('adminUpsertSupportedLanguage throws fallback message when json parsing fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(adminUpsertSupportedLanguage({ languageCode: 'en', displayName: 'English' })).rejects.toThrow('新增语言失败');
  });

  it('adminUpdateSupportedLanguage throws fallback message when json parsing fails', async () => {
    mockFetchResponseOnce({ ok: false, status: 500, jsonError: new Error('bad json') });
    await expect(adminUpdateSupportedLanguage('en', { languageCode: 'en', displayName: 'English' })).rejects.toThrow('修改语言失败');
  });

  it('listSupportedLanguages falls back when backend body is not an object', async () => {
    mockFetchJsonOnce({ ok: false, json: 'not-an-object' });
    await expect(listSupportedLanguages()).rejects.toThrow('获取支持语言列表失败');
  });
});
