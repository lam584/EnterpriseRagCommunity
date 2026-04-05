import { getCsrfToken } from '../utils/csrfUtils';
import type { AiCitationSource } from './aiChatService';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export type QaSessionDTO = {
  id: number;
  title: string | null;
  contextStrategy: 'RECENT_N' | 'SUMMARIZE' | 'NONE';
  isActive: boolean;
  createdAt: string;
  lastMessageAt?: string | null;
  lastMessagePreview?: string | null;
};

export type QaMessageDTO = {
  id: number;
  sessionId: number;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  model?: string | null;
  tokensIn?: number | null;
  tokensOut?: number | null;
  latencyMs?: number | null;
  firstTokenLatencyMs?: number | null;
  createdAt: string;
  isFavorite?: boolean;
  sources?: AiCitationSource[] | null;
};

export type QaSearchHitDTO = {
  type: 'SESSION_TITLE' | 'MESSAGE';
  sessionId: number;
  messageId?: number | null;
  title?: string | null;
  snippet: string;
  createdAt: string;
};

export type QaCompressContextResultDTO = {
  sessionId: number;
  summaryMessageId?: number | null;
  compressedDeletedCount?: number | null;
  keptLast?: number | null;
  summary?: string | null;
};

type PageEnvelope<T> = {
    content: T[];
    totalElements: number;
    number: number;
    size: number;
};

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(path), {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      ...(init?.headers || {})
    },
    ...init
  });

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(text || `请求失败: ${res.status}`);
  }

  // 兼容 204 No Content（例如 DELETE）
  if (res.status === 204) {
    return undefined as T;
  }

  return (await res.json()) as T;
}

function normalizePageEnvelope<T>(raw: unknown, fallbackPage: number, fallbackSize: number): PageEnvelope<T> {
    const data = raw && typeof raw === 'object' ? (raw as Record<string, unknown>) : {};
    const pageMeta = data.page && typeof data.page === 'object' ? (data.page as Record<string, unknown>) : {};

    const content = Array.isArray(data.content) ? (data.content as T[]) : [];
    const totalElements =
        typeof data.totalElements === 'number'
            ? data.totalElements
            : typeof pageMeta.totalElements === 'number'
                ? pageMeta.totalElements
                : content.length;
    const number =
        typeof data.number === 'number'
            ? data.number
            : typeof pageMeta.number === 'number'
                ? pageMeta.number
                : fallbackPage;
    const size =
        typeof data.size === 'number'
            ? data.size
            : typeof pageMeta.size === 'number'
                ? pageMeta.size
                : fallbackSize;

    return {
        content,
        totalElements,
        number,
        size,
    };
}

export async function listQaSessions(page = 0, size = 20) {
    const raw = await apiFetch<unknown>(`/api/ai/qa/sessions?page=${page}&size=${size}`);
    return normalizePageEnvelope<QaSessionDTO>(raw, page, size);
}

export async function getQaSessionMessages(sessionId: number) {
  return apiFetch<QaMessageDTO[]>(`api/ai/qa/sessions/${sessionId}/messages`);
}

export async function searchQaHistory(q: string, page = 0, size = 20) {
  const qs = new URLSearchParams({ q, page: String(page), size: String(size) });
    const raw = await apiFetch<unknown>(`api/ai/qa/search?${qs.toString()}`);
    return normalizePageEnvelope<QaSearchHitDTO>(raw, page, size);
}

export async function updateQaSession(sessionId: number, payload: { title?: string; isActive?: boolean }) {
  return apiFetch<QaSessionDTO>(`api/ai/qa/sessions/${sessionId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export async function compressQaSessionContext(sessionId: number) {
  return apiFetch<QaCompressContextResultDTO>(`api/ai/qa/sessions/${sessionId}/compress-context`, {
    method: 'POST'
  });
}

export async function deleteQaSession(sessionId: number) {
  return apiFetch<void>(`api/ai/qa/sessions/${sessionId}`, {
    method: 'DELETE'
  });
}

export async function updateQaMessage(messageId: number, payload: { content: string }) {
  return apiFetch<void>(`api/ai/qa/messages/${messageId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export async function deleteQaMessage(messageId: number) {
  return apiFetch<void>(`api/ai/qa/messages/${messageId}`, {
    method: 'DELETE'
  });
}

export async function toggleQaMessageFavorite(messageId: number) {
  return apiFetch<boolean>(`api/ai/qa/messages/${messageId}/favorite`, {
    method: 'PATCH'
  });
}

export async function listFavoriteQaMessages(page = 0, size = 20) {
    const raw = await apiFetch<unknown>(`api/ai/qa/favorites?page=${page}&size=${size}`);
    return normalizePageEnvelope<QaMessageDTO>(raw, page, size);
}
