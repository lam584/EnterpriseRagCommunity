import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

export type OpenSearchTokenizeResponse = {
  request_id?: string;
  latency?: number;
  code?: string;
  message?: string;
  usage?: { input_tokens?: number };
  result?: { token_ids?: number[]; tokens?: string[] };
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
    try {
      const obj = text ? (JSON.parse(text) as Record<string, unknown>) : null;
      const msg = obj && typeof obj.message === 'string' ? obj.message : '';
      throw new Error(msg || text || `请求失败: ${res.status}`);
    } catch {
      throw new Error(text || `请求失败: ${res.status}`);
    }
  }

  return (await res.json()) as T;
}

export async function tokenizeText(text: string, opts?: { workspaceName?: string; serviceId?: string }) {
  return apiFetch<OpenSearchTokenizeResponse>('/api/ai/tokenizer', {
    method: 'POST',
    body: JSON.stringify({ text, ...opts })
  });
}
