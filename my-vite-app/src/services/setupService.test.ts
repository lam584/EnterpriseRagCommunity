import { describe, expect, it, vi, beforeEach } from 'vitest';
import {
  checkEnvFile,
  checkIndicesStatus,
  completeSetup,
  encryptValue,
  generateTotpKey,
  getSetupStatus,
  initIndices,
  saveSetupConfig,
  testEsConnection,
} from './setupService';
import { mockFetch, mockFetchJsonOnce } from '../testUtils/mockFetch';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
  };
});

describe('setupService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('getSetupStatus throws on failure', async () => {
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(getSetupStatus()).rejects.toThrow('Failed to get status');
  });

  it('saveSetupConfig throws backend message when not ok', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'bad' } });
    await expect(saveSetupConfig({ a: 'b' })).rejects.toThrow('bad');
  });

  it('saveSetupConfig resolves on ok and throws fallback when json parsing fails', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValueOnce({ ok: true, json: async () => ({}) });
    await expect(saveSetupConfig({ a: 'b' })).resolves.toBeUndefined();

    (fetchMock as any).mockResolvedValueOnce({
      ok: false,
      json: async () => {
        throw new Error('bad');
      },
    });
    await expect(saveSetupConfig({ a: 'b' })).rejects.toThrow('Failed to save config');
  });

  it('testEsConnection returns body even when http not ok', async () => {
    mockFetchJsonOnce({ ok: false, json: { success: false, message: 'no' } });
    const res = await testEsConnection({ a: 'b' });
    expect(res.success).toBe(false);
    expect(res.message).toBe('no');
  });

  it('initIndices resolves on ok', async () => {
    mockFetchJsonOnce({ ok: true, json: {} });
    await expect(initIndices(['a'])).resolves.toBeUndefined();
  });

  it('generateTotpKey returns key on ok', async () => {
    mockFetchJsonOnce({ ok: true, json: { key: 'k' } });
    const key = await generateTotpKey();
    expect(key).toBe('k');
  });

  it('generateTotpKey throws on failure', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'x' } });
    await expect(generateTotpKey()).rejects.toThrow('Failed to generate TOTP key');
  });

  it('encryptValue posts value and returns encrypted', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValue({
      ok: true,
      json: async () => ({ encrypted: 'enc' }),
    });
    const enc = await encryptValue('v');
    expect(enc).toBe('enc');
    const init = (fetchMock as any).mock.calls[0]?.[1];
    expect(JSON.parse(String(init.body))).toEqual({ value: 'v' });
    expect(init.headers?.['X-XSRF-TOKEN']).toBe('csrf');
  });

  it('encryptValue throws on failure', async () => {
    mockFetchJsonOnce({ ok: false, json: { message: 'x' } });
    await expect(encryptValue('v')).rejects.toThrow('Failed to encrypt value');
  });

  it('getSetupStatus and checkEnvFile cover ok and failure branches', async () => {
    mockFetchJsonOnce({ ok: true, json: { isInitialized: true } });
    await expect(getSetupStatus()).resolves.toEqual({ isInitialized: true });

    mockFetchJsonOnce({ ok: true, json: { exists: false } });
    await expect(checkEnvFile()).resolves.toEqual({ exists: false });

    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(checkEnvFile()).rejects.toThrow('Failed to check env file');
  });

  it('initIndices throws on failure', async () => {
    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(initIndices(['a'])).rejects.toThrow('Failed to init indices');
  });

  it('completeSetup throws backend message and fallback message', async () => {
    const fetchMock = mockFetch();
    (fetchMock as any).mockResolvedValueOnce({ ok: false, json: async () => ({ message: 'bad' }) });
    await expect(completeSetup({})).rejects.toThrow('bad');

    (fetchMock as any).mockResolvedValueOnce({ ok: false, json: async () => {
      throw new Error('bad');
    } });
    await expect(completeSetup({})).rejects.toThrow('Failed to complete setup');
  });

  it('completeSetup resolves on ok', async () => {
    mockFetchJsonOnce({ ok: true, json: {} });
    await expect(completeSetup({})).resolves.toBeUndefined();
  });

  it('checkIndicesStatus returns json on ok and throws on failure', async () => {
    mockFetchJsonOnce({ ok: true, json: { a: 'ok' } });
    await expect(checkIndicesStatus({ k: 'v' }, ['i'])).resolves.toEqual({ a: 'ok' });

    mockFetchJsonOnce({ ok: false, json: {} });
    await expect(checkIndicesStatus({ k: 'v' }, ['i'])).rejects.toThrow('Failed to check indices status');
  });
});
