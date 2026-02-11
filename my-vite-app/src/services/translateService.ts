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

export async function translatePost(
  postId: number,
  targetLang: string,
  onProgress?: (partial: TranslateResultDTO) => void
): Promise<TranslateResultDTO> {
  const csrfToken = await getCsrfToken();
  const qs = new URLSearchParams({ targetLang: targetLang ?? '' }).toString();
  return fetchStreamTranslate(apiUrl(`/api/ai/posts/${postId}/translate?${qs}`), csrfToken, onProgress);
}

export async function translateComment(
  commentId: number,
  targetLang: string,
  onProgress?: (partial: TranslateResultDTO) => void
): Promise<TranslateResultDTO> {
  const csrfToken = await getCsrfToken();
  const qs = new URLSearchParams({ targetLang: targetLang ?? '' }).toString();
  return fetchStreamTranslate(apiUrl(`/api/ai/comments/${commentId}/translate?${qs}`), csrfToken, onProgress);
}

async function fetchStreamTranslate(
  url: string,
  csrfToken: string,
  onProgress?: (partial: TranslateResultDTO) => void
): Promise<TranslateResultDTO> {
  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({}),
  });

  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(getBackendMessage(data) || '翻译失败');
  }

  const reader = res.body?.getReader();
  if (!reader) {
    // Should not happen if backend returns SseEmitter, but fallback just in case
    const data = await res.json();
    return data as TranslateResultDTO;
  }

  const decoder = new TextDecoder();
  let buffer = '';
  let accumulatedMarkdown = '';
  let finalResult: TranslateResultDTO = {};

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const jsonStr = line.slice(5).trim();
        if (!jsonStr || jsonStr === '[DONE]') continue;
        try {
          const data = JSON.parse(jsonStr);
          if (data.delta) {
            accumulatedMarkdown += data.delta;
            if (onProgress) {
              onProgress({
                translatedMarkdown: accumulatedMarkdown,
                cached: false,
              });
            }
          } else if (data.translatedMarkdown || data.cached !== undefined) {
            // Full object or cached object
            finalResult = data as TranslateResultDTO;
            if (onProgress) onProgress(finalResult);
          }
        } catch (e) {
          console.error('SSE parse error', e);
        }
      }
    }
  }
  
  // If we accumulated markdown but finalResult is empty (e.g. error before end), use accumulated
  if (!finalResult.translatedMarkdown && accumulatedMarkdown) {
      finalResult.translatedMarkdown = accumulatedMarkdown;
  }
  
  return finalResult;
}
