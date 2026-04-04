import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export type PostLangLabelGenConfig = {
  enabled: boolean;
  promptCode: string;
  model?: string | null;
  providerId?: string | null;
  temperature?: number | null;
  topP?: number | null;
  enableThinking?: boolean | null;
  maxContentChars: number;
};

export type PostLangLabelGenConfigDTO = PostLangLabelGenConfig & {
  id?: number | null;
  version?: number | null;
  updatedAt?: string | null;
  updatedBy?: string | null;
};

export async function adminGetPostLangLabelGenConfig(): Promise<PostLangLabelGenConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/semantic/lang-label/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取语言标签生成配置失败');
  return data as PostLangLabelGenConfigDTO;
}

export async function adminUpsertPostLangLabelGenConfig(payload: PostLangLabelGenConfig): Promise<PostLangLabelGenConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/semantic/lang-label/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存语言标签生成配置失败');
  return data as PostLangLabelGenConfigDTO;
}
