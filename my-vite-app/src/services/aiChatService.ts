  import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

export type AiStreamEvent =
  | { type: 'meta'; sessionId: number; userMessageId?: number; questionMessageId?: number }
  | { type: 'delta'; content: string }
  | { type: 'sources'; sources: AiCitationSource[] }
  | { type: 'error'; message: string }
  | { type: 'done'; latencyMs?: number };

export type AiCitationSource = {
  index: number;
  postId?: number | null;
  chunkIndex?: number | null;
  score?: number | null;
  title?: string | null;
  url?: string | null;
};

export type AiChatStreamRequest = {
  sessionId?: number;
  message: string;
  model?: string;
  temperature?: number;
  historyLimit?: number;
  deepThink?: boolean;
  dryRun?: boolean;
};

export type AiChatRegenerateStreamRequest = {
  model?: string;
  temperature?: number;
  historyLimit?: number;
  deepThink?: boolean;
  dryRun?: boolean;
};

function parseEventBlock(block: string): AiStreamEvent | null {
  // block contains lines like:
  // event: delta
  // data: {...}
  const lines = block
    .split(/\r?\n/)
    .map((l) => l.trimEnd())
    .filter((l) => l.trim() !== '');

  let eventType: string | undefined;
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventType = line.slice('event:'.length).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice('data:'.length).trim());
    }
  }

  const dataStr = dataLines.join('\n');

  if (!eventType) return null;

  try {
    const data: unknown = dataStr ? JSON.parse(dataStr) : {};
    const obj = (data && typeof data === 'object') ? (data as Record<string, unknown>) : {};

    switch (eventType) {
      case 'meta':
        return {
          type: 'meta',
          sessionId: Number(obj.sessionId),
          userMessageId: obj.userMessageId as number | undefined,
          questionMessageId: obj.questionMessageId as number | undefined
        };
      case 'delta':
        return { type: 'delta', content: String(obj.content ?? '') };
      case 'sources': {
        const arr = (obj.sources as unknown) as unknown[];
        const sources = Array.isArray(arr)
          ? arr
              .map((x) => (x && typeof x === 'object' ? (x as Record<string, unknown>) : null))
              .filter(Boolean)
              .map((x) => ({
                index: Number((x as Record<string, unknown>).index ?? 0),
                postId: (x as Record<string, unknown>).postId as number | null | undefined,
                chunkIndex: (x as Record<string, unknown>).chunkIndex as number | null | undefined,
                score: (x as Record<string, unknown>).score as number | null | undefined,
                title: ((x as Record<string, unknown>).title as string | null | undefined) ?? null,
                url: ((x as Record<string, unknown>).url as string | null | undefined) ?? null,
              }))
          : [];
        return { type: 'sources', sources };
      }
      case 'error':
        return { type: 'error', message: String(obj.message ?? '请求失败') };
      case 'done':
        return { type: 'done', latencyMs: obj.latencyMs as number | undefined };
      default:
        return null;
    }
  } catch {
    // If backend sends non-JSON data, ignore.
    return null;
  }
}

export async function chatStream(
  payload: AiChatStreamRequest,
  onEvent: (ev: AiStreamEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl('/api/ai/chat/stream'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      Accept: 'text/event-stream'
    },
    credentials: 'include',
    body: JSON.stringify({ ...payload, dryRun: payload.dryRun ?? false, deepThink: payload.deepThink ?? false }),
    signal
  });

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(text || `请求失败: ${res.status}`);
  }

  const reader = res.body?.getReader();
  if (!reader) {
    throw new Error('浏览器不支持流式响应');
  }

  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    // SSE events are separated by double newline
    let idx: number;
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);

      const ev = parseEventBlock(block);
      if (ev) onEvent(ev);
    }
  }
}

export async function regenerateStream(
  questionMessageId: number,
  payload: AiChatRegenerateStreamRequest,
  onEvent: (ev: AiStreamEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl(`/api/ai/qa/messages/${questionMessageId}/regenerate/stream`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      Accept: 'text/event-stream'
    },
    credentials: 'include',
    body: JSON.stringify({ ...payload, dryRun: payload.dryRun ?? false, deepThink: payload.deepThink ?? false }),
    signal
  });

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(text || `请求失败: ${res.status}`);
  }

  const reader = res.body?.getReader();
  if (!reader) {
    throw new Error('浏览器不支持流式响应');
  }

  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    let idx: number;
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const block = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);

      const ev = parseEventBlock(block);
      if (ev) onEvent(ev);
    }
  }
}
