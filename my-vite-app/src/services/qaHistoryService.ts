import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

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
  createdAt: string;
};

export type QaSearchHitDTO = {
  type: 'SESSION_TITLE' | 'MESSAGE';
  sessionId: number;
  messageId?: number | null;
  title?: string | null;
  snippet: string;
  createdAt: string;
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

export async function listQaSessions(page = 0, size = 20) {
  return apiFetch<{ content: QaSessionDTO[]; totalElements: number; number: number; size: number }>(
    `/api/ai/qa/sessions?page=${page}&size=${size}`
  );
}

export async function getQaSessionMessages(sessionId: number) {
  return apiFetch<QaMessageDTO[]>(`/api/ai/qa/sessions/${sessionId}/messages`);
}

export async function searchQaHistory(q: string, page = 0, size = 20) {
  const qs = new URLSearchParams({ q, page: String(page), size: String(size) });
  return apiFetch<{ content: QaSearchHitDTO[]; totalElements: number; number: number; size: number }>(
    `/api/ai/qa/search?${qs.toString()}`
  );
}

export async function updateQaSession(sessionId: number, payload: { title?: string; isActive?: boolean }) {
  return apiFetch<QaSessionDTO>(`/api/ai/qa/sessions/${sessionId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  });
}

export async function deleteQaSession(sessionId: number) {
  return apiFetch<void>(`/api/ai/qa/sessions/${sessionId}`, {
    method: 'DELETE'
  });
}
