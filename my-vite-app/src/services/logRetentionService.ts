import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

export type LogRetentionMode = 'ARCHIVE_TABLE' | 'DELETE';

export type LogRetentionConfigDTO = {
  enabled: boolean;
  keepDays: number;
  mode: LogRetentionMode;
};

const apiUrl = serviceApiUrl;

export async function adminGetLogRetentionConfig(): Promise<LogRetentionConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/log-retention'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取配置失败');
  return data as LogRetentionConfigDTO;
}

export async function adminUpdateLogRetentionConfig(payload: LogRetentionConfigDTO): Promise<LogRetentionConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/log-retention'), {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    body: JSON.stringify(payload),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '更新配置失败');
  return data as LogRetentionConfigDTO;
}
