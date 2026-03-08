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

describe('p0AiStreamServices', () => {
  beforeEach(() => {
    resetServiceTest();
  });

  afterEach(() => {
    cleanup();
  });

  it('aiPostComposeService streams events and builds request (deepThink omitted by default)', async () => {
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({
      ok: true,
      body: createMockStreamBody([
        'event: meta\ndata: {"snapshotId": 7}\n\n' +
          'event: delta\ndata: {"content": "A"}\n\n' +
          'event: unknown\ndata: {"x": 1}\n\n',
        'event: delta\ndata: not-json\n\n',
        'event: error\ndata: {"message": "m"}\n\n' + 'event: done\ndata: {"latencyMs": 12}\n\n',
      ]),
    });

    const { postComposeEditStream } = await import('./aiPostComposeService');
    const events: any[] = [];
    await postComposeEditStream({ snapshotId: 1, currentContent: 'c' }, (ev) => events.push(ev));

    expect(events).toEqual([
      { type: 'meta', snapshotId: 7 },
      { type: 'delta', content: 'A' },
      { type: 'error', message: 'm' },
      { type: 'done', latencyMs: 12 },
    ]);

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toContain('/api/ai/post-compose/stream');
    expect(info?.method).toBe('POST');
    expect(info?.headers).toMatchObject({
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': 'csrf',
    });
    const body = JSON.parse(String(info?.body ?? '{}'));
    expect(body).toMatchObject({ snapshotId: 1, currentContent: 'c' });
    expect('deepThink' in body).toBe(false);
  });

  it('aiPostComposeService includes deepThink when explicitly provided', async () => {
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({
      ok: true,
      body: createMockStreamBody(['event: done\ndata: {}\n\n']),
    });

    const { postComposeEditStream } = await import('./aiPostComposeService');
    await postComposeEditStream({ snapshotId: 1, deepThink: false }, () => undefined);

    const info = getFetchCallInfo(lastCall());
    const body = JSON.parse(String(info?.body ?? '{}'));
    expect(body.deepThink).toBe(false);
  });

  it('aiPostComposeService throws when response not ok', async () => {
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 500, text: 'bad' });
    const { postComposeEditStream } = await import('./aiPostComposeService');
    await expect(postComposeEditStream({ snapshotId: 1 }, () => undefined)).rejects.toThrow('bad');
  });

  it('aiPostComposeService throws fallback error when response text is empty or throws', async () => {
    const { replyOnce } = installFetchMock();
    const { postComposeEditStream } = await import('./aiPostComposeService');

    replyOnce({ ok: false, status: 503, text: '' });
    await expect(postComposeEditStream({ snapshotId: 1 }, () => undefined)).rejects.toThrow('请求失败: 503');

    replyOnce({ ok: false, status: 504, textError: new Error('boom') });
    await expect(postComposeEditStream({ snapshotId: 1 }, () => undefined)).rejects.toThrow('请求失败: 504');
  });

  it('aiPostComposeService throws when stream reader missing', async () => {
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: true, json: {} });
    const { postComposeEditStream } = await import('./aiPostComposeService');
    await expect(postComposeEditStream({ snapshotId: 1 }, () => undefined)).rejects.toThrow('浏览器不支持流式响应');
  });

  it('aiPostComposeService parseEventBlock covers missing eventType, empty data, non-object json, and default fields', async () => {
    const { replyOnce } = installFetchMock();
    replyOnce({
      ok: true,
      body: createMockStreamBody([
        'data: {"x":1}\n\n' +
          'event: delta\n\n' +
          'event: delta\ndata: 1\n\n' +
          'event: error\ndata: {}\n\n' +
          'event: done\ndata: {}\n\n',
      ]),
    });

    const { postComposeEditStream } = await import('./aiPostComposeService');
    const events: any[] = [];
    await postComposeEditStream({ snapshotId: 1 }, (ev) => events.push(ev));

    expect(events).toEqual([
      { type: 'delta', content: '' },
      { type: 'delta', content: '' },
      { type: 'error', message: '请求失败' },
      { type: 'done', latencyMs: undefined },
    ]);
  });

  it('aiChatService chatStream parses meta/delta/sources/done and applies defaults', async () => {
    const { replyOnce, lastCall } = installFetchMock();
    replyOnce({
      ok: true,
      body: createMockStreamBody([
        'event: meta\ndata: {"sessionId": 3, "userMessageId": 1, "questionMessageId": 2}\n\n',
        'event: delta\ndata: {"content":"H"}\n\n',
        'event: delta\ndata: not-json\n\n',
        'event: sources\ndata: {"sources":[{"index":1,"postId":9,"chunkIndex":2,"score":0.9,"title":"t","url":"u"}]}\n\n',
        'event: done\ndata: {"latencyMs": 5}\n\n',
      ]),
    });

    const { chatStream } = await import('./aiChatService');
    const events: any[] = [];
    await chatStream({ message: 'hi' }, (ev) => events.push(ev));

    expect(events).toEqual([
      { type: 'meta', sessionId: 3, userMessageId: 1, questionMessageId: 2 },
      { type: 'delta', content: 'H' },
      { type: 'sources', sources: [{ index: 1, postId: 9, chunkIndex: 2, score: 0.9, title: 't', url: 'u' }] },
      { type: 'done', latencyMs: 5 },
    ]);

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toContain('/api/ai/chat/stream');
    expect(info?.method).toBe('POST');
    expect(info?.headers).toMatchObject({
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': 'csrf',
    });
    const body = JSON.parse(String(info?.body ?? '{}'));
    expect(body).toMatchObject({ message: 'hi', dryRun: false, deepThink: false, useRag: true });
  });

  it('aiChatService chatStream throws when response not ok', async () => {
    const { replyOnce } = installFetchMock();
    replyOnce({ ok: false, status: 500, text: 'bad' });
    const { chatStream } = await import('./aiChatService');
    await expect(chatStream({ message: 'hi' }, () => undefined)).rejects.toThrow('bad');
  });

  it('aiChatService chatOnce returns dto and applies defaults', async () => {
    const { replyJsonOnce, lastCall } = installFetchMock();
    replyJsonOnce({ ok: true, json: { sessionId: 1, content: 'x' } });
    const { chatOnce } = await import('./aiChatService');
    await expect(chatOnce({ message: 'hi' })).resolves.toMatchObject({ sessionId: 1, content: 'x' });

    const info = getFetchCallInfo(lastCall());
    expect(info?.url).toContain('/api/ai/chat');
    expect(info?.headers).toMatchObject({ Accept: 'application/json', 'X-XSRF-TOKEN': 'csrf' });
    const body = JSON.parse(String(info?.body ?? '{}'));
    expect(body).toMatchObject({ message: 'hi', dryRun: false, deepThink: false, useRag: true });
  });

  it('aiChatService chatOnce throws backend message on failure', async () => {
    const { replyJsonOnce } = installFetchMock();
    replyJsonOnce({ ok: false, status: 400, json: { message: 'bad' } });
    const { chatOnce } = await import('./aiChatService');
    await expect(chatOnce({ message: 'hi' })).rejects.toThrow('bad');
  });

  it('aiChatService regenerateOnce and regenerateStream build urls and handle errors', async () => {
    const { replyOnce, replyJsonOnce, lastCall } = installFetchMock();

    replyOnce({
      ok: true,
      body: createMockStreamBody(['event: done\ndata: {}\n\n']),
    });
    const { regenerateStream, regenerateOnce } = await import('./aiChatService');
    await regenerateStream(7, {}, () => undefined);
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/ai/qa/messages/7/regenerate/stream');

    replyJsonOnce({ ok: true, json: { sessionId: 2, content: 'y' } });
    await expect(regenerateOnce(8, {})).resolves.toMatchObject({ sessionId: 2, content: 'y' });
    expect(getFetchCallInfo(lastCall())?.url).toContain('/api/ai/qa/messages/8/regenerate');

    replyJsonOnce({ ok: false, status: 500, json: { error: 'e' } });
    await expect(regenerateOnce(9, {})).rejects.toThrow('e');
  });
});
