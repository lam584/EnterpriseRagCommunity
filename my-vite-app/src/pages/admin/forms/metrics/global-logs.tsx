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
  adminListAccessLogs,
  type AccessLogDTO,
} from '../../../../services/accessLogService';
import { downloadBlob } from '../../../../utils/download';
import { adminGetLogRetentionConfig, adminUpdateLogRetentionConfig } from '../../../../services/logRetentionService';
import type { LogRetentionConfigDTO } from '../../../../services/logRetentionService';

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

function parsePositiveInt(s: string | null): number | undefined {
  if (!s) return undefined;
  const n = Number(s);
  if (!Number.isFinite(n) || n <= 0) return undefined;
  return Math.floor(n);
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
  const [pageSize, setPageSize] = useState<number>(() => parsePositiveInt(searchParams.get('pageSize')) ?? 20);

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

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<AuditLogDTO | AccessLogDTO | null>(null);

  const [retentionLoading, setRetentionLoading] = useState(false);
  const [retentionError, setRetentionError] = useState<string | null>(null);
  const [retention, setRetention] = useState<LogRetentionConfigDTO | null>(null);

  const syncToUrl = useCallback(
    (nextTab: TabKey, nextPage: number, nextPageSize: number, nextAuditQuery: AuditQueryState, nextAccessQuery: AccessQueryState) => {
      setSearchParams(
        (prev) => {
          const sp = new URLSearchParams(prev);

          sp.delete('active');
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
        setAuditItems(res.content ?? []);
        setAccessItems([]);
        setTotalPages(res.totalPages ?? 1);
        setTotalElements(res.totalElements ?? 0);
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
        setAccessItems(res.content ?? []);
        setAuditItems([]);
        setTotalPages(res.totalPages ?? 1);
        setTotalElements(res.totalElements ?? 0);
        syncToUrl(tab, page, pageSize, auditQuery, accessQuery);
      }
    } catch (e) {
      if (!mountedRef.current) return;
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, [accessQuery, auditQuery, page, pageSize, syncToUrl, tab]);

  useEffect(() => {
    load();
  }, [load]);

  const openDetail = useCallback(async (id: number) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetail(null);
    try {
      if (tab === 'audit') {
        const res = await adminGetAuditLogDetail(id);
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

  const paginationText = useMemo(() => {
    return `第 ${page} 页 / 共 ${totalPages} 页（${totalElements} 条）`;
  }, [page, totalElements, totalPages]);

  const currentList = tab === 'audit' ? auditItems : accessItems;

  const updateRetention = useCallback(async (patch: Partial<LogRetentionConfigDTO>) => {
    if (!retention) return;
    setRetentionLoading(true);
    setRetentionError(null);
    try {
      const next: LogRetentionConfigDTO = {
        enabled: patch.enabled ?? retention.enabled,
        keepDays: patch.keepDays ?? retention.keepDays,
        mode: patch.mode ?? retention.mode,
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
            onClick={exportCsv}
            type="button"
          >
            导出 CSV
          </button>
        </div>
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
                placeholder="客户端IP（模糊）"
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
            placeholder="开始时间（如 2026-02-12T00:00:00）"
            value={tab === 'audit' ? auditQuery.createdFrom : accessQuery.createdFrom}
            onChange={(e) => {
              const v = e.target.value;
              if (tab === 'audit') setAuditQuery((p) => ({ ...p, createdFrom: v }));
              else setAccessQuery((p) => ({ ...p, createdFrom: v }));
            }}
          />
          <input
            className="rounded border px-3 py-2 text-sm"
            placeholder="结束时间（如 2026-02-12T23:59:59）"
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
                setPageSize(20);
                setPage(1);
                setAuditQuery({ ...defaultAuditQuery });
                setAccessQuery({ ...defaultAccessQuery });
                syncToUrl(tab, 1, 20, defaultAuditQuery, defaultAccessQuery);
              }}
              type="button"
              disabled={loading}
            >
              重置
            </button>
          </div>

          <div className="text-sm text-gray-600">{paginationText}</div>
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
                    <td className="px-3 py-2 whitespace-nowrap">{formatDateTime(it.createdAt)}</td>
                    <td className="px-3 py-2 whitespace-nowrap">{it.username ?? (it.userId ? `#${it.userId}` : '—')}</td>
                    <td className="px-3 py-2 whitespace-nowrap">{`${it.method ?? '—'} ${it.path ?? '—'}`}</td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      {(it.clientIp ?? '—') + (it.clientPort ? `:${it.clientPort}` : '') + ' → ' + (it.serverIp ?? '—') + (it.serverPort ? `:${it.serverPort}` : '')}
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap">{it.statusCode ?? '—'}</td>
                    <td className="px-3 py-2 whitespace-nowrap">{it.requestId ?? it.traceId ?? '—'}</td>
                    <td className="px-3 py-2 whitespace-nowrap text-right">
                      <button className="text-blue-600 hover:underline" type="button" onClick={() => openDetail(it.id)}>
                        详情
                      </button>
                    </td>
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
              const v = parsePositiveInt(e.target.value) ?? 20;
              setPageSize(v);
              setPage(1);
              syncToUrl(tab, 1, v, auditQuery, accessQuery);
            }}
          >
            {[10, 20, 50, 100, 200].map((n) => (
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
            disabled={loading || page >= totalPages}
            onClick={() => {
              const next = Math.min(totalPages, page + 1);
              setPage(next);
              syncToUrl(tab, next, pageSize, auditQuery, accessQuery);
            }}
            type="button"
          >
            下一页
          </button>
        </div>
      </div>

      <div className="rounded border p-3 space-y-3">
        <div className="flex items-center justify-between gap-3">
          <div>
            <div className="font-medium">日志自动清理/归档任务</div>
            <div className="text-xs text-gray-600">默认关闭。开启后将按保留天数定期清理/归档历史日志。</div>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={retention?.enabled ?? false}
              disabled={retentionLoading || !retention}
              onChange={(e) => updateRetention({ enabled: e.target.checked })}
            />
            <span>启用</span>
          </label>
        </div>

        {retentionError ? <div className="text-sm text-red-700">{retentionError}</div> : null}

        <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
          <div className="space-y-1">
            <div className="text-xs text-gray-600">保留天数</div>
            <input
              className="rounded border px-3 py-2 text-sm w-full"
              value={retention?.keepDays ?? 90}
              disabled={retentionLoading || !retention}
              onChange={(e) => updateRetention({ keepDays: toIntOrUndefined(e.target.value) ?? 90 })}
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
            <div className="rounded border px-3 py-2 text-sm bg-gray-50">
              {retentionLoading ? '正在加载…' : retention ? (retention.enabled ? '已开启' : '已关闭') : '—'}
            </div>
          </div>
        </div>
      </div>

      {detailOpen ? (
        <div className="fixed inset-0 z-50 flex items-stretch justify-end bg-black/30" onClick={() => setDetailOpen(false)}>
          <div className="w-full max-w-2xl h-full bg-white shadow-xl" onClick={(e) => e.stopPropagation()}>
            <div className="p-4 border-b flex items-center justify-between">
              <div className="font-semibold">日志详情</div>
              <button className="rounded border px-3 py-1.5 text-sm" type="button" onClick={() => setDetailOpen(false)}>
                关闭
              </button>
            </div>
            <div className="p-4 space-y-3 overflow-auto h-[calc(100%-57px)]">
              {detailLoading ? (
                <div className="text-sm text-gray-600">正在加载详情…</div>
              ) : detail ? (
                <>
                  {'action' in detail ? (
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
                      <div className="rounded border p-2 col-span-2">
                        <div className="text-xs text-gray-600">消息</div>
                        <div className="whitespace-pre-wrap break-words">{detail.message ?? '—'}</div>
                      </div>
                      <div className="rounded border p-2 col-span-2">
                        <div className="text-xs text-gray-600">详情（JSON）</div>
                        <pre className="text-xs bg-gray-50 rounded p-2 overflow-auto whitespace-pre-wrap break-words">
                          {safeJson(detail.details ?? {})}
                        </pre>
                      </div>
                    </div>
                  ) : (
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
                      <div className="rounded border p-2 col-span-2">
                        <div className="flex items-center justify-between gap-2">
                          <div className="text-xs text-gray-600">请求体</div>
                          {(() => {
                            const reqBody = getLogBody(detail.details ?? null, 'reqBody');
                            const body = reqBody?.body;
                            if (!body) return null;
                            return (
                              <button
                                className="text-xs rounded border px-2 py-1 bg-white"
                                type="button"
                                onClick={() => copyToClipboard(String(body))}
                              >
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
                                <pre className="text-xs bg-gray-50 rounded p-2 overflow-auto whitespace-pre-wrap break-words mt-2">
                                  {String(reqBody.body)}
                                </pre>
                              ) : (
                                <div className="text-xs text-gray-500 mt-2">—</div>
                              )}
                            </>
                          );
                        })()}
                      </div>
                      <div className="rounded border p-2 col-span-2">
                        <div className="flex items-center justify-between gap-2">
                          <div className="text-xs text-gray-600">响应体</div>
                          {(() => {
                            const resBody = getLogBody(detail.details ?? null, 'resBody');
                            const body = resBody?.body;
                            if (!body) return null;
                            return (
                              <button
                                className="text-xs rounded border px-2 py-1 bg-white"
                                type="button"
                                onClick={() => copyToClipboard(String(body))}
                              >
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
                                <pre className="text-xs bg-gray-50 rounded p-2 overflow-auto whitespace-pre-wrap break-words mt-2">
                                  {String(resBody.body)}
                                </pre>
                              ) : (
                                <div className="text-xs text-gray-500 mt-2">—</div>
                              )}
                            </>
                          );
                        })()}
                      </div>
                      <div className="rounded border p-2 col-span-2">
                        <div className="text-xs text-gray-600">详情（JSON）</div>
                        <pre className="text-xs bg-gray-50 rounded p-2 overflow-auto whitespace-pre-wrap break-words">
                          {safeJson(detail.details ?? {})}
                        </pre>
                      </div>
                    </div>
                  )}
                </>
              ) : (
                <div className="text-sm text-gray-600">未加载到详情</div>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default GlobalLogsForm;
