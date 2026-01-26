import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

export type TranslatePublicConfigDTO = {
  enabled?: boolean;
  allowedTargetLanguages?: string[] | null;
};

export type TranslateResultDTO = {
  targetLang?: string;
  translatedTitle?: string | null;
  translatedMarkdown?: string;
  model?: string | null;
  latencyMs?: number | null;
  cached?: boolean;
};

function getBackendMessage(data: unknown): string | null {
  if (!data || typeof data !== 'object') return null;
  const d = data as Record<string, unknown>;
  const msg = d.message ?? d.error;
  return msg == null ? null : String(msg);
}

export async function getTranslateConfig(): Promise<TranslatePublicConfigDTO> {
  const res = await fetch(apiUrl('/api/ai/translate/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '获取翻译配置失败');
  }
  return data as TranslatePublicConfigDTO;
}

export async function translatePost(postId: number, targetLang: string): Promise<TranslateResultDTO> {
  const csrfToken = await getCsrfToken();
  const qs = new URLSearchParams({ targetLang: targetLang ?? '' }).toString();
  const res = await fetch(apiUrl(`/api/ai/posts/${postId}/translate?${qs}`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({}),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '翻译失败');
  }
  return data as TranslateResultDTO;
}

export async function translateComment(commentId: number, targetLang: string): Promise<TranslateResultDTO> {
  const csrfToken = await getCsrfToken();
  const qs = new URLSearchParams({ targetLang: targetLang ?? '' }).toString();
  const res = await fetch(apiUrl(`/api/ai/comments/${commentId}/translate?${qs}`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({}),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '翻译失败');
  }
  return data as TranslateResultDTO;
}
