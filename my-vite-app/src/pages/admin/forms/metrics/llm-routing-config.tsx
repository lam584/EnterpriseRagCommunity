import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { adminGetAiProvidersConfig, adminListProviderModels, type AiProviderDTO } from '../../../../services/aiProvidersAdminService';
import { getAiChatOptions, type AiChatProviderOptionDTO } from '../../../../services/aiChatOptionsService';
import { adminProbeModel, type AdminAiModelProbeKind, type AdminAiModelProbeResultDTO } from '../../../../services/aiModelProbeAdminService';
import { AvailableModelsCard, AvailableModelsModals } from './AvailableModelsCard';
import { ScenarioPolicyPoolCard } from './ScenarioPolicyPoolCard';
import {
  adminGetLlmRoutingConfig,
  adminUpdateLlmRoutingConfig,
  type AdminLlmRoutingConfigDTO,
  type AdminLlmRoutingPolicyDTO,
  type AdminLlmRoutingTargetDTO,
} from '../../../../services/llmRoutingAdminService';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

function normTaskType(s: string | null | undefined): string {
  return String(s || '').trim().toUpperCase();
}

function normNonBlank(s: string | null | undefined): string | null {
  const t = String(s ?? '').trim();
  return t ? t : null;
}

function clampInt(v: unknown, min: number, max: number, def: number): number {
  if (typeof v === 'number' && Number.isFinite(v)) {
    const x = Math.trunc(v);
    return Math.max(min, Math.min(max, x));
  }
  if (typeof v === 'string') {
    const t = v.trim();
    if (t) {
      const n = Number(t);
      if (Number.isFinite(n)) {
        const x = Math.trunc(n);
        return Math.max(min, Math.min(max, x));
      }
    }
  }
  return def;
}

function clampFloat(v: unknown, min: number, max: number, def: number): number {
  if (typeof v === 'number' && Number.isFinite(v)) {
    return Math.max(min, Math.min(max, v));
  }
  if (typeof v === 'string') {
    const t = v.trim();
    if (t) {
      const n = Number(t);
      if (Number.isFinite(n)) {
        return Math.max(min, Math.min(max, n));
      }
    }
  }
  return def;
}

const DEFAULT_NEW_TARGET_WEIGHT = 100;
const DEFAULT_NEW_TARGET_PRIORITY = 1;

function computeDefaultTargetWeight(targets: AdminLlmRoutingTargetDTO[], taskType: string): number {
  const tt = normTaskType(taskType);
  const weights: number[] = [];
  for (const t of targets ?? []) {
    if (normTaskType(t?.taskType) !== tt) continue;
    const w = typeof t?.weight === 'number' && Number.isFinite(t.weight) ? Math.trunc(t.weight) : null;
    if (w != null && w > 0) weights.push(w);
  }
  if (!weights.length) return DEFAULT_NEW_TARGET_WEIGHT;
  const avg = Math.round(weights.reduce((a, b) => a + b, 0) / weights.length);
  return clampInt(avg, 1, 10000, DEFAULT_NEW_TARGET_WEIGHT);
}

function computeDefaultTargetPriority(targets: AdminLlmRoutingTargetDTO[], taskType: string): number {
  const tt = normTaskType(taskType);
  let minPriority: number | null = null;
  for (const t of targets ?? []) {
    if (normTaskType(t?.taskType) !== tt) continue;
    const p = typeof t?.priority === 'number' && Number.isFinite(t.priority) ? Math.trunc(t.priority) : null;
    if (p == null) continue;
    if (minPriority == null || p < minPriority) minPriority = p;
  }
  if (minPriority == null) return DEFAULT_NEW_TARGET_PRIORITY;
  let next = minPriority - 1;
  if (next === 0) next = -1;
  if (next < -10000) next = -10000;
  if (next > 10000) next = 10000;
  return next;
}

function copyConfig(cfg: AdminLlmRoutingConfigDTO | null): AdminLlmRoutingConfigDTO {
  return {
    scenarios: (cfg?.scenarios ?? []).map((s) => ({ ...s })),
    policies: (cfg?.policies ?? []).map((p) => ({ ...p })),
    targets: (cfg?.targets ?? []).map((t) => ({ ...t })),
  };
}

function ensurePolicy(cfg: AdminLlmRoutingConfigDTO, taskType: string): AdminLlmRoutingPolicyDTO {
  const tt = normTaskType(taskType);
  const list = cfg.policies ?? [];
  let found = list.find((p) => normTaskType(p.taskType) === tt);
  if (!found) {
    found = { taskType: tt, strategy: 'WEIGHTED_RR', maxAttempts: 2, failureThreshold: 2, cooldownMs: 30000 };
    cfg.policies = [...list, found];
  }
  return found;
}

function buildProviderLabel(p: AiProviderDTO): string {
  const id = String(p.id ?? '');
  const name = String(p.name ?? '').trim();
  return name ? `${name} (${id})` : id;
}

function parseTimestampMs(v: unknown): number | null {
  if (typeof v === 'number' && Number.isFinite(v)) {
    const ms = v > 1e12 ? v : v * 1000;
    return Number.isFinite(ms) ? ms : null;
  }
  if (typeof v === 'string') {
    const t = v.trim();
    if (!t) return null;
    const n = Number(t);
    if (Number.isFinite(n)) {
      const ms = n > 1e12 ? n : n * 1000;
      return Number.isFinite(ms) ? ms : null;
    }
    const d = new Date(t);
    const ms = d.getTime();
    return Number.isFinite(ms) ? ms : null;
  }
  return null;
}

function formatMmddHms(ms: number | null | undefined): string {
  if (!ms || !Number.isFinite(ms)) return '';
  const d = new Date(ms);
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mi = String(d.getMinutes()).padStart(2, '0');
  const ss = String(d.getSeconds()).padStart(2, '0');
  return `${mm}-${dd} ${hh}:${mi}:${ss}`;
}

const SEEN_MODELS_STORAGE_KEY = 'llm-routing-config.seen-models.v1';
function readSeenModelKeys(): Set<string> | null {
  try {
    const raw = window.localStorage.getItem(SEEN_MODELS_STORAGE_KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return null;
    const set = new Set<string>();
    parsed.forEach((x) => {
      const k = String(x ?? '').trim();
      if (k) set.add(k);
    });
    return set;
  } catch {
    return null;
  }
}

function writeSeenModelKeys(keys: Set<string>): void {
  try {
    window.localStorage.setItem(SEEN_MODELS_STORAGE_KEY, JSON.stringify(Array.from(keys.values())));
  } catch {
  }
}

type ProviderModelSets = {
  TEXT_CHAT: Set<string>;
  IMAGE_CHAT: Set<string>;
  EMBEDDING: Set<string>;
  RERANK: Set<string>;
};

const PROVIDER_MODELS_CACHE_KEY = 'llm-routing-config.provider-models-map.v2';
const PROVIDER_MODELS_CACHE_TTL_MS = 10 * 60 * 1000;
type ProviderModelsCache = {
  tsMs: number;
  providerIds: string[];
  map: Record<string, { TEXT_CHAT: string[]; IMAGE_CHAT: string[]; EMBEDDING: string[]; RERANK: string[] }>;
};

function readProviderModelsCache(providerIds: string[]): ProviderModelsCache | null {
  try {
    const raw = window.localStorage.getItem(PROVIDER_MODELS_CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as ProviderModelsCache;
    if (!parsed || typeof parsed !== 'object') return null;
    const tsMs = typeof parsed.tsMs === 'number' && Number.isFinite(parsed.tsMs) ? parsed.tsMs : null;
    if (!tsMs) return null;
    if (Date.now() - tsMs > PROVIDER_MODELS_CACHE_TTL_MS) return null;
    const ids = Array.isArray(parsed.providerIds) ? parsed.providerIds.map((x) => String(x ?? '').trim()).filter(Boolean) : [];
    if (ids.length !== providerIds.length) return null;
    const a = [...ids].sort();
    const b = [...providerIds].sort();
    for (let i = 0; i < a.length; i++) {
      if (a[i] !== b[i]) return null;
    }
    if (!parsed.map || typeof parsed.map !== 'object') return null;
    return { tsMs, providerIds: ids, map: parsed.map };
  } catch {
    return null;
  }
}

function writeProviderModelsCache(providerIds: string[], map: Record<string, ProviderModelSets>): void {
  try {
    const payload: ProviderModelsCache = {
      tsMs: Date.now(),
      providerIds,
      map: Object.fromEntries(
        Object.entries(map).map(([pid, sets]) => [
          pid,
          {
            TEXT_CHAT: Array.from(sets.TEXT_CHAT.values()),
            IMAGE_CHAT: Array.from(sets.IMAGE_CHAT.values()),
            EMBEDDING: Array.from(sets.EMBEDDING.values()),
            RERANK: Array.from(sets.RERANK.values()),
          },
        ]),
      ),
    };
    window.localStorage.setItem(PROVIDER_MODELS_CACHE_KEY, JSON.stringify(payload));
  } catch {
  }
}

const ROUTING_DRAFT_STORAGE_KEY = 'llm-routing-config.draft.v1';
type RoutingDraftCache = { tsMs: number; draft: AdminLlmRoutingConfigDTO };

function readRoutingDraftCache(): RoutingDraftCache | null {
  try {
    const raw = window.localStorage.getItem(ROUTING_DRAFT_STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as RoutingDraftCache;
    if (!parsed || typeof parsed !== 'object') return null;
    const tsMs = typeof parsed.tsMs === 'number' && Number.isFinite(parsed.tsMs) ? parsed.tsMs : null;
    if (!tsMs) return null;
    if (!parsed.draft || typeof parsed.draft !== 'object') return null;
    return { tsMs, draft: parsed.draft };
  } catch {
    return null;
  }
}

function writeRoutingDraftCache(draft: AdminLlmRoutingConfigDTO): void {
  try {
    const payload: RoutingDraftCache = { tsMs: Date.now(), draft };
    window.localStorage.setItem(ROUTING_DRAFT_STORAGE_KEY, JSON.stringify(payload));
  } catch {
  }
}

function clearRoutingDraftCache(): void {
  try {
    window.localStorage.removeItem(ROUTING_DRAFT_STORAGE_KEY);
  } catch {
  }
}

const ROUTING_RUNTIME_STATE_POLL_INTERVAL_MS = 5000;

function isSameRoutingConfig(a: AdminLlmRoutingConfigDTO | null | undefined, b: AdminLlmRoutingConfigDTO | null | undefined): boolean {
  try {
    return JSON.stringify(a ?? null) === JSON.stringify(b ?? null);
  } catch {
    return false;
  }
}

async function copyToClipboard(text: string): Promise<void> {
  const t = String(text ?? '');
  if (!t) return;
  try {
    await navigator.clipboard.writeText(t);
    return;
  } catch {
  }
  const ta = document.createElement('textarea');
  ta.value = t;
  ta.style.position = 'fixed';
  ta.style.left = '-9999px';
  ta.style.top = '-9999px';
  document.body.appendChild(ta);
  ta.select();
  try {
    document.execCommand('copy');
  } finally {
    document.body.removeChild(ta);
  }
}

type LlmHealthRecord = {
  ok?: boolean;
  success?: boolean;
  timestamp?: string | number;
  time?: string | number;
  ts?: string | number;
  errorCode?: string;
  code?: string;
  errorMessage?: string;
  message?: string;
  traceId?: string;
  tsMs?: number;
  durationMs?: number;
  taskId?: string;
  taskType?: string;
  status?: string;
  tokensIn?: number;
  tokensOut?: number;
  totalTokens?: number;
};

type LlmModelStatusDTO = {
  providerId?: string;
  provider?: string;
  modelName?: string;
  model?: string;
  runningCount?: number;
  records?: LlmHealthRecord[];
  calls?: LlmHealthRecord[];
  last10?: LlmHealthRecord[];
  recent?: LlmHealthRecord[];
};

type LlmHealthFailureDetail = {
  tsMs: number | null;
  errorCode: string;
  errorMessage: string;
  traceId: string;
  taskId: string;
  taskType: string;
};

type LlmHealthSummary = {
  severity: 'UNKNOWN' | 'NEVER_CALLED' | 'RUNNING' | 'NORMAL' | 'ABNORMAL' | 'THROTTLED' | 'TIMEOUT' | 'AUTH' | 'UPSTREAM';
  failureRate: number;
  consecutiveFailures: number;
  lastFailure: LlmHealthFailureDetail | null;
  recentFailures: LlmHealthFailureDetail[];
  runningCount: number;
  lastCallTsMs: number | null;
  lastCallOk: boolean | null;
  lastOkTsMs: number | null;
  lastThrottledTsMs: number | null;
  recentRecords: Array<{
    tsMs: number | null;
    ok: boolean;
    errorCode: string;
    errorMessage: string;
    taskId: string;
    taskType: string;
    status: string;
    durationMs: number | null;
    tokensIn: number | null;
    tokensOut: number | null;
    totalTokens: number | null;
  }>;
};

type ModelAvailabilityCheck = {
  kind: AdminAiModelProbeKind;
  ok: boolean;
  reason: string;
  checkedAtMs: number;
  latencyMs: number | null;
  usedProviderId: string | null;
  usedModel: string | null;
};

type LlmRoutingStateItem = {
  taskType: string;
  providerId: string;
  modelName: string;
  weight: number;
  priority: number;
  minDelayMs: number | null;
  qps: number | null;
  runningCount: number;
  consecutiveFailures: number;
  cooldownUntilMs: number;
  cooldownRemainingMs: number;
  currentWeight: number;
  lastDispatchAtMs: number;
  rateTokens: number;
  lastRefillAtMs: number;
};

type LlmRoutingStateResponse = {
  checkedAtMs: number;
  taskType: string;
  strategy: string;
  maxAttempts: number;
  failureThreshold: number;
  cooldownMs: number;
  items: LlmRoutingStateItem[];
};

function isFailureRecord(r: LlmHealthRecord): boolean {
  if (!r) return false;
  if (r.success === false) return true;
  if (r.ok === false) return true;
  const msg = String(r.errorMessage ?? '').trim() || String(r.message ?? '').trim();
  return !!msg;
}

function detectErrorCode(errorCodeRaw: unknown, errorMessage: string): string {
  const direct = String(errorCodeRaw ?? '').trim();
  if (direct) return direct;
  const msg = String(errorMessage ?? '').trim();
  if (!msg) return '';
  const m = msg.match(/\bHTTP\s+(\d{3})\b/i);
  if (m?.[1]) return m[1];
  const low = msg.toLowerCase();
  if (low.includes('429') || low.includes('too many requests') || low.includes('rate limit')) return '429';
  if (low.includes('401') || low.includes('unauthorized')) return '401';
  if (low.includes('403') || low.includes('forbidden')) return '403';
  if (low.includes('timeout') || low.includes('timed out')) return 'timeout';
  if (low.includes('unknownhost')) return 'dns';
  if (low.includes('connection reset')) return 'reset';
  if (low.includes('connect')) return 'connect';
  return '';
}

function isRateLimited(code: string, msg: string): boolean {
  const c = String(code ?? '').trim();
  if (c === '429') return true;
  const low = String(msg ?? '').toLowerCase();
  return low.includes('429') || low.includes('too many requests') || low.includes('rate limit');
}

function isTimeoutLike(code: string, msg: string): boolean {
  const c = String(code ?? '').trim().toLowerCase();
  if (c === 'timeout') return true;
  const low = String(msg ?? '').toLowerCase();
  return low.includes('timeout') || low.includes('timed out') || low.includes('sockettimeout');
}

function isAuthLike(code: string, msg: string): boolean {
  const c = String(code ?? '').trim();
  if (c === '401' || c === '403') return true;
  const low = String(msg ?? '').toLowerCase();
  return low.includes('401') || low.includes('403') || low.includes('unauthorized') || low.includes('forbidden');
}

function isUpstream5xxLike(code: string, msg: string): boolean {
  const c = String(code ?? '').trim();
  if (/^5\d\d$/.test(c)) return true;
  const low = String(msg ?? '').toLowerCase();
  return low.includes('http 5') || low.includes('5xx');
}

function normalizeHealthSummary(input: unknown): Record<string, LlmHealthSummary> {
  const modelsRaw: unknown =
    input && typeof input === 'object' && 'models' in (input as any)
      ? (input as any).models
      : input;
  const list: LlmModelStatusDTO[] = Array.isArray(modelsRaw) ? (modelsRaw as LlmModelStatusDTO[]) : [];

  const out: Record<string, LlmHealthSummary> = {};
  for (const item of list) {
    const providerId = String(item?.providerId ?? item?.provider ?? '').trim();
    const modelName = String(item?.modelName ?? item?.model ?? '').trim();
    if (!providerId || !modelName) continue;
    const key = `${providerId}|${modelName}`;

    const recordsRaw = (item?.records ?? item?.calls ?? item?.last10 ?? item?.recent ?? []).filter(Boolean);
    const runningCount = typeof item?.runningCount === 'number' && Number.isFinite(item.runningCount) ? Math.max(0, Math.trunc(item.runningCount)) : 0;

    const normalizedRecords = recordsRaw
      .map((r) => {
        const tsMs = parseTimestampMs(r?.tsMs ?? r?.timestamp ?? r?.time ?? r?.ts);
        const status = String(r?.status ?? '').trim();
        const okFromStatus = status.toUpperCase() === 'DONE' || status.toUpperCase() === 'OK';
        const okFromFlag = r?.success === true || r?.ok === true;
        const ok = okFromStatus || okFromFlag || !isFailureRecord(r);
        const errorMessage = String(r?.errorMessage ?? '').trim() || String(r?.message ?? '').trim() || '';
        const errorCode = detectErrorCode(r?.errorCode ?? r?.code, errorMessage);
        const taskId = String(r?.taskId ?? '').trim();
        const taskType = String(r?.taskType ?? '').trim();
        const durationMs =
          typeof r?.durationMs === 'number' && Number.isFinite(r.durationMs) ? Math.max(0, Math.trunc(r.durationMs)) : null;
        const tokensIn = typeof r?.tokensIn === 'number' && Number.isFinite(r.tokensIn) ? Math.trunc(r.tokensIn) : null;
        const tokensOut = typeof r?.tokensOut === 'number' && Number.isFinite(r.tokensOut) ? Math.trunc(r.tokensOut) : null;
        const totalTokens = typeof r?.totalTokens === 'number' && Number.isFinite(r.totalTokens) ? Math.trunc(r.totalTokens) : null;
        return { tsMs, ok, errorCode, errorMessage, taskId, taskType, status, durationMs, tokensIn, tokensOut, totalTokens };
      })
      .sort((a, b) => {
        if (a.tsMs == null && b.tsMs == null) return 0;
        if (a.tsMs == null) return 1;
        if (b.tsMs == null) return -1;
        return b.tsMs - a.tsMs;
      });

    if (!normalizedRecords.length) {
      out[key] = {
        severity: runningCount > 0 ? 'RUNNING' : 'NEVER_CALLED',
        failureRate: 0,
        consecutiveFailures: 0,
        lastFailure: null,
        recentFailures: [],
        runningCount,
        lastCallTsMs: null,
        lastCallOk: null,
        lastOkTsMs: null,
        lastThrottledTsMs: null,
        recentRecords: [],
      };
      continue;
    }

    const failures = normalizedRecords.filter((x) => !x.ok);
    const failureRate = failures.length / normalizedRecords.length;

    let consecutiveFailures = 0;
    for (const x of normalizedRecords) {
      if (x.ok) break;
      consecutiveFailures++;
    }

    const lastCall = normalizedRecords[0];
    const lastOkTsMs = normalizedRecords.find((x) => x.ok)?.tsMs ?? null;
    const lastThrottledTsMs = failures.find((x) => isRateLimited(x.errorCode, x.errorMessage))?.tsMs ?? null;
    const throttledRecent = lastThrottledTsMs != null && Number.isFinite(lastThrottledTsMs) && Date.now() - lastThrottledTsMs <= 10 * 60 * 1000;

    const failureDetails: LlmHealthFailureDetail[] = failures.map((x) => ({
      tsMs: x.tsMs,
      errorCode: x.errorCode,
      errorMessage: x.errorMessage,
      traceId: '',
      taskId: x.taskId,
      taskType: x.taskType,
    }));

    const lastFailure = failureDetails[0] ?? null;

    let severity: LlmHealthSummary['severity'] = 'UNKNOWN';
    if (throttledRecent) severity = 'THROTTLED';
    else if (lastCall.ok) severity = 'NORMAL';
    else if (lastFailure) {
      if (isTimeoutLike(lastFailure.errorCode, lastFailure.errorMessage)) severity = 'TIMEOUT';
      else if (isAuthLike(lastFailure.errorCode, lastFailure.errorMessage)) severity = 'AUTH';
      else if (isUpstream5xxLike(lastFailure.errorCode, lastFailure.errorMessage)) severity = 'UPSTREAM';
      else severity = 'ABNORMAL';
    } else {
      severity = 'ABNORMAL';
    }

    if (!lastCall.ok && !throttledRecent && severity === 'ABNORMAL') {
      if (failureRate < 0.2 && consecutiveFailures < 3) severity = 'ABNORMAL';
    }

    out[key] = {
      severity,
      failureRate,
      consecutiveFailures,
      lastFailure,
      recentFailures: failureDetails.slice(0, 10),
      runningCount,
      lastCallTsMs: lastCall.tsMs,
      lastCallOk: lastCall.ok,
      lastOkTsMs,
      lastThrottledTsMs,
      recentRecords: normalizedRecords.slice(0, 20),
    };
  }
  return out;
}

function severityToUi(severity: string): { dot: string; text: string } {
  const s = String(severity ?? '').trim().toUpperCase();
  if (s === 'NORMAL') return { dot: 'bg-green-500', text: '正常' };
  if (s === 'RUNNING') return { dot: 'bg-blue-500', text: '运行中' };
  if (s === 'THROTTLED') return { dot: 'bg-yellow-500', text: '限流' };
  if (s === 'TIMEOUT') return { dot: 'bg-purple-500', text: '超时' };
  if (s === 'AUTH') return { dot: 'bg-orange-500', text: '鉴权失败' };
  if (s === 'UPSTREAM') return { dot: 'bg-red-500', text: '上游异常' };
  if (s === 'ABNORMAL') return { dot: 'bg-red-500', text: '异常' };
  if (s === 'NEVER_CALLED') return { dot: 'bg-gray-400', text: '未调用' };
  if (s === 'IGNORED') return { dot: 'bg-gray-400', text: '已忽略' };
  return { dot: 'bg-gray-400', text: '未知' };
}

export const LlmRoutingConfigPanel: React.FC<{ providers: AiProviderDTO[]; activeProviderId?: string; disabled?: boolean }> = ({
  providers,
  activeProviderId: activeProviderIdProp,
  disabled,
}) => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState(false);
  const [okSource, setOkSource] = useState<'models' | 'routing' | null>(null);
  const [cfg, setCfg] = useState<AdminLlmRoutingConfigDTO | null>(null);
  const [draft, setDraft] = useState<AdminLlmRoutingConfigDTO | null>(null);
  const [chatProviders, setChatProviders] = useState<AiChatProviderOptionDTO[]>([]);
  const [chatOptionsActiveProviderId, setChatOptionsActiveProviderId] = useState<string>('');
  const [dragFromIndex, setDragFromIndex] = useState<number | null>(null);
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null);
  const [isEditingRouting, setIsEditingRouting] = useState(false);
  const [isEditingModelList, setIsEditingModelList] = useState(false);
  const isEditingAny = isEditingRouting || isEditingModelList;
  const [healthByKey, setHealthByKey] = useState<Record<string, LlmHealthSummary>>({});
  const [healthSortMode] = useState<'ABNORMAL_FIRST' | 'NAME'>('ABNORMAL_FIRST');
  const [ignoredHealthKeys, setIgnoredHealthKeys] = useState<Record<string, true>>({});
  const [healthModalKey, setHealthModalKey] = useState<string | null>(null);
  const [routingStateByKey, setRoutingStateByKey] = useState<Record<string, LlmRoutingStateItem>>({});
  const [availabilityChecking, setAvailabilityChecking] = useState(false);
  const availabilityAbortRef = useRef<AbortController | null>(null);
  const [availabilityProgress, setAvailabilityProgress] = useState<{ done: number; total: number } | null>(null);
  const [availabilityByKey, setAvailabilityByKey] = useState<Record<string, ModelAvailabilityCheck>>({});
  const [availabilitySummary, setAvailabilitySummary] = useState<{ total: number; ok: number; fail: number; checkedAtMs: number } | null>(null);
  const [availabilityModalOpen, setAvailabilityModalOpen] = useState(false);
  const [availabilityOnlyFailed, setAvailabilityOnlyFailed] = useState(true);
  const [providerModelsMap, setProviderModelsMap] = useState<Record<string, ProviderModelSets>>({});
  const [providerModelsLoading, setProviderModelsLoading] = useState(false);
  const [providerModelsFailedProviderIds, setProviderModelsFailedProviderIds] = useState<string[]>([]);
  const [providerModelsLoadedAtMs, setProviderModelsLoadedAtMs] = useState<number | null>(null);
  const providerModelsLoadSeqRef = useRef(0);
  const [routingDraftCacheBanner, setRoutingDraftCacheBanner] = useState<RoutingDraftCache | null>(null);
  const routingDraftPersistTimerRef = useRef<number | null>(null);
  const routingMonitorReady = !!draft;

  const scenarioMetadata = useMemo(() => {
    const map = new Map<string, { label: string; category: string }>();
    (draft?.scenarios ?? []).forEach((s) => {
      map.set(normTaskType(s.taskType), { label: s.label, category: s.category });
    });
    return map;
  }, [draft?.scenarios]);

  const formatTaskTypeLabel = useCallback(
    (tt: string) => {
      const up = normTaskType(tt);
      const meta = scenarioMetadata.get(up);
      return meta ? meta.label : up;
    },
    [scenarioMetadata],
  );

  const categorizedScenarios = useMemo(() => {
    const textGen: string[] = [];
    const embedding: string[] = [];
    const rerank: string[] = [];
    (draft?.scenarios ?? []).forEach((s) => {
      const tt = normTaskType(s.taskType);
      if (s.category === 'TEXT_GEN') textGen.push(tt);
      else if (s.category === 'EMBEDDING') embedding.push(tt);
      else if (s.category === 'RERANK') rerank.push(tt);
    });
    return { TEXT_GEN: textGen, EMBEDDING: embedding, RERANK: rerank };
  }, [draft?.scenarios]);

  const categorizedModelRows = useMemo(() => {
    const modelMap = new Map<string, { providerId: string; providerName: string; modelName: string; enabledScenarios: Set<string> }>();

    const getProviderName = (pid: string) => {
      const p = providers.find((x) => x.id === pid);
      return p?.name || pid;
    };

    // 1. 从已配置的 targets 中收集
    const targets = draft?.targets ?? [];
    targets.forEach((t) => {
      if (!t.providerId || !t.modelName) return;
      const key = `${t.providerId}|${t.modelName}`;
      if (!modelMap.has(key)) {
        modelMap.set(key, {
          providerId: t.providerId,
          providerName: getProviderName(t.providerId),
          modelName: t.modelName,
          enabledScenarios: new Set(),
        });
      }
      modelMap.get(key)!.enabledScenarios.add(normTaskType(t.taskType));
    });

    // 2. 从 chatProviders 中收集可用聊天模型
    chatProviders.forEach((cp) => {
      const pid = String(cp.id ?? '').trim();
      if (!pid) return;
      (cp.chatModels ?? []).forEach((m) => {
        const mname = String(m.name ?? '').trim();
        if (!mname) return;
        const key = `${pid}|${mname}`;
        if (!modelMap.has(key)) {
          modelMap.set(key, {
            providerId: pid,
            providerName: getProviderName(pid),
            modelName: mname,
            enabledScenarios: new Set(),
          });
        }
      });
    });

    // 3. 从 providers 中收集默认模型 (已移除：避免未显式添加的默认模型出现在列表中)
    // providers.forEach((p) => {
    //   const pid = String(p.id ?? '').trim();
    //   if (!pid) return;
    //   [p.defaultChatModel, p.defaultEmbeddingModel, p.defaultRerankModel].forEach((m) => {
    //     const mname = String(m ?? '').trim();
    //     if (!mname) return;
    //     const key = `${pid}|${mname}`;
    //     if (!modelMap.has(key)) {
    //       modelMap.set(key, {
    //         providerId: pid,
    //         providerName: p.name || pid,
    //         modelName: mname,
    //         enabledScenarios: new Set(),
    //       });
    //     }
    //   });
    // });

    // 4. 从 providerModelsMap 中收集管理员已添加的模型（覆盖 TEXT_CHAT/IMAGE_CHAT/EMBEDDING/RERANK）
    Object.entries(providerModelsMap).forEach(([pid, sets]) => {
      const providerName = providers.find((p) => String(p.id ?? '').trim() === pid)?.name || pid;
      (['TEXT_CHAT', 'IMAGE_CHAT', 'EMBEDDING', 'RERANK'] as const).forEach((purpose) => {
        for (const mname of sets[purpose]) {
          const key = `${pid}|${mname}`;
          if (!modelMap.has(key)) {
            modelMap.set(key, {
              providerId: pid,
              providerName,
              modelName: mname,
              enabledScenarios: new Set(),
            });
          }
        }
      });
    });

    const allRows = Array.from(modelMap.values());
    
    const result = {
      TEXT_GEN: [] as typeof allRows,
      EMBEDDING: [] as typeof allRows,
      RERANK: [] as typeof allRows,
    };

    allRows.forEach(row => {
      const { providerId, modelName, enabledScenarios } = row;
      
      // 判断是否属于文本生成
      const isTextGen = categorizedScenarios.TEXT_GEN.some(s => enabledScenarios.has(s)) ||
                        chatProviders.some(cp => cp.id === providerId && cp.chatModels?.some(m => m.name === modelName)) ||
                        providers.some(p => p.id === providerId && p.defaultChatModel === modelName) ||
                        (providerModelsMap[providerId]?.TEXT_CHAT?.has(modelName) ?? false) ||
                        (providerModelsMap[providerId]?.IMAGE_CHAT?.has(modelName) ?? false);
      
      // 判断是否属于嵌入
      const isEmbedding = categorizedScenarios.EMBEDDING.some(s => enabledScenarios.has(s)) ||
                          providers.some(p => p.id === providerId && p.defaultEmbeddingModel === modelName) ||
                          (providerModelsMap[providerId]?.EMBEDDING?.has(modelName) ?? false);
      
      // 判断是否属于重排
      const isRerank = categorizedScenarios.RERANK.some(s => enabledScenarios.has(s)) ||
                       providers.some(p => p.id === providerId && p.defaultRerankModel === modelName) ||
                       (providerModelsMap[providerId]?.RERANK?.has(modelName) ?? false);

      if (isTextGen) result.TEXT_GEN.push(row);
      if (isEmbedding) result.EMBEDDING.push(row);
      if (isRerank) result.RERANK.push(row);
    });

    const healthRank = (providerId: string, modelName: string): number => {
      const key = `${providerId}|${modelName}`;
      if (ignoredHealthKeys[key]) return 1;
      const s = healthByKey[key];
      if (!s) return 3;
      if (s.severity === 'ABNORMAL') return 0;
      if (s.severity === 'NORMAL') return 2;
      return 3;
    };

    const sortFn = (a: any, b: any) => {
      if (healthSortMode === 'ABNORMAL_FIRST') {
        const ra = healthRank(a.providerId, a.modelName);
        const rb = healthRank(b.providerId, b.modelName);
        if (ra !== rb) return ra - rb;
      }
      const pComp = a.providerName.localeCompare(b.providerName, 'zh-Hans-CN');
      if (pComp !== 0) return pComp;
      return a.modelName.localeCompare(b.modelName, 'en');
    };

    result.TEXT_GEN.sort(sortFn);
    result.EMBEDDING.sort(sortFn);
    result.RERANK.sort(sortFn);

    return result;
  }, [draft, chatProviders, providers, categorizedScenarios, healthByKey, healthSortMode, ignoredHealthKeys, providerModelsMap]);

  const probeJobs = useMemo(() => {
    const set = new Set<string>();
    const jobs: Array<{ providerId: string; providerName: string; modelName: string; kind: AdminAiModelProbeKind }> = [];
    const addRows = (rows: Array<{ providerId: string; providerName: string; modelName: string }>, kind: AdminAiModelProbeKind) => {
      rows.forEach((r) => {
        const providerId = String(r.providerId ?? '').trim();
        const modelName = String(r.modelName ?? '').trim();
        if (!providerId || !modelName) return;
        const key = `${providerId}|${modelName}|${kind}`;
        if (set.has(key)) return;
        set.add(key);
        jobs.push({ providerId, providerName: r.providerName, modelName, kind });
      });
    };
    addRows(categorizedModelRows.TEXT_GEN as any, 'CHAT');
    addRows(categorizedModelRows.EMBEDDING as any, 'EMBEDDING');
    addRows(categorizedModelRows.RERANK as any, 'RERANK');
    jobs.sort((a, b) => a.providerName.localeCompare(b.providerName, 'zh-Hans-CN') || a.modelName.localeCompare(b.modelName, 'en') || a.kind.localeCompare(b.kind, 'en'));
    return jobs;
  }, [categorizedModelRows]);

  const checkAllModelsAvailability = useCallback(async () => {
    if (availabilityChecking) return;
    if (!probeJobs.length) return;

    setAvailabilityChecking(true);
    setAvailabilityProgress(null);
    setError(null);
    setOk(false);

    const checkedAtMs = Date.now();
    let okCount = 0;
    let failCount = 0;
    let totalCount = 0;

    const controller = new AbortController();
    if (availabilityAbortRef.current) availabilityAbortRef.current.abort();
    availabilityAbortRef.current = controller;

    const timeoutMs = 15000;
    const total = probeJobs.length;
    let done = 0;
    setAvailabilityProgress({ done, total });

    for (const job of probeJobs) {
      if (controller.signal.aborted) break;
      const key = `${job.providerId}|${job.modelName}|${job.kind}`;
      try {
        const res: AdminAiModelProbeResultDTO = await adminProbeModel(job.kind, job.providerId, job.modelName, {
          timeoutMs,
          signal: controller.signal,
        });
        const ok = !!res.ok;
        totalCount++;
        if (ok) okCount++;
        else failCount++;
        setAvailabilityByKey((prev) => ({
          ...prev,
          [key]: {
            kind: job.kind,
            ok,
            reason: ok ? '' : String(res.errorMessage ?? '').trim() || '探活失败',
            checkedAtMs,
            latencyMs: typeof res.latencyMs === 'number' && Number.isFinite(res.latencyMs) ? Math.max(0, Math.trunc(res.latencyMs)) : null,
            usedProviderId: String(res.usedProviderId ?? '').trim() || null,
            usedModel: String(res.usedModel ?? '').trim() || null,
          },
        }));
      } catch (e) {
        if (controller.signal.aborted) break;
        totalCount++;
        failCount++;
        const reason = e instanceof Error ? e.message : String(e);
        setAvailabilityByKey((prev) => ({
          ...prev,
          [key]: {
            kind: job.kind,
            ok: false,
            reason: reason || '探活失败',
            checkedAtMs,
            latencyMs: null,
            usedProviderId: null,
            usedModel: null,
          },
        }));
      } finally {
        done++;
        setAvailabilityProgress({ done, total });
      }
    }

    setAvailabilitySummary({ total: totalCount, ok: okCount, fail: failCount, checkedAtMs });
    setAvailabilityModalOpen(true);
    setAvailabilityChecking(false);
    setAvailabilityProgress(null);
  }, [availabilityChecking, probeJobs]);

  const toggleModelForScenario = useCallback((providerId: string, modelName: string, taskType: string, enabled: boolean) => {
    setDraft((prev) => {
      if (!prev) return prev;
      const tt = normTaskType(taskType);
      const next = copyConfig(prev);
      const list = [...(next.targets ?? [])];

      if (enabled) {
        const exists = list.some((t) => t.providerId === providerId && t.modelName === modelName && normTaskType(t.taskType) === tt);
        if (!exists) {
          let maxSortIndex = -1;
          for (const t of list) {
            if (normTaskType(t?.taskType) !== tt) continue;
            const v = typeof t?.sortIndex === 'number' && Number.isFinite(t.sortIndex) ? Math.trunc(t.sortIndex) : 0;
            if (v > maxSortIndex) maxSortIndex = v;
          }
          list.push({
            taskType: tt,
            providerId,
            modelName,
            enabled: true,
            weight: computeDefaultTargetWeight(list, tt),
            priority: computeDefaultTargetPriority(list, tt),
            sortIndex: maxSortIndex + 1,
          });
        }
      } else {
        const idx = list.findIndex((t) => t.providerId === providerId && t.modelName === modelName && normTaskType(t.taskType) === tt);
        if (idx >= 0) list.splice(idx, 1);
      }

      next.targets = list;
      return next;
    });
    setOk(false);
  }, []);

  useEffect(() => {
    if (!draft) return;
    if (loading || saving) return;
    if (isEditingAny) return;
    if (!categorizedScenarios.TEXT_GEN.length && !categorizedScenarios.EMBEDDING.length && !categorizedScenarios.RERANK.length) return;

    const available = new Set<string>();
    (draft.targets ?? []).forEach((t) => {
      const pid = String(t?.providerId ?? '').trim();
      const mname = String(t?.modelName ?? '').trim();
      if (!pid || !mname) return;
      available.add(`${pid}|${mname}`);
    });
    chatProviders.forEach((cp) => {
      const pid = String(cp?.id ?? '').trim();
      if (!pid) return;
      (cp.chatModels ?? []).forEach((m) => {
        const mname = String(m?.name ?? '').trim();
        if (!mname) return;
        available.add(`${pid}|${mname}`);
      });
    });
    providers.forEach((p) => {
      const pid = String(p?.id ?? '').trim();
      if (!pid) return;
      [p.defaultChatModel, p.defaultEmbeddingModel, p.defaultRerankModel].forEach((m) => {
        const mname = String(m ?? '').trim();
        if (!mname) return;
        available.add(`${pid}|${mname}`);
      });
    });

    const seen = readSeenModelKeys();
    if (!seen) {
      writeSeenModelKeys(available);
      return;
    }

    const newKeys = Array.from(available.values()).filter((k) => !seen.has(k));
    if (!newKeys.length) return;

    const hasChatModel = (providerId: string, modelName: string): boolean => {
      const pid = String(providerId ?? '').trim();
      const mname = String(modelName ?? '').trim();
      if (!pid || !mname) return false;
      const cp = chatProviders.find((x) => String(x?.id ?? '').trim() === pid);
      if (!cp) return false;
      return (cp.chatModels ?? []).some((m) => String(m?.name ?? '').trim() === mname);
    };

    const next = copyConfig(draft);
    const list = [...(next.targets ?? [])];
    const taskTypeNextIndex = new Map<string, number>();
    const getNextSortIndex = (taskType: string): number => {
      const tt = normTaskType(taskType);
      if (taskTypeNextIndex.has(tt)) {
        const v = taskTypeNextIndex.get(tt)!;
        taskTypeNextIndex.set(tt, v + 1);
        return v;
      }
      let maxSortIndex = -1;
      for (const t of list) {
        if (normTaskType(t?.taskType) !== tt) continue;
        const v = typeof t?.sortIndex === 'number' && Number.isFinite(t.sortIndex) ? Math.trunc(t.sortIndex) : 0;
        if (v > maxSortIndex) maxSortIndex = v;
      }
      const first = maxSortIndex + 1;
      taskTypeNextIndex.set(tt, first + 1);
      return first;
    };

    const addToTaskTypes = (providerId: string, modelName: string, taskTypes: string[]) => {
      const pid = String(providerId ?? '').trim();
      const mname = String(modelName ?? '').trim();
      if (!pid || !mname) return false;
      let changed = false;
      taskTypes.forEach((taskType) => {
        const tt = normTaskType(taskType);
        if (!tt || tt === 'UNKNOWN') return;
        const exists = list.some((t) => t.providerId === pid && t.modelName === mname && normTaskType(t.taskType) === tt);
        if (exists) return;
        list.push({
          taskType: tt,
          providerId: pid,
          modelName: mname,
          enabled: true,
          weight: computeDefaultTargetWeight(list, tt),
          priority: computeDefaultTargetPriority(list, tt),
          sortIndex: getNextSortIndex(tt),
        });
        changed = true;
      });
      return changed;
    };

    let changedAny = false;
    newKeys.forEach((key) => {
      const [pidRaw, mnameRaw] = key.split('|');
      const pid = String(pidRaw ?? '').trim();
      const mname = String(mnameRaw ?? '').trim();
      if (!pid || !mname) return;

      const provider = providers.find((p) => String(p?.id ?? '').trim() === pid) ?? null;
      const isTextGen = hasChatModel(pid, mname) || String(provider?.defaultChatModel ?? '').trim() === mname;
      const isEmbedding = String(provider?.defaultEmbeddingModel ?? '').trim() === mname;
      const isRerank = String(provider?.defaultRerankModel ?? '').trim() === mname;

      if (isTextGen) changedAny = addToTaskTypes(pid, mname, categorizedScenarios.TEXT_GEN) || changedAny;
      if (isEmbedding) changedAny = addToTaskTypes(pid, mname, categorizedScenarios.EMBEDDING) || changedAny;
      if (isRerank) changedAny = addToTaskTypes(pid, mname, categorizedScenarios.RERANK) || changedAny;
    });

    newKeys.forEach((k) => seen.add(k));
    writeSeenModelKeys(seen);

    if (!changedAny) return;
    next.targets = list;
    setDraft(next);
    setOk(false);
  }, [draft, loading, saving, isEditingAny, categorizedScenarios, chatProviders, providers]);

  const taskTypes = useMemo(() => {
    const set = new Set<string>();
    // 添加数据库中定义的场景
    (draft?.scenarios ?? []).forEach((s) => set.add(normTaskType(s.taskType)));

    for (const p of cfg?.policies ?? []) set.add(normTaskType(p.taskType));
    for (const t of cfg?.targets ?? []) set.add(normTaskType(t.taskType));
    const arr = Array.from(set).filter((x) => x && x !== 'UNKNOWN' && x !== 'EMBEDDING');
    
    // 按数据库中定义的顺序排序，没在数据库中的排在后面
    const scenarioOrder = new Map<string, number>();
    (draft?.scenarios ?? []).forEach((s, idx) => scenarioOrder.set(normTaskType(s.taskType), idx));

    arr.sort((a, b) => {
      const oa = scenarioOrder.has(a) ? scenarioOrder.get(a)! : 999999;
      const ob = scenarioOrder.has(b) ? scenarioOrder.get(b)! : 999999;
      if (oa !== ob) return oa - ob;
      return a.localeCompare(b, 'en');
    });
    return arr;
  }, [cfg, draft?.scenarios]);

  const [selectedTaskType, setSelectedTaskType] = useState<string>('TEXT_CHAT');

  useEffect(() => {
    if (!taskTypes.length) return;
    const tt = normTaskType(selectedTaskType);
    if (taskTypes.includes(tt)) return;
    setSelectedTaskType(taskTypes[0]);
  }, [taskTypes, selectedTaskType]);

  const providerOptions = useMemo(() => {
    const arr = [...providers];
    arr.sort((a, b) => buildProviderLabel(a).localeCompare(buildProviderLabel(b), 'zh-Hans-CN'));
    return arr;
  }, [providers]);

  const providerIds = useMemo(() => {
    return providers
      .map((p) => String(p?.id ?? '').trim())
      .filter(Boolean);
  }, [providers]);

  const load = useCallback(async () => {
    setLoading(true);
    setSaving(false);
    setError(null);
    setOk(false);
    setIsEditingRouting(false);
    setIsEditingModelList(false);
    try {
      const [res, opts] = await Promise.all([
        adminGetLlmRoutingConfig(),
        getAiChatOptions().catch(() => null),
      ]);
      setCfg(res);
      let modelsMap: Map<string, string[]> | null = null;
      if (opts) {
        const rows = (opts.providers ?? []).filter(Boolean) as AiChatProviderOptionDTO[];
        setChatProviders(rows);
        setChatOptionsActiveProviderId(String(opts.activeProviderId ?? '').trim());
        const nextMap = new Map<string, string[]>();
        for (const p of rows) {
          const pid = String(p?.id ?? '').trim();
          if (!pid) continue;
          const models = (p?.chatModels ?? [])
            .map((m) => String(m?.name ?? '').trim())
            .filter(Boolean);
          nextMap.set(pid, models);
        }
        modelsMap = nextMap;
      }
      const pickDefaultModelNameInLoad = (providerId: string): string => {
        const pid = String(providerId ?? '').trim();
        if (!pid) return '';
        const provider = providerOptions.find((p) => String(p.id ?? '').trim() === pid);
        const dm = String(provider?.defaultChatModel ?? '').trim();
        if (dm) return dm;
        const list = modelsMap?.get(pid) ?? [];
        return String(list[0] ?? '').trim();
      };
      const nextDraft = copyConfig(res);
      nextDraft.targets = (nextDraft.targets ?? []).map((t) => {
        const providerId = String(t.providerId ?? '').trim();
        const modelName = String(t.modelName ?? '').trim();
        if (providerId && !modelName) {
          const fill = pickDefaultModelNameInLoad(providerId);
          if (fill) return { ...t, modelName: fill };
        }
        return t;
      });
      const cachedDraft = readRoutingDraftCache();
      if (cachedDraft && !isSameRoutingConfig(cachedDraft.draft, res)) {
        const countTargets = (cfg0: AdminLlmRoutingConfigDTO): number => {
          const list = cfg0?.targets ?? [];
          let n = 0;
          for (const t of list as any[]) {
            const pid = String((t as any)?.providerId ?? '').trim();
            const m = String((t as any)?.modelName ?? '').trim();
            if (pid && m) n++;
          }
          return n;
        };
        const serverCount = countTargets(res);
        const cachedCount = countTargets(cachedDraft.draft);
        if (serverCount === 0 && cachedCount > 0) {
          const restored = copyConfig(cachedDraft.draft);
          restored.targets = (restored.targets ?? []).map((t) => {
            const providerId = String(t.providerId ?? '').trim();
            const modelName = String(t.modelName ?? '').trim();
            if (providerId && !modelName) {
              const fill = pickDefaultModelNameInLoad(providerId);
              if (fill) return { ...t, modelName: fill };
            }
            return t;
          });
          setDraft(restored);
          setRoutingDraftCacheBanner(null);
        } else {
          setDraft(nextDraft);
          setRoutingDraftCacheBanner(cachedDraft);
        }
      } else {
        setDraft(nextDraft);
        setRoutingDraftCacheBanner(null);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
      setCfg(null);
      setDraft(null);
      setRoutingDraftCacheBanner(null);
    } finally {
      setLoading(false);
    }
  }, [providerOptions]);

  useEffect(() => {
    load();
  }, [load]);

  const loadProviderModels = useCallback(
    async (force?: boolean) => {
      const forceLoad = !!force;
      if (!providerIds.length) {
        setProviderModelsMap({});
        setProviderModelsFailedProviderIds([]);
        setProviderModelsLoadedAtMs(null);
        return;
      }

      const cached = forceLoad ? null : readProviderModelsCache(providerIds);
      if (cached) {
      const cachedMap: Record<string, ProviderModelSets> = {};
        Object.entries(cached.map).forEach(([pid, v]) => {
          cachedMap[pid] = {
          TEXT_CHAT: new Set<string>((v?.TEXT_CHAT ?? []).map((x) => String(x ?? '').trim()).filter(Boolean)),
          IMAGE_CHAT: new Set<string>((v?.IMAGE_CHAT ?? []).map((x) => String(x ?? '').trim()).filter(Boolean)),
            EMBEDDING: new Set<string>((v?.EMBEDDING ?? []).map((x) => String(x ?? '').trim()).filter(Boolean)),
            RERANK: new Set<string>((v?.RERANK ?? []).map((x) => String(x ?? '').trim()).filter(Boolean)),
          };
        });
        setProviderModelsMap(cachedMap);
        setProviderModelsLoadedAtMs(cached.tsMs);
      }

      const shouldShowLoading = forceLoad || !cached;
      if (shouldShowLoading) setProviderModelsLoading(true);
      else setProviderModelsLoading(false);
      setProviderModelsFailedProviderIds([]);

      const seq = providerModelsLoadSeqRef.current + 1;
      providerModelsLoadSeqRef.current = seq;

      const settled = await Promise.allSettled(
        providerIds.map(async (pid) => {
          const dto = await adminListProviderModels(pid);
          const sets: ProviderModelSets = { TEXT_CHAT: new Set<string>(), IMAGE_CHAT: new Set<string>(), EMBEDDING: new Set<string>(), RERANK: new Set<string>() };
          for (const r of dto.models ?? []) {
            const purpose = String(r?.purpose ?? '').trim().toUpperCase();
            const name = String(r?.modelName ?? '').trim();
            if (!purpose || !name) continue;
            if (purpose === 'IMAGE_CHAT') sets.IMAGE_CHAT.add(name);
            else if (purpose === 'TEXT_CHAT' || purpose === 'CHAT') sets.TEXT_CHAT.add(name);
            else if (purpose === 'EMBEDDING') sets.EMBEDDING.add(name);
            else if (purpose === 'RERANK') sets.RERANK.add(name);
          }
          return sets;
        }),
      );

      if (providerModelsLoadSeqRef.current !== seq) return;

      const map: Record<string, ProviderModelSets> = {};
      const failed: string[] = [];
      for (let i = 0; i < settled.length; i++) {
        const pid = providerIds[i];
        const res = settled[i];
        if (res.status === 'fulfilled') {
          map[pid] = res.value;
        } else {
          failed.push(pid);
        }
      }

      if (Object.keys(map).length) writeProviderModelsCache(providerIds, map);
      setProviderModelsMap(map);
      setProviderModelsFailedProviderIds(failed);
      setProviderModelsLoadedAtMs(Date.now());
      if (shouldShowLoading) setProviderModelsLoading(false);
    },
    [providerIds],
  );

  const cancelEdits = useCallback(() => {
    setIsEditingRouting(false);
    setIsEditingModelList(false);
    if (cfg) setDraft(copyConfig(cfg));
  }, [cfg]);

  useEffect(() => {
    loadProviderModels(false);
  }, [loadProviderModels]);

  const draftDirty = useMemo(() => {
    if (!draft) return false;
    if (!cfg) return true;
    return !isSameRoutingConfig(draft, cfg);
  }, [draft, cfg]);

  useEffect(() => {
    if (!draft) return;
    if (!draftDirty) {
      if (!routingDraftCacheBanner) clearRoutingDraftCache();
      return;
    }
    if (routingDraftPersistTimerRef.current != null) {
      window.clearTimeout(routingDraftPersistTimerRef.current);
    }
    const timer = window.setTimeout(() => {
      writeRoutingDraftCache(draft);
    }, 300);
    routingDraftPersistTimerRef.current = timer;
    return () => window.clearTimeout(timer);
  }, [draft, draftDirty, routingDraftCacheBanner]);

  useEffect(() => {
    if (!draft) return;
    let disposed = false;
    const controller = new AbortController();
    let inFlight = false;

    const pollOnce = async () => {
      if (disposed || inFlight) return;
      inFlight = true;
      try {
        const res = await fetch(apiUrl('/api/llm/status?windowSec=900&perModel=20'), { method: 'GET', credentials: 'include', signal: controller.signal });
        const data: unknown = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(getBackendMessage(data) || '获取模型健康状态失败');
        const next = normalizeHealthSummary(data);
        if (!disposed) setHealthByKey(next);
      } catch {
      } finally {
        inFlight = false;
      }
    };

    pollOnce();
    const timer = window.setInterval(pollOnce, 5000);
    return () => {
      disposed = true;
      window.clearInterval(timer);
      controller.abort();
    };
  }, [draft]);

  useEffect(() => {
    if (!routingMonitorReady) return;
    let disposed = false;
    const controller = new AbortController();
    let inFlight = false;

    const pollOnce = async () => {
      if (disposed || inFlight) return;
      inFlight = true;
      const tt = normTaskType(selectedTaskType);
      try {
        const res = await fetch(apiUrl(`/api/admin/metrics/llm-routing/state?taskType=${encodeURIComponent(tt)}`), {
          method: 'GET',
          credentials: 'include',
          signal: controller.signal,
        });
        const data: unknown = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(getBackendMessage(data) || '获取路由运行时状态失败');
        const dto = data as LlmRoutingStateResponse;
        const map: Record<string, LlmRoutingStateItem> = {};
        for (const it of (dto?.items ?? []) as any[]) {
          const providerId = String((it as any)?.providerId ?? '').trim();
          const modelName = String((it as any)?.modelName ?? '').trim();
          if (!providerId || !modelName) continue;
          map[`${providerId}|${modelName}`] = it as any;
        }
        if (!disposed) setRoutingStateByKey(map);
      } catch {
      } finally {
        inFlight = false;
      }
    };

    pollOnce();
    const timer = window.setInterval(pollOnce, ROUTING_RUNTIME_STATE_POLL_INTERVAL_MS);
    return () => {
      disposed = true;
      window.clearInterval(timer);
      controller.abort();
    };
  }, [routingMonitorReady, selectedTaskType]);

  useEffect(() => {
    if (!healthModalKey) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setHealthModalKey(null);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [healthModalKey]);

  useEffect(() => {
    if (!availabilityModalOpen) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setAvailabilityModalOpen(false);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [availabilityModalOpen]);

  const currentPolicy = useMemo(() => {
    if (!draft) return null;
    const tt = normTaskType(selectedTaskType);
    const p = (draft.policies ?? []).find((x) => normTaskType(x.taskType) === tt);
    return p ?? null;
  }, [draft, selectedTaskType]);

  const currentTargetRows = useMemo(() => {
    if (!draft) return [];
    const tt = normTaskType(selectedTaskType);
    const list = draft.targets ?? [];
    const rows: Array<{ target: AdminLlmRoutingTargetDTO; targetIndex: number }> = [];
    for (let i = 0; i < list.length; i++) {
      const t = list[i];
      if (normTaskType(t?.taskType) !== tt) continue;
      rows.push({ target: t, targetIndex: i });
    }
    return rows;
  }, [draft, selectedTaskType]);

  const updatePolicy = useCallback(
    (patch: Partial<AdminLlmRoutingPolicyDTO>) => {
      if (!draft) return;
      const tt = normTaskType(selectedTaskType);
      const next = copyConfig(draft);
      const p = ensurePolicy(next, tt);
      Object.assign(p, patch);
      setDraft(next);
      setOk(false);
    },
    [draft, selectedTaskType],
  );

  const updateTarget = useCallback(
    (targetIndex: number, patch: Partial<AdminLlmRoutingTargetDTO>) => {
      if (!draft) return;
      const next = copyConfig(draft);
      const list = next.targets ?? [];
      if (targetIndex < 0 || targetIndex >= list.length) return;
      list[targetIndex] = { ...list[targetIndex], ...patch };
      next.targets = list;
      setDraft(next);
      setOk(false);
    },
    [draft],
  );

  const moveTarget = useCallback(
    (fromIdx: number, toIdx: number) => {
      if (!draft) return;
      const tt = normTaskType(selectedTaskType);
      if (fromIdx === toIdx) return;
      const next = copyConfig(draft);
      const list = next.targets ?? [];
      const subsetIndexes: number[] = [];
    for (let i = 0; i < list.length; i++) {
      const t = list[i];
      if (normTaskType(t?.taskType) !== tt) continue;
      subsetIndexes.push(i);
    }
      if (fromIdx < 0 || fromIdx >= subsetIndexes.length) return;
      if (toIdx < 0 || toIdx >= subsetIndexes.length) return;

      const subset = subsetIndexes.map((i) => list[i]);
      const [moved] = subset.splice(fromIdx, 1);
      if (!moved) return;
      subset.splice(toIdx, 0, moved);

      for (let i = 0; i < subsetIndexes.length; i++) {
        list[subsetIndexes[i]] = subset[i];
      }
      next.targets = list;
      setDraft(next);
      setOk(false);
    },
    [draft, selectedTaskType],
  );

  const save = useCallback(async () => {
    if (!draft || savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    setError(null);
    setOk(false);
    try {
      const cleaned: AdminLlmRoutingConfigDTO = copyConfig(draft);
      cleaned.policies = (cleaned.policies ?? [])
        .map((p) => ({
          ...p,
          taskType: normTaskType(p.taskType),
          strategy: normNonBlank(p.strategy) ?? undefined,
          maxAttempts: clampInt(p.maxAttempts, 1, 10, 2),
          failureThreshold: clampInt(p.failureThreshold, 1, 20, 2),
          cooldownMs: clampInt(p.cooldownMs, 0, 3_600_000, 30_000),
        }))
        .filter((p) => p.taskType && p.taskType !== 'UNKNOWN');

      cleaned.targets = (cleaned.targets ?? [])
        .map((t) => ({
          ...t,
          taskType: normTaskType(t.taskType),
          providerId: String(t.providerId ?? '').trim(),
          modelName: String(t.modelName ?? '').trim(),
          enabled: t.enabled == null ? true : t.enabled,
          weight: clampInt(t.weight, 0, 10_000, 0),
          priority: clampInt(t.priority, -10_000, 10_000, 0),
          sortIndex: clampInt(t.sortIndex, 0, 1_000_000, 0),
          minDelayMs: (() => {
            const x = clampInt(t.minDelayMs, 0, 60_000, 0);
            return x > 0 ? x : undefined;
          })(),
          qps: (() => {
            const x = clampFloat(t.qps, 0, 100_000, 0);
            return x > 0 ? x : undefined;
          })(),
          priceConfigId: t.priceConfigId == null ? undefined : Number(t.priceConfigId),
        }))
        .filter((t) => t.taskType && t.taskType !== 'UNKNOWN' && t.providerId && t.modelName)
        .filter((t) => {
          const pid = t.providerId;
          const name = t.modelName;
          const purpose = t.taskType;
          const provider = providers.find((p) => p.id === pid) ?? null;
          const hasChatOptsForProvider = chatProviders.some((cp) => cp.id === pid);
          const hasProviderModelsForProvider = providerModelsMap[pid] != null;
          const canValidate = hasChatOptsForProvider || hasProviderModelsForProvider || provider != null;

          if (purpose === 'CHAT' || purpose === 'TEXT_CHAT' || purpose === 'IMAGE_CHAT') {
            const existsInChatOpts = chatProviders.some((cp) => cp.id === pid && cp.chatModels?.some((m) => m.name === name));
            const existsInProviderModels =
              (providerModelsMap[pid]?.TEXT_CHAT?.has(name) ?? false) || (providerModelsMap[pid]?.IMAGE_CHAT?.has(name) ?? false);
            const existsAsDefault = String(provider?.defaultChatModel ?? '').trim() === name;
            const ok = existsInChatOpts || existsInProviderModels || existsAsDefault;
            if (ok) return true;
            if (!canValidate) return true;
            if (!hasChatOptsForProvider && !hasProviderModelsForProvider) return true;
            return false;
          }
          if (purpose === 'EMBEDDING') {
            const existsInProviderModels = providerModelsMap[pid]?.EMBEDDING?.has(name) ?? false;
            const existsAsDefault = String(provider?.defaultEmbeddingModel ?? '').trim() === name;
            const ok = existsInProviderModels || existsAsDefault;
            if (ok) return true;
            if (!canValidate) return true;
            if (!hasProviderModelsForProvider) return true;
            return false;
          }
          if (purpose === 'RERANK') {
            const existsInProviderModels = providerModelsMap[pid]?.RERANK?.has(name) ?? false;
            const existsAsDefault = String(provider?.defaultRerankModel ?? '').trim() === name;
            const ok = existsInProviderModels || existsAsDefault;
            if (ok) return true;
            if (!canValidate) return true;
            if (!hasProviderModelsForProvider) return true;
            return false;
          }
          return true;
        });
      if (cleaned.targets) {
        const seq = new Map<string, number>();
        cleaned.targets = cleaned.targets.map((t) => {
          const tt = normTaskType(t.taskType);
          const nextIndex = seq.get(tt) ?? 0;
          seq.set(tt, nextIndex + 1);
          return { ...t, sortIndex: nextIndex };
        });
      }

      const res = await adminUpdateLlmRoutingConfig(cleaned);
      setCfg(res);
      setDraft(copyConfig(res));
      setOk(true);
      setIsEditingRouting(false);
      setIsEditingModelList(false);
      clearRoutingDraftCache();
      setRoutingDraftCacheBanner(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }, [draft, chatProviders, providerModelsMap, providers]);

  const saveFromModels = useCallback(() => {
    setOkSource('models');
    void save();
  }, [save]);

  const saveFromRouting = useCallback(() => {
    setOkSource('routing');
    void save();
  }, [save]);

  const canEdit = !disabled && !loading && isEditingAny;
  const okModels = ok && okSource === 'models';
  const okRouting = ok && okSource === 'routing';

  return (
    <div className="space-y-4">
      <AvailableModelsCard
        availabilitySummary={availabilitySummary}
        isEditingAny={isEditingAny}
        loading={loading}
        saving={saving}
        disabled={disabled}
        setIsEditingModelList={setIsEditingModelList}
        cancelEdits={cancelEdits}
        save={saveFromModels}
        ok={okModels}
        draft={draft}
        canEdit={canEdit}
        availabilityChecking={availabilityChecking}
        availabilityProgress={availabilityProgress}
        probeJobs={probeJobs}
        checkAllModelsAvailability={checkAllModelsAvailability}
        setAvailabilityModalOpen={setAvailabilityModalOpen}
        categorizedModelRows={categorizedModelRows}
        categorizedScenarios={categorizedScenarios}
        scenarioMetadata={scenarioMetadata}
        providerModelsMap={providerModelsMap}
        healthByKey={healthByKey}
        ignoredHealthKeys={ignoredHealthKeys}
        setHealthModalKey={setHealthModalKey}
        toggleModelForScenario={toggleModelForScenario}
        severityToUi={severityToUi}
        formatMmddHms={formatMmddHms}
      />

      <ScenarioPolicyPoolCard
        isEditingAny={isEditingAny}
        setIsEditingRouting={setIsEditingRouting}
        loading={loading}
        saving={saving}
        disabled={disabled}
        cancelEdits={cancelEdits}
        save={saveFromRouting}
        draft={draft}
        canEdit={canEdit}
        loadProviderModels={loadProviderModels}
        providerModelsLoading={providerModelsLoading}
        providerModelsLoadedAtMs={providerModelsLoadedAtMs}
        providerModelsFailedProviderIds={providerModelsFailedProviderIds}
        error={error}
        ok={okRouting}
        routingDraftCacheBanner={routingDraftCacheBanner}
        setDraft={setDraft}
        copyConfig={copyConfig}
        setOk={setOk}
        setIsEditingModelList={setIsEditingModelList}
        clearRoutingDraftCache={clearRoutingDraftCache}
        setRoutingDraftCacheBanner={setRoutingDraftCacheBanner}
        formatMmddHms={formatMmddHms}
        taskTypes={taskTypes}
        selectedTaskType={selectedTaskType}
        setSelectedTaskType={setSelectedTaskType}
        formatTaskTypeLabel={formatTaskTypeLabel}
        currentPolicy={currentPolicy}
        updatePolicy={updatePolicy}
        currentTargetRows={currentTargetRows}
        providerOptions={providerOptions}
        activeProviderIdProp={activeProviderIdProp}
        chatOptionsActiveProviderId={chatOptionsActiveProviderId}
        chatProviders={chatProviders}
        normNonBlank={normNonBlank}
        updateTarget={updateTarget}
        moveTarget={moveTarget}
        dragFromIndex={dragFromIndex}
        dragOverIndex={dragOverIndex}
        setDragFromIndex={setDragFromIndex}
        setDragOverIndex={setDragOverIndex}
        routingStateByKey={routingStateByKey}
      />

      <AvailableModelsModals
        healthModalKey={healthModalKey}
        setHealthModalKey={setHealthModalKey}
        healthByKey={healthByKey}
        setIgnoredHealthKeys={setIgnoredHealthKeys}
        copyToClipboard={copyToClipboard}
        formatMmddHms={formatMmddHms}
        availabilityModalOpen={availabilityModalOpen}
        setAvailabilityModalOpen={setAvailabilityModalOpen}
        availabilitySummary={availabilitySummary}
        availabilityOnlyFailed={availabilityOnlyFailed}
        setAvailabilityOnlyFailed={setAvailabilityOnlyFailed}
        checkAllModelsAvailability={checkAllModelsAvailability}
        availabilityChecking={availabilityChecking}
        availabilityProgress={availabilityProgress}
        probeJobs={probeJobs}
        availabilityByKey={availabilityByKey}
      />
    </div>
  );
};

export const LlmRoutingConfigPage: React.FC = () => {
  const [providers, setProviders] = useState<AiProviderDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadProviders = useCallback(async () => {
    try {
      const cfg = await adminGetAiProvidersConfig();
      setProviders(cfg.providers ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载模型提供商失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadProviders();
  }, [loadProviders]);

  if (loading) {
    return <div className="p-4 text-sm text-gray-600">加载模型配置中…</div>;
  }

  if (error) {
    return <div className="p-4 text-sm text-red-600">{error}</div>;
  }

  return (
    <div className="space-y-4">
      <LlmRoutingConfigPanel providers={providers} />
    </div>
  );
};

export default LlmRoutingConfigPage;
