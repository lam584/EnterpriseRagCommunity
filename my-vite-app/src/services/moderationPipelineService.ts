import { getCsrfToken } from '../utils/csrfUtils';

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

export type PipelineStepStage = 'RULE' | 'VEC' | 'LLM' | string;

export type AdminModerationPipelineRunDTO = {
  id: number;
  queueId: number;
  contentType: 'POST' | 'COMMENT' | string;
  contentId: number;
  status?: string | null;
  finalDecision?: string | null;
  traceId?: string | null;
  startedAt?: string | null;
  endedAt?: string | null;
  totalMs?: number | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  llmModel?: string | null;
  llmThreshold?: number | null;
  createdAt?: string | null;
};

export type AdminModerationPipelineStepDTO = {
  id: number;
  runId: number;
  stage: PipelineStepStage;
  stepOrder: number;
  decision?: string | null;
  score?: number | null;
  threshold?: number | null;
  details?: Record<string, unknown> | null;
  startedAt?: string | null;
  endedAt?: string | null;
  costMs?: number | null;
  errorCode?: string | null;
  errorMessage?: string | null;
};

export type AdminModerationPipelineRunDetailDTO = {
  run?: AdminModerationPipelineRunDTO | null;
  steps?: AdminModerationPipelineStepDTO[] | null;
};

export type AdminModerationPipelineRunHistoryPageDTO = {
  content?: AdminModerationPipelineRunDTO[] | null;
  totalPages?: number | null;
  totalElements?: number | null;
  page?: number | null;
  pageSize?: number | null;
};

export type AdminModerationPipelineHistoryQuery = {
  queueId?: number;
  contentType?: 'POST' | 'COMMENT' | string;
  contentId?: number;
  page?: number;
  pageSize?: number;
};

export async function adminListPipelineHistory(query: AdminModerationPipelineHistoryQuery): Promise<AdminModerationPipelineRunHistoryPageDTO> {
  const sp = new URLSearchParams();
  if (query.queueId) sp.set('queueId', String(query.queueId));
  if (query.contentType) sp.set('contentType', String(query.contentType));
  if (query.contentId) sp.set('contentId', String(query.contentId));
  sp.set('page', String(query.page ?? 1));
  sp.set('pageSize', String(query.pageSize ?? 20));

  const res = await fetch(apiUrl(`/api/admin/moderation/pipeline/history?${sp.toString()}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取流水线历史失败');
  return data as AdminModerationPipelineRunHistoryPageDTO;
}

export async function adminGetLatestPipelineByQueueId(queueId: number): Promise<AdminModerationPipelineRunDetailDTO> {
  const res = await fetch(apiUrl(`/api/admin/moderation/pipeline/latest?queueId=${encodeURIComponent(queueId)}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取流水线追溯信息失败');
  return data as AdminModerationPipelineRunDetailDTO;
}

// (Optional) maybe used later from logs page
export async function adminGetPipelineByRunId(runId: number): Promise<AdminModerationPipelineRunDetailDTO> {
  const res = await fetch(apiUrl(`/api/admin/moderation/pipeline/${encodeURIComponent(runId)}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取流水线详情失败');
  return data as AdminModerationPipelineRunDetailDTO;
}

// placeholder for future write endpoints (kept for symmetry)
export async function noopCsrfPing(): Promise<void> {
  await getCsrfToken();
}
