import { getCsrfToken } from '../utils/csrfUtils';

export type LlmQueueTaskStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'CANCELLED';

export type LlmQueueTaskType =
  | 'CHAT'
  | 'MULTIMODAL_CHAT'
  | 'TEXT_CHAT'
  | 'IMAGE_CHAT'
  | 'MODERATION'
  | 'MULTIMODAL_MODERATION'
  | 'TEXT_MODERATION'
  | 'IMAGE_MODERATION'
  | 'MODERATION_CHUNK'
  | 'TITLE_GEN'
  | 'TOPIC_TAG_GEN'
  | 'LANGUAGE_TAG_GEN'
  | 'SUMMARY_GEN'
  | 'TRANSLATION'
  | 'EMBEDDING'
  | 'POST_EMBEDDING'
  | 'SIMILARITY_EMBEDDING'
  | 'RERANK'
  | 'UNKNOWN';

export type LlmQueueTaskDTO = {
  id: string;
  seq: number;
  priority: number;
  type: LlmQueueTaskType;
  label?: string | null;
  status: LlmQueueTaskStatus;
  providerId?: string | null;
  model?: string | null;
  createdAt: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  waitMs?: number | null;
  durationMs?: number | null;
  tokensIn?: number | null;
  tokensOut?: number | null;
  totalTokens?: number | null;
  tokensPerSec?: number | null;
  error?: string | null;
};

export type LlmQueueSampleDTO = {
  ts: string;
  queueLen: number;
  running: number;
  tokensPerSec: number;
};

export type LlmQueueStatusDTO = {
  snapshotAtMs?: number | null;
  stale?: boolean | null;
  truncated?: boolean | null;
  maxConcurrent: number;
  runningCount: number;
  pendingCount: number;
  running: LlmQueueTaskDTO[];
  pending: LlmQueueTaskDTO[];
  recentCompleted: LlmQueueTaskDTO[];
  samples: LlmQueueSampleDTO[];
};

export type LlmQueueConfigDTO = {
  maxConcurrent: number;
  maxQueueSize: number;
  keepCompleted: number;
};

export type LlmQueueTaskDetailDTO = {
  id: string;
  seq: number;
  priority: number;
  type: LlmQueueTaskType;
  label?: string | null;
  status: LlmQueueTaskStatus;
  providerId?: string | null;
  model?: string | null;
  createdAt: string;
  startedAt?: string | null;
  finishedAt?: string | null;
  waitMs?: number | null;
  durationMs?: number | null;
  tokensIn?: number | null;
  tokensOut?: number | null;
  totalTokens?: number | null;
  tokensPerSec?: number | null;
  error?: string | null;
  input?: string | null;
  output?: string | null;
};

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function buildQuery(params: Record<string, unknown>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    sp.set(k, String(v));
  }
  const qs = sp.toString();
  return qs ? `?${qs}` : '';
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
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

export async function adminGetLlmQueueStatus(
  params: { windowSec?: number; signal?: AbortSignal; timeoutMs?: number } = {}
): Promise<LlmQueueStatusDTO> {
  const qs = buildQuery({ windowSec: params.windowSec });
  const { signal, cleanup } = createFetchSignal({ signal: params.signal, timeoutMs: params.timeoutMs });
  try {
    const res = await fetch(apiUrl(`/api/admin/metrics/llm-queue${qs}`), {
      method: 'GET',
      credentials: 'include',
      signal,
    });
    const data: unknown = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(getBackendMessage(data) || '获取 LLM 调用队列状态失败');
    return data as LlmQueueStatusDTO;
  } finally {
    cleanup();
  }
}

export async function adminGetLlmQueueConfig(): Promise<LlmQueueConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/metrics/llm-queue/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取 LLM 调用队列配置失败');
  return data as LlmQueueConfigDTO;
}

export async function adminUpdateLlmQueueConfig(payload: { maxConcurrent?: number }): Promise<LlmQueueConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/metrics/llm-queue/config'), {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    body: JSON.stringify(payload ?? {}),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '更新 LLM 调用队列配置失败');
  return data as LlmQueueConfigDTO;
}

export async function adminGetLlmQueueTaskDetail(
  taskId: string,
  params: { signal?: AbortSignal; timeoutMs?: number } = {}
): Promise<LlmQueueTaskDetailDTO> {
  const { signal, cleanup } = createFetchSignal({ signal: params.signal, timeoutMs: params.timeoutMs });
  try {
    const res = await fetch(apiUrl(`/api/admin/metrics/llm-queue/tasks/${encodeURIComponent(taskId)}`), {
      method: 'GET',
      credentials: 'include',
      signal,
    });
    const data: unknown = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(getBackendMessage(data) || '获取任务详情失败');
    return data as LlmQueueTaskDetailDTO;
  } finally {
    cleanup();
  }
}
