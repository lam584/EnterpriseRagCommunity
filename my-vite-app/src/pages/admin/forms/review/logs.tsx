import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  adminExportAuditLogsCsv,
  adminGetAuditLogDetail,
  adminListAuditLogs,
  type AuditLogDTO,
} from '../../../../services/auditLogService';
import { downloadBlob } from '../../../../utils/download';

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

const ACTION_LABELS: Record<string, string> = {
  LLM_DECISION: 'LLM 自动审核',
  RULE_DECISION: '规则自动审核',
  VEC_DECISION: '向量相似度审核',
  MODERATION_MANUAL_APPROVE: '人工：通过/驳回举报',
  MODERATION_MANUAL_REJECT: '人工：驳回/核实举报',
  MODERATION_MANUAL_CLAIM: '人工：认领',
  MODERATION_MANUAL_RELEASE: '人工：释放',
  MODERATION_MANUAL_TO_HUMAN: '人工：进入人工审核',
  MODERATION_MANUAL_REQUEUE: '人工：重新自动审核',
  MODERATION_MANUAL_SET_RISK_TAGS: '人工：更新风险标签',
  MODERATION_MANUAL_BACKFILL: '人工：补齐历史待审入队',
  EMAIL_SEND: '邮件发送',
};

function actionLabel(action?: string | null): string {
  if (!action) return '—';
  return ACTION_LABELS[action] ?? action;
}

const ENTITY_TYPE_LABELS: Record<string, string> = {
  MODERATION_QUEUE: '审核队列',
  POST: '帖子',
  COMMENT: '评论',
  SYSTEM: '系统',
  VECTOR_INDEX: '向量索引',
  EMAIL: '邮件',
};

function entityTypeLabel(entityType?: string | null): string {
  if (!entityType) return '—';
  return ENTITY_TYPE_LABELS[entityType] ?? entityType;
}

const RESULT_LABELS: Record<string, string> = {
  SUCCESS: '成功',
  FAIL: '失败',
  SKIP: '跳过',
};

function resultLabel(result?: string | null): string {
  if (!result) return '—';
  return RESULT_LABELS[result] ?? result;
}

type QueryState = {
  keyword: string;
  actorId: string;
  actorName: string;
  action: string;
  entityType: string;
  entityId: string;
  result: string;
  traceId: string;
  createdFrom: string;
  createdTo: string;
};

const defaultQueryState: QueryState = {
  keyword: '',
  actorId: '',
  actorName: '',
  action: '',
  entityType: '',
  entityId: '',
  result: '',
  traceId: '',
  createdFrom: '',
  createdTo: '',
};

const LogsForm: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [page, setPage] = useState<number>(() => parsePositiveInt(searchParams.get('page')) ?? 1);
  const [pageSize, setPageSize] = useState<number>(() => parsePositiveInt(searchParams.get('pageSize')) ?? 20);

  const [query, setQuery] = useState<QueryState>(() => {
    const q: QueryState = { ...defaultQueryState };
    for (const k of Object.keys(q) as (keyof QueryState)[]) {
      const v = searchParams.get(k);
      if (typeof v === 'string') q[k] = v;
    }
    return q;
  });

  const [items, setItems] = useState<AuditLogDTO[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<AuditLogDTO | null>(null);

  const actorIdParsed = useMemo(() => {
    const n = Number(query.actorId);
    if (!query.actorId.trim()) return undefined;
    return Number.isFinite(n) && n > 0 ? Math.floor(n) : undefined;
  }, [query.actorId]);

  const entityIdParsed = useMemo(() => {
    const n = Number(query.entityId);
    if (!query.entityId.trim()) return undefined;
    return Number.isFinite(n) && n > 0 ? Math.floor(n) : undefined;
  }, [query.entityId]);

  const syncToUrl = useCallback(
    (nextQuery: QueryState, nextPage = page, nextPageSize = pageSize) => {
      setSearchParams(
        (prev) => {
          const sp = new URLSearchParams(prev);

          // 关键：日志页内部的 URL 同步只维护自己的查询参数，不要触碰 AdminSection 用来控制二级菜单的 active。
          // 否则当 load()/筛选触发 setSearchParams 时，会把 active 也写回上一轮的值，引发父子互相“纠正”导致反复跳转。
          sp.delete('active');

          sp.set('page', String(nextPage));
          sp.set('pageSize', String(nextPageSize));

          for (const [k, v] of Object.entries(nextQuery)) {
            if (!v) sp.delete(k);
            else sp.set(k, v);
          }

          // 若 URL 没有变化，不要重复写入（避免触发导航节流/闪烁）
          if (sp.toString() === prev.toString()) return prev;

          return sp;
        },
        { replace: true }
      );
    },
    [page, pageSize, setSearchParams]
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await adminListAuditLogs({
        page,
        pageSize,
        keyword: query.keyword || undefined,
        actorId: actorIdParsed,
        actorName: query.actorName || undefined,
        action: query.action || undefined,
        entityType: query.entityType || undefined,
        entityId: entityIdParsed,
        result: query.result || undefined,
        traceId: query.traceId || undefined,
        createdFrom: query.createdFrom || undefined,
        createdTo: query.createdTo || undefined,
        sort: 'createdAt,desc',
      });

      if (!mountedRef.current) return;

      setItems(res.content ?? []);
      setTotalPages(res.totalPages ?? 1);
      setTotalElements(res.totalElements ?? 0);

      syncToUrl(query, page, pageSize);
    } catch (e) {
      if (!mountedRef.current) return;
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, [actorIdParsed, entityIdParsed, page, pageSize, query, syncToUrl]);

  useEffect(() => {
    load();
  }, [load]);

  const openDetail = useCallback(async (id: number) => {
    setDetailOpen(true);
    setDetailLoading(true);
    setDetail(null);
    setError(null);
    try {
      const d = await adminGetAuditLogDetail(id);
      setDetail(d);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const exportCsv = useCallback(async () => {
    setError(null);
    try {
      const blob = await adminExportAuditLogsCsv({
        keyword: query.keyword || undefined,
        actorId: actorIdParsed,
        actorName: query.actorName || undefined,
        action: query.action || undefined,
        entityType: query.entityType || undefined,
        entityId: entityIdParsed,
        result: query.result || undefined,
        traceId: query.traceId || undefined,
        createdFrom: query.createdFrom || undefined,
        createdTo: query.createdTo || undefined,
        sort: 'createdAt,desc',
      });
      const ts = new Date();
      const filename = `audit-logs_${ts.getFullYear()}-${String(ts.getMonth() + 1).padStart(2, '0')}-${String(ts.getDate()).padStart(2, '0')}.csv`;
      downloadBlob(blob, filename);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [actorIdParsed, entityIdParsed, query]);

  const resetFilters = () => {
    setQuery(defaultQueryState);
    setPage(1);
    syncToUrl(defaultQueryState, 1, pageSize);
  };

  const canPrev = page > 1;
  const canNext = page < totalPages;

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-lg font-semibold">审核日志与追溯</h3>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={exportCsv}
            className="rounded border px-4 py-2 disabled:opacity-60"
            disabled={loading}
            title="按当前筛选条件导出 CSV"
          >
            导出 CSV
          </button>
          <button
            type="button"
            onClick={load}
            className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60"
            disabled={loading}
          >
            {loading ? '加载中…' : '搜索'}
          </button>
        </div>
      </div>

      {error ? (
        <div className="rounded border border-red-200 bg-red-50 text-red-800 px-3 py-2 text-sm flex items-center justify-between gap-3">
          <span>错误：{error}</span>
          <button type="button" className="rounded bg-red-600 text-white px-3 py-1.5" onClick={load}>
            重试
          </button>
        </div>
      ) : null}

      {/* 过滤器 */}
      <div className="rounded border bg-gray-50 p-3 space-y-3">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <input
            className="rounded border px-3 py-2"
            placeholder="关键字（message/details/actor/entity）"
            value={query.keyword}
            onChange={(e) => {
              setQuery((q) => ({ ...q, keyword: e.target.value }));
              setPage(1);
            }}
          />

          <input
            className="rounded border px-3 py-2"
            placeholder="操作者ID（可选）"
            value={query.actorId}
            onChange={(e) => {
              setQuery((q) => ({ ...q, actorId: e.target.value }));
              setPage(1);
            }}
          />

          <input
            className="rounded border px-3 py-2"
            placeholder="操作者名称（可选）"
            value={query.actorName}
            onChange={(e) => {
              setQuery((q) => ({ ...q, actorName: e.target.value }));
              setPage(1);
            }}
          />

          <input
            className="rounded border px-3 py-2"
            placeholder="TraceId（用于追溯链路）"
            value={query.traceId}
            onChange={(e) => {
              setQuery((q) => ({ ...q, traceId: e.target.value }));
              setPage(1);
            }}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <input
            className="rounded border px-3 py-2"
            placeholder="操作（例如 LLM_DECISION）"
            value={query.action}
            onChange={(e) => {
              setQuery((q) => ({ ...q, action: e.target.value }));
              setPage(1);
            }}
          />

          <select
            className="rounded border px-3 py-2"
            value={query.entityType}
            onChange={(e) => {
              setQuery((q) => ({ ...q, entityType: e.target.value }));
              setPage(1);
            }}
          >
            <option value="">全部实体类型</option>
            <option value="MODERATION_QUEUE">审核队列（MODERATION_QUEUE）</option>
            <option value="POST">帖子（POST）</option>
            <option value="COMMENT">评论（COMMENT）</option>
            <option value="SYSTEM">系统（SYSTEM）</option>
          </select>

          <input
            className="rounded border px-3 py-2"
            placeholder="实体ID（可选）"
            value={query.entityId}
            onChange={(e) => {
              setQuery((q) => ({ ...q, entityId: e.target.value }));
              setPage(1);
            }}
          />

          <select
            className="rounded border px-3 py-2"
            value={query.result}
            onChange={(e) => {
              setQuery((q) => ({ ...q, result: e.target.value }));
              setPage(1);
            }}
          >
            <option value="">全部结果</option>
            <option value="SUCCESS">成功（SUCCESS）</option>
            <option value="FAIL">失败（FAIL）</option>
            <option value="SKIP">跳过（SKIP）</option>
          </select>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <input
            className="rounded border px-3 py-2"
            placeholder="开始时间（ISO 或 2025-12-31T00:00:00）"
            value={query.createdFrom}
            onChange={(e) => {
              setQuery((q) => ({ ...q, createdFrom: e.target.value }));
              setPage(1);
            }}
          />
          <input
            className="rounded border px-3 py-2"
            placeholder="结束时间（ISO）"
            value={query.createdTo}
            onChange={(e) => {
              setQuery((q) => ({ ...q, createdTo: e.target.value }));
              setPage(1);
            }}
          />

          <select
            className="rounded border px-3 py-2"
            value={pageSize}
            onChange={(e) => {
              setPageSize(Number(e.target.value));
              setPage(1);
            }}
          >
            <option value={10}>10 / 页</option>
            <option value={20}>20 / 页</option>
            <option value={50}>50 / 页</option>
            <option value={100}>100 / 页</option>
          </select>

          <div className="flex items-center gap-2">
            <button type="button" className="rounded border px-4 py-2" onClick={resetFilters}>
              重置
            </button>
            <button
              type="button"
              className="rounded border px-4 py-2"
              onClick={() => {
                // 应用当前条件，但不额外触发 load（useEffect 会触发）
                syncToUrl(query, 1, pageSize);
                setPage(1);
              }}
            >
              应用到URL
            </button>
          </div>
        </div>

        <div className="text-xs text-gray-600">
          提示：点击列表行可打开详情；在详情里点击 TraceId 可以一键追溯整条链路。
        </div>
      </div>

      {/* 列表 */}
      <div className="overflow-x-auto rounded border">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 text-gray-700">
            <tr className="text-left">
              <th className="px-3 py-2">时间</th>
              <th className="px-3 py-2">操作</th>
              <th className="px-3 py-2">实体</th>
              <th className="px-3 py-2">操作者</th>
              <th className="px-3 py-2">结果</th>
              <th className="px-3 py-2">追溯ID</th>
              <th className="px-3 py-2">消息</th>
            </tr>
          </thead>
          <tbody>
            {items.map((it) => (
              <tr
                key={it.id}
                className="border-t hover:bg-blue-50 cursor-pointer"
                onClick={() => openDetail(it.id)}
                title="点击查看详情"
              >
                <td className="px-3 py-2 whitespace-nowrap">{formatDateTime(it.createdAt)}</td>
                <td className="px-3 py-2 whitespace-nowrap" title={it.action ?? undefined}>
                  {actionLabel(it.action)}
                </td>
                <td className="px-3 py-2 whitespace-nowrap" title={it.entityType ?? undefined}>
                  {entityTypeLabel(it.entityType)}{it.entityId ? `#${it.entityId}` : ''}
                </td>
                <td className="px-3 py-2 whitespace-nowrap">
                  <div className="flex flex-col">
                    <span>{it.actorName || '—'}</span>
                    <span className="text-xs text-gray-500">{it.actorType || '—'}{it.actorId ? `#${it.actorId}` : ''}</span>
                  </div>
                </td>
                <td className="px-3 py-2 whitespace-nowrap" title={it.result ?? undefined}>{resultLabel(it.result)}</td>
                <td className="px-3 py-2 whitespace-nowrap font-mono text-xs">{it.traceId ?? '—'}</td>
                <td className="px-3 py-2 max-w-[520px] truncate">{it.message ?? '—'}</td>
              </tr>
            ))}

            {!loading && items.length === 0 ? (
              <tr>
                <td className="px-3 py-6 text-center text-gray-500" colSpan={7}>
                  暂无数据。可以尝试清空筛选条件，或检查时间范围/TraceId 是否正确。
                </td>
              </tr>
            ) : null}

            {loading ? (
              <tr>
                <td className="px-3 py-6 text-center text-gray-500" colSpan={7}>
                  加载中…
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>

      {/* 分页 */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
        <div className="text-sm text-gray-600">
          共 {totalElements} 条，{totalPages} 页；当前第 {page} 页
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            className="rounded border px-3 py-1.5 disabled:opacity-60"
            disabled={!canPrev || loading}
            onClick={() => setPage((p) => Math.max(1, p - 1))}
          >
            上一页
          </button>
          <button
            type="button"
            className="rounded border px-3 py-1.5 disabled:opacity-60"
            disabled={!canNext || loading}
            onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
          >
            下一页
          </button>
        </div>
      </div>

      {/* 详情抽屉（轻量实现） */}
      {detailOpen ? (
        <div className="fixed inset-0 z-50">
          <div className="absolute inset-0 bg-black/40" onClick={() => setDetailOpen(false)} />
          <div className="absolute right-0 top-0 h-full w-full max-w-2xl bg-white shadow-xl flex flex-col">
            <div className="p-4 border-b flex items-center justify-between">
              <div className="flex flex-col">
                <span className="font-semibold">日志详情</span>
                <span className="text-xs text-gray-500">ID：{detail?.id ?? '—'}</span>
              </div>
              <button type="button" className="rounded border px-3 py-1.5" onClick={() => setDetailOpen(false)}>
                关闭
              </button>
            </div>

            <div className="p-4 overflow-auto space-y-3">
              {detailLoading ? (
                <div className="text-gray-500">加载详情中…</div>
              ) : detail ? (
                <>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <div className="rounded border p-3">
                      <div className="text-xs text-gray-500">时间</div>
                      <div>{formatDateTime(detail.createdAt)}</div>
                    </div>
                    <div className="rounded border p-3">
                      <div className="text-xs text-gray-500">结果</div>
                      <div title={detail.result ?? undefined}>{resultLabel(detail.result)}</div>
                    </div>
                    <div className="rounded border p-3">
                      <div className="text-xs text-gray-500">操作</div>
                      <div className="break-all" title={detail.action ?? undefined}>{actionLabel(detail.action)}</div>
                    </div>
                    <div className="rounded border p-3">
                      <div className="text-xs text-gray-500">操作者</div>
                      <div>{detail.actorName ?? '—'}</div>
                      <div className="text-xs text-gray-500">{detail.actorType ?? '—'}{detail.actorId ? `#${detail.actorId}` : ''}</div>
                    </div>
                    <div className="rounded border p-3 md:col-span-2">
                      <div className="text-xs text-gray-500">实体</div>
                      <div className="break-all" title={detail.entityType ?? undefined}>
                        {entityTypeLabel(detail.entityType)}{detail.entityId ? `#${detail.entityId}` : ''}
                      </div>
                      <div className="mt-2 flex flex-wrap gap-2">
                        {detail.entityType === 'MODERATION_QUEUE' && detail.entityId ? (
                          <button
                            type="button"
                            className="rounded bg-blue-600 text-white px-3 py-1.5"
                            onClick={() => {
                              // 跳到审核队列并用任务 ID 定位
                              navigate('/admin/review?active=queue');
                              // 当前页面 URL 里的 active 由 sections.tsx 管，这里只做深链路跳转
                              window.setTimeout(() => {
                                // 如果 queue 页面支持 URL 参数，可在后续加上；这里先保持最小可用
                              }, 0);
                            }}
                          >
                            打开审核队列
                          </button>
                        ) : null}

                        {detail.entityType === 'POST' && detail.entityId ? (
                          <button
                            type="button"
                            className="rounded border px-3 py-1.5"
                            onClick={() => navigate(`/posts/${detail.entityId}`)}
                          >
                            查看帖子
                          </button>
                        ) : null}

                        {detail.entityType === 'COMMENT' && detail.entityId ? (
                          <button
                            type="button"
                            className="rounded border px-3 py-1.5"
                            onClick={() => navigate(`/comments/${detail.entityId}`)}
                            title="若站点没有 /comments/:id 路由，可改为跳到所属帖子并定位评论"
                          >
                            查看评论
                          </button>
                        ) : null}
                      </div>
                    </div>
                    <div className="rounded border p-3 md:col-span-2">
                      <div className="text-xs text-gray-500">TraceId（追溯链路）</div>
                      <div className="flex items-center justify-between gap-2">
                        <div className="font-mono break-all text-sm">{detail.traceId ?? '—'}</div>
                        {detail.traceId ? (
                          <button
                            type="button"
                            className="rounded bg-gray-900 text-white px-3 py-1.5"
                            onClick={() => {
                              const next = { ...query, traceId: detail.traceId ?? '' };
                              setQuery(next);
                              setPage(1);
                              syncToUrl(next, 1, pageSize);
                            }}
                          >
                            追溯该 Trace
                          </button>
                        ) : null}
                      </div>
                    </div>
                    <div className="rounded border p-3 md:col-span-2">
                      <div className="text-xs text-gray-500">消息</div>
                      <div className="whitespace-pre-wrap break-words">{detail.message ?? '—'}</div>
                    </div>
                  </div>

                  <div className="rounded border p-3">
                    <div className="text-xs text-gray-500 mb-2">详情（JSON）</div>
                    <pre className="text-xs bg-gray-50 rounded p-3 overflow-auto max-h-[320px]">{safeJson(detail.details ?? {})}</pre>
                  </div>

                  <div className="rounded border p-3">
                    <div className="text-xs text-gray-500 mb-2">原始数据</div>
                    <pre className="text-xs bg-gray-50 rounded p-3 overflow-auto max-h-[320px]">{safeJson(detail)}</pre>
                  </div>
                </>
              ) : (
                <div className="text-gray-500">未找到详情</div>
              )}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default LogsForm;
