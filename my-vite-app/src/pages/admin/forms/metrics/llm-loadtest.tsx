import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { adminGetAiProvidersConfig, type AiProviderDTO } from '../../../../services/aiProvidersAdminService';
import { getAiChatOptions, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { adminGetTokenMetrics, type TokenMetricsResponseDTO } from '../../../../services/tokenMetricsAdminService';
import {
  adminGetLlmLoadTestStatus,
  adminGetLlmLoadTestExportUrl,
  adminListLlmLoadTestHistory,
  adminStartLlmLoadTest,
  adminStopLlmLoadTest,
  adminUpsertLlmLoadTestHistory,
  type AdminLlmLoadTestStatus,
} from '../../../../services/llmLoadtestAdminService';
import { ProviderModelSelect } from '../../../../components/admin/ProviderModelSelect';
 
type RequestKind = 'CHAT_STREAM' | 'MODERATION_TEST';
 
type LoadTestConfig = {
  concurrency: number;
  totalRequests: number;
  requestKind: RequestKind;
  ratioChatStream: number;
  ratioModerationTest: number;
  providerId: string;
  model: string;
  stream: boolean;
  enableThinking: boolean;
  timeoutMs: number;
  retries: number;
  retryDelayMs: number;
  chatMessage: string;
  moderationText: string;
};
 
type RequestResult = {
  index: number;
  kind: RequestKind;
  ok: boolean;
  latencyMs: number | null;
  startedAtMs: number;
  finishedAtMs: number;
  error: string | null;
  tokens: number | null;
  tokensIn: number | null;
  tokensOut: number | null;
  model: string | null;
};
 
type QueuePeak = {
  maxPending: number;
  maxRunning: number;
  maxTotal: number;
  tokensPerSecMax: number;
  tokensPerSecAvg: number;
};
 
type LoadTestSummary = {
  runId: string;
  createdAt: string;
  startedAt: string;
  finishedAt: string;
  durationMs: number;
  config: LoadTestConfig;
  success: number;
  failed: number;
  successRate: number;
  avgLatencyMs?: number | null;
  maxLatencyMs?: number | null;
  p50LatencyMs: number | null;
  p95LatencyMs: number | null;
  tokensPerSec: number | null;
  tokensTotal: number | null;
  tokensInTotal?: number | null;
  tokensOutTotal?: number | null;
  tokensOutPerSec?: number | null;
  totalCost: string | number | null;
  currency: string | null;
  priceMissing?: boolean | null;
  queuePeak: QueuePeak;
  tokenMetrics?: TokenMetricsResponseDTO | null;
};
 
function pad2(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}
 
function formatLocalDateTime(d: Date): string {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`;
}
 
function toNum(v: unknown): number | null {
  if (v === null || v === undefined) return null;
  if (typeof v === 'number') return Number.isFinite(v) ? v : null;
  if (typeof v === 'string') {
    const t = v.trim();
    if (!t) return null;
    const n = Number(t);
    return Number.isFinite(n) ? n : null;
  }
  return null;
}
 
function fmtCost(v: unknown): string {
  const n = toNum(v);
  if (n === null) return '0';
  return n.toFixed(6).replace(/\.?0+$/, '');
}
 
function fmtInt(v: unknown): string {
  const n = toNum(v);
  if (n === null) return '0';
  return String(Math.round(n));
}
 
function fmtRate(v: unknown): string {
  const n = toNum(v);
  if (n === null) return '—';
  if (Math.abs(n) >= 1000) return String(Math.round(n));
  return n.toFixed(2).replace(/\.?0+$/, '');
}

function fmtDurationMs(v: unknown): string {
  const n = toNum(v);
  if (n === null || n < 0) return '—';
  if (n < 1000) return `${Math.round(n)}ms`;
  const s = n / 1000;
  if (s < 60) return `${s.toFixed(2).replace(/\.?0+$/, '')}s`;
  const m = Math.floor(s / 60);
  const r = s - m * 60;
  return `${m}m${Math.round(r)}s`;
}

function fmtDate(v: string | number | Date): string {
  const d = new Date(v);
  if (!Number.isFinite(d.getTime())) return String(v);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${day} ${hh}:${mm}`;
}

function isThinkingOnlyModel(model: string): boolean {
  const m = String(model ?? '').trim().toLowerCase();
  if (!m) return false;
  return m.includes('-thinking') || m.includes('thinking-') || m.endsWith('thinking');
}

function isAutoBalanced(providerId: string, model: string): boolean {
  return !(String(providerId ?? '').trim() || String(model ?? '').trim());
}

export function percentile(sortedAsc: number[], p: number): number | null {
  if (!sortedAsc.length) return null;
  if (!Number.isFinite(p)) return null;
  const q = Math.max(0, Math.min(1, p));
  if (sortedAsc.length === 1) return sortedAsc[0];
  const idx = (sortedAsc.length - 1) * q;
  const lo = Math.floor(idx);
  const hi = Math.ceil(idx);
  const a = sortedAsc[lo] ?? sortedAsc[sortedAsc.length - 1];
  const b = sortedAsc[hi] ?? sortedAsc[sortedAsc.length - 1];
  if (hi === lo) return a;
  const t = idx - lo;
  return a + (b - a) * t;
}

const STORAGE_KEY = 'llmLoadTestRunsV1';

function loadStoredRuns(): LoadTestSummary[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const arr: unknown = JSON.parse(raw);
    if (!Array.isArray(arr)) return [];
    return arr
      .map((x) => (x && typeof x === 'object' ? (x as LoadTestSummary) : null))
      .filter((x): x is LoadTestSummary => x !== null)
      .slice(-50);
  } catch {
    return [];
  }
}
 
function saveStoredRuns(runs: LoadTestSummary[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(runs.slice(-50)));
  } catch {}
}
 
export function toCsv(rows: Record<string, string>[]): string {
  const keys = Array.from(
    rows.reduce((s, r) => {
      for (const k of Object.keys(r)) s.add(k);
      return s;
    }, new Set<string>())
  );
  const esc = (v: string) => {
    if (v.includes('"') || v.includes(',') || v.includes('\n') || v.includes('\r')) {
      return `"${v.replace(/"/g, '""')}"`;
    }
    return v;
  };
  const header = keys.map(esc).join(',');
  const lines = rows.map((r) => keys.map((k) => esc(String(r[k] ?? ''))).join(','));
  return [header, ...lines].join('\n');
}
 
export const LlmLoadTestPanel: React.FC = () => {
  const [providersError, setProvidersError] = useState<string | null>(null);
  const [providers, setProviders] = useState<AiProviderDTO[]>([]);
  const [activeProviderId, setActiveProviderId] = useState<string>('');
  const [chatProviders, setChatProviders] = useState<AiChatProviderOptionDTO[]>([]);
 
  const [config, setConfig] = useState<LoadTestConfig>(() => ({
    concurrency: 10,
    totalRequests: 100,
    requestKind: 'CHAT_STREAM',
    ratioChatStream: 100,
    ratioModerationTest: 0,
    providerId: '',
    model: '',
    stream: true,
    enableThinking: false,
    timeoutMs: 60_000,
    retries: 1,
    retryDelayMs: 200,
    chatMessage: '压测：请用一句话回复“ok”。',
    moderationText: '压测：这是一条中性内容，用于审核模型吞吐测试。',
  }));
 
  const [running, setRunning] = useState(false);
  const [progress, setProgress] = useState({ done: 0, total: 0 });
  const [error, setError] = useState<string | null>(null);
  const [results, setResults] = useState<RequestResult[]>([]);
  const [summary, setSummary] = useState<LoadTestSummary | null>(null);
 
  const [storedRuns, setStoredRuns] = useState<LoadTestSummary[]>(() => loadStoredRuns());
  const [isHistoryExpanded, setIsHistoryExpanded] = useState(false);
  const [compareA, setCompareA] = useState<string>('');
  const [compareB, setCompareB] = useState<string>('');
  const [historyPage, setHistoryPage] = useState(0);
  const [historyPageSize, setHistoryPageSize] = useState(5);
 
  const abortRef = useRef<AbortController | null>(null);
  const runIdRef = useRef<string | null>(null);
  const isMountedRef = useRef(true);
 
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
      try {
        abortRef.current?.abort();
      } catch {}
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const rows = await adminListLlmLoadTestHistory({ limit: 200 });
        if (cancelled) return;
        const summaries = (rows || [])
          .map((r) => (r && typeof r === 'object' && 'summary' in r ? (r as any).summary : null))
          .filter((x): x is LoadTestSummary => Boolean(x) && typeof x === 'object')
          .slice()
          .reverse();
        if (summaries.length === 0) return;

        setStoredRuns((prev) => {
          const byId = new Map<string, LoadTestSummary>();
          for (const r of [...prev, ...summaries]) {
            if (!r || !r.runId) continue;
            byId.set(String(r.runId), { ...(r as any), tokenMetrics: null });
          }
          const merged = Array.from(byId.values())
            .sort((a, b) => String(a.createdAt || '').localeCompare(String(b.createdAt || '')))
            .slice(-200);
          saveStoredRuns(merged);
          return merged;
        });
      } catch {}
    })();
    return () => {
      cancelled = true;
    };
  }, []);
 
  const enabledProviders = useMemo(() => {
    return (providers || []).filter((p) => (p?.enabled == null ? true : Boolean(p.enabled)));
  }, [providers]);

  const providerNameById = useMemo(() => {
    const out: Record<string, string> = {};
    for (const p of providers) {
      const id = String(p?.id ?? '').trim();
      if (!id) continue;
      const name = String(p?.name ?? '').trim();
      out[id] = name || id;
    }
    return out;
  }, [providers]);
 
  const loadProviders = useCallback(async () => {
    setProvidersError(null);
    try {
      const cfg = await adminGetAiProvidersConfig();
      setProviders((cfg?.providers ?? []).filter(Boolean) as AiProviderDTO[]);
      setActiveProviderId(String(cfg?.activeProviderId ?? ''));
    } catch (e) {
      setProvidersError(e instanceof Error ? e.message : String(e));
      setProviders([]);
      setActiveProviderId('');
    }
  }, []);
 
  useEffect(() => {
    loadProviders();
  }, [loadProviders]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const opts = await getAiChatOptions();
        if (cancelled) return;
        setChatProviders((opts.providers ?? []).filter(Boolean) as AiChatProviderOptionDTO[]);
      } catch {
        if (cancelled) return;
        setChatProviders([]);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);
 
  const effectiveModelForThinking = useMemo(() => {
    const explicit = (config.model || '').trim();
    if (explicit) return explicit;
    const pid = (config.providerId || '').trim();
    if (pid) {
      const p = enabledProviders.find((x) => String(x?.id ?? '') === String(pid));
      const fallback = String(p?.defaultChatModel ?? '').trim();
      if (fallback) return fallback;
    }
    return '';
  }, [config.model, config.providerId, enabledProviders]);

  const thinkingOnly = useMemo(() => isThinkingOnlyModel(effectiveModelForThinking), [effectiveModelForThinking]);

  const effectiveEnableThinking = useMemo(() => {
    return thinkingOnly ? true : Boolean(config.enableThinking);
  }, [thinkingOnly, config.enableThinking]);

  const autoBalanced = useMemo(() => isAutoBalanced(config.providerId, config.model), [config.providerId, config.model]);

  const historyRuns = useMemo(() => storedRuns.slice().reverse(), [storedRuns]);
  const historyPageCount = useMemo(() => Math.max(1, Math.ceil(historyRuns.length / Math.max(1, historyPageSize))), [historyRuns.length, historyPageSize]);
  useEffect(() => {
    setHistoryPage((p) => Math.min(Math.max(0, p), Math.max(0, historyPageCount - 1)));
  }, [historyPageCount]);
  const historyPageRuns = useMemo(() => {
    const size = Math.max(1, historyPageSize);
    const start = Math.max(0, historyPage) * size;
    return historyRuns.slice(start, start + size);
  }, [historyRuns, historyPage, historyPageSize]);

  const getRunDisplayText = useCallback(
    (runId: string) => {
      const r = storedRuns.find((x) => x.runId === runId);
      if (!r) return '未选择';
      return `${r.runId}（${r.successRate.toFixed(2).replace(/\.?0+$/, '')}% / P95 ${r.p95LatencyMs == null ? '—' : Math.round(r.p95LatencyMs)}ms）`;
    },
    [storedRuns]
  );

  const getModelText = useCallback(
    (r: LoadTestSummary | null | undefined) => {
      if (!r) return '—';
      const explicit = String(r?.config?.model ?? '').trim();
      const pid = String(r?.config?.providerId ?? '').trim();
      if (!pid) return explicit || '自动（均衡负载）';
      const providerName = providerNameById[pid] || pid;
      if (explicit) return providerName ? `${providerName}：${explicit}` : explicit;
      const p = providers.find((x) => String(x?.id ?? '') === String(pid));
      const fallback = String(p?.defaultChatModel ?? '').trim();
      if (fallback) return providerName ? `${providerName}：${fallback}（默认）` : `${fallback}（默认）`;
      return providerName || '—';
    },
    [providerNameById, providers]
  );

  const exportCurrentJson = useCallback(() => {
    if (!summary) return;
    const url = adminGetLlmLoadTestExportUrl(summary.runId, 'json');
    const a = document.createElement('a');
    a.href = url;
    a.rel = 'noopener';
    a.target = '_blank';
    document.body.appendChild(a);
    a.click();
    a.remove();
  }, [summary]);
 
  const exportCurrentCsv = useCallback(() => {
    if (!summary) return;
    const url = adminGetLlmLoadTestExportUrl(summary.runId, 'csv');
    const a = document.createElement('a');
    a.href = url;
    a.rel = 'noopener';
    a.target = '_blank';
    document.body.appendChild(a);
    a.click();
    a.remove();
  }, [summary]);
 
  const clearCurrent = useCallback(() => {
    setCompareA('');
    setCompareB('');
    setSummary(null);
  }, []);

  const stop = useCallback(() => {
    const runId = runIdRef.current;
    try {
      abortRef.current?.abort();
    } catch {}
    abortRef.current = null;
    runIdRef.current = null;
    if (runId) void adminStopLlmLoadTest(runId).catch(() => {});
  }, []);
 
  const run = useCallback(async () => {
    if (running) return;
 
    const concurrency = Math.max(1, Math.min(500, Math.floor(config.concurrency || 1)));
    const totalRequests = Math.max(1, Math.min(200_000, Math.floor(config.totalRequests || 1)));
    const timeoutMs = Math.max(1000, Math.min(10 * 60_000, Math.floor(config.timeoutMs || 60_000)));
    const retries = Math.max(0, Math.min(10, Math.floor(config.retries || 0)));
    const retryDelayMs = Math.max(0, Math.min(60_000, Math.floor(config.retryDelayMs || 0)));
 
    const explicitModel = (config.model || '').trim();
    const explicitProviderId = (config.providerId || '').trim();
    const autoBalanced = isAutoBalanced(explicitProviderId, explicitModel);
    const resolvedProviderId = autoBalanced ? '' : explicitProviderId;
    const resolvedModel = autoBalanced ? '' : explicitModel;
 
    const chatMessage = (config.chatMessage || '').trim() || '压测：请回复 ok';
    const moderationText = (config.moderationText || '').trim() || '压测：中性内容';
 
    const requestKind: RequestKind =
      config.requestKind || (Math.floor(config.ratioChatStream || 0) >= Math.floor(config.ratioModerationTest || 0) ? 'CHAT_STREAM' : 'MODERATION_TEST');
    const ratioChatStream = requestKind === 'CHAT_STREAM' ? 100 : 0;
    const ratioModerationTest = requestKind === 'MODERATION_TEST' ? 100 : 0;
    const effectiveStream = requestKind === 'CHAT_STREAM' ? true : Boolean(config.stream);
 
    setRunning(true);
    setError(null);
    setResults([]);
    setSummary(null);
    setProgress({ done: 0, total: totalRequests });
 
    const abort = new AbortController();
    abortRef.current = abort;
    runIdRef.current = null;
 
    let lastStatus: AdminLlmLoadTestStatus | null = null;
    const delay = (ms: number) =>
      new Promise<void>((resolve, reject) => {
        const id = window.setTimeout(() => resolve(), ms);
        const onAbort = () => {
          window.clearTimeout(id);
          reject(new Error('已停止'));
        };
        abort.signal.addEventListener('abort', onAbort, { once: true });
      });

    try {
      const started = await adminStartLlmLoadTest({
        concurrency,
        totalRequests,
        ratioChatStream,
        ratioModerationTest,
        providerId: resolvedProviderId,
        model: resolvedModel,
        stream: effectiveStream,
        enableThinking: effectiveEnableThinking,
        timeoutMs,
        retries,
        retryDelayMs,
        chatMessage,
        moderationText,
      });
      runIdRef.current = started.runId;
    } catch (e) {
      if (!isMountedRef.current) return;
      setError(e instanceof Error ? e.message : String(e));
      setRunning(false);
      abortRef.current = null;
      runIdRef.current = null;
      return;
    }

    const runId = runIdRef.current;
    if (!runId) {
      setError('启动压测失败');
      setRunning(false);
      abortRef.current = null;
      return;
    }

    try {
      while (!abort.signal.aborted) {
        try {
          lastStatus = await adminGetLlmLoadTestStatus(runId, { signal: abort.signal, timeoutMs: 10_000 });
          if (!isMountedRef.current) return;

          setProgress({ done: Number(lastStatus.done || 0), total: Number(lastStatus.total || totalRequests) });
          const rs = (lastStatus.recentResults ?? []).map((r) => ({
            index: Number(r.index || 0),
            kind: (r.kind as RequestKind) || 'MODERATION_TEST',
            ok: Boolean(r.ok),
            latencyMs: typeof r.latencyMs === 'number' ? r.latencyMs : null,
            startedAtMs: Number(r.startedAtMs || 0),
            finishedAtMs: Number(r.finishedAtMs || 0),
            error: (r.error as string | null) ?? null,
            tokensIn: typeof r.tokensIn === 'number' ? r.tokensIn : null,
            tokensOut: typeof r.tokensOut === 'number' ? r.tokensOut : null,
            tokens:
              typeof r.tokens === 'number'
                ? r.tokens
                : typeof r.tokensIn === 'number' && typeof r.tokensOut === 'number'
                ? r.tokensIn + r.tokensOut
                : null,
            model: (r.model as string | null) ?? null,
          }));
          setResults(rs);

          if (!lastStatus.running) break;
        } catch {
        }
        await delay(400);
      }
    } catch {
    }

    if (abort.signal.aborted) {
      try {
        await adminStopLlmLoadTest(runId);
      } catch {}
      if (!isMountedRef.current) return;
      setRunning(false);
      abortRef.current = null;
      runIdRef.current = null;
      return;
    }
    if (!lastStatus) {
      setError('获取压测状态失败');
      setRunning(false);
      abortRef.current = null;
      runIdRef.current = null;
      return;
    }

    const startedAtMs = Number(lastStatus.startedAtMs || Date.now());
    const finishedAtMs = Number(lastStatus.finishedAtMs || Date.now());
    const startedAt = new Date(startedAtMs);
    const finishedAt = new Date(finishedAtMs);
    const queuePeak: QueuePeak = {
      maxPending: Number(lastStatus.queuePeak?.maxPending || 0),
      maxRunning: Number(lastStatus.queuePeak?.maxRunning || 0),
      maxTotal: Number(lastStatus.queuePeak?.maxTotal || 0),
      tokensPerSecMax: Number(lastStatus.queuePeak?.tokensPerSecMax || 0),
      tokensPerSecAvg: Number(lastStatus.queuePeak?.tokensPerSecAvg || 0),
    };
    const success = Number(lastStatus.success || 0);
    const failed = Number(lastStatus.failed || 0);
 
    const source = (() => {
      const hasChat = ratioChatStream > 0 && effectiveStream;
      const hasMod = ratioModerationTest > 0;
      if (hasChat && hasMod) return 'ALL';
      if (hasChat) return 'CHAT';
      if (hasMod) return 'MODERATION';
      return 'ALL';
    })();
 
    let tokenMetrics: TokenMetricsResponseDTO | null = null;
    try {
      tokenMetrics = await adminGetTokenMetrics({
        start: formatLocalDateTime(startedAt),
        end: formatLocalDateTime(finishedAt),
        source,
        pricingMode: effectiveEnableThinking ? 'THINKING' : 'NON_THINKING',
      });
    } catch {}
 
    const durationSec = Math.max(1e-6, (finishedAtMs - startedAtMs) / 1000);

    const derivedTokens = (() => {
      const items = lastStatus?.recentResults ?? [];
      let tokensTotalSum = 0;
      let tokensInSum = 0;
      let tokensOutSum = 0;
      let hasTotal = false;
      let hasIn = false;
      let hasOut = false;
      for (const it of items) {
        if (it && typeof it.tokensIn === 'number' && Number.isFinite(it.tokensIn)) {
          tokensInSum += it.tokensIn;
          hasIn = true;
        }
        if (it && typeof it.tokensOut === 'number' && Number.isFinite(it.tokensOut)) {
          tokensOutSum += it.tokensOut;
          hasOut = true;
        }
        if (it && typeof it.tokens === 'number' && Number.isFinite(it.tokens)) {
          tokensTotalSum += it.tokens;
          hasTotal = true;
        } else if (
          it &&
          typeof it.tokensIn === 'number' &&
          Number.isFinite(it.tokensIn) &&
          typeof it.tokensOut === 'number' &&
          Number.isFinite(it.tokensOut)
        ) {
          tokensTotalSum += it.tokensIn + it.tokensOut;
          hasTotal = true;
        }
      }
      return {
        tokensTotal: hasTotal ? tokensTotalSum : null,
        tokensInTotal: hasIn ? tokensInSum : null,
        tokensOutTotal: hasOut ? tokensOutSum : null,
      };
    })();

    const tokensInTotal =
      typeof lastStatus.tokensInTotal === 'number' ? lastStatus.tokensInTotal : derivedTokens.tokensInTotal;
    const tokensOutTotal =
      typeof lastStatus.tokensOutTotal === 'number' ? lastStatus.tokensOutTotal : derivedTokens.tokensOutTotal;
    const tokensTotal = (() => {
      const stTotal = typeof lastStatus.tokensTotal === 'number' ? lastStatus.tokensTotal : null;
      if (tokensInTotal != null && tokensOutTotal != null) {
        const sum = tokensInTotal + tokensOutTotal;
        if (stTotal == null) return sum;
        if (!Number.isFinite(stTotal)) return sum;
        return Math.round(stTotal) !== Math.round(sum) ? sum : stTotal;
      }
      return stTotal != null ? stTotal : derivedTokens.tokensTotal;
    })();

    const tokensPerSec = tokensTotal == null ? null : Math.round((tokensTotal / durationSec) * 100) / 100;
    const tokensOutPerSec = tokensOutTotal == null ? null : Math.round((tokensOutTotal / durationSec) * 100) / 100;
    const queuePeakEffective: QueuePeak = (() => {
      const fallback = tokensOutPerSec != null && tokensOutPerSec > 0 ? tokensOutPerSec : 0;
      return {
        ...queuePeak,
        tokensPerSecMax: Math.max(queuePeak.tokensPerSecMax || 0, fallback),
        tokensPerSecAvg: (queuePeak.tokensPerSecAvg || 0) > 0 ? queuePeak.tokensPerSecAvg : fallback,
      };
    })();
 
    const s: LoadTestSummary = {
      runId,
      createdAt: new Date().toISOString(),
      startedAt: startedAt.toISOString(),
      finishedAt: finishedAt.toISOString(),
      durationMs: finishedAtMs - startedAtMs,
      config: {
        ...config,
        enableThinking: effectiveEnableThinking,
        concurrency,
        totalRequests,
        ratioChatStream,
        ratioModerationTest,
        model: resolvedModel,
        providerId: resolvedProviderId,
        stream: effectiveStream,
        timeoutMs,
        retries,
        retryDelayMs,
        chatMessage,
        moderationText,
      },
      success,
      failed,
      successRate: Math.round((success / Math.max(1, success + failed)) * 10000) / 100,
      avgLatencyMs: lastStatus.avgLatencyMs == null ? null : Math.round(lastStatus.avgLatencyMs * 100) / 100,
      maxLatencyMs: lastStatus.maxLatencyMs == null ? null : Math.round(lastStatus.maxLatencyMs * 100) / 100,
      p50LatencyMs: lastStatus.p50LatencyMs == null ? null : Math.round(lastStatus.p50LatencyMs * 100) / 100,
      p95LatencyMs: lastStatus.p95LatencyMs == null ? null : Math.round(lastStatus.p95LatencyMs * 100) / 100,
      tokensPerSec,
      tokensTotal: tokensTotal == null ? null : Math.round(tokensTotal),
      tokensInTotal: tokensInTotal == null ? null : Math.round(tokensInTotal),
      tokensOutTotal: tokensOutTotal == null ? null : Math.round(tokensOutTotal),
      tokensOutPerSec,
      totalCost: lastStatus.totalCost ?? tokenMetrics?.totalCost ?? null,
      currency: lastStatus.currency ?? tokenMetrics?.currency ?? null,
      priceMissing: lastStatus.priceMissing ?? null,
      queuePeak: queuePeakEffective,
      tokenMetrics,
    };
 
    if (!isMountedRef.current) return;
    setSummary(s);
    setRunning(false);
    abortRef.current = null;
    runIdRef.current = null;
 
    const persistSummary = { ...s, tokenMetrics: null };
    void adminUpsertLlmLoadTestHistory({ runId: persistSummary.runId, summary: persistSummary }).catch(() => {});

    setStoredRuns((prev) => {
      const next = [...prev, persistSummary].slice(-200);
      saveStoredRuns(next);
      return next;
    });
  }, [running, config, effectiveEnableThinking]);
 
  const runA = useMemo(() => storedRuns.find((r) => r.runId === compareA), [storedRuns, compareA]);
  const runB = useMemo(() => storedRuns.find((r) => r.runId === compareB), [storedRuns, compareB]);
  const displaySummary = summary || runA || runB;

  const keyMetrics = useMemo(() => {
    if (!displaySummary) return null;

    const getDiffInfo = (valA: unknown, valB: unknown, better: 'higher' | 'lower', unit: string = '') => {
      if (!runA || !runB) return null;
      const va = toNum(valA);
      const vb = toNum(valB);
      if (va === null || vb === null) return null;
      const diff = vb - va;
      const pct = va === 0 ? null : (diff / va) * 100;
      let color = 'text-gray-500';
      if (diff !== 0) {
        const isBetter = better === 'higher' ? diff > 0 : diff < 0;
        color = isBetter ? 'text-green-600' : 'text-red-600';
      }
      return { diff, pct, color, unit };
    };

    return [
      { k: '成功率', v: `${displaySummary.successRate.toFixed(2).replace(/\.?0+$/, '')}%`, ...getDiffInfo(runA?.successRate, runB?.successRate, 'higher', '%') },
      { k: '总耗时', v: fmtDurationMs(Math.max(0, displaySummary.durationMs || 0)), ...getDiffInfo(runA?.durationMs, runB?.durationMs, 'lower', 'ms') },
      { k: '最高延迟', v: displaySummary.maxLatencyMs == null ? '—' : `${Math.round(displaySummary.maxLatencyMs)}ms`, ...getDiffInfo(runA?.maxLatencyMs, runB?.maxLatencyMs, 'lower', 'ms') },
      { k: '平均延迟', v: displaySummary.avgLatencyMs == null ? '—' : `${Math.round(displaySummary.avgLatencyMs)}ms`, ...getDiffInfo(runA?.avgLatencyMs, runB?.avgLatencyMs, 'lower', 'ms') },
      { k: 'P50 延迟', v: displaySummary.p50LatencyMs == null ? '—' : `${Math.round(displaySummary.p50LatencyMs)}ms`, ...getDiffInfo(runA?.p50LatencyMs, runB?.p50LatencyMs, 'lower', 'ms') },
      { k: 'P95 延迟', v: displaySummary.p95LatencyMs == null ? '—' : `${Math.round(displaySummary.p95LatencyMs)}ms`, ...getDiffInfo(runA?.p95LatencyMs, runB?.p95LatencyMs, 'lower', 'ms') },
      { k: '输出 tokens/sec 峰值', v: fmtRate(displaySummary.queuePeak.tokensPerSecMax), ...getDiffInfo(runA?.queuePeak.tokensPerSecMax, runB?.queuePeak.tokensPerSecMax, 'higher') },
      { k: '输出 tokens/sec 平均', v: fmtRate(displaySummary.queuePeak.tokensPerSecAvg), ...getDiffInfo(runA?.queuePeak.tokensPerSecAvg, runB?.queuePeak.tokensPerSecAvg, 'higher') },
      { k: '输入总 Token', v: displaySummary.tokensInTotal == null ? '—' : fmtInt(displaySummary.tokensInTotal), ...getDiffInfo(runA?.tokensInTotal, runB?.tokensInTotal, 'higher') },
      { k: '总 Token', v: displaySummary.tokensTotal == null ? '—' : fmtInt(displaySummary.tokensTotal), ...getDiffInfo(runA?.tokensTotal, runB?.tokensTotal, 'higher') },
      { k: '输出总 Token', v: displaySummary.tokensOutTotal == null ? '—' : fmtInt(displaySummary.tokensOutTotal), ...getDiffInfo(runA?.tokensOutTotal, runB?.tokensOutTotal, 'higher') },
      { k: '总费用', v: displaySummary.totalCost == null ? '—' : fmtCost(displaySummary.totalCost), ...getDiffInfo(runA?.totalCost, runB?.totalCost, 'lower') },
      { k: '队列峰值', v: String(displaySummary.queuePeak.maxTotal), ...getDiffInfo(runA?.queuePeak.maxTotal, runB?.queuePeak.maxTotal, 'lower') },
    ];
  }, [displaySummary, runA, runB]);
 
  const recentErrors = useMemo(() => {
    const arr = results.filter((r) => !r.ok && r.error).slice(-20).reverse();
    return arr;
  }, [results]);

  const rateLimit = useMemo(() => {
    let count = 0;
    for (const r of recentErrors) {
      const e = String(r?.error ?? '').toLowerCase();
      if (e.includes('http 429') || e.includes('limit_requests')) count++;
    }
    return { count, total: recentErrors.length };
  }, [recentErrors]);
 
  const selectClass = 'mt-1 w-full min-w-[100px] rounded border px-2 py-2 text-sm';

  return (
    <div className="rounded border p-4 space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="text-sm font-semibold">LLM 高并发脚本测试</div>
        </div>
      </div>
 
      {providersError && <div className="rounded border border-red-300 bg-red-50 text-red-700 px-3 py-2 text-sm">{providersError}</div>}
      {error && <div className="rounded border border-red-300 bg-red-50 text-red-700 px-3 py-2 text-sm">{error}</div>}
      {rateLimit.count > 0 ? (
        <div className="rounded border border-amber-300 bg-amber-50 text-amber-900 px-3 py-2 text-sm">
          最近失败记录中有 {rateLimit.count}/{rateLimit.total} 条为上游限流（HTTP 429 / limit_requests）。这通常不是本系统队列异常，而是上游配额/并发限制触发；
          建议降低并发数、增加重试间隔（ms），或更换/升级上游配额后再做高并发压测。
        </div>
      ) : null}
 
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
        <div className="rounded border p-3 space-y-3">
          <div className="text-sm font-semibold">压测配置</div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
            <label className="text-xs text-gray-700">
              并发数
              <input
                type="number"
                className={selectClass}
                value={config.concurrency}
                min={1}
                max={500}
                onChange={(e) => setConfig((c) => ({ ...c, concurrency: Number(e.target.value) }))}
                disabled={running}
              />
            </label>
            <label className="text-xs text-gray-700">
              总请求数
              <input
                type="number"
                className={selectClass}
                value={config.totalRequests}
                min={1}
                max={200000}
                onChange={(e) => setConfig((c) => ({ ...c, totalRequests: Number(e.target.value) }))}
                disabled={running}
              />
            </label>
            <label className="text-xs text-gray-700">
              流式输出
              <select
                className={selectClass}
                value={config.requestKind === 'CHAT_STREAM' ? '1' : config.stream ? '1' : '0'}
                onChange={(e) =>
                  setConfig((c) => {
                    const nextStream = e.target.value === '1';
                    if (!nextStream && c.requestKind === 'CHAT_STREAM') {
                      return { ...c, stream: nextStream, requestKind: 'MODERATION_TEST', ratioChatStream: 0, ratioModerationTest: 100 };
                    }
                    return { ...c, stream: nextStream };
                  })
                }
                disabled={running || config.requestKind === 'CHAT_STREAM'}
              >
                <option value="1">是</option>
                <option value="0">否</option>
              </select>
            </label>
            <label className="text-xs text-gray-700">
              启用深度思考{thinkingOnly ? <span className="text-[11px] text-gray-500"></span> : null}
              <select
                className={selectClass}
                value={effectiveEnableThinking ? '1' : '0'}
                onChange={(e) => setConfig((c) => ({ ...c, enableThinking: e.target.value === '1' }))}
                disabled={running || thinkingOnly}
              >
                <option value="1">是</option>
                <option value="0">否</option>
              </select>
            </label>
            <label className="text-xs text-gray-700">
              超时（ms）
              <input
                type="number"
                className={selectClass}
                value={config.timeoutMs}
                min={1000}
                max={600000}
                onChange={(e) => setConfig((c) => ({ ...c, timeoutMs: Number(e.target.value) }))}
                disabled={running}
              />
            </label>
            <label className="text-xs text-gray-700">
              重试次数
              <input
                type="number"
                className={selectClass}
                value={config.retries}
                min={0}
                max={10}
                onChange={(e) => setConfig((c) => ({ ...c, retries: Number(e.target.value) }))}
                disabled={running}
              />
            </label>
            <label className="text-xs text-gray-700">
              重试间隔（ms）
              <input
                type="number"
                className={selectClass}
                value={config.retryDelayMs}
                min={0}
                max={60000}
                onChange={(e) => setConfig((c) => ({ ...c, retryDelayMs: Number(e.target.value) }))}
                disabled={running}
              />
            </label>
            <div className="col-span-2 md:col-span-2">
              <ProviderModelSelect
                providers={providers}
                activeProviderId={activeProviderId}
                chatProviders={chatProviders}
                mode="chat"
                providerId={config.providerId}
                model={config.model}
                disabled={running}
                selectClassName={selectClass}
                onChange={(next) => setConfig((c) => ({ ...c, providerId: next.providerId, model: next.model }))}
              />
              {autoBalanced ? (
                <div className="mt-1 text-[11px] text-gray-600">
                  当前为自动（均衡负载）：按请求类型走路由场景（聊天流式→CHAT，内容审核→MODERATION）
                </div>
              ) : null}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <label className="text-xs text-gray-700 col-span-2">
              请求类型
              <select
                className={selectClass}
                value={config.requestKind}
                onChange={(e) => {
                  const next = (e.target.value as RequestKind) || 'CHAT_STREAM';
                  setConfig((c) => {
                    if (next === 'CHAT_STREAM') {
                      return { ...c, requestKind: next, ratioChatStream: 100, ratioModerationTest: 0, stream: true };
                    }
                    return { ...c, requestKind: next, ratioChatStream: 0, ratioModerationTest: 100 };
                  });
                }}
                disabled={running}
              >
                <option value="CHAT_STREAM">聊天流式</option>
                <option value="MODERATION_TEST">内容审核</option>
              </select>
            </label>
          </div>
          <div className="grid grid-cols-1 gap-2">
            <label className="text-xs text-gray-700">
              聊天请求内容（模板）
              <textarea
                className="mt-1 w-full rounded border px-2 py-2 text-sm"
                rows={3}
                value={config.chatMessage}
                onChange={(e) => setConfig((c) => ({ ...c, chatMessage: e.target.value }))}
                disabled={running}
              />
            </label>
            <label className="text-xs text-gray-700">
              审核请求内容（模板）
              <textarea
                className="mt-1 w-full rounded border px-2 py-2 text-sm"
                rows={3}
                value={config.moderationText}
                onChange={(e) => setConfig((c) => ({ ...c, moderationText: e.target.value }))}
                disabled={running}
              />
            </label>
          </div>
 
          <div className="flex items-center gap-2">
            <button type="button" className="rounded bg-blue-600 text-white px-3 py-2 text-sm disabled:opacity-50" onClick={run} disabled={running}>
              开始压测
            </button>
            <button type="button" className="rounded border px-3 py-2 text-sm disabled:opacity-50" onClick={stop} disabled={!running}>
              停止
            </button>
            <div className="text-xs text-gray-600">
              进度：{fmtInt(progress.done)}/{fmtInt(progress.total)}
            </div>
          </div>
        </div>
 
        <div className="rounded border p-3 space-y-3">
          <div className="flex items-center justify-between">
            <div className="text-sm font-semibold">统计与导出</div>
            {displaySummary && (
              <div className="text-[11px] text-gray-500 font-mono">
                Run: {displaySummary.runId} | Model: {getModelText(displaySummary)}
              </div>
            )}
          </div>
          {!displaySummary ? (
            <div className="text-sm text-gray-500">运行结束后展示关键指标与导出</div>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-2">
                {keyMetrics?.map((x) => (
                  <div key={x.k} className="rounded border p-2 flex flex-col justify-between">
                    <div className="text-xs text-gray-600">{x.k}</div>
                    <div className="flex items-baseline flex-wrap gap-x-2">
                      <div className="text-lg font-semibold">{x.v}</div>
                      {x.diff !== undefined && x.diff !== null && (
                        <div className={`text-sm font-bold ${x.color}`}>
                          {x.diff > 0 ? `+${fmtRate(x.diff)}` : fmtRate(x.diff)}
                          {x.unit}
                          {x.pct != null && ` (${x.pct > 0 ? '+' : ''}${x.pct.toFixed(1)}%)`}
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              <div className="rounded border bg-gray-50 p-3 space-y-2">
                <div className="text-xs font-semibold text-gray-700">配置信息</div>
                <div className="grid grid-cols-2 gap-2">
                  {[
                    { k: '超时（ms）', v: String(displaySummary.config.timeoutMs ?? 0) },
                    { k: '启用深度思考', v: displaySummary.config.enableThinking ? '是' : '否' },
                    { k: '流式输出', v: displaySummary.config.stream ? '是' : '否' },
                    { k: '重试次数', v: String(displaySummary.config.retries ?? 0) },
                    { k: '重试间隔（ms）', v: String(displaySummary.config.retryDelayMs ?? 0) },
                  ].map((x) => (
                    <div key={x.k} className="rounded border bg-white p-2 flex flex-col justify-between">
                      <div className="text-xs text-gray-600">{x.k}</div>
                      <div className="text-lg font-semibold">{x.v}</div>
                    </div>
                  ))}
                </div>
              </div>

              {displaySummary.priceMissing ? (
                <div className="rounded border border-amber-300 bg-amber-50 text-amber-900 px-3 py-2 text-xs">
                  检测到部分模型未配置 {displaySummary.config.enableThinking ? '思考' : '非思考'} 计费规则，费用可能偏低；请到「Token 成本统计」页面配置对应模型价格。
                </div>
              ) : null}

              <div className="flex flex-wrap items-center gap-2">
                <button type="button" className="rounded border px-3 py-2 text-sm" onClick={exportCurrentCsv} disabled={!summary}>
                  导出本次 CSV
                </button>
                <button type="button" className="rounded border px-3 py-2 text-sm" onClick={exportCurrentJson} disabled={!summary}>
                  导出本次 JSON
                </button>
                <button type="button" className="rounded border px-3 py-2 text-sm text-red-600 hover:bg-red-50" onClick={clearCurrent} disabled={!summary && !compareA && !compareB}>
                  清除
                </button>
              </div>
            </>
          )}
        </div>
      </div>

      <div className="rounded border p-3 space-y-3">
        <div
          className="flex items-center justify-between gap-2 cursor-pointer select-none"
          onClick={() => setIsHistoryExpanded(!isHistoryExpanded)}
        >
          <div className="flex items-center gap-2">
            <div className="text-sm font-semibold">回归对比（数据库保存）</div>
            {isHistoryExpanded ? <ChevronUp className="w-4 h-4 text-gray-400" /> : <ChevronDown className="w-4 h-4 text-gray-400" />}
          </div>
          <div className="text-xs text-gray-600">最多展示 200 条</div>
        </div>

        {isHistoryExpanded && (
          <>
            {storedRuns.length === 0 ? (
              <div className="text-sm text-gray-500">暂无历史记录</div>
            ) : (
              <>
                <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-gray-600">
                  <div className="flex items-center gap-4">
                    <div>共 {storedRuns.length} 条</div>
                    <div className="flex items-center gap-2">
                      <span className="whitespace-nowrap">对比 A:</span>
                      <input
                          type="text"
                          readOnly
                          className="rounded border px-2 py-1 bg-gray-50 w-[450px] truncate"
                          value={getRunDisplayText(compareA)}
                          title={getRunDisplayText(compareA)}
                        />
                        {compareA && (
                          <button type="button" className="text-red-600 hover:underline" onClick={() => setCompareA('')}>
                            清除
                          </button>
                        )}
                      </div>
                      <div className="flex items-center gap-2">
                        <span className="whitespace-nowrap">对比 B:</span>
                        <input
                          type="text"
                          readOnly
                          className="rounded border px-2 py-1 bg-gray-50 w-[450px] truncate"
                          value={getRunDisplayText(compareB)}
                          title={getRunDisplayText(compareB)}
                        />
                      {compareB && (
                        <button type="button" className="text-red-600 hover:underline" onClick={() => setCompareB('')}>
                          清除
                        </button>
                      )}
                    </div>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <div className="flex items-center gap-1">
                      <span>每页</span>
                      <select
                        className="rounded border px-2 py-1"
                        value={String(historyPageSize)}
                        onChange={(e) => {
                          const n = Math.max(1, Number(e.target.value) || 5);
                          setHistoryPageSize(n);
                          setHistoryPage(0);
                        }}
                      >
                        {[5, 15, 50, 200].map((n) => (
                          <option key={n} value={String(n)}>
                            {n}
                          </option>
                        ))}
                      </select>
                    </div>
                    <button
                      type="button"
                      className="rounded border px-2 py-1 disabled:opacity-50"
                      onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
                      disabled={historyPage <= 0}
                    >
                      上一页
                    </button>
                    <button
                      type="button"
                      className="rounded border px-2 py-1 disabled:opacity-50"
                      onClick={() => setHistoryPage((p) => Math.min(historyPageCount - 1, p + 1))}
                      disabled={historyPage >= historyPageCount - 1}
                    >
                      下一页
                    </button>
                    <div>
                      第 {historyPage + 1}/{historyPageCount} 页
                    </div>
                  </div>
                </div>
                <div className="overflow-x-auto">
                  <table className="min-w-full text-sm">
                    <thead className="bg-gray-50 text-gray-700">
                      <tr className="text-left">
                        <th className="px-3 py-2 text-center">A</th>
                        <th className="px-3 py-2 text-center">B</th>
                        <th className="px-3 py-2">日期</th>
                        <th className="px-3 py-2">runId</th>
                        <th className="px-3 py-2">模型</th>
                        <th className="px-3 py-2">并发/总量</th>
                        <th className="px-3 py-2 text-right">成功率</th>
                        <th className="px-3 py-2 text-right">平均(ms)</th>
                        <th className="px-3 py-2 text-right">P50(ms)</th>
                        <th className="px-3 py-2 text-right">P95(ms)</th>
                        <th className="px-3 py-2 text-right">输出 tokens/sec(均值)</th>
                        <th className="px-3 py-2 text-right">总费用</th>
                        <th className="px-3 py-2 text-center">操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {historyPageRuns.map((r) => (
                        <tr key={r.runId} className={`border-t hover:bg-gray-50 transition-colors ${compareA === r.runId || compareB === r.runId ? 'bg-blue-50/50' : ''}`}>
                          <td className="px-3 py-2 text-center">
                            <input
                              type="radio"
                              name="compareA"
                              checked={compareA === r.runId}
                              onChange={() => setCompareA(r.runId)}
                              className="cursor-pointer"
                            />
                          </td>
                          <td className="px-3 py-2 text-center">
                            <input
                              type="radio"
                              name="compareB"
                              checked={compareB === r.runId}
                              onChange={() => setCompareB(r.runId)}
                              className="cursor-pointer"
                            />
                          </td>
                          <td className="px-3 py-2 text-xs text-gray-600 whitespace-nowrap">{fmtDate(r.createdAt)}</td>
                          <td className="px-3 py-2 font-mono text-xs" title={r.runId}>
                            {r.runId}
                          </td>
                          <td className="px-3 py-2 text-xs text-gray-600 truncate max-w-[180px]" title={getModelText(r)}>
                            {getModelText(r)}
                          </td>
                          <td className="px-3 py-2 text-xs text-gray-600">
                            {r.config.concurrency}/{r.config.totalRequests}
                          </td>
                          <td className="px-3 py-2 text-right">{r.successRate.toFixed(2).replace(/\.?0+$/, '')}%</td>
                          <td className="px-3 py-2 text-right">{r.avgLatencyMs == null ? '—' : Math.round(r.avgLatencyMs)}</td>
                          <td className="px-3 py-2 text-right">{r.p50LatencyMs == null ? '—' : Math.round(r.p50LatencyMs)}</td>
                          <td className="px-3 py-2 text-right">{r.p95LatencyMs == null ? '—' : Math.round(r.p95LatencyMs)}</td>
                          <td
                            className="px-3 py-2 text-right"
                            title={`输出 tokens/sec(均值): ${fmtRate((r as any)?.queuePeak?.tokensPerSecAvg ?? r.tokensOutPerSec)}；总 tokens/sec: ${fmtRate(r.tokensPerSec)}`}
                          >
                            {fmtRate((r as any)?.queuePeak?.tokensPerSecAvg ?? r.tokensOutPerSec ?? r.tokensPerSec)}
                          </td>
                          <td className="px-3 py-2 text-right font-mono text-xs">{r.totalCost == null ? '—' : fmtCost(r.totalCost)}</td>
                          <td className="px-3 py-2 text-center whitespace-nowrap">
                            <button
                              type="button"
                              className="text-blue-600 hover:underline text-xs"
                              onClick={() => {
                                setSummary(r);
                                window.scrollTo({ top: 0, behavior: 'smooth' });
                              }}
                            >
                              详情
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </>
        )}
      </div>
 
      {recentErrors.length > 0 && (
        <div className="rounded border p-3 space-y-2">
          <div className="text-sm font-semibold">最近错误样本</div>
          <div className="space-y-1">
            {recentErrors.map((r) => (
              <div key={`${r.kind}-${r.index}-${r.startedAtMs}`} className="text-xs font-mono text-red-700">
                #{r.index + 1} {r.kind} {r.error}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
 
export default LlmLoadTestPanel;
