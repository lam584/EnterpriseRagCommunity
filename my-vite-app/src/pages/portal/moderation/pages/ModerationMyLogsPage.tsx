import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  portalExportMyAuditLogsCsv,
  portalListMyAuditLogs,
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

function parsePositiveInt(s: string): number | undefined {
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

function extractReason(it: AuditLogDTO): string {
  const d = it.details;
  if (!d || typeof d !== 'object') return '';
  const v = (d as Record<string, unknown>)['reason'];
  if (typeof v === 'string') return v;
  if (v == null) return '';
  return String(v);
}

type QueryState = {
  keyword: string;
  action: string;
  entityType: string;
  entityId: string;
  result: string;
  traceId: string;
};

const defaultQueryState: QueryState = {
  keyword: '',
  action: '',
  entityType: '',
  entityId: '',
  result: '',
  traceId: '',
};

export default function ModerationMyLogsPage() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [query, setQuery] = useState<QueryState>(defaultQueryState);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const [items, setItems] = useState<AuditLogDTO[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<AuditLogDTO | null>(null);

  const entityIdParsed = useMemo(() => {
    if (!query.entityId.trim()) return undefined;
    return parsePositiveInt(query.entityId.trim());
  }, [query.entityId]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await portalListMyAuditLogs({
        page,
        pageSize,
        keyword: query.keyword.trim() || undefined,
        action: query.action.trim() || undefined,
        entityType: query.entityType.trim() || undefined,
        entityId: entityIdParsed,
        result: query.result.trim() || undefined,
        traceId: query.traceId.trim() || undefined,
        sort: 'createdAt,desc',
      });
      setItems(res.content ?? []);
      setTotalPages(res.totalPages ?? 1);
      setTotalElements(res.totalElements ?? 0);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setItems([]);
      setTotalPages(1);
      setTotalElements(0);
    } finally {
      setLoading(false);
    }
  }, [entityIdParsed, page, pageSize, query.action, query.entityType, query.keyword, query.result, query.traceId]);

  useEffect(() => {
    void load();
  }, [load]);

  const exportCsv = useCallback(async () => {
    setError(null);
    try {
      const blob = await portalExportMyAuditLogsCsv({
        keyword: query.keyword.trim() || undefined,
        action: query.action.trim() || undefined,
        entityType: query.entityType.trim() || undefined,
        entityId: entityIdParsed,
        result: query.result.trim() || undefined,
        traceId: query.traceId.trim() || undefined,
        sort: 'createdAt,desc',
      });
      downloadBlob(blob, 'my-moderation-audit-logs.csv');
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, [entityIdParsed, query.action, query.entityType, query.keyword, query.result, query.traceId]);

  const pageLabel = useMemo(() => {
    const start = totalElements === 0 ? 0 : (page - 1) * pageSize + 1;
    const end = Math.min(page * pageSize, totalElements);
    return `${start}-${end} / ${totalElements}`;
  }, [page, pageSize, totalElements]);

  return (
    <div className="space-y-4">
      <div className="rounded bg-white shadow p-4 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="space-y-1">
            <div className="text-lg font-semibold text-gray-900">我的治理记录</div>
            <div className="text-xs text-gray-500">默认仅展示 MODERATION_* 相关操作，可用于自证与追溯</div>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={exportCsv}
              className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-50 disabled:opacity-60"
              disabled={loading}
            >
              导出 CSV
            </button>
            <button
              type="button"
              onClick={() => void load()}
              className="rounded bg-gray-100 px-3 py-1.5 text-sm text-gray-700 hover:bg-gray-200 disabled:opacity-60"
              disabled={loading}
            >
              {loading ? '加载中…' : '刷新'}
            </button>
          </div>
        </div>

        {error ? <div className="text-sm text-red-600">{error}</div> : null}

        <div className="grid grid-cols-1 md:grid-cols-6 gap-3">
          <input
            className="rounded border border-gray-300 px-3 py-2 text-sm md:col-span-2"
            placeholder="关键词（action/entityType）"
            value={query.keyword}
            onChange={(e) => {
              setPage(1);
              setQuery((q) => ({ ...q, keyword: e.target.value }));
            }}
            disabled={loading}
          />
          <input
            className="rounded border border-gray-300 px-3 py-2 text-sm"
            placeholder="action（可选）"
            value={query.action}
            onChange={(e) => {
              setPage(1);
              setQuery((q) => ({ ...q, action: e.target.value }));
            }}
            disabled={loading}
          />
          <input
            className="rounded border border-gray-300 px-3 py-2 text-sm"
            placeholder="entityType（可选）"
            value={query.entityType}
            onChange={(e) => {
              setPage(1);
              setQuery((q) => ({ ...q, entityType: e.target.value }));
            }}
            disabled={loading}
          />
          <input
            className="rounded border border-gray-300 px-3 py-2 text-sm"
            placeholder="entityId（可选）"
            value={query.entityId}
            onChange={(e) => {
              setPage(1);
              setQuery((q) => ({ ...q, entityId: e.target.value }));
            }}
            disabled={loading}
          />
          <input
            className="rounded border border-gray-300 px-3 py-2 text-sm"
            placeholder="traceId（可选）"
            value={query.traceId}
            onChange={(e) => {
              setPage(1);
              setQuery((q) => ({ ...q, traceId: e.target.value }));
            }}
            disabled={loading}
          />
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <select
              className="rounded border border-gray-300 px-3 py-2 text-sm"
              value={query.result}
              onChange={(e) => {
                setPage(1);
                setQuery((q) => ({ ...q, result: e.target.value }));
              }}
              disabled={loading}
            >
              <option value="">全部结果</option>
              <option value="SUCCESS">成功</option>
              <option value="FAIL">失败</option>
              <option value="SKIP">跳过</option>
            </select>
            <select
              className="rounded border border-gray-300 px-3 py-2 text-sm"
              value={String(pageSize)}
              onChange={(e) => {
                const n = parsePositiveInt(e.target.value) ?? 20;
                setPage(1);
                setPageSize(Math.min(Math.max(n, 1), 200));
              }}
              disabled={loading}
            >
              <option value="10">10/页</option>
              <option value="20">20/页</option>
              <option value="50">50/页</option>
              <option value="100">100/页</option>
            </select>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-xs text-gray-500">{pageLabel}</span>
            <button
              type="button"
              className="rounded border border-gray-300 px-3 py-1 text-sm text-gray-700 disabled:opacity-60"
              disabled={loading || page <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
            >
              上一页
            </button>
            <button
              type="button"
              className="rounded border border-gray-300 px-3 py-1 text-sm text-gray-700 disabled:opacity-60"
              disabled={loading || page >= totalPages}
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            >
              下一页
            </button>
          </div>
        </div>

        <div className="overflow-auto rounded border border-gray-200">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 text-gray-700">
              <tr>
                <th className="px-3 py-2 text-left font-medium">时间</th>
                <th className="px-3 py-2 text-left font-medium">动作</th>
                <th className="px-3 py-2 text-left font-medium">目标</th>
                <th className="px-3 py-2 text-left font-medium">结果</th>
                <th className="px-3 py-2 text-left font-medium">原因</th>
                <th className="px-3 py-2 text-left font-medium">摘要</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {items.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-3 py-6 text-center text-gray-500">
                    {loading ? '加载中…' : '暂无记录'}
                  </td>
                </tr>
              ) : (
                items.map((it) => (
                  <tr
                    key={it.id}
                    className="hover:bg-gray-50 cursor-pointer"
                    onClick={() => {
                      setDetail(it);
                      setDetailOpen(true);
                    }}
                  >
                    <td className="px-3 py-2 whitespace-nowrap text-gray-900">{formatDateTime(it.createdAt)}</td>
                    <td className="px-3 py-2 whitespace-nowrap">{actionLabel(it.action)}</td>
                    <td className="px-3 py-2 whitespace-nowrap">
                      {entityTypeLabel(it.entityType)}
                      {it.entityId ? `#${it.entityId}` : ''}
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap">{resultLabel(it.result)}</td>
                    <td className="px-3 py-2 text-gray-700">
                      <div className="line-clamp-2">{extractReason(it) || '—'}</div>
                    </td>
                    <td className="px-3 py-2 text-gray-700">{it.message ?? ''}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {detailOpen ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-3xl rounded bg-white shadow-lg">
            <div className="flex items-center justify-between border-b px-4 py-3">
              <div className="text-sm font-semibold text-gray-900">治理记录详情</div>
              <button
                type="button"
                onClick={() => {
                  setDetailOpen(false);
                  setDetail(null);
                }}
                className="text-gray-500 hover:text-gray-800"
              >
                ×
              </button>
            </div>
            <div className="p-4 space-y-3">
              {detail ? (
                <>
                  <div className="text-xs text-gray-500">
                    {formatDateTime(detail.createdAt)} · {actionLabel(detail.action)} · {entityTypeLabel(detail.entityType)}
                    {detail.entityId ? `#${detail.entityId}` : ''} · {resultLabel(detail.result)}
                  </div>
                  {extractReason(detail) ? (
                    <div className="rounded border border-gray-200 bg-gray-50 px-3 py-2 text-xs text-gray-700">
                      原因：{extractReason(detail)}
                    </div>
                  ) : null}
                  <div className="rounded border border-gray-200 p-3 text-xs whitespace-pre-wrap max-h-[360px] overflow-auto">
                    {safeJson(detail.details ?? detail)}
                  </div>
                </>
              ) : null}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
