import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  adminGetModerationReviewTraceTaskDetail,
  adminListModerationReviewTraceTasks,
  type ModerationReviewTraceTaskDetail,
  type ModerationReviewTraceTaskItem,
} from '../../../../services/moderationReviewTraceService';
import { ModerationPipelineHistoryPanel } from '../../../../components/admin/ModerationPipelineHistoryPanel';
import { ModerationPipelineTracePanel } from '../../../../components/admin/ModerationPipelineTracePanel';
import ChunkEvidenceView from '../../../../components/admin/ChunkEvidenceView';
import EvidenceListView from '../../../../components/admin/EvidenceListView';
import { buildEvidenceImageUrlMap, extractLatestRunImageUrls } from '../../../../utils/evidenceImageMap';
import { countUniqueEvidence, extractEvidenceFromDetails, shouldSkipStepEvidenceForChunkedReview } from '../../../../utils/evidence-utils';
import DetailDialog from '../../../../components/common/DetailDialog';

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

function toChunkRows(v: unknown): Array<Record<string, unknown>> {
  const pickArray = (a: unknown): Array<Record<string, unknown>> => {
    if (!Array.isArray(a)) return [];
    return a.filter((x) => x && typeof x === 'object') as Array<Record<string, unknown>>;
  };
  if (Array.isArray(v)) return pickArray(v);
  if (v && typeof v === 'object') {
    const o = v as Record<string, unknown>;
    for (const k of ['chunks', 'items', 'content', 'list', 'rows']) {
      const rows = pickArray(o[k]);
      if (rows.length) return rows;
    }
  }
  return [];
}

function chunkRowKey(row: Record<string, unknown>, idx: number): string {
  const v = row.id ?? row.chunkId ?? row.chunkIndex ?? row.index ?? row.seq ?? row.no;
  return v == null ? String(idx) : String(v);
}

function shortCell(v: unknown): string {
  if (v === undefined || v === null) return '—';
  if (typeof v === 'string') return v.trim() ? v : '—';
  if (typeof v === 'number' || typeof v === 'boolean') return String(v);
  return String(v);
}

function riskScoreCell(v: unknown): string {
  if (v === undefined || v === null) return '—';
  const n =
    typeof v === 'number'
      ? v
      : typeof v === 'string'
        ? (() => {
            const t = v.trim();
            if (!t) return undefined;
            const x = Number(t);
            return Number.isFinite(x) ? x : undefined;
          })()
        : undefined;
  if (n === undefined) return shortCell(v);
  return n.toFixed(4);
}

function toRecord(v: unknown): Record<string, unknown> | null {
  if (typeof v === 'string') {
    const t = v.trim();
    if (!t) return null;
    try {
      const parsed = JSON.parse(t) as unknown;
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed as Record<string, unknown>;
    } catch {
    }
    return null;
  }
  if (!v || typeof v !== 'object' || Array.isArray(v)) return null;
  return v as Record<string, unknown>;
}

function toStringDict(v: unknown): Record<string, string> {
  const out: Record<string, string> = {};
  const o = toRecord(v);
  if (!o) return out;
  for (const [k0, v0] of Object.entries(o)) {
    const k = String(k0).trim();
    const t = v0 == null ? '' : String(v0).trim();
    if (!k || !t) continue;
    out[k] = t;
  }
  return out;
}

function toStringListDict(v: unknown): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  const o = toRecord(v);
  if (!o) return out;
  for (const [k0, v0] of Object.entries(o)) {
    const k = String(k0).trim();
    if (!k) continue;
    if (!Array.isArray(v0)) continue;
    const list = v0
      .map((x) => (x == null ? '' : String(x).trim()))
      .filter(Boolean);
    if (!list.length) continue;
    out[k] = list;
  }
  return out;
}

function toInt(v: unknown): number | null {
  if (typeof v === 'number' && Number.isFinite(v)) return Math.floor(v);
  if (typeof v === 'string') {
    const t = v.trim();
    if (!t) return null;
    const n = Number(t);
    if (Number.isFinite(n)) return Math.floor(n);
  }
  return null;
}

function rowChunkIndex(row: Record<string, unknown>): number | null {
  for (const k of ['chunkIndex', 'index', 'seq', 'no']) {
    const n = toInt(row[k]);
    if (n != null) return n;
  }
  return null;
}

function parseEvidenceSpan(v: unknown): { start: number; end: number } | null {
  const o = toRecord(v);
  if (!o) return null;
  const start = toInt(o.start);
  const end = toInt(o.end);
  if (start == null || end == null) return null;
  if (start < 0 || end < 0) return null;
  if (end < start) return null;
  return { start, end };
}

function parseEvidenceItem(v: unknown): { span?: { start: number; end: number }; text: string; imageId?: string } {
  const s0 = v == null ? '' : typeof v === 'string' ? v.trim() : '';
  const extractImageId = (obj: unknown): string | undefined => {
    const o = obj && typeof obj === 'object' ? (obj as Record<string, unknown>) : null;
    if (!o) return undefined;
    const id = o.image_id;
    return typeof id === 'string' && id.trim() ? id.trim() : undefined;
  };
  if (s0) {
    try {
      const parsed = JSON.parse(s0) as unknown;
      const span = parseEvidenceSpan(parsed);
      const imageId = extractImageId(parsed);
      if (span) return { span, text: s0, imageId };
      if (imageId) return { text: s0, imageId };
    } catch {
    }
    return { text: s0 };
  }
  const span = parseEvidenceSpan(v);
  const imageId = extractImageId(v);
  if (span) return { span, text: JSON.stringify(span), imageId };
  if (imageId) return { text: shortCell(v), imageId };
  return { text: shortCell(v) };
}

function parsePositiveInt(s: string | null): number | undefined {
  if (!s) return undefined;
  const n = Number(s);
  if (!Number.isFinite(n) || n <= 0) return undefined;
  return Math.floor(n);
}

function toIntOrUndefined(s: string): number | undefined {
  const t = s.trim();
  if (!t) return undefined;
  const n = Number(t);
  if (!Number.isFinite(n) || n <= 0) return undefined;
  return Math.floor(n);
}

type QueryState = {
  queueId: string;
  contentType: string;
  contentId: string;
  traceId: string;
  status: string;
  updatedFrom: string;
  updatedTo: string;
};

const defaultQueryState: QueryState = {
  queueId: '',
  contentType: '',
  contentId: '',
  traceId: '',
  status: '',
  updatedFrom: '',
  updatedTo: '',
};

function statusLabel(status?: string | null): string {
  switch (status) {
    case 'PENDING':
      return '待自动审核';
    case 'REVIEWING':
      return '审核中';
    case 'HUMAN':
      return '待人工审核';
    case 'APPROVED':
      return '已通过';
    case 'REJECTED':
      return '已驳回';
    default:
      return status || '—';
  }
}

function decisionLabel(decision?: string | null): string {
  if (!decision) return '—';
  if (decision === 'HUMAN') return '转人工';
  if (decision === 'ERROR') return '错误';
  if (decision === 'WAIT_FILES') return '等待文件';
  return decision;
}

function stageCell(stage?: { decision?: string | null; costMs?: number | null; score?: number | null } | null): string {
  if (!stage) return '—';
  const d = decisionLabel(stage.decision ?? null);
  const ms = stage.costMs == null ? '' : ` · ${formatMs(stage.costMs)}`;
  const s = stage.score == null ? '' : ` · score=${stage.score}`;
  return `${d}${ms}${s}`;
}

function isTrueLike(v: unknown): boolean {
  if (typeof v === 'boolean') return v;
  return typeof v === 'string' && v.toLowerCase() === 'true';
}

function readNonEmptyString(v: unknown): string | null {
  if (typeof v !== 'string') return null;
  const t = v.trim();
  return t ? t : null;
}

function readFiniteNumber(v: unknown): number | null {
  if (typeof v === 'number' && Number.isFinite(v)) return v;
  if (typeof v === 'string') {
    const t = v.trim();
    if (!t) return null;
    const n = Number(t);
    if (Number.isFinite(n)) return n;
  }
  return null;
}

const ReviewTraceForm: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

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

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [items, setItems] = useState<ModerationReviewTraceTaskItem[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState<ModerationReviewTraceTaskDetail | null>(null);
  const [detailTab, setDetailTab] = useState<'overview' | 'pipeline' | 'llm'>('overview');
  const [expandedChunkKey, setExpandedChunkKey] = useState<string | null>(null);
  const [chunkIndexFilter, setChunkIndexFilter] = useState<string>('');

  const canPrev = page > 1;
  const canNext = totalPages > page;

  const syncToUrl = useCallback(
    (nextQuery: QueryState, nextPage: number, nextPageSize: number) => {
      setSearchParams((prev) => {
        const sp = new URLSearchParams(prev);
        sp.set('active', 'logs');
        sp.set('page', String(nextPage));
        sp.set('pageSize', String(nextPageSize));
        for (const k of Object.keys(defaultQueryState) as (keyof QueryState)[]) {
          sp.delete(k);
        }
        for (const [k, v] of Object.entries(nextQuery)) {
          if (!v) sp.delete(k);
          else sp.set(k, v);
        }
        if (sp.toString() === prev.toString()) return prev;
        return sp;
      });
    },
    [setSearchParams]
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await adminListModerationReviewTraceTasks({
        page,
        pageSize,
        queueId: toIntOrUndefined(query.queueId),
        contentType: query.contentType || undefined,
        contentId: toIntOrUndefined(query.contentId),
        traceId: query.traceId || undefined,
        status: query.status || undefined,
        updatedFrom: query.updatedFrom || undefined,
        updatedTo: query.updatedTo || undefined,
      });
      if (!mountedRef.current) return;
      setItems(res.content ?? []);
      setTotalPages(res.totalPages ?? 1);
      setTotalElements(res.totalElements ?? 0);
    } catch (e) {
      if (!mountedRef.current) return;
      setError(e instanceof Error ? e.message : String(e));
      setItems([]);
      setTotalPages(1);
      setTotalElements(0);
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }, [page, pageSize, query]);

  useEffect(() => {
    syncToUrl(query, page, pageSize);
    void load();
  }, [load, page, pageSize, query, syncToUrl]);

  const openDetail = useCallback(async (queueId: number) => {
    setDetailOpen(true);
    setDetailTab('overview');
    setDetailLoading(true);
    setDetail(null);
    setExpandedChunkKey(null);
    try {
      const d = await adminGetModerationReviewTraceTaskDetail(queueId);
      if (!mountedRef.current) return;
      setDetail(d);
    } catch (e) {
      if (!mountedRef.current) return;
      setDetail(null);
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      if (mountedRef.current) setDetailLoading(false);
    }
  }, []);

  const closeDetail = useCallback(() => {
    setDetailOpen(false);
    setDetailTab('overview');
  }, []);

  const resetFilters = () => {
    setQuery({ ...defaultQueryState });
    setPage(1);
  };

  const activeTraceId = useMemo(() => {
    const run = (detail?.latestRun as { run?: { traceId?: unknown } } | null | undefined)?.run;
    const t = run && typeof run === 'object' ? (run as { traceId?: unknown }).traceId : undefined;
    return typeof t === 'string' && t.trim() ? t.trim() : null;
  }, [detail]);

  const chunkRows = useMemo(() => toChunkRows(detail?.chunkProgress ?? null), [detail?.chunkProgress]);
  const chunkIdByChunkIndex = useMemo(() => {
    const map: Record<string, number> = {};
    for (const row of chunkRows) {
      const ci = rowChunkIndex(row);
      const id = toInt(row.chunkId ?? row.id);
      if (ci == null || id == null) continue;
      map[String(ci)] = id;
    }
    return map;
  }, [chunkRows]);
  const chunkOutput = useMemo(() => {
    const mem = toRecord(detail?.chunkSet?.memoryJson ?? null);
    const summaries = toStringDict(mem?.summaries);
    const llmEvidenceByChunk = toStringListDict(mem?.llmEvidenceByChunk);

    const filter = chunkIndexFilter.trim();
    const filterIdx = filter ? toInt(filter) : null;
    return { mem, summaries, llmEvidenceByChunk, filterIdx };
  }, [detail?.chunkSet?.memoryJson, chunkIndexFilter]);

  const llmSteps = useMemo(() => {
    const latestRun = toRecord(detail?.latestRun ?? null);
    const steps0 = latestRun?.steps;
    if (!Array.isArray(steps0)) return [];
    const out: Array<Record<string, unknown>> = [];
    for (const it of steps0) {
      const s = toRecord(it);
      if (!s) continue;
      const details = toRecord(s.details ?? null);
      const stage = s.stage;
      const stageStr = typeof stage === 'string' ? stage : '';
      const looksLikeLlm =
        stageStr === 'LLM' ||
        (details != null && ('rawModelOutput' in details || 'raw_output' in details || 'full_response' in details || 'evidence' in details || 'decision_suggestion' in details || 'risk_score' in details));
      if (!looksLikeLlm) continue;
      out.push(s);
    }
    out.sort((a, b) => (toInt(a.stepOrder) ?? 0) - (toInt(b.stepOrder) ?? 0));
    return out;
  }, [detail?.latestRun]);

  const stepEvidenceGroups = useMemo(() => {
    const latestRun = toRecord(detail?.latestRun ?? null);
    const steps0 = latestRun?.steps;
    if (!Array.isArray(steps0)) return [];
    const chunkIndexByChunkId: Record<string, number> = {};
    if (detail?.chunkSet) {
      for (const [k, v] of Object.entries(chunkIdByChunkIndex)) {
        const ci = toInt(k);
        if (ci == null) continue;
        chunkIndexByChunkId[String(v)] = ci;
      }
    }
    const out: Array<{ key: string; title: string; evidence: unknown[]; order: number; stage: string }> = [];
    for (const it of steps0) {
      const step = toRecord(it);
      if (!step) continue;
      const details = toRecord(step.details ?? null);
      const stage = typeof step.stage === 'string' && step.stage.trim() ? step.stage.trim() : 'STEP';
      const stepIdNum = toInt(step.id);
      const ci = stepIdNum == null ? undefined : chunkIndexByChunkId[String(stepIdNum)];
      if (shouldSkipStepEvidenceForChunkedReview({ stage, details, hasChunkSet: Boolean(detail?.chunkSet), chunkIndex: ci })) continue;
      const evidence = extractEvidenceFromDetails(details);
      if (!evidence.length) continue;
      const order = toInt(step.stepOrder) ?? 0;
      const id = step.id == null ? '' : String(step.id);
      const decision = step.decision == null ? '' : String(step.decision);
      const costMs = toInt(step.costMs);
      const title = `${stage}${step.stepOrder == null ? '' : `#${order}`}${id ? ` · id=${id}` : ''}${decision ? ` · ${decision}` : ''}${costMs == null ? '' : ` · ${formatMs(costMs)}`}`;
      out.push({ key: `${stage}-${order}-${id}-${decision}`, title, evidence, order, stage });
    }
    out.sort((a, b) => a.order - b.order);
    return out;
  }, [chunkIdByChunkIndex, detail?.chunkSet, detail?.latestRun]);

  const stepEvidenceCount = useMemo(() => {
    let n = 0;
    for (const g of stepEvidenceGroups) n += g.evidence.length;
    return n;
  }, [stepEvidenceGroups]);

  const uniqueEvidenceCount = useMemo(() => {
    const all: unknown[] = [];
    for (const g of stepEvidenceGroups) {
      for (const ev of g.evidence) all.push(ev);
    }
    if (detail?.chunkSet) {
      for (const [k, v] of Object.entries(chunkOutput.llmEvidenceByChunk)) {
        const ci = toInt(k);
        if (chunkOutput.filterIdx != null && ci != null && ci !== chunkOutput.filterIdx) continue;
        for (const ev of v) all.push(ev);
      }
    }
    return countUniqueEvidence(all);
  }, [chunkOutput.filterIdx, chunkOutput.llmEvidenceByChunk, detail?.chunkSet, stepEvidenceGroups]);

  const evidenceImageUrlById = useMemo(() => {
    const q = detail?.queue as Record<string, unknown> | null | undefined;
    if (!q) return {};
    const pickAttachments = (v: unknown): Array<Record<string, unknown>> => {
      if (!Array.isArray(v)) return [];
      return v.filter((x) => x && typeof x === 'object') as Array<Record<string, unknown>>;
    };
    const post = toRecord(q.post ?? null);
    const profile = toRecord(q.profile ?? null);
    const attachments = [
      ...pickAttachments(post?.attachments),
      ...pickAttachments(q.attachments),
      ...pickAttachments(q.postAttachments),
    ];
    return buildEvidenceImageUrlMap({
      attachments,
      extraImageUrls: [
        ...extractLatestRunImageUrls(detail?.latestRun),
        typeof profile?.pendingAvatarUrl === 'string' ? profile.pendingAvatarUrl : null,
        typeof profile?.publicAvatarUrl === 'string' ? profile.publicAvatarUrl : null,
      ],
    });
  }, [detail?.latestRun, detail?.queue]);

  const copyText = useCallback(async (text: string) => {
    const t = text ?? '';
    try {
      await navigator.clipboard.writeText(t);
      return true;
    } catch {
      try {
        const el = document.createElement('textarea');
        el.value = t;
        el.style.position = 'fixed';
        el.style.left = '-10000px';
        el.style.top = '0';
        document.body.appendChild(el);
        el.focus();
        el.select();
        const ok = document.execCommand('copy');
        document.body.removeChild(el);
        return ok;
      } catch {
        return false;
      }
    }
  }, []);

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-lg font-semibold">审核日志与追溯（按任务聚合）</h3>
        <div className="flex items-center gap-2">
          <button type="button" onClick={load} className="rounded bg-blue-600 text-white px-4 py-2 disabled:opacity-60" disabled={loading}>
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

      <div className="rounded border bg-gray-50 p-3 space-y-3">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <input
            className="rounded border px-3 py-2"
            placeholder="任务ID（queueId）"
            value={query.queueId}
            onChange={(e) => {
              setQuery((q) => ({ ...q, queueId: e.target.value }));
              setPage(1);
            }}
          />
          <select
            className="rounded border px-3 py-2"
            value={query.contentType}
            onChange={(e) => {
              setQuery((q) => ({ ...q, contentType: e.target.value }));
              setPage(1);
            }}
          >
            <option value="">全部内容类型</option>
            <option value="POST">帖子（POST）</option>
            <option value="COMMENT">评论（COMMENT）</option>
            <option value="PROFILE">个人简介（PROFILE）</option>
          </select>
          <input
            className="rounded border px-3 py-2"
            placeholder="内容ID（contentId）"
            value={query.contentId}
            onChange={(e) => {
              setQuery((q) => ({ ...q, contentId: e.target.value }));
              setPage(1);
            }}
          />
          <input
            className="rounded border px-3 py-2"
            placeholder="TraceId（追溯链路）"
            value={query.traceId}
            onChange={(e) => {
              setQuery((q) => ({ ...q, traceId: e.target.value }));
              setPage(1);
            }}
          />
        </div>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
          <select
            className="rounded border px-3 py-2"
            value={query.status}
            onChange={(e) => {
              setQuery((q) => ({ ...q, status: e.target.value }));
              setPage(1);
            }}
          >
            <option value="">全部最终状态</option>
            <option value="PENDING">待自动审核</option>
            <option value="REVIEWING">审核中</option>
            <option value="HUMAN">待人工审核</option>
            <option value="APPROVED">已通过</option>
            <option value="REJECTED">已驳回</option>
          </select>
          <input
            className="rounded border px-3 py-2"
            placeholder="更新时间开始（ISO）"
            value={query.updatedFrom}
            onChange={(e) => {
              setQuery((q) => ({ ...q, updatedFrom: e.target.value }));
              setPage(1);
            }}
          />
          <input
            className="rounded border px-3 py-2"
            placeholder="更新时间结束（ISO）"
            value={query.updatedTo}
            onChange={(e) => {
              setQuery((q) => ({ ...q, updatedTo: e.target.value }));
              setPage(1);
            }}
          />
          <div className="flex items-center gap-2">
            <button type="button" className="rounded border px-4 py-2" onClick={resetFilters}>
              重置
            </button>
            <button
              type="button"
              className="rounded border px-4 py-2"
              onClick={() => {
                syncToUrl(query, 1, pageSize);
                setPage(1);
              }}
            >
              应用到URL
            </button>
          </div>
        </div>

        <div className="text-xs text-gray-600">
          提示：列表按“审核任务”聚合展示流水线阶段结果；点击详情可查看 run 历史、steps、分片信息与关联审计日志。
        </div>
      </div>

      <div className="overflow-x-auto rounded border">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 text-gray-700">
            <tr className="text-left">
              <th className="px-3 py-2">时间</th>
              <th className="px-3 py-2">任务</th>
              <th className="px-3 py-2">RULE</th>
              <th className="px-3 py-2">VEC</th>
              <th className="px-3 py-2">LLM</th>
              <th className="px-3 py-2">分片</th>
              <th className="px-3 py-2">最终状态</th>
              <th className="px-3 py-2">人工</th>
              <th className="px-3 py-2">TraceId</th>
              <th className="px-3 py-2">操作</th>
            </tr>
          </thead>
          <tbody>
            {items.map((it) => (
              <tr
                key={it.queueId}
                className="border-t hover:bg-blue-50 cursor-pointer"
                onClick={() => void openDetail(it.queueId)}
                title="点击查看详情"
              >
                <td className="px-3 py-2 whitespace-nowrap">{formatDateTime(it.latestStartedAt ?? it.queueUpdatedAt ?? null)}</td>
                <td className="px-3 py-2 whitespace-nowrap">
                  <div className="flex flex-col">
                    <span className="font-mono text-xs">#{it.queueId}</span>
                    <span className="text-xs text-gray-500">
                      {(it.contentType ?? '—')}{it.contentId ? `#${it.contentId}` : ''}
                    </span>
                  </div>
                </td>
                <td className="px-3 py-2 whitespace-nowrap">
                  <div className="flex items-center gap-1.5">
                    <span>{stageCell(it.rule ?? null)}</span>
                    {isTrueLike((it.rule?.details as Record<string, unknown> | null | undefined)?.antiSpamHit) ? (
                      <span
                        className="inline-flex items-center rounded-full border border-orange-300 bg-orange-50 px-1.5 py-0.5 text-[11px] text-orange-700"
                        title={(() => {
                          const details = (it.rule?.details as Record<string, unknown> | null | undefined) ?? null;
                          const hitType = readNonEmptyString(details?.antiSpamType) ?? '未知类型';
                          const actualCount = readFiniteNumber(details?.actualCount);
                          const threshold = readFiniteNumber(details?.threshold);
                          const countText = actualCount == null ? '—' : String(actualCount);
                          const thresholdText = threshold == null ? '—' : String(threshold);
                          return `命中类型：${hitType} · 计数：${countText}/${thresholdText}`;
                        })()}
                      >
                        anti_spam 命中
                      </span>
                    ) : null}
                  </div>
                </td>
                <td className="px-3 py-2 whitespace-nowrap">{stageCell(it.vec ?? null)}</td>
                <td className="px-3 py-2 whitespace-nowrap">{stageCell(it.llm ?? null)}</td>
                <td className="px-3 py-2 whitespace-nowrap">
                  {it.chunk?.chunked ? (
                    <div className="flex flex-col">
                      <span>chunked#{it.chunk.chunkSetId ?? '—'}</span>
                      <span className="text-xs text-gray-500">
                        {it.chunk.completedChunks ?? 0}/{it.chunk.totalChunks ?? 0} · failed={it.chunk.failedChunks ?? 0} · max={it.chunk.maxScore ?? '—'} · avg={it.chunk.avgMs ?? '—'}ms
                      </span>
                    </div>
                  ) : (
                    '—'
                  )}
                </td>
                <td className="px-3 py-2 whitespace-nowrap">{statusLabel(it.queueStatus ?? null)}</td>
                <td className="px-3 py-2 whitespace-nowrap">
                  {it.manual?.hasManual ? (
                    <span title={`${it.manual.lastAction ?? ''} ${it.manual.lastActorName ?? ''} ${it.manual.lastAt ?? ''}`}>有</span>
                  ) : (
                    '—'
                  )}
                </td>
                <td className="px-3 py-2 whitespace-nowrap font-mono text-xs">{it.latestTraceId ?? '—'}</td>
                <td className="px-3 py-2 whitespace-nowrap">
                  <button
                    type="button"
                    className="rounded border px-3 py-1.5"
                    onClick={(e) => {
                      e.stopPropagation();
                      void openDetail(it.queueId);
                    }}
                  >
                    详情
                  </button>
                </td>
              </tr>
            ))}

            {!loading && items.length === 0 ? (
              <tr>
                <td className="px-3 py-6 text-center text-gray-500" colSpan={10}>
                  暂无数据。可以尝试清空筛选条件，或检查 queueId/时间范围/TraceId 是否正确。
                </td>
              </tr>
            ) : null}

            {loading ? (
              <tr>
                <td className="px-3 py-6 text-center text-gray-500" colSpan={10}>
                  加载中…
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>

      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-3">
        <div className="text-sm text-gray-600">
          共 {totalElements} 条，{totalPages} 页；当前第 {page} 页
        </div>

        <div className="flex items-center gap-2">
          <select
            className="rounded border px-3 py-1.5"
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
          <button type="button" className="rounded border px-3 py-1.5 disabled:opacity-60" disabled={!canPrev || loading} onClick={() => setPage((p) => Math.max(1, p - 1))}>
            上一页
          </button>
          <button type="button" className="rounded border px-3 py-1.5 disabled:opacity-60" disabled={!canNext || loading} onClick={() => setPage((p) => Math.min(totalPages, p + 1))}>
            下一页
          </button>
        </div>
      </div>

      {detailOpen ? (
        <DetailDialog
          open={detailOpen}
          onClose={closeDetail}
          variant="drawerRight"
          title="审核任务详情"
          subtitle={
            <>
              queueId：
              {(detail?.queue as { id?: unknown } | null | undefined)?.id ? String((detail?.queue as { id?: unknown }).id) : '—'}
              {activeTraceId ? ` · traceId：${activeTraceId}` : ''}
            </>
          }
          headerActions={
            <>
              {activeTraceId ? (
                <button
                  type="button"
                  className="rounded bg-gray-900 text-white px-3 py-2 text-sm"
                  onClick={() => {
                    const next = { ...query, traceId: activeTraceId };
                    setQuery(next);
                    setPage(1);
                    syncToUrl(next, 1, pageSize);
                    closeDetail();
                  }}
                >
                  追溯该 Trace
                </button>
              ) : null}
              <button
                type="button"
                className="rounded border px-3 py-2 text-sm"
                onClick={() => {
                  const qid = (detail?.queue as { id?: unknown } | null | undefined)?.id;
                  if (typeof qid === 'number') navigate(`/admin/review?active=queue&taskId=${qid}`);
                }}
              >
                打开队列项
              </button>
            </>
          }
          tabs={[
            { id: 'overview', label: '概览' },
            { id: 'pipeline', label: '流水线' },
            { id: 'llm', label: 'LLM 输出' },
          ]}
          activeTabId={detailTab}
          onTabChange={(id) => setDetailTab(id as 'overview' | 'pipeline' | 'llm')}
          containerClassName="max-w-5xl xl:max-w-6xl overflow-hidden"
          bodyClassName="flex-1 overflow-auto p-4 space-y-3"
        >
              {detailLoading ? (
                <div className="text-gray-500">加载详情中…</div>
              ) : detail ? (
                <>
                  {detailTab === 'overview' ? (
                    <div className="rounded border p-3">
                      <div className="text-xs text-gray-500 mb-2">队列详情（JSON）</div>
                      <pre className="text-xs bg-gray-50 rounded p-3 overflow-auto max-h-[260px]">{safeJson(detail.queue)}</pre>
                    </div>
                  ) : null}

                  {detailTab === 'pipeline' ? (
                    <div className="rounded border p-2 space-y-2">
                      <div className="text-xs text-gray-500 leading-tight">流水线</div>
                      {typeof (detail.queue as { id?: unknown } | null | undefined)?.id === 'number' ? (
                        <>
                          <ModerationPipelineTracePanel queueId={(detail.queue as { id: number }).id} density="compact" />
                          <div className="border-t pt-2">
                            <ModerationPipelineHistoryPanel
                              title="历史 Run + Steps"
                              titleClassName="text-sm font-normal"
                              density="compact"
                              initialMode={{ kind: 'queue', queueId: (detail.queue as { id: number }).id }}
                              autoLatest={false}
                              autoLoadDetails={true}
                              showPageSummary={false}
                              showPaginationTitle={false}
                              showRefreshButton={false}
                              paginationPlacement="header"
                            />
                          </div>
                        </>
                      ) : null}
                    </div>
                  ) : null}

                  {detailTab === 'llm' ? (
                    <>
                      <div className="rounded border p-3 space-y-2">
                      <div className="flex items-center justify-between gap-3">
                        <div className="text-xs text-gray-500">LLM 输出（steps）</div>
                        {llmSteps.length === 0 ? <div className="text-xs text-gray-400">未找到包含 evidence/rawModelOutput 的 LLM step</div> : null}
                      </div>

                      {llmSteps.map((s0) => {
                        const stepId = s0.id == null ? '' : String(s0.id);
                        const stage = shortCell(s0.stage);
                        const order = toInt(s0.stepOrder);
                        const decision = shortCell(s0.decision);
                        const costMs = toInt(s0.costMs);
                        const details = toRecord(s0.details ?? null);
                        const reasons = Array.isArray(details?.reasons) ? details?.reasons : null;
                        const labels = Array.isArray(details?.labels) ? details?.labels : null;
                        const riskTags = Array.isArray(details?.riskTags) ? details?.riskTags : null;
                        const evItems = extractEvidenceFromDetails(details);
                        const rawModelOutput = details?.rawModelOutput == null ? '' : String(details.rawModelOutput);
                        const rowTitle = `${stage}${order == null ? '' : `#${order}`}${stepId ? ` · id=${stepId}` : ''}`;
                        return (
                          <details key={stepId || rowTitle} className="rounded border p-2">
                            <summary className="cursor-pointer select-none text-sm">
                              {rowTitle} · {decision}
                              {costMs == null ? '' : ` · ${formatMs(costMs)}`}
                            </summary>
                            <div className="mt-2 space-y-2">
                              <div className="grid grid-cols-1 lg:grid-cols-3 gap-2">
                                <div className="rounded border bg-gray-50 p-2">
                                  <div className="text-xs text-gray-500">核心字段</div>
                                  <pre className="text-xs whitespace-pre-wrap break-words max-h-[220px] overflow-auto">
                                    {safeJson({
                                      model: details?.model ?? null,
                                      decision: details?.decision ?? null,
                                      decision_suggestion: details?.decision_suggestion ?? null,
                                      score: details?.score ?? null,
                                      risk_score: details?.risk_score ?? null,
                                      severity: details?.severity ?? null,
                                      uncertainty: details?.uncertainty ?? null,
                                      inputMode: details?.inputMode ?? null,
                                      latencyMs: details?.latencyMs ?? null,
                                      usage: details?.usage ?? null,
                                    })}
                                  </pre>
                                </div>
                                <div className="rounded border bg-gray-50 p-2">
                                  <div className="text-xs text-gray-500">标签与原因</div>
                                  <pre className="text-xs whitespace-pre-wrap break-words max-h-[220px] overflow-auto">
                                    {safeJson({
                                      reasons: reasons ?? null,
                                      riskTags: riskTags ?? null,
                                      labels: labels ?? null,
                                    })}
                                  </pre>
                                </div>
                                <div className="rounded border bg-gray-50 p-2">
                                  <div className="flex items-center justify-between gap-2">
                                    <div className="text-xs text-gray-500">evidence</div>
                                    {evItems.length ? (
                                      <button
                                        type="button"
                                        className="rounded border px-2 py-1 text-xs"
                                        onClick={() => void copyText(evItems.map((x) => parseEvidenceItem(x).text).join('\n'))}
                                      >
                                        复制
                                      </button>
                                    ) : null}
                                  </div>
                                  {evItems.length ? (
                                    <div className="mt-1 space-y-1 max-h-[220px] overflow-auto">
                                      {evItems.map((x, i) => {
                                        const it = parseEvidenceItem(x);
                                        const headParts: string[] = [];
                                        if (it.imageId) headParts.push(`图片: ${it.imageId}`);
                                        if (it.span) headParts.push(`span: ${it.span.start}-${it.span.end}`);
                                        const head = headParts.join(' · ');
                                        return (
                                          <div key={`${stepId}-${i}`} className="rounded border bg-white p-2">
                                            {head ? <div className="text-xs text-gray-500">{head}</div> : null}
                                            <div className="text-xs font-mono break-words whitespace-pre-wrap">{it.text}</div>
                                          </div>
                                        );
                                      })}
                                    </div>
                                  ) : (
                                    <div className="text-xs text-gray-400 mt-1">—</div>
                                  )}
                                </div>
                              </div>

                            {rawModelOutput ? (
                              <div className="rounded border bg-white p-2">
                                <div className="flex items-center justify-between gap-2">
                                  <div className="text-xs text-gray-500">rawModelOutput（截断）</div>
                                  <button type="button" className="rounded border px-2 py-1 text-xs" onClick={() => void copyText(rawModelOutput)}>
                                    复制
                                  </button>
                                </div>
                                <pre className="text-xs whitespace-pre-wrap break-words max-h-[320px] overflow-auto mt-1">{rawModelOutput}</pre>
                              </div>
                            ) : null}

                            {details?.stages ? (
                              <div className="rounded border bg-white p-2">
                                <div className="text-xs text-gray-500">stages（JSON）</div>
                                <pre className="text-xs bg-gray-50 rounded p-3 overflow-auto max-h-[320px] mt-1">{safeJson(details.stages)}</pre>
                              </div>
                            ) : null}
                          </div>
                        </details>
                      );
                    })}

                    {!detail.chunkSet ? (
                      <div className="text-xs text-gray-500">
                        提示：当前任务未启用分片审核（chunked），因此 summaryForNext 不适用；如需查看原文，可点击“打开队列项”。
                      </div>
                    ) : null}
                      </div>

                  <div className="rounded border p-3">
                    <div className="text-xs text-gray-500 mb-2">分片汇总（JSON）</div>
                    <pre className="text-xs bg-gray-50 rounded p-3 overflow-auto max-h-[260px]">{safeJson(detail.chunkSet ?? null)}</pre>
                  </div>

                  <div className="rounded border p-3 space-y-3">
                    <div className="flex items-center justify-between gap-3">
                      <div className="text-xs text-gray-500">证据与分片审核输出汇总</div>
                      <div className="flex items-center gap-2">
                        <input
                          className="rounded border px-2 py-1 text-xs w-[160px]"
                          placeholder="chunkIndex 筛选"
                          value={chunkIndexFilter}
                          onChange={(e) => setChunkIndexFilter(e.target.value)}
                        />
                        <button
                          type="button"
                          className="rounded border px-2 py-1 text-xs"
                          onClick={() => {
                            const lines: string[] = [];
                            const entries = Object.entries(chunkOutput.summaries)
                              .map(([k, v]) => ({ k, v, n: toInt(k) }))
                              .sort((a, b) => (a.n ?? 0) - (b.n ?? 0));
                            for (const it of entries) {
                              if (chunkOutput.filterIdx != null && it.n !== chunkOutput.filterIdx) continue;
                              lines.push(`chunkIndex=${it.k}\n${it.v}\n`);
                            }
                            void copyText(lines.join('\n'));
                          }}
                        >
                          复制 summaries
                        </button>
                      </div>
                    </div>

                    <details className="rounded border p-2" open>
                      <summary className="cursor-pointer select-none text-sm">
                        evidence（列表）（{uniqueEvidenceCount}）
                      </summary>
                      <div className="mt-2">
                        <EvidenceListView
                          stepEvidenceGroups={stepEvidenceGroups}
                          chunkEvidenceByChunkIndex={detail.chunkSet ? (chunkOutput.llmEvidenceByChunk as unknown as Record<string, unknown[]>) : undefined}
                          chunkIdByChunkIndex={chunkIdByChunkIndex}
                          chunkIndexFilter={chunkOutput.filterIdx}
                          imageUrlByImageId={evidenceImageUrlById}
                        />
                      </div>
                    </details>

                    {detail.chunkSet ? (
                      <details className="rounded border p-2">
                        <summary className="cursor-pointer select-none text-sm">summaryForNext（{Object.keys(chunkOutput.summaries).length}）</summary>
                        <div className="mt-2 overflow-auto">
                          <table className="min-w-[860px] w-full text-sm">
                            <thead>
                              <tr className="text-left text-xs text-gray-500">
                                <th className="py-2 pr-3 w-[120px]">chunkIndex</th>
                                <th className="py-2 pr-3">summaryForNext</th>
                                <th className="py-2 pr-3 w-[90px]">操作</th>
                              </tr>
                            </thead>
                            <tbody>
                              {Object.entries(chunkOutput.summaries)
                                .map(([k, v]) => ({ k, v, n: toInt(k) }))
                                .sort((a, b) => (a.n ?? 0) - (b.n ?? 0))
                                .filter((x) => (chunkOutput.filterIdx == null ? true : x.n === chunkOutput.filterIdx))
                                .map((x) => (
                                  <tr key={x.k} className="border-t align-top">
                                    <td className="py-2 pr-3 whitespace-nowrap font-mono text-xs">{x.k}</td>
                                    <td className="py-2 pr-3">
                                      <pre className="text-xs whitespace-pre-wrap break-words max-h-[220px] overflow-auto">{x.v}</pre>
                                    </td>
                                    <td className="py-2 pr-3 whitespace-nowrap">
                                      <button type="button" className="rounded border px-2 py-1 text-xs" onClick={() => void copyText(x.v)}>
                                        复制
                                      </button>
                                    </td>
                                  </tr>
                                ))}
                              {Object.keys(chunkOutput.summaries).length === 0 ? (
                                <tr>
                                  <td className="py-4 text-center text-gray-500" colSpan={3}>
                                    暂无 summaries
                                  </td>
                                </tr>
                              ) : null}
                            </tbody>
                          </table>
                        </div>
                      </details>
                    ) : stepEvidenceCount <= 0 ? (
                      <div className="text-xs text-gray-400">无 chunkSet（非分片审核）· 暂无 evidence</div>
                    ) : null}
                  </div>

                  <div className="rounded border p-3 space-y-3">
                    <div className="text-xs text-gray-500">分片进度/明细</div>
                    {chunkRows.length > 0 ? (
                      <div className="overflow-auto">
                        <table className="min-w-[860px] w-full text-sm">
                          <thead>
                            <tr className="text-left text-xs text-gray-500">
                              <th className="py-2 pr-3 w-[140px]">分片</th>
                              <th className="py-2 pr-3 w-[140px]">状态</th>
                              <th className="py-2 pr-3 w-[140px]">结论</th>
                              <th className="py-2 pr-3 w-[120px]">风险分数</th>
                              <th className="py-2 pr-3 w-[120px]">耗时</th>
                              <th className="py-2 pr-3 w-[220px]">Evidence</th>
                              <th className="py-2 pr-3 w-[260px]">Summary</th>
                              <th className="py-2 pr-3">错误</th>
                            </tr>
                          </thead>
                          <tbody>
                            {chunkRows.map((row, idx) => {
                              const key = chunkRowKey(row, idx);
                              const opened = expandedChunkKey === key;
                              const label = row.chunkId ?? row.id ?? row.chunkIndex ?? row.index ?? idx + 1;
                              const status = row.status ?? row.state ?? row.phase;
                              const verdict = row.verdict ?? row.decision ?? row.result ?? row.finalVerdict;
                              const score =
                                row.riskScore ??
                                row.risk_score ??
                                row.score ??
                                row.confidence ??
                                row.maxScore;
                              const cost = row.costMs ?? row.latencyMs ?? row.durationMs;
                              const err = row.error ?? row.errorMessage ?? row.err;
                              const ci = rowChunkIndex(row);
                              const ck = ci == null ? null : String(ci);
                              const llmEv = ck ? chunkOutput.llmEvidenceByChunk[ck] : undefined;
                              const summary = ck ? chunkOutput.summaries[ck] : undefined;
                              const evPreview = llmEv && llmEv.length ? `${llmEv.length}条 · ${llmEv[0]}` : '—';
                              const sumPreview = summary ? (summary.length > 80 ? `${summary.slice(0, 80)}…` : summary) : '—';
                              return (
                                <React.Fragment key={key}>
                                  <tr
                                    className="border-t align-top hover:bg-gray-50 cursor-pointer"
                                    onClick={() => setExpandedChunkKey((cur) => (cur === key ? null : key))}
                                    title="点击展开/收起该分片 JSON"
                                  >
                                    <td className="py-2 pr-3 whitespace-nowrap font-mono text-xs">{shortCell(label)}</td>
                                    <td className="py-2 pr-3 whitespace-nowrap">{shortCell(status)}</td>
                                    <td className="py-2 pr-3 whitespace-nowrap">{shortCell(verdict)}</td>
                                    <td className="py-2 pr-3 whitespace-nowrap font-mono text-xs">{riskScoreCell(score)}</td>
                                    <td className="py-2 pr-3 whitespace-nowrap">{shortCell(cost)}</td>
                                    <td className="py-2 pr-3 max-w-[220px] truncate" title={llmEv ? llmEv.join('\n') : undefined}>
                                      {evPreview}
                                    </td>
                                    <td className="py-2 pr-3 max-w-[260px] truncate" title={summary ?? undefined}>
                                      {sumPreview}
                                    </td>
                                    <td className="py-2 pr-3 max-w-[520px] truncate">{shortCell(err)}</td>
                                  </tr>
                                  {opened ? (
                                    <tr className="border-t bg-gray-50">
                                      <td className="py-2 pr-3" colSpan={8}>
                                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-2 mb-2">
                                          <div className="rounded border bg-white p-2">
                                            <div className="flex items-center justify-between gap-2">
                                              <div className="text-xs text-gray-500">summaryForNext</div>
                                              {summary ? (
                                                <button type="button" className="rounded border px-2 py-1 text-xs" onClick={() => void copyText(summary)}>
                                                  复制
                                                </button>
                                              ) : null}
                                            </div>
                                            <pre className="text-xs whitespace-pre-wrap break-words max-h-[220px] overflow-auto">{summary ?? '—'}</pre>
                                          </div>
                                          <div className="rounded border bg-white p-2">
                                            <div className="flex items-center justify-between gap-2">
                                              <div className="text-xs text-gray-500">evidence（LLM 输出）</div>
                                              {llmEv && llmEv.length ? (
                                                <button type="button" className="rounded border px-2 py-1 text-xs" onClick={() => void copyText(llmEv.join('\n'))}>
                                                  复制
                                                </button>
                                              ) : null}
                                            </div>
                                          <ChunkEvidenceView chunkId={toInt(row.chunkId ?? row.id)} evidence={llmEv ?? null} />
                                          </div>
                                        </div>
                                        <pre className="text-xs bg-white rounded border p-3 overflow-auto max-h-[320px]">{safeJson(row)}</pre>
                                      </td>
                                    </tr>
                                  ) : null}
                                </React.Fragment>
                              );
                            })}
                          </tbody>
                        </table>
                      </div>
                    ) : (
                      <pre className="text-xs bg-gray-50 rounded p-3 overflow-auto max-h-[320px]">{safeJson(detail.chunkProgress ?? null)}</pre>
                    )}
                  </div>
                    </>
                  ) : null}

                  {detailTab === 'overview' ? (
                    <div className="rounded border p-3">
                    <div className="text-xs text-gray-500 mb-2">关联审计日志（最近 200 条）</div>
                    <div className="overflow-auto">
                      <table className="min-w-[860px] w-full text-sm">
                        <thead>
                          <tr className="text-left text-xs text-gray-500">
                            <th className="py-2 pr-3 w-[180px]">时间</th>
                            <th className="py-2 pr-3 w-[220px]">动作</th>
                            <th className="py-2 pr-3 w-[90px]">结果</th>
                            <th className="py-2 pr-3">消息</th>
                            <th className="py-2 pr-3 w-[220px]">TraceId</th>
                          </tr>
                        </thead>
                        <tbody>
                          {(detail.auditLogs ?? []).map((x, idx) => (
                            <tr key={String((x as { id?: unknown }).id ?? idx)} className="border-t align-top">
                              <td className="py-2 pr-3 whitespace-nowrap">{formatDateTime((x as { createdAt?: string | null }).createdAt ?? null)}</td>
                              <td className="py-2 pr-3 font-mono text-xs">{String((x as { action?: unknown }).action ?? '—')}</td>
                              <td className="py-2 pr-3">{String((x as { result?: unknown }).result ?? '—')}</td>
                              <td className="py-2 pr-3 max-w-[520px] truncate">{String((x as { message?: unknown }).message ?? '—')}</td>
                              <td className="py-2 pr-3 font-mono text-xs">{String((x as { traceId?: unknown }).traceId ?? '—')}</td>
                            </tr>
                          ))}
                          {(detail.auditLogs ?? []).length === 0 ? (
                            <tr>
                              <td className="py-4 text-center text-gray-500" colSpan={5}>
                                暂无审计日志
                              </td>
                            </tr>
                          ) : null}
                        </tbody>
                      </table>
                    </div>
                    </div>
                  ) : null}

                  {detailTab === 'overview' ? (
                    <div className="rounded border p-3">
                      <div className="text-xs text-gray-500 mb-2">原始数据</div>
                      <pre className="text-xs bg-gray-50 rounded p-3 overflow-auto max-h-[320px]">{safeJson(detail)}</pre>
                    </div>
                  ) : null}
                </>
              ) : (
                <div className="text-gray-500">未找到详情</div>
              )}
        </DetailDialog>
      ) : null}
    </div>
  );
};

export default ReviewTraceForm;
