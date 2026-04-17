import { getCsrfToken } from '../utils/csrfUtils';
import type { SpringPage } from '../types/page';
import { getBackendMessage } from './serviceErrorUtils';
import { buildQuery } from './serviceQueryUtils';
import { serviceApiUrl } from './serviceUrlUtils';

export type AccessLogDTO = {
  id: number;
  createdAt: string;

  tenantId?: number | null;
  userId?: number | null;
  username?: string | null;

  method?: string | null;
  path?: string | null;
  queryString?: string | null;

  statusCode?: number | null;
  latencyMs?: number | null;

  clientIp?: string | null;
  clientPort?: number | null;
  serverIp?: string | null;
  serverPort?: number | null;

  host?: string | null;
  scheme?: string | null;

  requestId?: string | null;
  traceId?: string | null;

  userAgent?: string | null;
  referer?: string | null;

  details?: Record<string, unknown> | null;
};

export type AccessLogPageQuery = {
  page?: number;
  pageSize?: number;
  keyword?: string;

  userId?: number;
  username?: string;
  method?: string;
  path?: string;
  statusCode?: number;

  clientIp?: string;
  requestId?: string;
  traceId?: string;

  createdFrom?: string;
  createdTo?: string;
  sort?: string;
};

export type AccessLogEsIndexStatusDTO = {
  indexName: string;
  collectionName: string;
  sinkMode: string;
  esSinkEnabled: boolean;
  consumerEnabled: boolean;
  exists: boolean;
  available: boolean;
  health?: string | null;
  status?: string | null;
  docsCount?: number | null;
  storeSize?: string | null;
  availabilityMessage?: string | null;
};

const apiUrl = serviceApiUrl;

export async function adminListAccessLogs(query: AccessLogPageQuery = {}): Promise<SpringPage<AccessLogDTO>> {
  const qs = buildQuery({
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
    keyword: query.keyword,
    userId: query.userId,
    username: query.username,
    method: query.method,
    path: query.path,
    statusCode: query.statusCode,
    clientIp: query.clientIp,
    requestId: query.requestId,
    traceId: query.traceId,
    createdFrom: query.createdFrom,
    createdTo: query.createdTo,
    sort: query.sort ?? 'createdAt,desc',
  });

  const res = await fetch(apiUrl(`/api/admin/access-logs${qs}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取访问日志失败');
  return data as SpringPage<AccessLogDTO>;
}

export async function adminGetAccessLogDetail(id: string | number): Promise<AccessLogDTO> {
  const res = await fetch(apiUrl(`/api/admin/access-logs/${encodeURIComponent(String(id))}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取日志详情失败');
  return data as AccessLogDTO;
}

export async function adminExportAccessLogsCsv(query: Omit<AccessLogPageQuery, 'page' | 'pageSize'> = {}): Promise<Blob> {
  const csrfToken = await getCsrfToken();
  const qs = buildQuery({
    keyword: query.keyword,
    userId: query.userId,
    username: query.username,
    method: query.method,
    path: query.path,
    statusCode: query.statusCode,
    clientIp: query.clientIp,
    requestId: query.requestId,
    traceId: query.traceId,
    createdFrom: query.createdFrom,
    createdTo: query.createdTo,
    sort: query.sort ?? 'createdAt,desc',
  });

  const res = await fetch(apiUrl(`/api/admin/access-logs/export.csv${qs}`), {
    method: 'POST',
    credentials: 'include',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
  });

  if (!res.ok) {
    const data: unknown = await res.json().catch(() => ({}));
    throw new Error(getBackendMessage(data) || '导出失败');
  }

  return res.blob();
}

export async function adminGetAccessLogEsIndexStatus(): Promise<AccessLogEsIndexStatusDTO> {
  const res = await fetch(apiUrl('/api/admin/access-logs/es-index-status'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取日志索引状态失败');
  return data as AccessLogEsIndexStatusDTO;
}
