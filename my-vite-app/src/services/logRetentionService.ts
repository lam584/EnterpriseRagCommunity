import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

export type LogRetentionMode = 'ARCHIVE_TABLE' | 'DELETE';

export type LogRetentionConfigDTO = {
  enabled: boolean;
  keepDays: number;
  mode: LogRetentionMode;
  maxPerRun: number;
  auditLogsEnabled: boolean;
  accessLogsEnabled: boolean;
  purgeArchivedEnabled: boolean;
  purgeArchivedKeepDays: number;
};

function normalizeConfig(raw: unknown): LogRetentionConfigDTO {
  const r = (raw ?? {}) as Partial<LogRetentionConfigDTO>;
  return {
    enabled: Boolean(r.enabled),
    keepDays: Number.isFinite(r.keepDays) ? Number(r.keepDays) : 90,
    mode: r.mode === 'DELETE' ? 'DELETE' : 'ARCHIVE_TABLE',
    maxPerRun: Number.isFinite(r.maxPerRun) ? Number(r.maxPerRun) : 5000,
    auditLogsEnabled: r.auditLogsEnabled !== false,
    accessLogsEnabled: r.accessLogsEnabled !== false,
    purgeArchivedEnabled: Boolean(r.purgeArchivedEnabled),
    purgeArchivedKeepDays: Number.isFinite(r.purgeArchivedKeepDays) ? Number(r.purgeArchivedKeepDays) : 365,
  };
}

const apiUrl = serviceApiUrl;

export async function adminGetLogRetentionConfig(): Promise<LogRetentionConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/log-retention'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取配置失败');
  return normalizeConfig(data);
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
  return normalizeConfig(data);
}
