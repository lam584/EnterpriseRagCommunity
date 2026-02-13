import { getCsrfToken } from '../utils/csrfUtils';

export type LogRetentionMode = 'ARCHIVE_TABLE' | 'DELETE';

export type LogRetentionConfigDTO = {
  enabled: boolean;
  keepDays: number;
  mode: LogRetentionMode;
};

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

