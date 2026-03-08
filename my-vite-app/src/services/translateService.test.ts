import { beforeEach, describe, expect, it, vi } from 'vitest';
import { mockFetchResponseOnce } from '../testUtils/mockFetch';
 
vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf-token'),
  };
});
 
function setApiBase(value: string | undefined) {
  const meta = import.meta as any;
  meta.env = meta.env || {};
  if (value === undefined) {
    delete meta.env.VITE_API_BASE_URL;
  } else {
    meta.env.VITE_API_BASE_URL = value;
  }
}

function createMockStreamBody(chunks: string[]) {
  const encoder = new TextEncoder();
  const bytes = chunks.map((c) => encoder.encode(c));
  let i = 0;
 
  return {
    getReader() {
      return {
        async read() {
          if (i >= bytes.length) return { done: true, value: undefined as unknown as Uint8Array };
          const value = bytes[i];
          i += 1;
          return { done: false, value };
        },
      };
    },
  };
}
 
describe('translateService', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.resetModules();
    setApiBase('');
  });
 
  it('getTranslateConfig sends GET with credentials and returns dto', async () => {
    const { getTranslateConfig } = await import('./translateService');
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { enabled: true, allowedTargetLanguages: ['en'] } });
 
    const res = await getTranslateConfig();
 
    expect(res).toEqual({ enabled: true, allowedTargetLanguages: ['en'] });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/ai/translate/config');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({ method: 'GET', credentials: 'include' });
  });
 
  it('getTranslateConfig throws backend message when not ok', async () => {
    const { getTranslateConfig } = await import('./translateService');
    mockFetchResponseOnce({ ok: false, status: 400, json: { message: 'bad' } });
    await expect(getTranslateConfig()).rejects.toThrow('bad');
  });

  it('apiUrl normalizes leading slash and supports api base', async () => {
    setApiBase('');
    const mod1 = await import('./translateService');
    expect(mod1.apiUrl('api/x')).toBe('/api/x');

    vi.resetModules();
    setApiBase('http://base');
    const mod2 = await import('./translateService');
    expect(mod2.apiUrl('/api/x')).toBe('http://base/api/x');
  });

  it('getTranslateConfig falls back to default message when backend message is absent', async () => {
    const { getTranslateConfig } = await import('./translateService');
    mockFetchResponseOnce({ ok: false, status: 400, json: {} });
    await expect(getTranslateConfig()).rejects.toThrow('获取翻译配置失败');
  });

  it('getTranslateConfig stringifies non-string backend error field', async () => {
    const { getTranslateConfig } = await import('./translateService');
    mockFetchResponseOnce({ ok: false, status: 400, json: { error: 1 } });
    await expect(getTranslateConfig()).rejects.toThrow('1');
  });
 
  it('translatePost builds url, sends csrf header, parses SSE delta stream, and calls onProgress', async () => {
    const { translatePost } = await import('./translateService');
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      body: createMockStreamBody(['data: {"delta":"H"}\n', 'data: {"delta":"i"}\n', 'data: [DONE]\n']),
    });
    const onProgress = vi.fn();
 
    const res = await translatePost(7, 'en', onProgress);
 
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const url = String(fetchMock.mock.calls[0]?.[0]);
    expect(url).toContain('/api/ai/posts/7/translate?');
    expect(url).toContain('targetLang=en');
    expect(fetchMock.mock.calls[0]?.[1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': 'csrf-token' },
      body: JSON.stringify({}),
    });
 
    expect(onProgress).toHaveBeenCalled();
    const last = onProgress.mock.calls[onProgress.mock.calls.length - 1]?.[0];
    expect(last).toMatchObject({ translatedMarkdown: 'Hi' });
    expect(res).toMatchObject({ translatedMarkdown: 'Hi' });
  });
 
  it('translatePost throws backend message on failure', async () => {
    const { translatePost } = await import('./translateService');
    mockFetchResponseOnce({ ok: false, status: 500, json: { error: 'fail' } });
    await expect(translatePost(1, 'en')).rejects.toThrow('fail');
  });
 
  it('translateComment falls back to json body when stream is missing', async () => {
    const { translateComment } = await import('./translateService');
    const fetchMock = mockFetchResponseOnce({ ok: true, json: { translatedMarkdown: 'x', cached: true } });
 
    const res = await translateComment(9, 'en');
 
    expect(res).toEqual({ translatedMarkdown: 'x', cached: true });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('/api/ai/comments/9/translate?');
  });

  it('translatePost supports empty targetLang and does not require onProgress', async () => {
    const { translatePost } = await import('./translateService');
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      body: createMockStreamBody(['data: {"delta":"A"}\n', 'data: [DONE]\n']),
    });
    const res = await translatePost(1, undefined as any);
    expect(res).toMatchObject({ translatedMarkdown: 'A' });
    expect(String(fetchMock.mock.calls[0]?.[0])).toContain('targetLang=');
  });

  it('translatePost handles full object SSE updates and ignores non-data lines', async () => {
    const { translatePost } = await import('./translateService');
    const fetchMock = mockFetchResponseOnce({
      ok: true,
      body: createMockStreamBody([
        'event: ping\n',
        'data: \n',
        'data: {"delta":"H"}\n',
        'data: {"translatedMarkdown":"Hello","cached":true}\n',
        'data: [DONE]\n',
      ]),
    });
    const onProgress = vi.fn();
    const res = await translatePost(7, 'en', onProgress);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(onProgress).toHaveBeenCalled();
    expect(res).toEqual({ translatedMarkdown: 'Hello', cached: true });
  });

  it('translatePost swallows SSE parse errors and continues', async () => {
    const { translatePost } = await import('./translateService');
    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    mockFetchResponseOnce({
      ok: true,
      body: createMockStreamBody(['data: {bad}\n', 'data: {"delta":"x"}\n', 'data: [DONE]\n']),
    });
    const res = await translatePost(1, 'en');
    expect(res).toMatchObject({ translatedMarkdown: 'x' });
    expect(errSpy).toHaveBeenCalled();
    errSpy.mockRestore();
  });
});
