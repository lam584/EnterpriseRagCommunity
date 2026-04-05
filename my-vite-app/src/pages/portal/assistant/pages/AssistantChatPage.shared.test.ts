import { describe, expect, it } from 'vitest';
import { flattenProviderModelOptions, mapQaMessagesToChatState } from './AssistantChatPage.shared';

describe('AssistantChatPage.shared', () => {
  it('deduplicates and sorts flat provider model options', () => {
    const result = flattenProviderModelOptions([
      {
        id: 'p2',
        name: 'Beta',
        chatModels: [{ name: 'm2' }, { name: 'm1' }, { name: 'm1' }],
      },
      {
        id: 'p1',
        name: 'Alpha',
        chatModels: [{ name: 'm3' }],
      },
      {
        id: '   ',
        name: 'ignored',
        chatModels: [{ name: 'm0' }],
      },
    ] as any);

    expect(result).toEqual([
      {
        providerId: 'p1',
        providerLabel: 'Alpha',
        model: 'm3',
        value: 'p1|m3',
      },
      {
        providerId: 'p2',
        providerLabel: 'Beta',
        model: 'm1',
        value: 'p2|m1',
      },
      {
        providerId: 'p2',
        providerLabel: 'Beta',
        model: 'm2',
        value: 'p2|m2',
      },
    ]);
  });

  it('maps persisted messages and assistant sources together', () => {
    const sources = [{ title: '文档 A', url: '/docs/a' }];
    const result = mapQaMessagesToChatState([
      {
        id: 1,
        sessionId: 10,
        role: 'USER',
        content: '你好',
        createdAt: '2026-01-01T00:00:00Z',
        tokensIn: '12' as any,
      },
      {
        id: 2,
        sessionId: 10,
        role: 'ASSISTANT',
        content: '回答',
        createdAt: '2026-01-01T00:00:01Z',
        tokensOut: '34' as any,
        latencyMs: 56,
        firstTokenLatencyMs: '7' as any,
        isFavorite: true,
        sources: sources as any,
      },
      {
        id: 3,
        sessionId: 10,
        role: 'SYSTEM',
        content: '系统提示',
        createdAt: '2026-01-01T00:00:02Z',
        model: 'sys',
      },
    ] as any);

    expect(result.messages).toEqual([
      {
        id: '1',
        role: 'user',
        content: '你好',
        createdAt: '2026-01-01T00:00:00Z',
        model: null,
        tokensIn: 12,
        tokensOut: null,
        latencyMs: null,
        isFavorite: undefined,
        firstTokenLatencyMs: null,
      },
      {
        id: '2',
        role: 'assistant',
        content: '回答',
        createdAt: '2026-01-01T00:00:01Z',
        model: null,
        tokensIn: null,
        tokensOut: 34,
        latencyMs: 56,
        isFavorite: true,
        firstTokenLatencyMs: 7,
      },
      {
        id: '3',
        role: 'system',
        content: '系统提示',
        createdAt: '2026-01-01T00:00:02Z',
        model: 'sys',
        tokensIn: null,
        tokensOut: null,
        latencyMs: null,
        isFavorite: undefined,
        firstTokenLatencyMs: null,
      },
    ]);
    expect(result.sourcesByMsgId).toEqual({ '2': sources });
  });
});
