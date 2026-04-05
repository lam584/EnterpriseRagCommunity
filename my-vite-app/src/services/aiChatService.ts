import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { consumeSseResponse } from './serviceSseUtils';
import { parseSseBlock } from './serviceSseUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

function getLooseBackendMessage(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const record = data as Record<string, unknown>;
  const value = record.message ?? record.error;
  return value == null ? undefined : String(value);
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
    commentId?: number | null;
  chunkIndex?: number | null;
  score?: number | null;
  title?: string | null;
  url?: string | null;
  snippet?: string | null;
};

export type AiChatStreamRequest = {
  sessionId?: number;
  message: string;
  model?: string;
  providerId?: string;
  temperature?: number;
  topP?: number;
  historyLimit?: number;
  deepThink?: boolean;
  useRag?: boolean;
  ragTopK?: number;
  dryRun?: boolean;
  images?: Array<{ url: string; mimeType?: string; fileAssetId?: number }>;
  files?: Array<{ url: string; mimeType?: string; fileAssetId?: number; fileName?: string }>;
};

export type AiChatRegenerateStreamRequest = {
  model?: string;
  providerId?: string;
  temperature?: number;
  topP?: number;
  historyLimit?: number;
  deepThink?: boolean;
  useRag?: boolean;
  ragTopK?: number;
  dryRun?: boolean;
};

export type AiChatResponse = {
  sessionId: number;
  userMessageId?: number;
  questionMessageId?: number;
  assistantMessageId?: number;
  content: string;
  sources?: AiCitationSource[];
  latencyMs?: number;
};

function parseEventBlock(block: string): AiStreamEvent | null {
  const { eventType, dataStr } = parseSseBlock(block);
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
                  commentId: (x as Record<string, unknown>).commentId as number | null | undefined,
                chunkIndex: (x as Record<string, unknown>).chunkIndex as number | null | undefined,
                score: (x as Record<string, unknown>).score as number | null | undefined,
                title: ((x as Record<string, unknown>).title as string | null | undefined) ?? null,
                url: ((x as Record<string, unknown>).url as string | null | undefined) ?? null,
                snippet: ((x as Record<string, unknown>).snippet as string | null | undefined) ?? null,
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
    body: JSON.stringify({
      ...payload,
      dryRun: payload.dryRun ?? false,
      deepThink: payload.deepThink ?? false,
      useRag: payload.useRag ?? true,
    }),
    signal
  });

  await consumeSseResponse(res, parseEventBlock, onEvent);
}

export async function chatOnce(payload: AiChatStreamRequest, signal?: AbortSignal): Promise<AiChatResponse> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl('/api/ai/chat'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      Accept: 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify({
      ...payload,
      dryRun: payload.dryRun ?? false,
      deepThink: payload.deepThink ?? false,
      useRag: payload.useRag ?? true,
    }),
    signal,
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getLooseBackendMessage(data) || getBackendMessage(data) || `请求失败: ${res.status}`);
  return data as AiChatResponse;
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
    body: JSON.stringify({
      ...payload,
      dryRun: payload.dryRun ?? false,
      deepThink: payload.deepThink ?? false,
      useRag: payload.useRag ?? true,
    }),
    signal
  });

  await consumeSseResponse(res, parseEventBlock, onEvent);
}

export async function regenerateOnce(
  questionMessageId: number,
  payload: AiChatRegenerateStreamRequest,
  signal?: AbortSignal
): Promise<AiChatResponse> {
  const csrfToken = await getCsrfToken();

  const res = await fetch(apiUrl(`/api/ai/qa/messages/${questionMessageId}/regenerate`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      Accept: 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify({
      ...payload,
      dryRun: payload.dryRun ?? false,
      deepThink: payload.deepThink ?? false,
      useRag: payload.useRag ?? true,
    }),
    signal,
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getLooseBackendMessage(data) || getBackendMessage(data) || `请求失败: ${res.status}`);
  return data as AiChatResponse;
}
