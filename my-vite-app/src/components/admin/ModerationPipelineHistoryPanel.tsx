import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  adminGetPipelineByRunId,
  adminListPipelineHistory,
  type AdminModerationPipelineRunDTO,
  type AdminModerationPipelineRunDetailDTO,
  type AdminModerationPipelineRunHistoryPageDTO,
  type AdminModerationPipelineHistoryQuery,
  type PipelineStepStage,
} from '../../services/moderationPipelineService';

function formatDateTime(s?: string | null): string {
  if (!s) return '—';
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return String(s);
  return d.toLocaleString();
}

function formatMs(ms?: number | null): string {
  if (ms == null) return '—';
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

function safeJson(v: unknown): string {
  try {
    return JSON.stringify(v, null, 2);
  } catch {
    return String(v);
  }
}

type Mode =
  | { kind: 'queue'; queueId: number }
  | { kind: 'content'; contentType: 'POST' | 'COMMENT'; contentId: number };

export const ModerationPipelineHistoryPanel: React.FC<{
  title?: string;
  initialMode?: Mode;
  /** If true (default), and initialMode is not provided, auto load latest runs (page=1) on mount. */
  autoLatest?: boolean;
  /**
   * 只展示指定 stage 的 steps。
   * - 不传：展示全部 stages（兼容现有行为）
   * - 传入：例如 ['RULE'] / ['VEC'] / ['LLM']
   */
  stageFilter?: PipelineStepStage[];
}> = ({ title = '历史记录（流水线 Run + Steps）', initialMode, autoLatest = true, stageFilter }) => {
  const navigate = useNavigate();

  const [mode, setMode] = useState<Mode | null>(initialMode ?? null);

  const [queueId, setQueueId] = useState<string>(() => (initialMode?.kind === 'queue' ? String(initialMode.queueId) : ''));
  const [contentType, setContentType] = useState<'POST' | 'COMMENT'>(() => (initialMode?.kind === 'content' ? initialMode.contentType : 'POST'));
  const [contentId, setContentId] = useState<string>(() => (initialMode?.kind === 'content' ? String(initialMode.contentId) : ''));

  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [data, setData] = useState<AdminModerationPipelineRunHistoryPageDTO | null>(null);

  // Sync external initialMode -> internal state (important when page reads URL params)
  useEffect(() => {
    if (!initialMode) return;
    setMode(initialMode);
    if (initialMode.kind === 'queue') {
      setQueueId(String(initialMode.queueId));
    } else {
      setContentType(initialMode.contentType);
      setContentId(String(initialMode.contentId));
    }
    setPage(1);
  }, [initialMode]);

  const parsedQueueId = useMemo(() => {
    const n = Number(queueId);
    return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
  }, [queueId]);

  const parsedContentId = useMemo(() => {
    const n = Number(contentId);
    return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
  }, [contentId]);

  const canPrev = page > 1;
  const canNext = (data?.totalPages ?? 1) > page;

  const buildQuery = useCallback((): AdminModerationPipelineHistoryQuery => {
    if (mode?.kind === 'queue') {
      return { queueId: mode.queueId, page, pageSize };
    }
    if (mode?.kind === 'content') {
      return { contentType: mode.contentType, contentId: mode.contentId, page, pageSize };
    }
    // fallback: try infer from inputs
    if (parsedQueueId) return { queueId: parsedQueueId, page, pageSize };
    if (parsedContentId) return { contentType, contentId: parsedContentId, page, pageSize };
    // final fallback: global latest
    return { page, pageSize };
  }, [contentType, mode, page, pageSize, parsedContentId, parsedQueueId]);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const q = buildQuery();
      const res = await adminListPipelineHistory(q);
      setData(res);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [buildQuery]);

  // Auto load on mount:
  // - if mode is set (e.g. initialMode), normal effect below will handle it
  // - otherwise, optionally load latest logs once for convenience
  const [autoLatestLoaded, setAutoLatestLoaded] = useState(false);
  useEffect(() => {
    if (mode) return;
    if (!autoLatest) return;
    if (autoLatestLoaded) return;
    setAutoLatestLoaded(true);
    void load();
  }, [autoLatest, autoLatestLoaded, load, mode]);

  // Auto load whenever mode/page/pageSize changes (this is what users expect after clicking “按 queueId 查询” or paging)
  useEffect(() => {
    // If a mode exists, always auto-load.
    if (mode) {
      void load();
    }
  }, [load, mode, page, pageSize]);

  const applyQueueMode = () => {
    if (!parsedQueueId) {
      setError('请输入合法的 queueId');
      return;
    }
    setError(null);
    setPage(1);
    setMode({ kind: 'queue', queueId: parsedQueueId });
  };

  const applyContentMode = () => {
    if (!parsedContentId) {
      setError('请输入合法的 contentId');
      return;
    }
    setError(null);
    setPage(1);
    setMode({ kind: 'content', contentType, contentId: parsedContentId });
  };

  // details cache: runId -> detail
  const [detailMap, setDetailMap] = useState<Record<string, AdminModerationPipelineRunDetailDTO | undefined>>({});
  const [detailLoadingId, setDetailLoadingId] = useState<number | null>(null);

  const openDetail = async (runId: number) => {
    setDetailLoadingId(runId);
    setError(null);
    try {
      const d = await adminGetPipelineByRunId(runId);
      setDetailMap((m) => ({ ...m, [String(runId)]: d }));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setDetailLoadingId(null);
    }
  };

  const runs = useMemo(() => {
    const runsRaw: AdminModerationPipelineRunDTO[] = (data?.content ?? []) as AdminModerationPipelineRunDTO[];
    // Sort by createdAt desc (fallback to startedAt/endAt)
    return runsRaw
      .slice()
      .sort((a, b) => {
        const ta = new Date(a.createdAt ?? a.startedAt ?? a.endedAt ?? 0).getTime();
        const tb = new Date(b.createdAt ?? b.startedAt ?? b.endedAt ?? 0).getTime();
        return tb - ta;
      });
  }, [data]);

  const stageFilterSet = useMemo(() => {
    if (!stageFilter || stageFilter.length === 0) return null;
    return new Set(stageFilter.map((s) => String(s)));
  }, [stageFilter]);

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-3">
      <div className="flex items-center justify-between gap-3">
        <div>
          <div className="text-lg font-semibold">{title}</div>
          <div className="text-sm text-gray-500">支持按 queueId 或按 contentType+contentId 聚合查询。</div>
        </div>
        <button type="button" className="rounded border px-3 py-2 disabled:opacity-60" disabled={loading} onClick={() => void load()}>
          刷新
        </button>
      </div>

      {error ? <div className="rounded border border-red-200 bg-red-50 text-red-700 px-3 py-2 text-sm">{error}</div> : null}

      <div className="rounded border bg-gray-50 p-3 space-y-2">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-2 items-end">
          <div>
            <div className="text-sm font-medium mb-1">queueId</div>
            <input className="w-full rounded border px-3 py-2" value={queueId} onChange={(e) => setQueueId(e.target.value)} placeholder="例如 123" />
          </div>
          <div className="flex items-end">
            <button type="button" className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60" onClick={applyQueueMode} disabled={loading}>
              按 queueId 查询
            </button>
          </div>
          <div className="text-xs text-gray-500 md:text-right">
            当前模式：{mode ? (mode.kind === 'queue' ? `queueId=${mode.queueId}` : `${mode.contentType}#${mode.contentId}`) : '未选择'}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-2 items-end">
          <div>
            <div className="text-sm font-medium mb-1">contentType</div>
            <select className="w-full rounded border px-3 py-2" value={contentType} onChange={(e) => setContentType(e.target.value as 'POST' | 'COMMENT')}>
              <option value="POST">POST</option>
              <option value="COMMENT">COMMENT</option>
            </select>
          </div>
          <div>
            <div className="text-sm font-medium mb-1">contentId</div>
            <input className="w-full rounded border px-3 py-2" value={contentId} onChange={(e) => setContentId(e.target.value)} placeholder="例如 456" />
          </div>
          <div className="flex items-end">
            <button type="button" className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60" onClick={applyContentMode} disabled={loading}>
              按内容聚合查询
            </button>
          </div>
          <div className="flex items-end justify-end gap-2">
            <select
              className="rounded border px-2 py-2 text-sm"
              value={String(pageSize)}
              onChange={(e) => {
                setPageSize(Math.max(1, Math.min(200, Number(e.target.value) || 20)));
                setPage(1);
              }}
            >
              {[10, 20, 50, 100].map((n) => (
                <option key={n} value={String(n)}>
                  {n}/页
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="flex items-center justify-between">
          <div className="text-xs text-gray-600">total: {data?.totalElements ?? '—'}，pages: {data?.totalPages ?? '—'}</div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded border px-3 py-1 text-sm disabled:opacity-60"
              disabled={!canPrev || loading}
              onClick={() => {
                setPage((p) => Math.max(1, p - 1));
              }}
            >
              上一页
            </button>
            <span className="text-xs text-gray-600">{page}</span>
            <button
              type="button"
              className="rounded border px-3 py-1 text-sm disabled:opacity-60"
              disabled={!canNext || loading}
              onClick={() => {
                setPage((p) => p + 1);
              }}
            >
              下一页
            </button>
            <button type="button" className="rounded border px-3 py-1 text-sm" disabled={loading} onClick={() => void load()}>
              跳转/刷新
            </button>
          </div>
        </div>
      </div>

      {loading ? <div className="text-sm text-gray-600">加载中…</div> : null}

      {!loading && runs.length === 0 ? <div className="text-sm text-gray-500">暂无记录。先选择查询模式，然后会自动加载。</div> : null}

      {runs.length ? (
        <div className="space-y-2">
          {runs.map((r) => {
            const detail = detailMap[String(r.id)];
            return (
              <details
                key={r.id}
                className="border rounded p-2"
                onToggle={(e) => {
                  const open = (e.target as HTMLDetailsElement).open;
                  if (open && !detail) void openDetail(r.id);
                }}
              >
                <summary className="cursor-pointer select-none text-sm flex items-center justify-between gap-3">
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="font-mono">runId={r.id}</span>
                    <span className="text-gray-600">queueId={r.queueId}</span>
                    <span className="text-gray-600">{r.contentType}#{r.contentId}</span>
                    <span className="text-gray-700">{r.finalDecision ?? '—'}</span>
                    <span className="text-gray-500">{formatMs(r.totalMs)}</span>
                    {r.errorMessage ? <span className="text-red-700">ERR</span> : null}
                  </span>

                  <span className="text-gray-500">{formatDateTime(r.createdAt ?? r.startedAt ?? r.endedAt)}</span>
                </summary>

                <div className="mt-2 space-y-2">
                  <div className="text-xs text-gray-600 grid grid-cols-1 md:grid-cols-2 gap-2">
                    <div>traceId: <span className="font-mono">{r.traceId ?? '—'}</span></div>
                    <div>status: {r.status ?? '—'}</div>
                    <div>startedAt: {formatDateTime(r.startedAt)}</div>
                    <div>endedAt: {formatDateTime(r.endedAt)}</div>
                    <div>llmModel: {r.llmModel ?? '—'}</div>
                    <div>llmThreshold: {r.llmThreshold ?? '—'}</div>
                  </div>

                  {r.traceId ? (
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        className="rounded border px-3 py-1 text-sm"
                        onClick={() => navigate(`/admin/review?active=logs&traceId=${encodeURIComponent(r.traceId ?? '')}`)}
                      >
                        打开审核日志（traceId）
                      </button>
                    </div>
                  ) : null}

                  {detailLoadingId === r.id ? <div className="text-sm text-gray-600">加载 steps 中…</div> : null}

                  {detail?.steps?.length ? (
                    <div className="space-y-2">
                      {detail.steps
                        .slice()
                        .filter((s) => {
                          if (!stageFilterSet) return true;
                          return stageFilterSet.has(String(s.stage));
                        })
                        .sort((a, b) => {
                          // Prefer stepOrder, but keep stable time ordering within same stepOrder
                          const o = (a.stepOrder ?? 0) - (b.stepOrder ?? 0);
                          if (o !== 0) return o;
                          const ta = new Date(a.startedAt ?? a.endedAt ?? 0).getTime();
                          const tb = new Date(b.startedAt ?? b.endedAt ?? 0).getTime();
                          return ta - tb;
                        })
                        .map((s) => (
                          <details key={s.id} className="border rounded p-2">
                            <summary className="cursor-pointer select-none text-sm flex items-center justify-between gap-3">
                              <span>
                                <b>{s.stage}</b> · {s.decision || '—'} · {formatMs(s.costMs)}
                              </span>
                              <span className="text-gray-500">score={s.score ?? '—'} threshold={s.threshold ?? '—'}</span>
                            </summary>
                            <div className="text-xs text-gray-500 mt-1">
                              {formatDateTime(s.startedAt)} → {formatDateTime(s.endedAt)}
                            </div>
                            <pre className="mt-2 whitespace-pre-wrap text-xs bg-gray-50 rounded p-2 overflow-auto max-h-[260px]">{safeJson(s.details)}</pre>
                          </details>
                        ))}
                    </div>
                  ) : detail && !detail.steps?.length ? (
                    <div className="text-sm text-gray-500">该 run 暂无 steps 记录。</div>
                  ) : null}
                </div>
              </details>
            );
          })}
        </div>
      ) : null}
    </div>
  );
};
