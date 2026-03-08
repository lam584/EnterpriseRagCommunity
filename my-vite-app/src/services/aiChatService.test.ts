import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { getFetchCallInfo, installFetchMock, resetServiceTest } from '../testUtils/serviceTestHarness';

vi.mock('../utils/csrfUtils', () => {
  return {
    getCsrfToken: vi.fn(async () => 'csrf'),
    clearCsrfToken: vi.fn(),
  };
});

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

describe('aiChatService', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('chatStream builds request with explicit flags and parses error/sources with fallbacks', async () => {
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({
      ok: true,
      body: createMockStreamBody([
        'event: error\ndata: {}\n\n',
        'event: delta\ndata: not-json\n\n',
        'event: sources\ndata: {"sources":[{"index":"2","postId":null,"chunkIndex":3,"score":0.2},{"index":5}]}\n\n',
        'event: done\ndata: {"latencyMs": 9}\n\n',
      ]),
    });

    const { chatStream } = await import('./aiChatService');
    const events: any[] = [];
    await chatStream({ message: 'hi', dryRun: true, deepThink: true, useRag: false }, (ev) => events.push(ev));

    expect(events).toEqual([
      { type: 'error', message: '请求失败' },
      {
        type: 'sources',
        sources: [
          { index: 2, postId: null, chunkIndex: 3, score: 0.2, title: null, url: null, snippet: null },
          { index: 5, postId: undefined, chunkIndex: undefined, score: undefined, title: null, url: null, snippet: null },
        ],
      },
      { type: 'done', latencyMs: 9 },
    ]);

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toContain('/api/ai/chat/stream');
    expect(info?.headers).toMatchObject({
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': 'csrf',
    });
    const body = JSON.parse(String(info?.body ?? '{}'));
    expect(body).toMatchObject({ message: 'hi', dryRun: true, deepThink: true, useRag: false });
  });

  it('chatStream throws fallback error when response text is empty', async () => {
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 503, text: '' });
    const { chatStream } = await import('./aiChatService');
    await expect(chatStream({ message: 'hi' }, () => undefined)).rejects.toThrow('请求失败: 503');
  });

  it('chatStream throws fallback error when response text throws', async () => {
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 504, textError: new Error('boom') });
    const { chatStream } = await import('./aiChatService');
    await expect(chatStream({ message: 'hi' }, () => undefined)).rejects.toThrow('请求失败: 504');
  });

  it('chatStream throws when stream reader missing', async () => {
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: true, json: {} });
    const { chatStream } = await import('./aiChatService');
    await expect(chatStream({ message: 'hi' }, () => undefined)).rejects.toThrow('浏览器不支持流式响应');
  });

  it('chatOnce maps success response and preserves fields', async () => {
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({
      ok: true,
      json: { sessionId: 10, userMessageId: 1, questionMessageId: 2, assistantMessageId: 3, content: 'ok', latencyMs: 7 },
    });
    const { chatOnce } = await import('./aiChatService');
    await expect(chatOnce({ message: 'hi' })).resolves.toMatchObject({
      sessionId: 10,
      userMessageId: 1,
      questionMessageId: 2,
      assistantMessageId: 3,
      content: 'ok',
      latencyMs: 7,
    });
  });

  it('chatOnce throws backend error field and falls back to status when json parsing fails', async () => {
    const { replyJsonOnce, replyOnce } = installFetchMock();

    replyJsonOnce({ ok: false, status: 400, json: { error: 'bad' } });
    const { chatOnce } = await import('./aiChatService');
    await expect(chatOnce({ message: 'hi' })).rejects.toThrow('bad');

    replyOnce({ ok: false, status: 400, jsonError: new Error('no-json') });
    await expect(chatOnce({ message: 'hi' })).rejects.toThrow('请求失败: 400');
  });

  it('regenerateStream handles http errors, missing reader, and ignores unknown/non-json events', async () => {
    const { replyOnce } = installFetchMock();
    const { regenerateStream } = await import('./aiChatService');

    replyOnce({ ok: false, status: 503, text: '' });
    await expect(regenerateStream(1, {}, () => undefined)).rejects.toThrow('请求失败: 503');

    replyOnce({ ok: false, status: 504, textError: new Error('boom') });
    await expect(regenerateStream(1, {}, () => undefined)).rejects.toThrow('请求失败: 504');

    replyOnce({ ok: true, json: {} });
    await expect(regenerateStream(1, {}, () => undefined)).rejects.toThrow('浏览器不支持流式响应');

    replyOnce({
      ok: true,
      body: createMockStreamBody([
        'data: {"x":1}\n\n' +
          'event: delta\n\n' +
          'event: sources\ndata: {"sources":"bad"}\n\n' +
          'event: sources\ndata: {"sources":[1, {"postId":9}]}\n\n' +
          'event: unknown\ndata: {}\n\n' +
          'event: done\ndata: {}\n\n',
      ]),
    });
    const events: any[] = [];
    await regenerateStream(1, {}, (ev) => events.push(ev));
    expect(events).toEqual([
      { type: 'delta', content: '' },
      { type: 'sources', sources: [] },
      { type: 'sources', sources: [{ index: 0, postId: 9, chunkIndex: undefined, score: undefined, title: null, url: null, snippet: null }] },
      { type: 'done', latencyMs: undefined },
    ]);
  });

  it('chatStream covers meta/custom error/chunk-split and ignores blocks without event', async () => {
    const { replyOnce } = installFetchMock();
    replyOnce({
      ok: true,
      body: createMockStreamBody([
        'data: {"ignored":1}\n\n',
        'event: meta\ndata: {"sessionId":"12","userMessageId":3,"questionMessageId":4}\n\n',
        'event: er',
        'ror\ndata: {"message":"bad-msg"}\n\n',
      ]),
    });
    const { chatStream } = await import('./aiChatService');
    const events: any[] = [];
    await chatStream({ message: 'hi' }, (ev) => events.push(ev));
    expect(events).toEqual([
      { type: 'meta', sessionId: 12, userMessageId: 3, questionMessageId: 4 },
      { type: 'error', message: 'bad-msg' },
    ]);
  });

  it('chatOnce uses API_BASE and stringifies backend message', async () => {
    vi.resetModules();
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.com');
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: false, status: 418, json: { message: 404 } as any });

    const { chatOnce } = await import('./aiChatService');
    await expect(chatOnce({ message: 'hi' })).rejects.toThrow('404');
    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toBe('https://api.example.com/api/ai/chat');
    vi.unstubAllEnvs();
    vi.resetModules();
  });

  it('regenerateOnce throws message/error and falls back when body is not an object or json parsing fails', async () => {
    const { replyJsonOnce, replyOnce } = installFetchMock();
    const { regenerateOnce } = await import('./aiChatService');

    replyJsonOnce({ ok: false, status: 400, json: { message: 'm' } });
    await expect(regenerateOnce(1, {})).rejects.toThrow('m');

    replyJsonOnce({ ok: false, status: 400, json: { error: 'e' } });
    await expect(regenerateOnce(1, {})).rejects.toThrow('e');

    replyJsonOnce({ ok: false, status: 400, json: 'x' as any });
    await expect(regenerateOnce(1, {})).rejects.toThrow('请求失败: 400');

    replyOnce({ ok: false, status: 500, jsonError: new Error('bad') });
    await expect(regenerateOnce(1, {})).rejects.toThrow('请求失败: 500');
  });
});
