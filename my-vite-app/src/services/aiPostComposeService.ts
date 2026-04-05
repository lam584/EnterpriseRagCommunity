import { getCsrfToken } from '../utils/csrfUtils';
import { consumeSseResponse } from './serviceSseUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

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
  const body: Record<string, unknown> = { ...payload };
  if (payload.deepThink === undefined) delete body.deepThink;
  const res = await fetch(apiUrl('/api/ai/post-compose/stream'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      Accept: 'text/event-stream',
    },
    credentials: 'include',
    body: JSON.stringify(body),
    signal,
  });

  await consumeSseResponse(res, parseEventBlock, onEvent);
}
