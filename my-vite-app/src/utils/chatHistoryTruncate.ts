export type ChatRole = 'user' | 'assistant' | 'system';

export type ChatMsgLike = {
  id: string;
  role: ChatRole;
};

export function computeTruncateAfterAssistant(
  messages: ChatMsgLike[],
  assistantMessageId: string
): { keepCount: number; deleteIds: string[]; deletePersistedIds: number[] } {
  const idx = messages.findIndex((m) => m.id === assistantMessageId);
  if (idx < 0) return { keepCount: messages.length, deleteIds: [], deletePersistedIds: [] };
  if (idx >= messages.length - 1) return { keepCount: idx + 1, deleteIds: [], deletePersistedIds: [] };

  const suffix = messages.slice(idx + 1);
  const deleteIds = suffix.flatMap((m) => (m ? [m.id] : []));
  const deletePersistedIds: number[] = [];

  for (let i = messages.length - 1; i > idx; i--) {
    const m = messages[i];
    if (!m) continue;
    if (!/^\d+$/.test(m.id)) continue;
    if (m.role === 'assistant') {
      const prev = messages[i - 1];
      if (prev?.role === 'user' && i - 1 > idx && /^\d+$/.test(prev.id)) continue;
    }
    deletePersistedIds.push(Number(m.id));
  }

  return { keepCount: idx + 1, deleteIds, deletePersistedIds };
}

export function canResendEditedUserMessage(args: {
  isUser: boolean;
  isEditing: boolean;
  draft: string;
  nextRole?: ChatRole;
  nextId?: string;
  isStreaming: boolean;
}): boolean {
  if (!args.isUser) return false;
  if (!args.isEditing) return false;
  if (args.isStreaming) return false;
  if (!args.draft.trim()) return false;
  if (args.nextRole !== 'assistant') return false;
  if (!args.nextId || !/^\d+$/.test(args.nextId)) return false;
  return true;
}
