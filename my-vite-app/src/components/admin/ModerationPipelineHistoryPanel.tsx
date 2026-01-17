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

// Narrow helper types to avoid `any` while still being tolerant to DTO shape changes.
type RunLike = Partial<AdminModerationPipelineRunDTO> & {
  id?: number;
  runId?: number;
  queueId?: number;
  contentType?: 'POST' | 'COMMENT' | string;
  contentId?: number;
  finalDecision?: string | null;
  totalMs?: number | null;
  errorMessage?: string | null;
  createdAt?: string | null;
  startedAt?: string | null;
  endedAt?: string | null;
  status?: string | null;
  traceId?: string | null;
  llmModel?: string | null;
  llmThreshold?: number | null;
};

type StepLike = {
  id: number;
  stage?: string | null;
  decision?: string | null;
  costMs?: number | null;
  score?: number | null;
  threshold?: number | null;
  startedAt?: string | null;
  endedAt?: string | null;
  stepOrder?: number | null;
  details?: unknown;
};

const isNonEmptyString = (v: unknown): v is string => typeof v === 'string' && v.trim().length > 0;

const readStringPath = (obj: unknown, path: Array<string | number>): string | null => {
  let cur: unknown = obj;
  for (const p of path) {
    if (cur == null) return null;
    if (typeof p === 'number') {
      if (!Array.isArray(cur)) return null;
      cur = cur[p];
      continue;
    }
    if (typeof cur !== 'object') return null;
    cur = (cur as Record<string, unknown>)[p];
  }
  return isNonEmptyString(cur) ? cur.trim() : null;
};

const readNumberPath = (obj: unknown, path: Array<string | number>): number | null => {
  let cur: unknown = obj;
  for (const p of path) {
    if (cur == null) return null;
    if (typeof p === 'number') {
      if (!Array.isArray(cur)) return null;
      cur = cur[p];
      continue;
    }
    if (typeof cur !== 'object') return null;
    cur = (cur as Record<string, unknown>)[p];
  }

  if (typeof cur === 'number' && Number.isFinite(cur)) return cur;
  if (typeof cur === 'string' && cur.trim() !== '') {
    const n = Number(cur);
    if (Number.isFinite(n)) return n;
  }
  return null;
};

const resolveLlmStep = (detail?: AdminModerationPipelineRunDetailDTO): StepLike | undefined => {
  const steps = asSteps(detail);
  const llmSteps = steps.filter((s) => String(s.stage).toUpperCase() === 'LLM');
  if (!llmSteps.length) return undefined;
  // Prefer the last executed step if multiple exist.
  return llmSteps
    .slice()
    .sort((a, b) => {
      const o = (a.stepOrder ?? 0) - (b.stepOrder ?? 0);
      if (o !== 0) return o;
      const ta = new Date(a.endedAt ?? a.startedAt ?? 0).getTime();
      const tb = new Date(b.endedAt ?? b.startedAt ?? 0).getTime();
      return ta - tb;
    })
    .at(-1);
};

const resolveLlmModel = (run: RunLike, detail?: AdminModerationPipelineRunDetailDTO): string | null => {
  if (isNonEmptyString(run.llmModel)) return run.llmModel.trim();
  const fromDetailRun = readStringPath(detail, ['run', 'llmModel']);
  if (fromDetailRun) return fromDetailRun;

  const llmStep = resolveLlmStep(detail);
  // Some backends store model on step.details (or use aliases), not on run.
  const candidates: Array<Array<string | number>> = [
    ['details', 'model'],
    ['details', 'llmModel'],
    ['details', 'llm_model'],
    ['details', 'modelName'],
    ['details', 'llm', 'model'],
  ];
  for (const p of candidates) {
    const v = readStringPath(llmStep, p);
    if (v) return v;
  }
  return null;
};

const resolveLlmThreshold = (run: RunLike, detail?: AdminModerationPipelineRunDetailDTO): number | null => {
  // Important: threshold can legitimately be 0, so only treat null/undefined as missing.
  if (typeof run.llmThreshold === 'number' && Number.isFinite(run.llmThreshold)) return run.llmThreshold;
  const fromDetailRun = readNumberPath(detail, ['run', 'llmThreshold']);
  if (fromDetailRun != null) return fromDetailRun;

  const llmStep = resolveLlmStep(detail);
  if (typeof llmStep?.threshold === 'number' && Number.isFinite(llmStep.threshold)) return llmStep.threshold;

  const candidates: Array<Array<string | number>> = [
    ['details', 'threshold'],
    ['details', 'llmThreshold'],
    ['details', 'llm_threshold'],
    ['details', 'rejectThreshold'],
    ['details', 'policyThreshold'],
    ['details', 'llm', 'threshold'],
  ];
  for (const p of candidates) {
    const v = readNumberPath(llmStep, p);
    if (v != null) return v;
  }
  return null;
};

const asRunId = (r: RunLike): number | null => {
  const v = r.id ?? r.runId;
  return typeof v === 'number' && Number.isFinite(v) ? v : null;
};

const asSteps = (detail?: AdminModerationPipelineRunDetailDTO): StepLike[] => {
  const raw = (detail as { steps?: unknown })?.steps;
  if (!Array.isArray(raw)) return [];
  const out: StepLike[] = [];
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue;
    const id = (item as { id?: unknown }).id;
    if (typeof id !== 'number' || !Number.isFinite(id)) continue;
    out.push(item as StepLike);
  }
  return out;
};

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
  /**
   * 是否在列表加载后自动拉取每条 run 的 steps 详情并直接展示。
   * - true（默认）：无需点击展开，页面直接展示完整日志信息
   * - false：不自动拉取（仅展示 run 列表）
   *
   * 注意：为了让列表更紧凑且提升性能，现在即使拉取了详情也会默认折叠 JSON，需要点击“JSON”才展示。
   */
  autoLoadDetails?: boolean;
  /**
   * steps 的 details(JSON) 展示风格：
   * - 'wrap'（默认）：自动换行，适合阅读
   * - 'nowrap'：不换行，改用横向滚动，适合对齐查看
   */
  stepDetailsFormat?: 'wrap' | 'nowrap';
}> = ({
  title = '历史记录（流水线 Run + Steps）',
  initialMode,
  autoLatest = true,
  stageFilter,
  autoLoadDetails = true,
  stepDetailsFormat = 'wrap',
}) => {
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

  // details cache: runId -> detail (only fetched on-demand)
  const [detailMap, setDetailMap] = useState<Record<string, AdminModerationPipelineRunDetailDTO | undefined>>({});
  const [detailLoadingId, setDetailLoadingId] = useState<number | null>(null);

  // UI state: which runs' steps are expanded
  const [expandedRunIds, setExpandedRunIds] = useState<Record<string, boolean>>({});
  // UI state: which steps' JSON are expanded (key: stepId)
  const [expandedStepJsonIds, setExpandedStepJsonIds] = useState<Record<string, boolean>>({});

  const openDetail = async (runId: number) => {
    // 已经有缓存就不重复请求（降低并发 + 避免重复 render）
    if (detailMap[String(runId)]) return;
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

  const toggleRunExpanded = (runId: number) => {
    const runKey = String(runId);
    const nextExpanded = !expandedRunIds[runKey];

    setExpandedRunIds((m) => ({ ...m, [runKey]: nextExpanded }));

    // 展开时才按需拉取 steps
    if (nextExpanded && !detailMap[runKey]) {
      void openDetail(runId);
      return;
    }

    // 需求：点击 Steps 展开时，自动展开该 run 下所有 steps 的 JSON
    if (nextExpanded) {
      const detail = detailMap[runKey];
      const steps = asSteps(detail);
      if (steps.length) {
        setExpandedStepJsonIds((m) => {
          const next = { ...m };
          for (const s of steps) next[String(s.id)] = true;
          return next;
        });
      }
    }
  };

  // 当展开 run 触发了详情加载时，详情回来后也自动展开 JSON（仅该 run 当前处于展开状态时）。
  useEffect(() => {
    for (const [runKey, detail] of Object.entries(detailMap)) {
      if (!detail) continue;
      if (!expandedRunIds[runKey]) continue;

      const steps = asSteps(detail);
      if (!steps.length) continue;

      setExpandedStepJsonIds((m) => {
        let changed = false;
        const next = { ...m };
        for (const s of steps) {
          const k = String(s.id);
          if (!next[k]) {
            next[k] = true;
            changed = true;
          }
        }
        return changed ? next : m;
      });
    }
  }, [detailMap, expandedRunIds]);
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

  // 当翻页/切换查询条件时，清理展开状态与详情缓存，保持 UI 紧凑且避免“串页”展示。
  useEffect(() => {
    setExpandedRunIds({});
    setExpandedStepJsonIds({});
    setDetailLoadingId(null);
    setDetailMap({});
  }, [mode, page, pageSize]);

  // 兼容保留 autoLoadDetails：仅预取当前页的 run 详情（为了让展开更快）。
  // 但仍然不自动展开任何 run，也不自动渲染 JSON（需点“Steps/JSON”）。
  useEffect(() => {
    if (!autoLoadDetails) return;
    if (!runs.length) return;

    let cancelled = false;
    const prefetch = async () => {
      for (const r of runs) {
        if (cancelled) return;
        if (detailMap[String(r.id)]) continue;
        // 不等待，避免阻塞；openDetail 内部会做去重
        void openDetail(r.id);
      }
    };

    void prefetch();
    return () => {
      cancelled = true;
    };
    // 只在当前页 runs 变化时触发（避免把 detailMap/openDetail 作为依赖导致循环）
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoLoadDetails, runs]);

  const stepPreClass =
    stepDetailsFormat === 'nowrap'
      ? 'mt-1 whitespace-pre text-[11px] bg-gray-50 rounded p-2 overflow-auto'
      : 'mt-1 whitespace-pre-wrap break-words text-[11px] bg-gray-50 rounded p-2 overflow-auto';

  const badgeBase = 'inline-flex items-center rounded px-1.5 py-0.5 text-[11px] leading-4 border';
  const decisionBadgeClass = (decision?: string | null) => {
    const d = (decision ?? '').toUpperCase();
    if (d.includes('REJECT') || d.includes('BLOCK') || d.includes('DENY')) return `${badgeBase} bg-red-50 text-red-700 border-red-200`;
    if (d.includes('APPROVE') || d.includes('ALLOW') || d.includes('PASS')) return `${badgeBase} bg-green-50 text-green-700 border-green-200`;
    if (d.includes('HUMAN') || d.includes('REVIEW')) return `${badgeBase} bg-yellow-50 text-yellow-700 border-yellow-200`;
    if (d.includes('ERROR') || d.includes('FAIL')) return `${badgeBase} bg-red-50 text-red-700 border-red-200`;
    return `${badgeBase} bg-gray-50 text-gray-700 border-gray-200`;
  };

  const stageBadgeClass = `${badgeBase} bg-gray-50 text-gray-700 border-gray-200`;

  return (
    <div className="bg-white rounded-lg shadow p-3 space-y-3">
      <div className="flex items-center justify-between gap-2">
        <div className="font-semibold text-lg">{title}</div>
        <div className="text-xs text-gray-500">
          页码 {page}/{data?.totalPages ?? 1} · 总数 {data?.totalElements ?? '—'}
        </div>
      </div>

      {/* 查询区（保持原有能力，但更紧凑） */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
        <div className="border rounded p-2 space-y-1">
          <div className="text-xs font-medium">按队列ID</div>
          <div className="flex items-center gap-2">
            <input
              className="border rounded px-2 py-1 text-xs w-full"
              placeholder="队列ID"
              value={queueId}
              onChange={(e) => setQueueId(e.target.value)}
            />
            <button type="button" className="border rounded px-2 py-1 text-xs" onClick={applyQueueMode}>
              查询
            </button>
          </div>
        </div>

        <div className="border rounded p-2 space-y-1">
          <div className="text-xs font-medium">按内容</div>
          <div className="flex items-center gap-2">
            <select
              className="border rounded px-2 py-1 text-xs"
              value={contentType}
              onChange={(e) => setContentType(e.target.value === 'COMMENT' ? 'COMMENT' : 'POST')}
            >
              {/* 注意：option 的 value 必须保留 POST/COMMENT（技术字段值），这里只改显示文案 */}
              <option value="POST">帖子</option>
              <option value="COMMENT">评论</option>
            </select>
            <input
              className="border rounded px-2 py-1 text-xs w-full"
              placeholder="内容ID"
              value={contentId}
              onChange={(e) => setContentId(e.target.value)}
            />
            <button type="button" className="border rounded px-2 py-1 text-xs" onClick={applyContentMode}>
              查询
            </button>
          </div>
        </div>

        <div className="border rounded p-2 space-y-1">
          <div className="text-xs font-medium">分页</div>
          <div className="flex items-center gap-2 flex-wrap">
            <button type="button" className="border rounded px-2 py-1 text-xs disabled:opacity-60" disabled={!canPrev || loading} onClick={() => setPage((p) => Math.max(1, p - 1))}>
              上一页
            </button>
            <button type="button" className="border rounded px-2 py-1 text-xs disabled:opacity-60" disabled={!canNext || loading} onClick={() => setPage((p) => p + 1)}>
              下一页
            </button>
            <button type="button" className="border rounded px-2 py-1 text-xs disabled:opacity-60" disabled={loading} onClick={() => void load()}>
              刷新
            </button>
            <label className="text-xs text-gray-600 flex items-center gap-1">
              每页条数
              <select className="border rounded px-1.5 py-1 text-xs" value={pageSize} onChange={(e) => setPageSize(Number(e.target.value))}>
                {[10, 20, 50, 100].map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
            </label>
          </div>
        </div>
      </div>

      {error ? <div className="text-xs text-red-600">{error}</div> : null}
      {loading ? <div className="text-xs text-gray-600">加载中…</div> : null}

      {runs.length ? (
        <div className="space-y-2">
          {runs.map((r) => {
            const rr = r as RunLike;
            const rid = asRunId(rr);
            if (rid == null) return null;

            const runKey = String(rid);
            const runId = rid;
            const isExpanded = Boolean(expandedRunIds[runKey]);
            const detail = detailMap[runKey];

            const filteredSteps = asSteps(detail)
              .slice()
              .filter((s) => {
                if (!stageFilterSet) return true;
                return stageFilterSet.has(String(s.stage));
              })
              .sort((a, b) => {
                const o = (a.stepOrder ?? 0) - (b.stepOrder ?? 0);
                if (o !== 0) return o;
                const ta = new Date(a.startedAt ?? a.endedAt ?? 0).getTime();
                const tb = new Date(b.startedAt ?? b.endedAt ?? 0).getTime();
                return ta - tb;
              });

            return (
              <div
                key={runKey}
                className="border rounded-md px-2 py-1.5 space-y-1 cursor-pointer hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-200"
                role="button"
                tabIndex={0}
                aria-expanded={isExpanded}
                onClick={(e) => {
                  // If the click originates from an interactive element (buttons/links/inputs/etc.),
                  // don't toggle the card to avoid double-actions.
                  const target = e.target as HTMLElement | null;
                  if (!target) return;
                  const interactive = target.closest(
                    // NOTE: do NOT include `[role="button"]` here because the card itself has `role="button"`.
                    // Including it would cause clicks on the card to be ignored.
                    'button,a,input,select,textarea,label,[data-no-toggle]'
                  );
                  if (interactive) return;
                  toggleRunExpanded(runId);
                }}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    toggleRunExpanded(runId);
                  }
                }}
              >
                <div className="text-xs flex items-start justify-between gap-2">
                  <div className="min-w-0 flex flex-wrap items-center gap-x-2 gap-y-1">
                    <span className="font-mono" title={`运行ID=${runKey}`}>运行ID={runKey}</span>
                    {typeof rr.queueId === 'number' ? (
                      <span className="text-gray-600" title={`队列ID=${rr.queueId}`}>队列={rr.queueId}</span>
                    ) : null}
                    {rr.contentType && typeof rr.contentId === 'number' ? (
                      <span className="text-gray-600" title={`${rr.contentType}#${rr.contentId}`}>
                        {rr.contentType}#{rr.contentId}
                      </span>
                    ) : null}
                    <span className={decisionBadgeClass(rr.finalDecision)} title={rr.finalDecision ?? ''}>
                      {rr.finalDecision ?? '—'}
                    </span>
                    <span className="text-gray-500" title={String(rr.totalMs ?? '')}>{formatMs(rr.totalMs)}</span>
                    {rr.errorMessage ? (
                      <span className={`${badgeBase} bg-red-50 text-red-700 border-red-200`} title={rr.errorMessage}>
                        错误
                      </span>
                    ) : null}
                  </div>

                  <div className="shrink-0 flex items-center gap-2">
                    <span className="text-gray-500" title={rr.createdAt ?? rr.startedAt ?? rr.endedAt ?? ''}>
                      {formatDateTime(rr.createdAt ?? rr.startedAt ?? rr.endedAt)}
                    </span>
                    <button
                      type="button"
                      className="rounded border px-2 py-0.5 text-xs disabled:opacity-60"
                      disabled={detailLoadingId === runId}
                      onClick={() => toggleRunExpanded(runId)}
                      title={isExpanded ? '收起' : '展开'}
                    >
                      {isExpanded ? '收起' : '展开'}
                    </button>
                  </div>
                </div>

                <div className="text-[11px] text-gray-500 flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span>状态：{rr.status ?? '—'}</span>
                  <span>
                    链路ID：<span className="font-mono">{rr.traceId ?? '—'}</span>
                  </span>
                  <span>模型：{resolveLlmModel(rr, detail) ?? '—'}</span>
                  <span>阈值：{resolveLlmThreshold(rr, detail) ?? '—'}</span>

                  {rr.traceId ? (
                    <span className="ml-auto">
                      <button
                        type="button"
                        className="rounded border px-2 py-0.5 text-[11px] text-black"
                        onClick={() => navigate(`/admin/review?active=logs&traceId=${encodeURIComponent(String(rr.traceId ?? ''))}`)}
                      >
                        打开 trace 日志
                      </button>
                    </span>
                  ) : null}
                </div>

                {isExpanded ? (
                  <div className="pt-1">
                    {detailLoadingId === runId && !detail ? <div className="text-xs text-gray-600">加载详情中…</div> : null}

                    {detail ? (
                      filteredSteps.length ? (
                        <div className="space-y-1">
                          {filteredSteps.map((s) => {
                            const stepKey = String(s.id);
                            const jsonExpanded = Boolean(expandedStepJsonIds[stepKey]);
                            return (
                              <div key={stepKey} className="border rounded-md px-2 py-1">
                                <div className="text-xs flex items-center justify-between gap-2">
                                  <div className="min-w-0 flex flex-wrap items-center gap-x-2 gap-y-1">
                                    <span className={stageBadgeClass}>{s.stage ?? '—'}</span>
                                    <span className={decisionBadgeClass(s.decision)}>{s.decision || '—'}</span>
                                    <span className="text-gray-500">{formatMs(s.costMs)}</span>
                                    <span className="text-gray-500">得分={s.score ?? '—'} 阈值={s.threshold ?? '—'}</span>
                                  </div>
                                </div>

                                <div className="text-[11px] text-gray-500 mt-0.5">
                                  {formatDateTime(s.startedAt)} → {formatDateTime(s.endedAt)}
                                </div>

                                {jsonExpanded ? <pre className={`${stepPreClass} max-h-[220px]`}>{safeJson(s.details)}</pre> : null}
                              </div>
                            );
                          })}
                        </div>
                      ) : (
                        <div className="text-xs text-gray-500">该 run 在当前筛选 stage 下暂无 steps 记录。</div>
                      )
                    ) : null}
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
      ) : (
        <div className="text-xs text-gray-500">暂无记录。</div>
      )}
    </div>
  );
};
