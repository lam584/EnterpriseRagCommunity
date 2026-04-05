import { getCsrfToken } from '../utils/csrfUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export type TranslatePreferencesDTO = {
  targetLanguage: string;
  autoTranslatePosts: boolean;
  autoTranslateComments: boolean;
  titleGenCount?: number | null;
  tagGenCount?: number | null;
};

function getBackendMessage(data: unknown): string | null {
  if (!data || typeof data !== 'object') return null;
  const d = data as Record<string, unknown>;
  const msg = d.message ?? d.error;
  return msg == null ? null : String(msg);
}

export async function getMyTranslatePreferences(): Promise<TranslatePreferencesDTO> {
  const res = await fetch(apiUrl('/api/account/preferences'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '获取偏好失败');
  }
  return data as TranslatePreferencesDTO;
}

export async function updateMyTranslatePreferences(payload: Partial<TranslatePreferencesDTO>): Promise<TranslatePreferencesDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/account/preferences'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '保存偏好失败');
  }
  return data as TranslatePreferencesDTO;
}
