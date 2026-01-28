import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

export type SupportedLanguageDTO = {
  languageCode: string;
  displayName: string;
  nativeName?: string | null;
  sortOrder?: number | null;
};

function getBackendMessage(data: unknown): string | null {
  if (!data || typeof data !== 'object') return null;
  const d = data as Record<string, unknown>;
  const msg = d.message ?? d.error;
  return msg == null ? null : String(msg);
}

export async function listSupportedLanguages(): Promise<SupportedLanguageDTO[]> {
  const res = await fetch(apiUrl('/api/ai/supported-languages'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ([]));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '获取支持语言列表失败');
  }
  return (Array.isArray(data) ? data : []) as SupportedLanguageDTO[];
}

export async function adminUpsertSupportedLanguage(payload: SupportedLanguageDTO): Promise<SupportedLanguageDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/ai/supported-languages'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '新增语言失败');
  return data as SupportedLanguageDTO;
}

export async function adminUpdateSupportedLanguage(originalLanguageCode: string, payload: SupportedLanguageDTO): Promise<SupportedLanguageDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/ai/supported-languages/${encodeURIComponent(originalLanguageCode)}`), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '修改语言失败');
  return data as SupportedLanguageDTO;
}

export async function adminDeleteSupportedLanguage(languageCode: string): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/ai/supported-languages/${encodeURIComponent(languageCode)}`), {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '删除语言失败');
}
