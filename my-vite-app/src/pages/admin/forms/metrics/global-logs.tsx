import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  adminExportAuditLogsCsv,
  adminGetAuditLogDetail,
  adminListAuditLogs,
  type AuditLogDTO,
} from '../../../../services/auditLogService';
import {
  adminExportAccessLogsCsv,
  adminGetAccessLogDetail,
  adminGetAccessLogEsIndexStatus,
  adminListAccessLogs,
  type AccessLogEsIndexStatusDTO,
  type AccessLogDTO,
} from '../../../../services/accessLogService';
import { downloadBlob } from '../../../../utils/download';
import { adminGetLogRetentionConfig, adminUpdateLogRetentionConfig } from '../../../../services/logRetentionService';
import type { LogRetentionConfigDTO } from '../../../../services/logRetentionService';
import DetailDialog from '../../../../components/common/DetailDialog';
import { resolveMetricsPageMeta } from './metricsPageMeta';

const PAGE_SIZE_OPTIONS = [15, 30, 100, 500, 1000, 5000, 20000] as const;

function normalizePageSize(n?: number): number {
  if (!n) return PAGE_SIZE_OPTIONS[0];
  return (PAGE_SIZE_OPTIONS as readonly number[]).includes(n) ? n : PAGE_SIZE_OPTIONS[0];
}

function formatDateTime(s?: string | null): string {
  if (!s) return '—';
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return String(s);
  return d.toLocaleString();
}

function safeJson(v: unknown): string {
  try {
    return JSON.stringify(v, null, 2);
  } catch {
    return String(v);
  }
}

type AuditFieldChange = { field: string; from: unknown; to: unknown };

function shallowDiff(before: unknown, after: unknown): AuditFieldChange[] {
  const b = (before && typeof before === 'object' ? (before as Record<string, unknown>) : {}) as Record<string, unknown>;
  const a = (after && typeof after === 'object' ? (after as Record<string, unknown>) : {}) as Record<string, unknown>;
  const keys = new Set([...Object.keys(b), ...Object.keys(a)]);
  const out: AuditFieldChange[] = [];
  for (const k of keys) {
    const bv = b[k];
    const av = a[k];
    if (safeJson(bv) === safeJson(av)) continue;
    out.push({ field: k, from: bv, to: av });
  }
  return out;
}

function readAuditChanges(details: unknown): AuditFieldChange[] {
  if (!details || typeof details !== 'object') return [];
  const d = details as Record<string, unknown>;
  const c = d.changes;
  if (Array.isArray(c)) {
    return c
      .map((x) => {
        const r = x as Record<string, unknown>;
        const field = typeof r.field === 'string' ? r.field : '';
        if (!field) return null;
        return { field, from: r.from, to: r.to } as AuditFieldChange;
      })
      .filter((x): x is AuditFieldChange => Boolean(x));
  }
  if (d.before && d.after) return shallowDiff(d.before, d.after);
  return [];
}

function parsePositiveInt(s: string | null): number | undefined {
  if (!s) return undefined;
  const n = Number(s);
  if (!Number.isFinite(n) || n <= 0) return undefined;
  return Math.floor(n);
}

function firstNonBlankString(...values: Array<string | null | undefined>): string | null {
  for (const value of values) {
    if (typeof value !== 'string') continue;
    const trimmed = value.trim();
    if (trimmed) return trimmed;
  }
  return null;
}

type LogBodyInfo = {
  contentType?: string;
  captured?: boolean;
  reason?: string;
  capturedBytes?: number;
  limitBytes?: number;
  truncated?: boolean;
  sha256?: string | null;
  body?: string | null;
  status?: number;
};

function asRecord(v: unknown): Record<string, unknown> | null {
  if (!v || typeof v !== 'object') return null;
  return v as Record<string, unknown>;
}

function asLogBodyInfo(v: unknown): LogBodyInfo | null {
  const r = asRecord(v);
  if (!r) return null;
  return r as unknown as LogBodyInfo;
}

function getLogBody(details: Record<string, unknown> | null | undefined, key: 'reqBody' | 'resBody'): LogBodyInfo | null {
  const d = asRecord(details);
  if (!d) return null;
  return asLogBodyInfo(d[key]);
}

async function copyToClipboard(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
  }
}

type TabKey = 'audit' | 'access';

type AuditQueryState = {
  keyword: string;
  actorId: string;
  actorName: string;
  action: string;
  op: string;
  entityType: string;
  entityId: string;
  result: string;
  traceId: string;
  createdFrom: string;
  createdTo: string;
};

const defaultAuditQuery: AuditQueryState = {
  keyword: '',
  actorId: '',
  actorName: '',
  action: '',
  op: '',
  entityType: '',
  entityId: '',
  result: '',
  traceId: '',
  createdFrom: '',
  createdTo: '',
};

type AccessQueryState = {
  keyword: string;
  userId: string;
  username: string;
  method: string;
  path: string;
  statusCode: string;
  clientIp: string;
  requestId: string;
  traceId: string;
  createdFrom: string;
  createdTo: string;
};

const defaultAccessQuery: AccessQueryState = {
  keyword: '',
  userId: '',
  username: '',
  method: '',
  path: '',
  statusCode: '',
  clientIp: '',
  requestId: '',
  traceId: '',
  createdFrom: '',
  createdTo: '',
};

function toIntOrUndefined(s: string): number | undefined {
  const t = s.trim();
  if (!t) return undefined;
  const n = Number(t);
  if (!Number.isFinite(n) || n < 0) return undefined;
  return Math.floor(n);
}

function normalizeIso(s: string): string | undefined {
  const t = s.trim();
  if (!t) return undefined;
  const d = new Date(t);
  if (Number.isNaN(d.getTime())) return undefined;
  return d.toISOString();
}

const GlobalLogsForm: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const [tab, setTab] = useState<TabKey>(() => {
    const v = (searchParams.get('glTab') ?? '').trim();
    return v === 'access' ? 'access' : 'audit';
  });

  const [page, setPage] = useState<number>(() => parsePositiveInt(searchParams.get('page')) ?? 1);
  const [pageSize, setPageSize] = useState<number>(() => normalizePageSize(parsePositiveInt(searchParams.get('pageSize'))));

  const [auditQuery, setAuditQuery] = useState<AuditQueryState>(() => {
    const q: AuditQueryState = { ...defaultAuditQuery };
    for (const k of Object.keys(q) as (keyof AuditQueryState)[]) {
      const v = searchParams.get(k);
      if (typeof v === 'string') q[k] = v;
    }
    return q;
  });

  const [accessQuery, setAccessQuery] = useState<AccessQueryState>(() => {
    const q: AccessQueryState = { ...defaultAccessQuery };
    for (const k of Object.keys(q) as (keyof AccessQueryState)[]) {
      const v = searchParams.get(k);
      if (typeof v === 'string') q[k] = v;
    }
    return q;
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [auditItems, setAuditItems] = useState<AuditLogDTO[]>([]);
  const [accessItems, setAccessItems] = useState<AccessLogDTO[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [hasNextPage, setHasNextPage] = useState(false);

  const [exportOpen, setExportOpen] = useState(false);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<AuditLogDTO | AccessLogDTO | null>(null);
  const [detailTab, setDetailTab] = useState<'overview' | 'body' | 'json'>('overview');

  const [retentionLoading, setRetentionLoading] = useState(false);
  const [retentionError, setRetentionError] = useState<string | null>(null);
  const [retention, setRetention] = useState<LogRetentionConfigDTO | null>(null);
  const [accessIndexStatusLoading, setAccessIndexStatusLoading] = useState(false);
  const [accessIndexStatusError, setAccessIndexStatusError] = useState<string | null>(null);
  const [accessIndexStatus, setAccessIndexStatus] = useState<AccessLogEsIndexStatusDTO | null>(null);
  const accessQueryUsesKafkaEs = (accessIndexStatus?.sinkMode ?? '').trim().toUpperCase() === 'KAFKA';
  const accessClientIpPlaceholder = accessQueryUsesKafkaEs ? '客户端IP（模糊，ES 子串匹配）' : '客户端IP（模糊）';

  const syncToUrl = useCallback(
    (nextTab: TabKey, nextPage: number, nextPageSize: number, nextAuditQuery: AuditQueryState, nextAccessQuery: AccessQueryState) => {
      setSearchParams(
        (prev) => {
          const sp = new URLSearchParams(prev);

          sp.set('active', 'global-logs');
          sp.set('glTab', nextTab);
          sp.set('page', String(nextPage));
          sp.set('pageSize', String(nextPageSize));

          const payload = nextTab === 'audit' ? nextAuditQuery : nextAccessQuery;
          const keys = Object.keys(payload) as string[];
          for (const k of keys) {
            sp.delete(k);
          }
          for (const [k, v] of Object.entries(payload)) {
            if (!v) sp.delete(k);
            else sp.set(k, v);
          }

          if (sp.toString() === prev.toString()) return prev;
          return sp;
        },
        { replace: true }
      );
    },
    [setSearchParams]
  );

  useEffect(() => {
    setSearchParams((prev) => {
      const current = prev.get('active') ?? '';
      if (current === 'global-logs') return prev;
      const sp = new URLSearchParams(prev);
      sp.set('active', 'global-logs');
      return sp;
    }, { replace: true });
  }, [setSearchParams]);

  const loadRetention = useCallback(async () => {
    setRetentionLoading(true);
    setRetentionError(null);
    try {
      const res = await adminGetLogRetentionConfig();
      if (!mountedRef.current) return;
      setRetention(res);
    } catch (e) {
      if (!mountedRef.current) return;
      setRetentionError(e instanceof Error ? e.message : String(e));
    } finally {
      if (mountedRef.current) setRetentionLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRetention();
  }, [loadRetention]);

  const loadAccessIndexStatus = useCallback(async () => {
    setAccessIndexStatusLoading(true);
    setAccessIndexStatusError(null);
    try {
      const res = await adminGetAccessLogEsIndexStatus();
      if (!mountedRef.current) return;
      setAccessIndexStatus(res);
    } catch (e) {
      if (!mountedRef.current) return;
      setAccessIndexStatusError(e instanceof Error ? e.message : String(e));
    } finally {
      if (mountedRef.current) setAccessIndexStatusLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAccessIndexStatus();
  }, [loadAccessIndexStatus]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (tab === 'audit') {
        const res = await adminListAuditLogs({
          page,
          pageSize,
          keyword: auditQuery.keyword || undefined,
          actorId: toIntOrUndefined(auditQuery.actorId),
          actorName: auditQuery.actorName || undefined,
          action: auditQuery.action || undefined,
          op: auditQuery.op || undefined,
          entityType: auditQuery.entityType || undefined,
          entityId: toIntOrUndefined(auditQuery.entityId),
          result: auditQuery.result || undefined,
          traceId: auditQuery.traceId || undefined,
          createdFrom: normalizeIso(auditQuery.createdFrom),
          createdTo: normalizeIso(auditQuery.createdTo),
          sort: 'createdAt,desc',
        });

        if (!mountedRef.current) return;
        const pageMeta = resolveMetricsPageMeta(page, pageSize, res);
        setAuditItems(res.content ?? []);
        setAccessItems([]);
        setTotalPages(pageMeta.totalPages);
        setTotalElements(pageMeta.totalElements);
        setHasNextPage(pageMeta.hasNextPage);
        syncToUrl(tab, page, pageSize, auditQuery, accessQuery);
      } else {
        const res = await adminListAccessLogs({
          page,
          pageSize,
          keyword: accessQuery.keyword || undefined,
          userId: toIntOrUndefined(accessQuery.userId),
          username: accessQuery.username || undefined,
          method: accessQuery.method || undefined,
          path: accessQuery.path || undefined,
          statusCode: toIntOrUndefined(accessQuery.statusCode),
          clientIp: accessQuery.clientIp || undefined,
          requestId: accessQuery.requestId || undefined,
          traceId: accessQuery.traceId || undefined,
          createdFrom: normalizeIso(accessQuery.createdFrom),
          createdTo: normalizeIso(accessQuery.createdTo),
          sort: 'createdAt,desc',
        });

        if (!mountedRef.current) return;
        const pageMeta = resolveMetricsPageMeta(page, pageSize, res);
        setAccessItems(res.content ?? []);
        setAuditItems([]);
        setTotalPages(pageMeta.totalPages);
        setTotalElements(pageMeta.totalElements);
        setHasNextPage(pageMeta.hasNextPage);
        syncToUrl(tab, page, pageSize, auditQuery, accessQuery);
      }
    } catch (e) {
      if (!mountedRef.current) return;
      setHasNextPage(false);
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, [accessQuery, auditQuery, page, pageSize, syncToUrl, tab]);

  useEffect(() => {
    load();
  }, [load]);

  const openDetail = useCallback(async (id: string | number) => {
    setDetailOpen(true);
    setDetailTab('overview');
    setDetailLoading(true);
    setDetail(null);
    try {
      if (tab === 'audit') {
        const auditId = typeof id === 'number' ? id : Number(id);
        const res = await adminGetAuditLogDetail(auditId);
        if (!mountedRef.current) return;
        setDetail(res);
      } else {
        const res = await adminGetAccessLogDetail(id);
        if (!mountedRef.current) return;
        setDetail(res);
      }
    } catch (e) {
      if (!mountedRef.current) return;
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      if (mountedRef.current) setDetailLoading(false);
    }
  }, [tab]);

  const closeDetail = useCallback(() => {
    setDetailOpen(false);
    setDetailTab('overview');
  }, []);

  const exportCsv = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (tab === 'audit') {
        const blob = await adminExportAuditLogsCsv({
          keyword: auditQuery.keyword || undefined,
          actorId: toIntOrUndefined(auditQuery.actorId),
          actorName: auditQuery.actorName || undefined,
          action: auditQuery.action || undefined,
          op: auditQuery.op || undefined,
          entityType: auditQuery.entityType || undefined,
          entityId: toIntOrUndefined(auditQuery.entityId),
          result: auditQuery.result || undefined,
          traceId: auditQuery.traceId || undefined,
          createdFrom: normalizeIso(auditQuery.createdFrom),
          createdTo: normalizeIso(auditQuery.createdTo),
          sort: 'createdAt,desc',
        });
        downloadBlob(blob, `audit-logs-${new Date().toISOString()}.csv`);
      } else {
        const blob = await adminExportAccessLogsCsv({
          keyword: accessQuery.keyword || undefined,
          userId: toIntOrUndefined(accessQuery.userId),
          username: accessQuery.username || undefined,
          method: accessQuery.method || undefined,
          path: accessQuery.path || undefined,
          statusCode: toIntOrUndefined(accessQuery.statusCode),
          clientIp: accessQuery.clientIp || undefined,
          requestId: accessQuery.requestId || undefined,
          traceId: accessQuery.traceId || undefined,
          createdFrom: normalizeIso(accessQuery.createdFrom),
          createdTo: normalizeIso(accessQuery.createdTo),
          sort: 'createdAt,desc',
        });
        downloadBlob(blob, `access-logs-${new Date().toISOString()}.csv`);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, [accessQuery, auditQuery, tab]);

  const exportRange = useMemo(() => {
    const q = tab === 'audit' ? auditQuery : accessQuery;
    const fromIso = normalizeIso(q.createdFrom);
    const toIso = normalizeIso(q.createdTo);
    return {
      from: fromIso ? formatDateTime(fromIso) : '未限制',
      to: toIso ? formatDateTime(toIso) : '未限制',
    };
  }, [accessQuery, auditQuery, tab]);

  const currentList = tab === 'audit' ? auditItems : accessItems;
  const resolvedTotalElements = useMemo(() => {
    if (currentList.length === 0) return totalElements;
    return Math.max(totalElements, (page - 1) * pageSize + currentList.length);
  }, [currentList.length, page, pageSize, totalElements]);

  const resolvedTotalPages = useMemo(
    () => Math.max(totalPages, Math.ceil(resolvedTotalElements / pageSize) || 1, hasNextPage ? page + 1 : page),
    [hasNextPage, page, pageSize, resolvedTotalElements, totalPages]
  );

  const paginationText = useMemo(() => {
    return `第 ${page} 页 / 共 ${resolvedTotalPages} 页（${resolvedTotalElements} 条）`;
  }, [page, resolvedTotalElements, resolvedTotalPages]);

  const updateRetention = useCallback(async (patch: Partial<LogRetentionConfigDTO>) => {
    if (!retention) return;
    setRetentionLoading(true);
    setRetentionError(null);
    try {
      const next: LogRetentionConfigDTO = {
        enabled: patch.enabled ?? retention.enabled,
        keepDays: patch.keepDays ?? retention.keepDays,
        mode: patch.mode ?? retention.mode,
        maxPerRun: patch.maxPerRun ?? retention.maxPerRun,
        auditLogsEnabled: patch.auditLogsEnabled ?? retention.auditLogsEnabled,
        accessLogsEnabled: patch.accessLogsEnabled ?? retention.accessLogsEnabled,
        purgeArchivedEnabled: patch.purgeArchivedEnabled ?? retention.purgeArchivedEnabled,
        purgeArchivedKeepDays: patch.purgeArchivedKeepDays ?? retention.purgeArchivedKeepDays,
      };
      const res = await adminUpdateLogRetentionConfig(next);
      if (!mountedRef.current) return;
      setRetention(res);
    } catch (e) {
      if (!mountedRef.current) return;
      setRetentionError(e instanceof Error ? e.message : String(e));
    } finally {
      if (mountedRef.current) setRetentionLoading(false);
    }
  }, [retention]);

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div className="space-y-1">
          <h3 className="text-lg font-semibold">全局日志中心</h3>
          <div className="text-xs text-gray-600">
            说明：本页聚合“业务审计日志”和“HTTP 访问日志”，用于排障、取证与追溯。导出内容默认不包含敏感凭据。
          </div>
        </div>
        <div className="flex items-center gap-2">
          <div className="inline-flex rounded border overflow-hidden">
            <button
              className={`px-3 py-1.5 text-sm ${tab === 'audit' ? 'bg-gray-900 text-white' : 'bg-white text-gray-700'}`}
              onClick={() => {
                if (tab === 'audit') return;
                setTab('audit');
                setPage(1);
                syncToUrl('audit', 1, pageSize, auditQuery, accessQuery);
              }}
              type="button"
            >
              业务审计
            </button>
            <button
              className={`px-3 py-1.5 text-sm ${tab === 'access' ? 'bg-gray-900 text-white' : 'bg-white text-gray-700'}`}
              onClick={() => {
                if (tab === 'access') return;
                setTab('access');
                setPage(1);
                syncToUrl('access', 1, pageSize, auditQuery, accessQuery);
              }}
              type="button"
            >
              HTTP 访问
            </button>
          </div>
          <button
            className="rounded bg-blue-600 text-white px-3 py-2 text-sm disabled:opacity-50"
            disabled={loading}
            onClick={() => setExportOpen(true)}
            type="button"
          >
            导出 CSV
          </button>
        </div>
      </div>

      <div className="rounded border p-3 space-y-3">
        <div className="flex items-center gap-3">
          <div>
            <div className="font-medium">日志自动清理/归档任务</div>
            <div className="text-xs text-gray-600">默认关闭。开启后可按保留天数、处理范围、单轮处理量执行归档/清理，并可选清理已归档数据。</div>
          </div>
        </div>

        {retentionError ? <div className="text-sm text-red-700">{retentionError}</div> : null}

        <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
          <div className="space-y-1">
            <div className="text-xs text-gray-600">保留天数</div>
            <input
              className="rounded border px-3 py-2 text-sm w-full"
              value={retention?.keepDays ?? 180}
              disabled={retentionLoading || !retention}
              onChange={(e) => updateRetention({ keepDays: toIntOrUndefined(e.target.value) ?? 180 })}
            />
          </div>
          <div className="space-y-1">
            <div className="text-xs text-gray-600">单轮最大处理量</div>
            <input
              className="rounded border px-3 py-2 text-sm w-full"
              value={retention?.maxPerRun ?? 5000}
              disabled={retentionLoading || !retention}
              onChange={(e) => updateRetention({ maxPerRun: toIntOrUndefined(e.target.value) ?? 5000 })}
            />
          </div>
          <div className="space-y-1">
            <div className="text-xs text-gray-600">模式</div>
            <select
              className="rounded border px-3 py-2 text-sm bg-white w-full"
              value={retention?.mode ?? 'ARCHIVE_TABLE'}
              disabled={retentionLoading || !retention}
              onChange={(e) => updateRetention({ mode: e.target.value as LogRetentionConfigDTO['mode'] })}
            >
              <option value="ARCHIVE_TABLE">归档到归档表</option>
              <option value="DELETE">直接删除</option>
            </select>
          </div>
          <div className="space-y-1">
            <div className="text-xs text-gray-600">状态</div>
            <select
              className={`rounded border px-3 py-2 text-sm bg-white w-full ${retention?.enabled ? 'text-green-700' : 'text-gray-700'}`}
              value={retention ? (retention.enabled ? 'ENABLED' : 'DISABLED') : retentionLoading ? '__loading__' : '__none__'}
              disabled={retentionLoading || !retention}
              onChange={(e) => {
                const v = e.target.value;
                if (v === 'ENABLED' || v === 'DISABLED') updateRetention({ enabled: v === 'ENABLED' });
              }}
            >
              {retention ? (
                <>
                  <option value="ENABLED">已开启</option>
                  <option value="DISABLED">已关闭</option>
                </>
              ) : (
                <>
                  <option value="__loading__">正在加载…</option>
                  <option value="__none__">—</option>
                </>
              )}
            </select>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
          <div className="space-y-1">
            <div className="text-xs text-gray-600">处理业务审计日志</div>
            <select
              className="rounded border px-3 py-2 text-sm bg-white w-full"
              value={retention?.auditLogsEnabled ? 'ENABLED' : 'DISABLED'}
              disabled={retentionLoading || !retention}
              onChange={(e) => {
                const v = e.target.value;
                if (v === 'ENABLED' || v === 'DISABLED') updateRetention({ auditLogsEnabled: v === 'ENABLED' });
              }}
            >
              <option value="ENABLED">开启</option>
              <option value="DISABLED">关闭</option>
            </select>
          </div>
          <div className="space-y-1">
            <div className="text-xs text-gray-600">处理 HTTP 访问日志</div>
            <select
              className="rounded border px-3 py-2 text-sm bg-white w-full"
              value={retention?.accessLogsEnabled ? 'ENABLED' : 'DISABLED'}
              disabled={retentionLoading || !retention}
              onChange={(e) => {
                const v = e.target.value;
                if (v === 'ENABLED' || v === 'DISABLED') updateRetention({ accessLogsEnabled: v === 'ENABLED' });
              }}
            >
              <option value="ENABLED">开启</option>
              <option value="DISABLED">关闭</option>
            </select>
          </div>
          <div className="space-y-1">
            <div className="text-xs text-gray-600">清理已归档日志</div>
            <select
              className="rounded border px-3 py-2 text-sm bg-white w-full"
              value={retention?.purgeArchivedEnabled ? 'ENABLED' : 'DISABLED'}
              disabled={retentionLoading || !retention}
              onChange={(e) => {
                const v = e.target.value;
                if (v === 'ENABLED' || v === 'DISABLED') updateRetention({ purgeArchivedEnabled: v === 'ENABLED' });
              }}
            >
              <option value="DISABLED">关闭</option>
              <option value="ENABLED">开启</option>
            </select>
          </div>
          <div className="space-y-1">
            <div className="text-xs text-gray-600">归档保留天数</div>
            <input
              className="rounded border px-3 py-2 text-sm w-full"
              value={retention?.purgeArchivedKeepDays ?? 365}
              disabled={retentionLoading || !retention || !retention.purgeArchivedEnabled}
              onChange={(e) => updateRetention({ purgeArchivedKeepDays: toIntOrUndefined(e.target.value) ?? 365 })}
            />
          </div>
        </div>
      </div>

      <div className="rounded border p-3 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <div className="font-medium">日志索引状态（ES）</div>
            <div className="text-xs text-gray-600">用于观察 HTTP 日志 ES 索引是否可用，便于灰度切换查询链路。</div>
          </div>
          <button
            type="button"
            className="rounded border px-3 py-1.5 text-sm bg-white disabled:opacity-50"
            onClick={() => {
              void loadAccessIndexStatus();
            }}
            disabled={accessIndexStatusLoading}
          >
            刷新
          </button>
        </div>

        {accessIndexStatusError ? <div className="text-sm text-red-700">{accessIndexStatusError}</div> : null}

        {!accessIndexStatus ? (
          <div className="text-sm text-gray-500 py-4 text-center bg-gray-50 rounded border border-dashed">
            {accessIndexStatusLoading ? '加载中…' : '暂无索引状态数据'}
          </div>
        ) : (
          <div className="overflow-x-auto border rounded-md">
            <table className="min-w-full text-sm divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr className="text-left text-gray-500 font-medium text-sm uppercase tracking-wider">
                  <th className="py-2 px-3">索引名</th>
                  <th className="py-2 px-3">集合名</th>
                  <th className="py-2 px-3">文档数</th>
                  <th className="py-2 px-3">状态</th>
                  <th className="py-2 px-3">写入模式</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 bg-white">
                <tr className="hover:bg-gray-50 transition-colors">
                  <td className="py-2 px-3 font-mono text-gray-900 break-all text-sm font-medium">{accessIndexStatus.indexName || '—'}</td>
                  <td className="py-2 px-3 text-gray-600 text-sm">{accessIndexStatus.collectionName || '—'}</td>
                  <td className="py-2 px-3 text-gray-600 text-sm">
                    {typeof accessIndexStatus.docsCount === 'number' ? accessIndexStatus.docsCount.toLocaleString() : '—'}
                  </td>
                  <td className="py-2 px-3">
                    <div className="flex flex-col gap-1 items-start">
                      <div className="flex flex-wrap items-center gap-2">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium uppercase tracking-wide ${
                            accessIndexStatus.exists ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                          }`}
                        >
                          {accessIndexStatus.exists ? '存在' : '不存在'}
                        </span>
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium uppercase tracking-wide ${
                            accessIndexStatus.available ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'
                          }`}
                        >
                          {accessIndexStatus.available ? '可用' : '不可用'}
                        </span>
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-700">
                          {accessIndexStatus.health || 'unknown'} / {accessIndexStatus.status || 'unknown'}
                        </span>
                      </div>
                      {accessIndexStatus.availabilityMessage ? (
                        <div className="text-xs text-gray-500">{accessIndexStatus.availabilityMessage}</div>
                      ) : null}
                    </div>
                  </td>
                  <td className="py-2 px-3 text-sm text-gray-600">
                    <div className="flex flex-col gap-1">
                      <div>sink-mode: {accessIndexStatus.sinkMode || '—'}</div>
                      <div className="text-xs text-gray-500">
                        ES sink: {accessIndexStatus.esSinkEnabled ? '开启' : '关闭'} / consumer: {accessIndexStatus.consumerEnabled ? '开启' : '关闭'}
                      </div>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        )}
      </div>

      {error ? <div className="rounded border border-red-200 bg-red-50 p-3 text-sm text-red-700">{error}</div> : null}

      <div className="rounded border p-3 space-y-3">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
          <input
            className="rounded border px-3 py-2 text-sm"
            placeholder="关键字（action/entity/path 等）"
            value={tab === 'audit' ? auditQuery.keyword : accessQuery.keyword}
            onChange={(e) => {
              const v = e.target.value;
              if (tab === 'audit') setAuditQuery((p) => ({ ...p, keyword: v }));
              else setAccessQuery((p) => ({ ...p, keyword: v }));
            }}
          />

          {tab === 'audit' ? (
            <>
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder="用户ID"
                value={auditQuery.actorId}
                onChange={(e) => setAuditQuery((p) => ({ ...p, actorId: e.target.value }))}
              />
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder="用户账号（模糊）"
                value={auditQuery.actorName}
                onChange={(e) => setAuditQuery((p) => ({ ...p, actorName: e.target.value }))}
              />
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder="TraceId（模糊）"
                value={auditQuery.traceId}
                onChange={(e) => setAuditQuery((p) => ({ ...p, traceId: e.target.value }))}
              />
            </>
          ) : (
            <>
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder="用户ID"
                value={accessQuery.userId}
                onChange={(e) => setAccessQuery((p) => ({ ...p, userId: e.target.value }))}
              />
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder="用户账号（模糊）"
                value={accessQuery.username}
                onChange={(e) => setAccessQuery((p) => ({ ...p, username: e.target.value }))}
              />
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder={accessClientIpPlaceholder}
                value={accessQuery.clientIp}
                onChange={(e) => setAccessQuery((p) => ({ ...p, clientIp: e.target.value }))}
              />
            </>
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-2">
          {tab === 'audit' ? (
            <>
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder="操作类型 action（模糊）"
                value={auditQuery.action}
                onChange={(e) => setAuditQuery((p) => ({ ...p, action: e.target.value }))}
              />
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder="实体类型 entityType（模糊）"
                value={auditQuery.entityType}
                onChange={(e) => setAuditQuery((p) => ({ ...p, entityType: e.target.value }))}
              />
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder="实体ID"
                value={auditQuery.entityId}
                onChange={(e) => setAuditQuery((p) => ({ ...p, entityId: e.target.value }))}
              />
              <select
                className="rounded border px-3 py-2 text-sm bg-white"
                value={auditQuery.result}
                onChange={(e) => setAuditQuery((p) => ({ ...p, result: e.target.value }))}
              >
                <option value="">结果（全部）</option>
                <option value="SUCCESS">SUCCESS</option>
                <option value="FAIL">FAIL</option>
                <option value="SKIP">SKIP</option>
              </select>
            </>
          ) : (
            <>
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder="Method（GET/POST...）"
                value={accessQuery.method}
                onChange={(e) => setAccessQuery((p) => ({ ...p, method: e.target.value }))}
              />
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder="Path（模糊）"
                value={accessQuery.path}
                onChange={(e) => setAccessQuery((p) => ({ ...p, path: e.target.value }))}
              />
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder="状态码"
                value={accessQuery.statusCode}
                onChange={(e) => setAccessQuery((p) => ({ ...p, statusCode: e.target.value }))}
              />
              <input
                className="rounded border px-3 py-2 text-sm"
                placeholder="RequestId/TraceId（模糊）"
                value={(accessQuery.requestId || accessQuery.traceId).trim() ? `${accessQuery.requestId}${accessQuery.traceId ? ` / ${accessQuery.traceId}` : ''}` : ''}
                onChange={(e) => {
                  const v = e.target.value;
                  setAccessQuery((p) => ({ ...p, requestId: v, traceId: v }));
                }}
              />
            </>
          )}
        </div>

        {tab === 'audit' ? (
          <div className="flex flex-wrap items-center gap-2">
            <div className="text-xs text-gray-600">数据库变更快捷：</div>
            {[
              { label: '全部', op: '' },
              { label: '增（CREATE）', op: 'CREATE' },
              { label: '改（UPDATE）', op: 'UPDATE' },
              { label: '删（DELETE）', op: 'DELETE' },
            ].map((x) => (
              <button
                key={x.label}
                type="button"
                className={`rounded border px-2 py-1 text-xs bg-white ${auditQuery.op.trim().toUpperCase() === x.op ? 'border-gray-900 text-gray-900' : 'text-gray-700'}`}
                onClick={() => {
                  setPage(1);
                  setAuditQuery((p) => ({ ...p, op: x.op }));
                }}
              >
                {x.label}
              </button>
            ))}
          </div>
        ) : null}

        {tab === 'access' ? (
          <div className="flex flex-wrap items-center gap-2">
            <div className="text-xs text-gray-600">CRUD 快捷：</div>
            {[
              { label: '全部', method: '' },
              { label: '查（GET）', method: 'GET' },
              { label: '增（POST）', method: 'POST' },
              { label: '改（PUT）', method: 'PUT' },
              { label: '改（PATCH）', method: 'PATCH' },
              { label: '删（DELETE）', method: 'DELETE' },
            ].map((x) => (
              <button
                key={x.label}
                type="button"
                className={`rounded border px-2 py-1 text-xs bg-white ${accessQuery.method.trim().toUpperCase() === x.method ? 'border-gray-900 text-gray-900' : 'text-gray-700'}`}
                onClick={() => {
                  setPage(1);
                  setAccessQuery((p) => ({ ...p, method: x.method }));
                }}
              >
                {x.label}
              </button>
            ))}
          </div>
        ) : null}

        <div className="grid grid-cols-1 md:grid-cols-4 gap-2 items-center">
          <input
            className="rounded border px-3 py-2 text-sm"
            type="datetime-local"
            step="1"
            value={tab === 'audit' ? auditQuery.createdFrom : accessQuery.createdFrom}
            onChange={(e) => {
              const v = e.target.value;
              if (tab === 'audit') setAuditQuery((p) => ({ ...p, createdFrom: v }));
              else setAccessQuery((p) => ({ ...p, createdFrom: v }));
            }}
          />
          <input
            className="rounded border px-3 py-2 text-sm"
            type="datetime-local"
            step="1"
            value={tab === 'audit' ? auditQuery.createdTo : accessQuery.createdTo}
            onChange={(e) => {
              const v = e.target.value;
              if (tab === 'audit') setAuditQuery((p) => ({ ...p, createdTo: v }));
              else setAccessQuery((p) => ({ ...p, createdTo: v }));
            }}
          />

          <div className="flex items-center gap-2">
            <button
              className="rounded border px-3 py-2 text-sm bg-white"
              onClick={() => {
                setPage(1);
                load();
              }}
              type="button"
              disabled={loading}
            >
              查询
            </button>
            <button
              className="rounded border px-3 py-2 text-sm bg-white"
              onClick={() => {
                setPageSize(PAGE_SIZE_OPTIONS[0]);
                setPage(1);
                setAuditQuery({ ...defaultAuditQuery });
                setAccessQuery({ ...defaultAccessQuery });
                syncToUrl(tab, 1, PAGE_SIZE_OPTIONS[0], defaultAuditQuery, defaultAccessQuery);
              }}
              type="button"
              disabled={loading}
            >
              重置
            </button>
          </div>
        </div>
      </div>

      <div className="overflow-auto rounded border">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 text-gray-700">
            {tab === 'audit' ? (
              <tr>
                <th className="text-left px-3 py-2 whitespace-nowrap">时间</th>
                <th className="text-left px-3 py-2 whitespace-nowrap">用户</th>
                <th className="text-left px-3 py-2 whitespace-nowrap">操作</th>
                <th className="text-left px-3 py-2 whitespace-nowrap">实体</th>
                <th className="text-left px-3 py-2 whitespace-nowrap">请求/消息</th>
                <th className="text-left px-3 py-2 whitespace-nowrap">结果</th>
                <th className="text-left px-3 py-2 whitespace-nowrap">TraceId</th>
                <th className="text-right px-3 py-2 whitespace-nowrap">操作</th>
              </tr>
            ) : (
              <tr>
                <th className="text-left px-3 py-2 whitespace-nowrap">时间</th>
                <th className="text-left px-3 py-2 whitespace-nowrap">用户</th>
                <th className="text-left px-3 py-2 whitespace-nowrap">类型</th>
                <th className="text-left px-3 py-2 whitespace-nowrap">来源 → 目标</th>
                <th className="text-left px-3 py-2 whitespace-nowrap">结果</th>
                <th className="text-left px-3 py-2 whitespace-nowrap">关联</th>
                <th className="text-right px-3 py-2 whitespace-nowrap">操作</th>
              </tr>
            )}
          </thead>
          <tbody>
            {currentList.length === 0 ? (
              <tr>
                <td className="px-3 py-6 text-center text-gray-500" colSpan={tab === 'audit' ? 8 : 7}>
                  {loading ? '正在加载…' : '暂无数据'}
                </td>
              </tr>
            ) : null}

            {tab === 'audit'
              ? auditItems.map((it) => (
                  <tr key={it.id} className="border-t">
                    <td className="px-3 py-2 whitespace-nowrap">{formatDateTime(it.createdAt)}</td>
                    <td className="px-3 py-2 whitespace-nowrap">{it.actorName ?? (it.actorId ? `#${it.actorId}` : 'SYSTEM')}</td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      {(() => {
                        const a = String(it.action ?? '').trim().toUpperCase();
                        const badge =
                          a === 'CRUD_CREATE'
                            ? 'border-green-200 bg-green-50 text-green-700'
                            : a === 'CRUD_UPDATE'
                              ? 'border-amber-200 bg-amber-50 text-amber-700'
                              : a === 'CRUD_DELETE'
                                ? 'border-red-200 bg-red-50 text-red-700'
                                : 'border-gray-200 bg-white text-gray-700';
                        return <span className={`inline-flex items-center rounded border px-2 py-0.5 text-xs ${badge}`}>{it.action}</span>;
                      })()}
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      {it.entityType}
                      {it.entityId ? <span className="text-gray-500">{` #${it.entityId}`}</span> : null}
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      <div className="font-mono text-xs">
                        {it.method && it.path ? `${it.method} ${it.path}` : it.message ?? '—'}
                      </div>
                      <div className="text-xs text-gray-500">{it.ip ?? '—'}</div>
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap">{it.result ?? '—'}</td>
                    <td className="px-3 py-2 whitespace-nowrap">{it.traceId ?? '—'}</td>
                    <td className="px-3 py-2 whitespace-nowrap text-right">
                      <button className="text-blue-600 hover:underline" type="button" onClick={() => openDetail(it.id)}>
                        详情
                      </button>
                    </td>
                  </tr>
                ))
              : accessItems.map((it) => (
                  <tr key={it.id} className="border-t">
                    {(() => {
                      const detailKey = firstNonBlankString(it.requestId, it.traceId, String(it.id));
                      return (
                        <>
                    <td className="px-3 py-2 whitespace-nowrap">{formatDateTime(it.createdAt)}</td>
                    <td className="px-3 py-2 whitespace-nowrap">{it.username ?? (it.userId ? `#${it.userId}` : '—')}</td>
                    <td className="px-3 py-2 whitespace-nowrap">{`${it.method ?? '—'} ${it.path ?? '—'}`}</td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      {(it.clientIp ?? '—') + (it.clientPort ? `:${it.clientPort}` : '') + ' → ' + (it.serverIp ?? '—') + (it.serverPort ? `:${it.serverPort}` : '')}
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap">{it.statusCode ?? '—'}</td>
                    <td className="px-3 py-2 whitespace-nowrap">{it.requestId ?? it.traceId ?? '—'}</td>
                    <td className="px-3 py-2 whitespace-nowrap text-right">
                      {detailKey ? (
                        <button className="text-blue-600 hover:underline" type="button" onClick={() => openDetail(detailKey)}>
                          详情
                        </button>
                      ) : (
                        <span className="text-xs text-gray-400">缺少可用详情键</span>
                      )}
                    </td>
                        </>
                      );
                    })()}
                  </tr>
                ))}
          </tbody>
        </table>
      </div>

      <div className="flex items-center justify-between gap-3">
        <div className="text-sm text-gray-600">{paginationText}</div>
        <div className="flex items-center gap-2">
          <select
            className="rounded border px-2 py-2 text-sm bg-white"
            value={pageSize}
            onChange={(e) => {
              const v = normalizePageSize(parsePositiveInt(e.target.value));
              setPageSize(v);
              setPage(1);
              syncToUrl(tab, 1, v, auditQuery, accessQuery);
            }}
          >
            {PAGE_SIZE_OPTIONS.map((n) => (
              <option key={n} value={n}>
                {n} / 页
              </option>
            ))}
          </select>
          <button
            className="rounded border px-3 py-2 text-sm bg-white disabled:opacity-50"
            disabled={loading || page <= 1}
            onClick={() => {
              const next = Math.max(1, page - 1);
              setPage(next);
              syncToUrl(tab, next, pageSize, auditQuery, accessQuery);
            }}
            type="button"
          >
            上一页
          </button>
          <button
            className="rounded border px-3 py-2 text-sm bg-white disabled:opacity-50"
            disabled={loading || !hasNextPage}
            onClick={() => {
              const next = page + 1;
              setPage(next);
              syncToUrl(tab, next, pageSize, auditQuery, accessQuery);
            }}
            type="button"
          >
            下一页
          </button>
        </div>
      </div>

      {exportOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" onClick={() => setExportOpen(false)}>
          <div className="w-full max-w-lg bg-white rounded shadow-xl" onClick={(e) => e.stopPropagation()}>
            <div className="p-4 border-b flex items-center justify-between">
              <div className="font-semibold">导出 CSV</div>
              <button className="rounded border px-3 py-1.5 text-sm" type="button" onClick={() => setExportOpen(false)}>
                关闭
              </button>
            </div>
            <div className="p-4 space-y-3">
              <div className="text-sm text-gray-700">导出范围（时间）</div>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-2 text-sm">
                <div className="rounded border p-2">
                  <div className="text-xs text-gray-600">开始时间</div>
                  <div>{exportRange.from}</div>
                </div>
                <div className="rounded border p-2">
                  <div className="text-xs text-gray-600">结束时间</div>
                  <div>{exportRange.to}</div>
                </div>
              </div>
              <div className="text-xs text-gray-500">未设置时间表示不限制。</div>
              <div className="flex justify-end gap-2 pt-1">
                <button
                  className="rounded border px-3 py-2 text-sm bg-white disabled:opacity-50"
                  disabled={loading}
                  onClick={() => setExportOpen(false)}
                  type="button"
                >
                  取消
                </button>
                <button
                  className="rounded bg-blue-600 text-white px-3 py-2 text-sm disabled:opacity-50"
                  disabled={loading}
                  onClick={async () => {
                    setExportOpen(false);
                    await exportCsv();
                  }}
                  type="button"
                >
                  确认导出
                </button>
              </div>
            </div>
          </div>
        </div>
      ) : null}

      {detailOpen ? (
        <DetailDialog
          open={detailOpen}
          onClose={closeDetail}
          variant="drawerRight"
          title="日志详情"
          tabs={[
            { id: 'overview', label: '概览' },
            { id: 'body', label: '正文' },
            { id: 'json', label: 'JSON' },
          ]}
          activeTabId={detailTab}
          onTabChange={(id) => setDetailTab(id as 'overview' | 'body' | 'json')}
          containerClassName="max-w-2xl overflow-hidden rounded-l-2xl"
          bodyClassName="flex-1 overflow-auto p-4 space-y-3"
        >
          {detailLoading ? (
            <div className="text-sm text-gray-600">正在加载详情…</div>
          ) : detail ? (
            <>
              {'action' in detail ? (
                <>
                  {detailTab === 'overview' ? (
                    <div className="grid grid-cols-2 gap-2 text-sm">
                      <div className="rounded border p-2">
                        <div className="text-xs text-gray-600">时间</div>
                        <div>{formatDateTime(detail.createdAt)}</div>
                      </div>
                      <div className="rounded border p-2">
                        <div className="text-xs text-gray-600">用户</div>
                        <div>{detail.actorName ?? (detail.actorId ? `#${detail.actorId}` : 'SYSTEM')}</div>
                      </div>
                      <div className="rounded border p-2">
                        <div className="text-xs text-gray-600">操作类型</div>
                        <div>{detail.action}</div>
                      </div>
                      <div className="rounded border p-2">
                        <div className="text-xs text-gray-600">结果</div>
                        <div>{detail.result ?? '—'}</div>
                      </div>
                      <div className="rounded border p-2 col-span-2">
                        <div className="text-xs text-gray-600">网络源地址</div>
                        <div>{detail.ip ?? '—'}</div>
                      </div>
                      <div className="rounded border p-2 col-span-2">
                        <div className="text-xs text-gray-600">关联ID（TraceId）</div>
                        <div>{detail.traceId ?? '—'}</div>
                      </div>
                    </div>
                  ) : null}

                  {detailTab === 'body' ? (
                    <div className="grid grid-cols-2 gap-2 text-sm">
                      <div className="rounded border p-2 col-span-2">
                        <div className="text-xs text-gray-600">消息</div>
                        <div className="whitespace-pre-wrap break-words">{detail.message ?? '—'}</div>
                      </div>
                      {(() => {
                        const changes = readAuditChanges(detail.details ?? {});
                        if (!changes.length) return null;
                        return (
                          <div className="rounded border p-2 col-span-2">
                            <div className="text-xs text-gray-600 mb-2">字段变更</div>
                            <div className="overflow-auto">
                              <table className="min-w-[720px] w-full text-sm">
                                <thead>
                                  <tr className="text-left text-xs text-gray-500">
                                    <th className="py-2 pr-3 w-[240px]">字段</th>
                                    <th className="py-2 pr-3">旧值</th>
                                    <th className="py-2">新值</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {changes.map((c) => (
                                    <tr key={c.field} className="border-t align-top">
                                      <td className="py-2 pr-3 font-mono break-all">{c.field}</td>
                                      <td className="py-2 pr-3">
                                        <pre className="text-xs bg-gray-50 rounded p-2 overflow-auto max-h-[180px]">{safeJson(c.from)}</pre>
                                      </td>
                                      <td className="py-2">
                                        <pre className="text-xs bg-gray-50 rounded p-2 overflow-auto max-h-[180px]">{safeJson(c.to)}</pre>
                                      </td>
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                          </div>
                        );
                      })()}
                    </div>
                  ) : null}

                  {detailTab === 'json' ? (
                    <div className="rounded border p-2">
                      <div className="text-xs text-gray-600 mb-2">详情（JSON）</div>
                      <pre className="text-xs bg-gray-50 rounded p-2 overflow-auto whitespace-pre-wrap break-words">{safeJson(detail.details ?? {})}</pre>
                    </div>
                  ) : null}
                </>
              ) : (
                <>
                  {detailTab === 'overview' ? (
                    <div className="grid grid-cols-2 gap-2 text-sm">
                      <div className="rounded border p-2">
                        <div className="text-xs text-gray-600">时间</div>
                        <div>{formatDateTime(detail.createdAt)}</div>
                      </div>
                      <div className="rounded border p-2">
                        <div className="text-xs text-gray-600">用户</div>
                        <div>{detail.username ?? (detail.userId ? `#${detail.userId}` : '—')}</div>
                      </div>
                      <div className="rounded border p-2 col-span-2">
                        <div className="text-xs text-gray-600">请求</div>
                        <div>{`${detail.method ?? '—'} ${detail.path ?? '—'}`}</div>
                      </div>
                      <div className="rounded border p-2">
                        <div className="text-xs text-gray-600">状态码</div>
                        <div>{detail.statusCode ?? '—'}</div>
                      </div>
                      <div className="rounded border p-2">
                        <div className="text-xs text-gray-600">耗时</div>
                        <div>{typeof detail.latencyMs === 'number' ? `${detail.latencyMs} ms` : '—'}</div>
                      </div>
                      <div className="rounded border p-2 col-span-2">
                        <div className="text-xs text-gray-600">网络源 → 目标</div>
                        <div>
                          {(detail.clientIp ?? '—') +
                            (detail.clientPort ? `:${detail.clientPort}` : '') +
                            ' → ' +
                            (detail.serverIp ?? '—') +
                            (detail.serverPort ? `:${detail.serverPort}` : '')}
                        </div>
                      </div>
                      <div className="rounded border p-2 col-span-2">
                        <div className="text-xs text-gray-600">客户端硬件/特征</div>
                        <div className="whitespace-pre-wrap break-words">{detail.userAgent ?? '—'}</div>
                      </div>
                      <div className="rounded border p-2 col-span-2">
                        <div className="text-xs text-gray-600">关联ID（RequestId/TraceId）</div>
                        <div>{detail.requestId ?? detail.traceId ?? '—'}</div>
                      </div>
                    </div>
                  ) : null}

                  {detailTab === 'body' ? (
                    <>
                      <div className="rounded border p-2">
                        <div className="flex items-center justify-between gap-2">
                          <div className="text-xs text-gray-600">请求体</div>
                          {(() => {
                            const reqBody = getLogBody(detail.details ?? null, 'reqBody');
                            const body = reqBody?.body;
                            if (!body) return null;
                            return (
                              <button className="text-xs rounded border px-2 py-1 bg-white" type="button" onClick={() => copyToClipboard(String(body))}>
                                复制
                              </button>
                            );
                          })()}
                        </div>
                        {(() => {
                          const reqBody = getLogBody(detail.details ?? null, 'reqBody');
                          if (!reqBody) return <div className="text-xs text-gray-500">—</div>;
                          const meta =
                            (reqBody.contentType ? `Content-Type: ${reqBody.contentType}` : 'Content-Type: —') +
                            (reqBody.captured === false ? `（未采集：${reqBody.reason ?? '—'}）` : '') +
                            (typeof reqBody.capturedBytes === 'number' ? `，captured: ${reqBody.capturedBytes}B` : '') +
                            (reqBody.truncated ? '，已截断' : '');
                          return (
                            <>
                              <div className="text-xs text-gray-500">{meta}</div>
                              {reqBody.body ? (
                                <pre className="text-xs bg-gray-50 rounded p-2 overflow-auto whitespace-pre-wrap break-words mt-2">{String(reqBody.body)}</pre>
                              ) : (
                                <div className="text-xs text-gray-500 mt-2">—</div>
                              )}
                            </>
                          );
                        })()}
                      </div>
                      <div className="rounded border p-2">
                        <div className="flex items-center justify-between gap-2">
                          <div className="text-xs text-gray-600">响应体</div>
                          {(() => {
                            const resBody = getLogBody(detail.details ?? null, 'resBody');
                            const body = resBody?.body;
                            if (!body) return null;
                            return (
                              <button className="text-xs rounded border px-2 py-1 bg-white" type="button" onClick={() => copyToClipboard(String(body))}>
                                复制
                              </button>
                            );
                          })()}
                        </div>
                        {(() => {
                          const resBody = getLogBody(detail.details ?? null, 'resBody');
                          if (!resBody) return <div className="text-xs text-gray-500">—</div>;
                          const meta =
                            (typeof resBody.status === 'number' ? `Status: ${resBody.status}，` : '') +
                            (resBody.contentType ? `Content-Type: ${resBody.contentType}` : 'Content-Type: —') +
                            (resBody.captured === false ? `（未采集：${resBody.reason ?? '—'}）` : '') +
                            (typeof resBody.capturedBytes === 'number' ? `，captured: ${resBody.capturedBytes}B` : '') +
                            (resBody.truncated ? '，已截断' : '');
                          return (
                            <>
                              <div className="text-xs text-gray-500">{meta}</div>
                              {resBody.body ? (
                                <pre className="text-xs bg-gray-50 rounded p-2 overflow-auto whitespace-pre-wrap break-words mt-2">{String(resBody.body)}</pre>
                              ) : (
                                <div className="text-xs text-gray-500 mt-2">—</div>
                              )}
                            </>
                          );
                        })()}
                      </div>
                    </>
                  ) : null}

                  {detailTab === 'json' ? (
                    <div className="rounded border p-2">
                      <div className="text-xs text-gray-600 mb-2">详情（JSON）</div>
                      <pre className="text-xs bg-gray-50 rounded p-2 overflow-auto whitespace-pre-wrap break-words">{safeJson(detail.details ?? {})}</pre>
                    </div>
                  ) : null}
                </>
              )}
            </>
          ) : (
            <div className="text-sm text-gray-600">未加载到详情</div>
          )}
        </DetailDialog>
      ) : null}
    </div>
  );
};

export default GlobalLogsForm;
