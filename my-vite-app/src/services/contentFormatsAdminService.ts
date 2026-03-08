import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

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

