import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { buildQuery, createFetchSignal } from './serviceQueryUtils';
import { serviceApiUrl } from './serviceUrlUtils';

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

const apiUrl = serviceApiUrl;

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
