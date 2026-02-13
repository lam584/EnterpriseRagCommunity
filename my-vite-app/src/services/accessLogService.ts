import { getCsrfToken } from '../utils/csrfUtils';
import type { SpringPage } from '../types/page';

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

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function buildQuery(params: Record<string, unknown>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    sp.set(k, String(v));
  }
  const qs = sp.toString();
  return qs ? `?${qs}` : '';
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

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

export async function adminGetAccessLogDetail(id: number): Promise<AccessLogDTO> {
  const res = await fetch(apiUrl(`/api/admin/access-logs/${id}`), {
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

