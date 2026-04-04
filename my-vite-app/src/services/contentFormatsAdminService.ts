import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export type UploadFormatRuleDTO = {
  format?: string | null;
  enabled?: boolean | null;
  extensions?: string[] | null;
  maxFileSizeBytes?: number | null;
  parseEnabled?: boolean | null;
};

export type UploadFormatsConfigDTO = {
  enabled?: boolean | null;
  maxFilesPerRequest?: number | null;
  maxFileSizeBytes?: number | null;
  maxTotalSizeBytes?: number | null;
  parseTimeoutMillis?: number | null;
  parseMaxChars?: number | null;
  formats?: UploadFormatRuleDTO[] | null;
};

export async function adminGetContentFormatsConfig(): Promise<UploadFormatsConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/content/formats/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '加载格式配置失败');
  return data as UploadFormatsConfigDTO;
}

export async function adminUpdateContentFormatsConfig(payload: UploadFormatsConfigDTO): Promise<UploadFormatsConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/content/formats/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload ?? {}),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存格式配置失败');
  return data as UploadFormatsConfigDTO;
}
