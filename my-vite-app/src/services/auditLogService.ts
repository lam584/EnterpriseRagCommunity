import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { buildQuery } from './serviceQueryUtils';
import { serviceApiUrl } from './serviceUrlUtils';
import type { SpringPage } from '../types/page';

export type AuditLogAction =
  | 'QUEUE_ENQUEUE'
  | 'QUEUE_ASSIGN'
  | 'QUEUE_STAGE'
  | 'QUEUE_DECISION'
  | 'RULE_DECISION'
  | 'VEC_DECISION'
  | 'LLM_DECISION'
  | 'RISK_TAGS_GENERATED'
  | 'CONFIG_CHANGE'
  | 'UNKNOWN';

export type AuditLogEntityType = 'MODERATION_QUEUE' | 'POST' | 'COMMENT' | 'SYSTEM' | 'UNKNOWN';

export type AuditLogResult = 'SUCCESS' | 'FAIL' | 'SKIP' | 'UNKNOWN';

export type AuditLogDTO = {
  id: number;
  createdAt: string;
  actorType?: 'ADMIN' | 'SYSTEM' | 'USER' | string | null;
  actorId?: number | null;
  actorName?: string | null;

  action: AuditLogAction | string;
  entityType: AuditLogEntityType | string;
  entityId?: number | null;

  result?: AuditLogResult | string | null;
  message?: string | null;
  ip?: string | null;
  traceId?: string | null;
  method?: string | null;
  path?: string | null;
  autoCrud?: boolean | null;

  details?: Record<string, unknown> | null;
};

export type AuditLogPageQuery = {
  page?: number;
  pageSize?: number;

  keyword?: string;
  actorId?: number;
  actorName?: string;

  action?: string;
  op?: string;
  entityType?: string;
  entityId?: number;
  result?: string;

  createdFrom?: string; // ISO
  createdTo?: string; // ISO

  traceId?: string;
  sort?: string; // eg: createdAt,desc
};

const apiUrl = serviceApiUrl;

export async function adminListAuditLogs(query: AuditLogPageQuery = {}): Promise<SpringPage<AuditLogDTO>> {
  const qs = buildQuery({
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
    keyword: query.keyword,
    actorId: query.actorId,
    actorName: query.actorName,
    action: query.action,
    op: query.op,
    entityType: query.entityType,
    entityId: query.entityId,
    result: query.result,
    createdFrom: query.createdFrom,
    createdTo: query.createdTo,
    traceId: query.traceId,
    sort: query.sort ?? 'createdAt,desc',
  });

  const res = await fetch(apiUrl(`/api/admin/audit-logs${qs}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取审核日志失败');
  return data as SpringPage<AuditLogDTO>;
}

export async function adminGetAuditLogDetail(id: number): Promise<AuditLogDTO> {
  const res = await fetch(apiUrl(`/api/admin/audit-logs/${id}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取日志详情失败');
  return data as AuditLogDTO;
}

export async function adminExportAuditLogsCsv(query: Omit<AuditLogPageQuery, 'page' | 'pageSize'> = {}): Promise<Blob> {
  const csrfToken = await getCsrfToken();
  const qs = buildQuery({
    keyword: query.keyword,
    actorId: query.actorId,
    actorName: query.actorName,
    action: query.action,
    op: query.op,
    entityType: query.entityType,
    entityId: query.entityId,
    result: query.result,
    createdFrom: query.createdFrom,
    createdTo: query.createdTo,
    traceId: query.traceId,
    sort: query.sort ?? 'createdAt,desc',
  });

  const res = await fetch(apiUrl(`/api/admin/audit-logs/export.csv${qs}`), {
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

export async function portalListMyAuditLogs(query: Omit<AuditLogPageQuery, 'actorId' | 'actorName'> = {}): Promise<SpringPage<AuditLogDTO>> {
  const qs = buildQuery({
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
    keyword: query.keyword,
    action: query.action,
    op: query.op,
    entityType: query.entityType,
    entityId: query.entityId,
    result: query.result,
    createdFrom: query.createdFrom,
    createdTo: query.createdTo,
    traceId: query.traceId,
    sort: query.sort ?? 'createdAt,desc',
  });

  const res = await fetch(apiUrl(`/api/portal/audit-logs/me${qs}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取治理记录失败');
  return data as SpringPage<AuditLogDTO>;
}

export async function portalExportMyAuditLogsCsv(
  query: Omit<AuditLogPageQuery, 'page' | 'pageSize' | 'actorId' | 'actorName'> = {}
): Promise<Blob> {
  const csrfToken = await getCsrfToken();
  const qs = buildQuery({
    keyword: query.keyword,
    action: query.action,
    op: query.op,
    entityType: query.entityType,
    entityId: query.entityId,
    result: query.result,
    createdFrom: query.createdFrom,
    createdTo: query.createdTo,
    traceId: query.traceId,
    sort: query.sort ?? 'createdAt,desc',
  });

  const res = await fetch(apiUrl(`/api/portal/audit-logs/me/export.csv${qs}`), {
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
