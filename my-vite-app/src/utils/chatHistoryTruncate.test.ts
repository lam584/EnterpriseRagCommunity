import { describe, expect, it } from 'vitest';
import { canResendEditedUserMessage, computeTruncateAfterAssistant, type ChatMsgLike } from './chatHistoryTruncate';

describe('chatHistoryTruncate', () => {
  it('computeTruncateAfterAssistant returns no-op when assistant id not found', () => {
    const messages: ChatMsgLike[] = [
      { id: '1', role: 'user' },
      { id: '2', role: 'assistant' }
    ];
    expect(computeTruncateAfterAssistant(messages, '999')).toEqual({ keepCount: 2, deleteIds: [], deletePersistedIds: [] });
  });

  it('computeTruncateAfterAssistant returns no-op when assistant is the last message', () => {
    const messages: ChatMsgLike[] = [
      { id: '1', role: 'user' },
      { id: '2', role: 'assistant' }
    ];
    expect(computeTruncateAfterAssistant(messages, '2')).toEqual({ keepCount: 2, deleteIds: [], deletePersistedIds: [] });
  });

  it('computeTruncateAfterAssistant skips deleting assistant when its user will be deleted', () => {
    const messages: ChatMsgLike[] = [
      { id: '1', role: 'user' },
      { id: '2', role: 'assistant' },
      { id: '3', role: 'assistant' },
      { id: '7', role: 'user' },
      { id: '8', role: 'assistant' }
    ];
    const plan = computeTruncateAfterAssistant(messages, '3');
    expect(plan.keepCount).toBe(3);
    expect(plan.deleteIds).toEqual(['7', '8']);
    expect(plan.deletePersistedIds).toEqual([7]);
  });

  it('computeTruncateAfterAssistant tolerates missing items in array', () => {
    const messages = [
      { id: '1', role: 'user' },
      { id: '2', role: 'assistant' },
      { id: '3', role: 'assistant' },
      undefined,
      { id: '8', role: 'assistant' }
    ] as unknown as ChatMsgLike[];
    const plan = computeTruncateAfterAssistant(messages, '3');
    expect(plan.keepCount).toBe(3);
  });

  it('computeTruncateAfterAssistant deletes assistant if previous user is not persisted', () => {
    const messages: ChatMsgLike[] = [
      { id: '1', role: 'user' },
      { id: '2', role: 'assistant' },
      { id: '3', role: 'assistant' },
      { id: 'tmp', role: 'user' },
      { id: '8', role: 'assistant' }
    ];
    const plan = computeTruncateAfterAssistant(messages, '3');
    expect(plan.deletePersistedIds).toEqual([8]);
  });

  it('canResendEditedUserMessage allows resend even if content is unchanged', () => {
    expect(
      canResendEditedUserMessage({
        isUser: true,
        isEditing: true,
        draft: 'hi',
        nextRole: 'assistant',
        nextId: '123',
        isStreaming: false
      })
    ).toBe(true);
  });

  it('canResendEditedUserMessage blocks invalid states', () => {
    expect(
      canResendEditedUserMessage({
        isUser: true,
        isEditing: true,
        draft: 'hi',
        nextRole: 'assistant',
        nextId: 'tmp',
        isStreaming: false
      })
    ).toBe(false);
    expect(
      canResendEditedUserMessage({
        isUser: true,
        isEditing: true,
        draft: '   ',
        nextRole: 'assistant',
        nextId: '1',
        isStreaming: false
      })
    ).toBe(false);
    expect(
      canResendEditedUserMessage({
        isUser: true,
        isEditing: false,
        draft: 'hi',
        nextRole: 'assistant',
        nextId: '1',
        isStreaming: false
      })
    ).toBe(false);
    expect(
      canResendEditedUserMessage({
        isUser: true,
        isEditing: true,
        draft: 'hi',
        nextRole: 'assistant',
        nextId: '1',
        isStreaming: true
      })
    ).toBe(false);
    expect(
      canResendEditedUserMessage({
        isUser: false,
        isEditing: true,
        draft: 'hi',
        nextRole: 'assistant',
        nextId: '1',
        isStreaming: false
      })
    ).toBe(false);
    expect(
      canResendEditedUserMessage({
        isUser: true,
        isEditing: true,
        draft: 'hi',
        nextRole: 'user',
        nextId: '1',
        isStreaming: false
      })
    ).toBe(false);
  });
});
