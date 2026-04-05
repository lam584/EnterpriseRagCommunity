import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export type SupportedLanguageDTO = {
  languageCode: string;
  displayName: string;
  nativeName?: string | null;
  sortOrder?: number | null;
};

export function normalizeSupportedLanguages(data: unknown): SupportedLanguageDTO[] {
  if (!Array.isArray(data)) return [];
  return data.filter((x): x is SupportedLanguageDTO => {
    return Boolean(x && typeof x === 'object' && typeof (x as SupportedLanguageDTO).languageCode === 'string' && typeof (x as SupportedLanguageDTO).displayName === 'string');
  });
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
  return normalizeSupportedLanguages(data);
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
