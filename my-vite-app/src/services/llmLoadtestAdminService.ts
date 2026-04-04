import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

function buildQuery(params: Record<string, unknown>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    sp.set(k, String(v));
  }
  const qs = sp.toString();
  return qs ? `?${qs}` : '';
}

function createFetchSignal(args: { signal?: AbortSignal; timeoutMs?: number }): { signal?: AbortSignal; cleanup: () => void } {
  const hasOuter = Boolean(args.signal);
  const hasTimeout = typeof args.timeoutMs === 'number' && Number.isFinite(args.timeoutMs) && args.timeoutMs > 0;
  if (!hasOuter && !hasTimeout) return { signal: undefined, cleanup: () => {} };

  const controller = new AbortController();
  const onAbort = () => {
    try {
      controller.abort();
    } catch {}
  };

  let timeoutId: number | null = null;
  if (hasTimeout) {
    timeoutId = window.setTimeout(() => onAbort(), Math.max(1, Math.floor(args.timeoutMs as number)));
  }

  if (args.signal) {
    if (args.signal.aborted) onAbort();
    else args.signal.addEventListener('abort', onAbort, { once: true });
  }

  return {
    signal: controller.signal,
    cleanup: () => {
      if (timeoutId != null) window.clearTimeout(timeoutId);
      if (args.signal) args.signal.removeEventListener('abort', onAbort);
    },
  };
}

export type AdminLlmLoadTestRunRequest = {
  concurrency: number;
  totalRequests: number;
  ratioChatStream: number;
  ratioModerationTest: number;
  providerId?: string;
  model?: string;
  stream: boolean;
  enableThinking?: boolean;
  timeoutMs: number;
  retries: number;
  retryDelayMs: number;
  chatMessage: string;
  moderationText: string;
};

export type AdminLlmLoadTestQueuePeak = {
  maxPending: number;
  maxRunning: number;
  maxTotal: number;
  tokensPerSecMax: number;
  tokensPerSecAvg: number;
};

export type AdminLlmLoadTestResult = {
  index: number;
  kind: 'CHAT_STREAM' | 'MODERATION_TEST';
  ok: boolean;
  latencyMs: number | null;
  startedAtMs: number;
  finishedAtMs: number;
  error: string | null;
  tokens: number | null;
  tokensIn?: number | null;
  tokensOut?: number | null;
  model: string | null;
};

export type AdminLlmLoadTestStatus = {
  runId: string;
  createdAtMs: number;
  startedAtMs?: number | null;
  finishedAtMs?: number | null;
  running: boolean;
  cancelled: boolean;
  error?: string | null;
  done: number;
  total: number;
  success: number;
  failed: number;
  avgLatencyMs?: number | null;
  maxLatencyMs?: number | null;
  p50LatencyMs?: number | null;
  p95LatencyMs?: number | null;
  tokensTotal?: number | null;
  tokensInTotal?: number | null;
  tokensOutTotal?: number | null;
  totalCost?: string | number | null;
  currency?: string | null;
  priceMissing?: boolean | null;
  queuePeak: AdminLlmLoadTestQueuePeak;
  recentResults: AdminLlmLoadTestResult[];
};

export type AdminLlmLoadTestHistoryRecord = {
  runId: string;
  createdAt: string;
  providerId?: string | null;
  model?: string | null;
  stream?: boolean | null;
  enableThinking?: boolean | null;
  retries?: number | null;
  retryDelayMs?: number | null;
  timeoutMs?: number | null;
  summary: unknown;
};

export async function adminStartLlmLoadTest(payload: AdminLlmLoadTestRunRequest): Promise<{ runId: string }> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/metrics/llm-loadtest/run'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload ?? {}),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '启动压测失败');
  return data as { runId: string };
}

export async function adminGetLlmLoadTestStatus(
  runId: string,
  opts: { signal?: AbortSignal; timeoutMs?: number } = {}
): Promise<AdminLlmLoadTestStatus> {
  const { signal, cleanup } = createFetchSignal({ signal: opts.signal, timeoutMs: opts.timeoutMs });
  try {
    const res = await fetch(apiUrl(`/api/admin/metrics/llm-loadtest/${encodeURIComponent(runId)}`), {
      method: 'GET',
      credentials: 'include',
      signal,
    });
    const data: unknown = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(getBackendMessage(data) || '获取压测状态失败');
    return data as AdminLlmLoadTestStatus;
  } finally {
    cleanup();
  }
}

export async function adminStopLlmLoadTest(runId: string): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/metrics/llm-loadtest/${encodeURIComponent(runId)}/stop`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({}),
  });
  if (!res.ok) {
    const data: unknown = await res.json().catch(() => ({}));
    throw new Error(getBackendMessage(data) || '停止压测失败');
  }
}

export async function adminListLlmLoadTestHistory(params: { limit?: number } = {}): Promise<AdminLlmLoadTestHistoryRecord[]> {
  const qs = buildQuery({ limit: params.limit });
  const res = await fetch(apiUrl(`api/admin/metrics/llm-loadtest/history${qs}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取压测历史失败');
  return data as AdminLlmLoadTestHistoryRecord[];
}

export async function adminUpsertLlmLoadTestHistory(payload: { runId: string; summary: unknown }): Promise<AdminLlmLoadTestHistoryRecord> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/metrics/llm-loadtest/history'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload ?? {}),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存压测历史失败');
  return data as AdminLlmLoadTestHistoryRecord;
}

export async function adminDeleteLlmLoadTestHistory(runId: string): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/metrics/llm-loadtest/history/${encodeURIComponent(runId)}`), {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  if (!res.ok) {
    const data: unknown = await res.json().catch(() => ({}));
    throw new Error(getBackendMessage(data) || '删除压测历史失败');
  }
}

export function adminGetLlmLoadTestExportUrl(runId: string, format: 'csv' | 'json' = 'json'): string {
  const qs = buildQuery({ format });
  return apiUrl(`/api/admin/metrics/llm-loadtest/${encodeURIComponent(runId)}/export${qs}`);
}
