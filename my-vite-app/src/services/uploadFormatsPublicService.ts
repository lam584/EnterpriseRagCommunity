import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';
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

export async function getUploadFormatsConfig(): Promise<UploadFormatsConfigDTO> {
  const res = await fetch(apiUrl('/api/public/uploads/formats-config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取上传格式配置失败');
  return data as UploadFormatsConfigDTO;
}

