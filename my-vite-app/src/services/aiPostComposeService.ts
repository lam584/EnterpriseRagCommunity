import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

export type PostComposeAiStreamEvent =
  | { type: 'meta'; snapshotId: number }
  | { type: 'delta'; content: string }
  | { type: 'error'; message: string }
  | { type: 'done'; latencyMs?: number };

export type AiPostComposeStreamRequest = {
  snapshotId: number;
  instruction?: string;
  currentTitle?: string;
  currentContent?: string;
  chatHistory?: Array<{ role: 'user' | 'assistant'; content: string }>;
  model?: string;
  providerId?: string;
  temperature?: number;
  topP?: number;
  deepThink?: boolean;
  images?: Array<{ url: string; mimeType?: string; fileAssetId?: number }>;
};

function parseEventBlock(block: string): PostComposeAiStreamEvent | null {
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

  if (!eventType) return null;
  const dataStr = dataLines.join('\n');

  try {
    const data: unknown = dataStr ? JSON.parse(dataStr) : {};
    const obj = data && typeof data === 'object' ? (data as Record<string, unknown>) : {};

    switch (eventType) {
      case 'meta':
        return { type: 'meta', snapshotId: Number(obj.snapshotId) };
      case 'delta':
        return { type: 'delta', content: String(obj.content ?? '') };
      case 'error':
        return { type: 'error', message: String(obj.message ?? '请求失败') };
      case 'done':
        return { type: 'done', latencyMs: obj.latencyMs as number | undefined };
      default:
        return null;
    }
  } catch {
    return null;
  }
}

export async function postComposeEditStream(
  payload: AiPostComposeStreamRequest,
  onEvent: (ev: PostComposeAiStreamEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/ai/post-compose/stream'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      Accept: 'text/event-stream',
    },
    credentials: 'include',
    body: JSON.stringify({
      ...payload,
      deepThink: payload.deepThink ?? false,
    }),
    signal,
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
