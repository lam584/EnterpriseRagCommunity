import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Edit2, Check } from 'lucide-react';
import {
  adminGetLlmQueueStatus,
  adminGetLlmQueueTaskDetail,
  adminUpdateLlmQueueConfig,
  type LlmQueueStatusDTO,
  type LlmQueueTaskDTO,
  type LlmQueueTaskDetailDTO,
} from '../../../../services/llmQueueAdminService';
import { adminGetLlmRoutingConfig } from '../../../../services/llmRoutingAdminService';
import { adminGetAiProvidersConfig } from '../../../../services/aiProvidersAdminService';
import LlmLoadTestPanel from './llm-loadtest';
import { stripThinkBlocks } from '../../../../utils/thinkTags';
import { SparkLine, fmtMs, fmtNum, fmtTs, fmtHmsTs, fmtType, normalizeSamples, sliceSamplesByWindow } from './llm-queue.sparkline';
import { TaskTable } from './llm-queue.task-table';
import DetailDialog from '../../../../components/common/DetailDialog';

function fmtJson(s: string | null | undefined): string {
  if (!s) return '—';
  try {
    const o = JSON.parse(s);
    return JSON.stringify(o, null, 2);
  } catch {
    return s;
  }
}

const LlmQueueForm: React.FC = () => {
  const [queueWindowSec, setQueueWindowSec] = useState(300);
  const [speedWindowSec, setSpeedWindowSec] = useState(300);
  const [queueTrend, setQueueTrend] = useState<'total' | 'pending' | 'running'>('total');
  const [loading, setLoading] = useState(false);
  const [savingConfig, setSavingConfig] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [backgroundError, setBackgroundError] = useState<string | null>(null);
  const [data, setData] = useState<LlmQueueStatusDTO | null>(null);
  const [maxConcurrentInput, setMaxConcurrentInput] = useState('');
  const [maxConcurrentDirty, setMaxConcurrentDirty] = useState(false);
  const [isEditingMaxConcurrent, setIsEditingMaxConcurrent] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshIntervalMs, setRefreshIntervalMs] = useState(5000);
  const [refreshIntervalInput, setRefreshIntervalInput] = useState('5000');
  const [refreshIntervalDirty, setRefreshIntervalDirty] = useState(false);
  const [lastUpdatedAtMs, setLastUpdatedAtMs] = useState<number | null>(null);
  const [lastRefreshCostMs, setLastRefreshCostMs] = useState<number | null>(null);
  const inflightRef = useRef<AbortController | null>(null);
  const reqSeqRef = useRef(0);
  const detailAbortRef = useRef<AbortController | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailTask, setDetailTask] = useState<LlmQueueTaskDTO | null>(null);
  const [detail, setDetail] = useState<LlmQueueTaskDetailDTO | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailLoadError, setDetailLoadError] = useState<string | null>(null);
  const [detailRefreshError, setDetailRefreshError] = useState<string | null>(null);
  const [detailRefreshing, setDetailRefreshing] = useState(false);
  const [detailTab, setDetailTab] = useState<'overview' | 'input' | 'output'>('overview');
  const [typeLabels, setTypeLabels] = useState<Record<string, string>>({});
  const [providerNameById, setProviderNameById] = useState<Record<string, string>>({});

  useEffect(() => {
    adminGetLlmRoutingConfig()
      .then((cfg) => {
        const labels: Record<string, string> = {};
        (cfg.scenarios ?? []).forEach((s) => {
          if (!s?.taskType) return;
          labels[String(s.taskType).trim().toUpperCase()] = s.label;
        });
        labels.SUMMARY_GEN = '摘要生成';
        labels.LANGUAGE_TAG_GEN = '语言标签生成';
        labels.EMBEDDING = '嵌入向量化';
        labels.TOPIC_TAG_GEN = '主题标签生成';
        labels.MODERATION_CHUNK = '分片审核';
        setTypeLabels(labels);
      })
      .catch((e) => console.error('Failed to load routing config for labels', e));
  }, []);

  useEffect(() => {
    adminGetAiProvidersConfig()
      .then((cfg) => {
        const map: Record<string, string> = {};
        (cfg.providers ?? []).forEach((p) => {
          const id = String(p?.id ?? '').trim();
          if (!id) return;
          const name = String(p?.name ?? '').trim();
          map[id] = name || id;
        });
        setProviderNameById(map);
      })
      .catch((e) => console.error('Failed to load providers config for names', e));
  }, []);

  const fetchWindowSec = useMemo(() => Math.max(queueWindowSec, speedWindowSec), [queueWindowSec, speedWindowSec]);

  const rawSamples = useMemo(() => normalizeSamples(data?.samples), [data?.samples]);

  const samples = useMemo(() => {
    if (rawSamples.length === 0) return rawSamples;
    let lastNonZero = 0;
    let lastNonZeroAtMs = 0;
    return rawSamples.map((s) => {
      const tsMs = Number.isFinite(Date.parse(s.ts)) ? Date.parse(s.ts) : 0;
      let v = Number(s.tokensPerSec || 0);
      if (!Number.isFinite(v) || v < 0) v = 0;
      if (v > 0) {
        lastNonZero = v;
        lastNonZeroAtMs = tsMs;
        return { ...s, tokensPerSec: v };
      }

      const runningN = Number((s as { running?: unknown }).running || 0);
      if (!(runningN > 0) || !(lastNonZero > 0) || !(tsMs > 0) || !(lastNonZeroAtMs > 0)) return { ...s, tokensPerSec: 0 };

      const ageMs = tsMs - lastNonZeroAtMs;
      if (ageMs <= 0) return { ...s, tokensPerSec: lastNonZero };
      if (ageMs <= 60_000) return { ...s, tokensPerSec: lastNonZero };
      if (ageMs >= 300_000) return { ...s, tokensPerSec: 0 };
      const ageSecBeyondHold = (ageMs - 60_000) / 1000;
      const tauSec = 60;
      const decayed = lastNonZero * Math.exp(-ageSecBeyondHold / tauSec);
      return { ...s, tokensPerSec: decayed < 0.05 ? 0 : decayed };
    });
  }, [rawSamples]);

  const queueSamples = useMemo(() => sliceSamplesByWindow(samples, queueWindowSec), [samples, queueWindowSec]);
  const speedSamples = useMemo(() => sliceSamplesByWindow(samples, speedWindowSec), [samples, speedWindowSec]);

  const latestTokensPerSec = useMemo(() => {
    if (samples.length === 0) return 0;
    return samples[samples.length - 1].tokensPerSec || 0;
  }, [samples]);

  useEffect(() => {
    try {
      const v = localStorage.getItem('admin.llmQueue.refreshIntervalMs');
      const n = Math.floor(Number(v));
      if (Number.isFinite(n) && n > 0) setRefreshIntervalMs(Math.max(200, Math.min(60000, n)));
    } catch {}
  }, []);

  useEffect(() => {
    if (refreshIntervalDirty) return;
    setRefreshIntervalInput(String(refreshIntervalMs));
  }, [refreshIntervalMs, refreshIntervalDirty]);

  const applyRefreshInterval = useCallback(() => {
    const n = Math.floor(Number(refreshIntervalInput));
    if (!Number.isFinite(n) || n <= 0) {
      setError('刷新间隔必须是正整数（ms）');
      return;
    }
    const clamped = Math.max(200, Math.min(60000, n));
    setRefreshIntervalMs(clamped);
    setRefreshIntervalInput(String(clamped));
    setRefreshIntervalDirty(false);
    try {
      localStorage.setItem('admin.llmQueue.refreshIntervalMs', String(clamped));
    } catch {}
  }, [refreshIntervalInput]);

  const abortInFlight = useCallback(() => {
    try {
      inflightRef.current?.abort();
    } catch {}
    inflightRef.current = null;
  }, []);

  const closeDetail = useCallback(() => {
    setDetailOpen(false);
    setDetailTab('overview');
    setDetailTask(null);
    setDetail(null);
    setDetailLoading(false);
    setDetailLoadError(null);
    setDetailRefreshError(null);
    setDetailRefreshing(false);
    try {
      detailAbortRef.current?.abort();
    } catch {}
    detailAbortRef.current = null;
  }, []);

  const fetchDetail = useCallback(async (taskId: string, opts: { background?: boolean; timeoutMs?: number } = {}) => {
    if (detailAbortRef.current) return;

    const background = Boolean(opts.background);
    if (background) {
      setDetailRefreshing(true);
      setDetailRefreshError(null);
    } else {
      setDetailLoading(true);
      setDetailLoadError(null);
      setDetailRefreshError(null);
    }

    const controller = new AbortController();
    detailAbortRef.current = controller;

    const timeoutMs = Number(opts.timeoutMs);
    const clampedTimeoutMs = Number.isFinite(timeoutMs) && timeoutMs > 0 ? Math.max(500, Math.min(60000, Math.floor(timeoutMs))) : 20000;

    try {
      const d = await adminGetLlmQueueTaskDetail(taskId, { signal: controller.signal, timeoutMs: clampedTimeoutMs });
      if (detailAbortRef.current !== controller) return;
      setDetail(d);
      setDetailLoadError(null);
      setDetailRefreshError(null);
    } catch (e) {
      if (detailAbortRef.current !== controller) return;
      const isAbort = e instanceof DOMException && e.name === 'AbortError';
      if (isAbort) return;
      const msg = e instanceof Error ? e.message : String(e);
      if (background) setDetailRefreshError(msg);
      else setDetailLoadError(msg);
    } finally {
      if (detailAbortRef.current === controller) detailAbortRef.current = null;
      if (background) setDetailRefreshing(false);
      else setDetailLoading(false);
    }
  }, []);

  const openDetail = useCallback(
    (t: LlmQueueTaskDTO) => {
      setDetailOpen(true);
      setDetailTab('overview');
      setDetailTask(t);
      setDetail(null);
      setDetailLoadError(null);
      setDetailRefreshError(null);
      setDetailRefreshing(false);
      void fetchDetail(t.id);
    },
    [fetchDetail]
  );

  const canAutoRefreshDetail = useMemo(() => {
    const s = detail?.status;
    return detailOpen && Boolean(detailTask?.id) && (s === 'PENDING' || s === 'RUNNING');
  }, [detail?.status, detailOpen, detailTask?.id]);

  const detailRefreshIntervalMs = useMemo(() => {
    const base = Math.floor(Number(refreshIntervalMs));
    if (!Number.isFinite(base) || base <= 0) return 2000;
    return Math.max(500, Math.min(5000, base));
  }, [refreshIntervalMs]);

  useEffect(() => {
    if (!canAutoRefreshDetail || !detailTask?.id) return;
    let stopped = false;
    let timer: number | null = null;
    const timeoutMs = Math.max(800, Math.min(15000, Math.floor(detailRefreshIntervalMs * 2)));

    const tick = async () => {
      if (stopped) return;
      if (document.visibilityState === 'hidden') {
        timer = window.setTimeout(() => void tick(), detailRefreshIntervalMs);
        return;
      }
      if (!detailAbortRef.current) {
        const id = detailTask.id;
        await fetchDetail(id, { background: true, timeoutMs });
      }
      if (stopped) return;
      timer = window.setTimeout(() => void tick(), detailRefreshIntervalMs);
    };

    timer = window.setTimeout(() => void tick(), detailRefreshIntervalMs);
    return () => {
      stopped = true;
      if (timer != null) window.clearTimeout(timer);
    };
  }, [canAutoRefreshDetail, detailRefreshIntervalMs, detailTask?.id, fetchDetail]);

  const load = useCallback(
    async (opts: { background?: boolean; timeoutMs?: number } = {}) => {
      const seq = ++reqSeqRef.current;
      const startedAt = performance.now();
      const background = Boolean(opts.background);
      if (!background) {
        setLoading(true);
        setError(null);
      }

      const controller = new AbortController();
      inflightRef.current = controller;

      try {
        const r = await adminGetLlmQueueStatus({
          windowSec: fetchWindowSec,
          signal: controller.signal,
          timeoutMs: opts.timeoutMs,
        });
        if (reqSeqRef.current !== seq) return;
        setData(r);
        setBackgroundError(null);
        setLastUpdatedAtMs(Date.now());
      } catch (e) {
        const isAbort = e instanceof DOMException && e.name === 'AbortError';
        if (isAbort) return;
        const msg = e instanceof Error ? e.message : String(e);
        if (background) {
          setBackgroundError(msg);
        } else {
          setError(msg);
          setData(null);
        }
      } finally {
        if (inflightRef.current === controller) inflightRef.current = null;
        const cost = performance.now() - startedAt;
        if (reqSeqRef.current === seq) setLastRefreshCostMs(cost);
        if (!background) setLoading(false);
      }
    },
    [fetchWindowSec]
  );

  useEffect(() => {
    load({ timeoutMs: 30000 });
  }, [load]);

  useEffect(() => {
    if (maxConcurrentDirty) return;
    const v = data?.maxConcurrent;
    if (typeof v === 'number' && Number.isFinite(v) && v > 0) setMaxConcurrentInput(String(v));
  }, [data?.maxConcurrent, maxConcurrentDirty]);

  useEffect(() => {
    if (!autoRefresh) return;
    const interval = Math.max(200, Math.min(60000, Math.floor(refreshIntervalMs)));
    const timeoutMs = Math.max(800, Math.min(15000, Math.floor(interval * 2)));
    let stopped = false;
    let timer: number | null = null;
    const tick = async () => {
      if (stopped) return;
      if (document.visibilityState === 'hidden') {
        timer = window.setTimeout(() => void tick(), interval);
        return;
      }
      if (inflightRef.current) {
        timer = window.setTimeout(() => void tick(), interval);
        return;
      }
      await load({ background: true, timeoutMs });
      if (stopped) return;
      timer = window.setTimeout(() => void tick(), interval);
    };
    void tick();
    return () => {
      stopped = true;
      if (timer != null) window.clearTimeout(timer);
    };
  }, [autoRefresh, refreshIntervalMs, load]);

  const pending = useMemo(() => data?.pending ?? [], [data]);
  const running = useMemo(() => data?.running ?? [], [data]);
  const recent = useMemo(() => data?.recentCompleted ?? [], [data]);

  const rateLimit = useMemo(() => {
    const list = data?.recentCompleted ?? [];
    let count = 0;
    for (const t of list) {
      const e = String(t?.error ?? '').toLowerCase();
      if (e.includes('http 429') || e.includes('limit_requests')) count++;
    }
    return { count, total: list.length };
  }, [data?.recentCompleted]);

  const detailOutputText = useMemo(() => {
    const rawOut = (detail?.output || '').trim();
    if (!rawOut) return '—';
    const inputRaw = detail?.input || '';
    let enableThinking: boolean | null = null;
    try {
      const parsed = JSON.parse(inputRaw) as unknown;
      if (parsed && typeof parsed === 'object' && 'enableThinking' in (parsed as Record<string, unknown>)) {
        const v = (parsed as Record<string, unknown>).enableThinking;
        if (typeof v === 'boolean') enableThinking = v;
      }
    } catch {}
    const lower = inputRaw.toLowerCase();
    const outLower = rawOut.toLowerCase();
    const shouldStrip =
      enableThinking === false ||
      lower.includes('/no_think') ||
      outLower.includes('<think') ||
      outLower.includes('reasoning_content');
    const out = shouldStrip ? stripThinkBlocks(rawOut) : rawOut;
    return fmtJson(out.trim());
  }, [detail?.input, detail?.output]);

  const detailMeta = useMemo(() => {
    const raw = String(detail?.input || '').trim();
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw) as unknown;
      if (!parsed || typeof parsed !== 'object') return null;
      const o = parsed as Record<string, unknown>;
      const queueId = typeof o.queueId === 'number' ? o.queueId : Number(o.queueId);
      const chunkId = typeof o.chunkId === 'number' ? o.chunkId : Number(o.chunkId);
      const kind = typeof o.kind === 'string' ? o.kind : null;
      return {
        kind,
        queueId: Number.isFinite(queueId) && queueId > 0 ? queueId : null,
        chunkId: Number.isFinite(chunkId) && chunkId > 0 ? chunkId : null,
      };
    } catch {
      return null;
    }
  }, [detail?.input]);

  const detailInputText = useMemo(() => {
    return fmtJson(detail?.input);
  }, [detail?.input]);



  const saveMaxConcurrent = useCallback(async () => {
    const n = Math.floor(Number(maxConcurrentInput));
    if (!Number.isFinite(n)) {
      setError('并发上限必须是数字');
      return;
    }
    setSavingConfig(true);
    setError(null);
    try {
      await adminUpdateLlmQueueConfig({ maxConcurrent: Math.max(1, Math.min(1024, n)) });
      setMaxConcurrentDirty(false);
      abortInFlight();
      await load({ timeoutMs: 30000 });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSavingConfig(false);
    }
  }, [abortInFlight, load, maxConcurrentInput]);

  const busy = loading;

  const lastUpdatedLabel = useMemo(() => {
    if (!lastUpdatedAtMs) return '—';
    return fmtTs(new Date(lastUpdatedAtMs).toISOString());
  }, [lastUpdatedAtMs]);

  const snapshotInfo = useMemo(() => {
    const at = Number(data?.snapshotAtMs || 0);
    if (!Number.isFinite(at) || at <= 0) return null;
    const ageMs = Date.now() - at;
    const ageSec = Math.max(0, Math.floor(ageMs / 1000));
    return {
      atIso: new Date(at).toISOString(),
      ageSec,
      stale: Boolean(data?.stale),
      truncated: Boolean(data?.truncated),
    };
  }, [data?.snapshotAtMs, data?.stale, data?.truncated]);

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-lg font-semibold">LLM 调用队列</h3>
          <div className="text-[11px] text-gray-500">
            自动刷新：{autoRefresh ? `${refreshIntervalMs}ms` : '关闭'} · 最近刷新：{lastUpdatedLabel} · 耗时：
            {lastRefreshCostMs == null ? '—' : `${Math.max(0, Math.round(lastRefreshCostMs))}ms`}
            {snapshotInfo ? (
              <span className="ml-2" title={`快照时间：${fmtHmsTs(snapshotInfo.atIso)}`}>
                · 快照：{snapshotInfo.stale ? '降级' : '实时'}（{snapshotInfo.ageSec}s 前）
                {snapshotInfo.truncated ? ' · 已裁剪' : ''}
              </span>
            ) : null}
            {backgroundError ? (
              <span className="ml-2 text-red-600" title={backgroundError}>
                自动刷新异常
              </span>
            ) : null}
          </div>
        </div>
        <div className="flex items-center gap-2">
          <label className="flex items-center gap-1 text-sm text-gray-700 select-none">
            <input
              className="h-4 w-4"
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => {
                const checked = e.target.checked;
                setAutoRefresh(checked);
                if (!checked) abortInFlight();
              }}
            />
            自动刷新
          </label>
          <input
            className="w-20 rounded border px-2 py-2 text-sm"
            type="number"
            min={200}
            max={60000}
            step={100}
            value={refreshIntervalInput}
            onChange={(e) => {
              setRefreshIntervalInput(e.target.value);
              setRefreshIntervalDirty(true);
            }}
            onBlur={() => {
              if (refreshIntervalDirty) applyRefreshInterval();
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') applyRefreshInterval();
            }}
            disabled={!autoRefresh}
          />
          <button
            type="button"
            className="rounded border px-3 py-2 text-sm"
            onClick={() => {
              abortInFlight();
              void load({ timeoutMs: 30000 });
            }}
            disabled={busy}
          >
            {busy ? '刷新中…' : '刷新'}
          </button>
        </div>
      </div>

      {error && <div className="rounded border border-red-300 bg-red-50 text-red-700 px-3 py-2 text-sm">{error}</div>}
      {rateLimit.count > 0 ? (
        <div className="rounded border border-amber-300 bg-amber-50 text-amber-900 px-3 py-2 text-sm">
          检测到最近完成任务中有 {rateLimit.count}/{rateLimit.total} 条疑似上游限流（HTTP 429 / limit_requests）。这会导致任务快速失败且
          tokens/sec 接近 0；如需更清晰观察排队趋势，可降低压测并发/增加重试间隔，或将队列并发上限调小以制造可观察的 pending。
        </div>
      ) : null}

      <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
        <div className="rounded border p-3">
          <div className="text-xs text-gray-600">排队中</div>
          <div className="text-3xl font-semibold">{data ? fmtNum(data.pendingCount) : '—'}</div>
        </div>
        <div className="rounded border p-3">
          <div className="text-xs text-gray-600">执行中</div>
          <div className="text-3xl font-semibold">{data ? fmtNum(data.runningCount) : '—'}</div>
        </div>
        <div className="rounded border p-3">
          <div className="text-xs text-gray-600">生成速度</div>
          <div className="text-3xl font-semibold">
            {data ? fmtNum(latestTokensPerSec) : '—'}
            <span className="ml-1 text-xs font-normal text-gray-500">tokens/s</span>
          </div>
        </div>
        <div className="rounded border p-3">
          <div className="flex items-center justify-between">
            <div className="text-xs text-gray-600">并发上限</div>
            <button
              type="button"
              className="text-gray-400 hover:text-blue-600 transition-colors"
              onClick={() => {
                if (isEditingMaxConcurrent) {
                  saveMaxConcurrent();
                  setIsEditingMaxConcurrent(false);
                } else {
                  setIsEditingMaxConcurrent(true);
                }
              }}
              disabled={savingConfig}
              title={isEditingMaxConcurrent ? '保存' : '编辑'}
            >
              {isEditingMaxConcurrent ? <Check className="w-4 h-4" /> : <Edit2 className="w-4 h-4" />}
            </button>
          </div>
          <input
            className={`text-3xl font-semibold w-full bg-transparent outline-none transition-all ${
              isEditingMaxConcurrent ? 'border rounded px-2 py-1 mt-1 text-2xl' : 'border-none p-0'
            }`}
            type="number"
            min={1}
            max={1024}
            value={maxConcurrentInput}
            readOnly={!isEditingMaxConcurrent}
            onChange={(e) => {
              setMaxConcurrentInput(e.target.value);
              setMaxConcurrentDirty(true);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                saveMaxConcurrent();
                setIsEditingMaxConcurrent(false);
              }
            }}
            autoFocus={isEditingMaxConcurrent}
          />
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
        <div className="rounded border p-4 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div className="text-sm font-semibold">
              队列趋势
              <span className="ml-2 text-xs font-normal text-gray-600">
                等待 {data ? fmtNum(data.pendingCount) : '—'} · 执行 {data ? fmtNum(data.runningCount) : '—'} · 全部{' '}
                {data ? fmtNum((data.pendingCount || 0) + (data.runningCount || 0)) : '—'}
              </span>
            </div>
            <div className="flex items-center gap-2">
              <select
                className="rounded border px-2 py-1 text-sm"
                value={queueTrend}
                onChange={(e) => setQueueTrend((e.target.value as 'total' | 'pending' | 'running') || 'total')}
              >
                <option value="total">全部</option>
                <option value="running">执行中</option>
                <option value="pending">排队中</option>
              </select>
              <select
                className="rounded border px-2 py-1 text-sm"
                value={String(queueWindowSec)}
                onChange={(e) => setQueueWindowSec(Math.max(30, Math.min(3600, Number(e.target.value) || 300)))}
              >
                <option value="60">近 1 分钟</option>
                <option value="300">近 5 分钟</option>
                <option value="900">近 15 分钟</option>
                <option value="3600">近 1 小时</option>
              </select>
              <div className="text-sm text-gray-600">条</div>
            </div>
          </div>
          {queueSamples.length === 0 ? (
            <div className="text-sm text-gray-500">暂无趋势数据</div>
          ) : (
            <SparkLine
              samples={queueSamples}
              value={(s) => {
                const pendingV = Number(s.queueLen || 0);
                const runningV = Number((s as { running?: number }).running || 0);
                if (queueTrend === 'pending') return pendingV;
                if (queueTrend === 'running') return runningV;
                return pendingV + runningV;
              }}
              height={128}
              lineWidth={1}
              axisFontSize={14}
              unitFontSize={14}
            />
          )}
        </div>
        <div className="rounded border p-4 space-y-3">
          <div className="flex items-center justify-between gap-2">
            <div className="text-sm font-semibold">生成速度曲线</div>
            <div className="flex items-center gap-2">
              <select
                className="rounded border px-2 py-1 text-sm"
                value={String(speedWindowSec)}
                onChange={(e) => setSpeedWindowSec(Math.max(30, Math.min(3600, Number(e.target.value) || 300)))}
              >
                <option value="60">近 1 分钟</option>
                <option value="300">近 5 分钟</option>
                <option value="900">近 15 分钟</option>
                <option value="3600">近 1 小时</option>
              </select>
              <div className="text-sm text-gray-600">tokens/sec</div>
            </div>
          </div>
          {speedSamples.length === 0 ? (
            <div className="text-sm text-gray-500">暂无趋势数据</div>
          ) : (
            <SparkLine
              samples={speedSamples}
              value={(s) => Number(s.tokensPerSec || 0)}
              color="#16a34a"
              height={128}
              lineWidth={1}
              axisFontSize={14}
              unitFontSize={14}
            />
          )}
        </div>
      </div>

      </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
        <TaskTable
          title={`当前执行任务（${running.length}）`}
          tasks={running}
          pageable
          showDuration={false}
          showTokensPerSec={false}
          showTokensOut={false}
          typeLabels={typeLabels}
        />
        <TaskTable
          title={`队列中任务（${pending.length}）`}
          tasks={pending}
          pageable
          showDuration={false}
          showTokensPerSec={false}
          showTokensOut={false}
          typeLabels={typeLabels}
        />
      </div>
      <TaskTable
        title={`最近完成任务（${recent.length}）`}
        tasks={recent}
        pageable
        showIndex
        onViewDetails={openDetail}
        typeLabels={typeLabels}
        showModelProvider
        providerNameById={providerNameById}
      />

      {detailOpen ? (
        <DetailDialog
          open={detailOpen}
          onClose={closeDetail}
          title={detailTask?.id ? `任务详情 #${detailTask.id}` : '任务详情'}
          headerActions={
            <>
              {detailRefreshing ? <div className="text-xs text-gray-500">自动刷新中…</div> : null}
              <button
                type="button"
                className="rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-700 shadow-sm hover:bg-gray-50 active:bg-gray-100 disabled:opacity-60"
                onClick={() => {
                  if (!detailTask?.id) return;
                  void fetchDetail(detailTask.id, { background: true });
                }}
                disabled={!detailTask?.id || detailRefreshing || detailLoading}
              >
                刷新
              </button>
            </>
          }
          tabs={[
            { id: 'overview', label: '概览' },
            { id: 'input', label: '输入' },
            { id: 'output', label: '输出' },
          ]}
          activeTabId={detailTab}
          onTabChange={(id) => setDetailTab(id as 'overview' | 'input' | 'output')}
          containerClassName="max-w-5xl max-h-[92vh]"
          bodyClassName="flex-1 overflow-auto p-4 space-y-4"
        >
          <div className="text-xs text-gray-600 font-mono break-all">{detailTask ? `ID: ${detailTask.id}` : ''}</div>
          {detail?.label ? <div className="text-xs text-gray-600 font-mono break-all">Label: {detail.label}</div> : null}
          {detailMeta?.queueId ? (
            <div className="text-xs">
              <a className="text-blue-700 hover:underline" href={`/admin/review?active=queue&taskId=${detailMeta.queueId}`} title="在审核队列中查看该队列编号">
                打开审核队列（queueId={detailMeta.queueId}）
              </a>
              {detailMeta.chunkId ? (
                <a className="ml-3 text-blue-700 hover:underline" href="/admin/review?active=chunk-review" title="打开分片审核页面，可按队列编号/分片编号筛选">
                  打开分片审核页（chunkId={detailMeta.chunkId}）
                </a>
              ) : null}
            </div>
          ) : null}
          {detailLoading && !detail ? (
            <div className="text-sm text-gray-600">加载中...</div>
          ) : detailLoadError && !detail ? (
            <div className="text-sm text-red-600">{detailLoadError}</div>
          ) : detail ? (
            <>
              {detailRefreshError ? <div className="text-sm text-amber-700">{detailRefreshError}</div> : null}
              {detailTab === 'overview' ? (
                <div className="space-y-4">
                  <div className="rounded border overflow-hidden">
                    <div className="px-3 py-2 text-sm font-semibold bg-gray-50">概要</div>
                    <div className="p-3 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-sm">
                      <div className="rounded border px-3 py-2">
                        <div className="text-xs text-gray-500">类型</div>
                        <div className="font-mono">{fmtType(detail.type)}</div>
                      </div>
                      <div className="rounded border px-3 py-2">
                        <div className="text-xs text-gray-500">状态</div>
                        <div className="font-mono">{detail.status || '—'}</div>
                      </div>
                      <div className="rounded border px-3 py-2">
                        <div className="text-xs text-gray-500">Provider</div>
                        <div className="font-mono break-all">{detail.providerId || '—'}</div>
                      </div>
                      <div className="rounded border px-3 py-2">
                        <div className="text-xs text-gray-500">Model</div>
                        <div className="font-mono break-all">{detail.model || '—'}</div>
                      </div>
                      {detail.label ? (
                        <div className="rounded border px-3 py-2 sm:col-span-2 lg:col-span-2">
                          <div className="text-xs text-gray-500">标签</div>
                          <div className="font-mono break-all">{detail.label}</div>
                        </div>
                      ) : null}
                      <div className="rounded border px-3 py-2">
                        <div className="text-xs text-gray-500">创建</div>
                        <div className="font-mono">{fmtHmsTs(detail.createdAt)}</div>
                      </div>
                      <div className="rounded border px-3 py-2">
                        <div className="text-xs text-gray-500">开始</div>
                        <div className="font-mono">{fmtHmsTs(detail.startedAt)}</div>
                      </div>
                      <div className="rounded border px-3 py-2">
                        <div className="text-xs text-gray-500">完成</div>
                        <div className="font-mono">{fmtHmsTs(detail.finishedAt)}</div>
                      </div>
                      <div className="rounded border px-3 py-2">
                        <div className="text-xs text-gray-500">等待 / 耗时</div>
                        <div className="font-mono">
                          {fmtMs(detail.waitMs)} / {fmtMs(detail.durationMs)}
                        </div>
                      </div>
                      <div className="rounded border px-3 py-2">
                        <div className="text-xs text-gray-500">Tokens(in/out/total)</div>
                        <div className="font-mono">
                          {detail.tokensIn ?? '—'} / {detail.tokensOut ?? '—'} / {detail.totalTokens ?? '—'}
                        </div>
                      </div>
                      <div className="rounded border px-3 py-2">
                        <div className="text-xs text-gray-500">Tokens/s</div>
                        <div className="font-mono">{fmtNum(detail.tokensPerSec)}</div>
                      </div>
                      <div className="rounded border px-3 py-2 sm:col-span-2 lg:col-span-2">
                        <div className="text-xs text-gray-500">错误</div>
                        <div className={`font-mono break-all ${detail.error ? 'text-red-700' : ''}`}>{detail.error || '—'}</div>
                      </div>
                    </div>
                  </div>
                </div>
              ) : null}
              {detailTab === 'input' ? (
                <div className="rounded border overflow-hidden">
                  <div className="px-3 py-2 text-sm font-semibold bg-gray-50">输入</div>
                  <pre className="p-3 text-xs font-mono whitespace-pre-wrap break-words">{detailInputText}</pre>
                </div>
              ) : null}
              {detailTab === 'output' ? (
                <div className="rounded border overflow-hidden">
                  <div className="px-3 py-2 text-sm font-semibold bg-gray-50">输出</div>
                  <pre className="p-3 text-xs font-mono whitespace-pre-wrap break-words">{detailOutputText}</pre>
                </div>
              ) : null}
            </>
          ) : (
            <div className="text-sm text-gray-600">暂无详情</div>
          )}
        </DetailDialog>
      ) : null}
    </div>

      <div className="bg-white rounded-lg shadow p-4 space-y-4">
        <LlmLoadTestPanel />
      </div>
    </div>
  );
};

export default LlmQueueForm;
