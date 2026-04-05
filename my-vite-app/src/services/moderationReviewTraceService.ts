import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';
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


export type ModerationReviewTraceStageSummary = {
  stage?: string | null;
  decision?: string | null;
  score?: number | null;
  threshold?: number | null;
  costMs?: number | null;
  details?: Record<string, unknown> | null;
};

export type ModerationReviewTraceChunkSummary = {
  chunked?: boolean | null;
  chunkSetId?: number | null;
  totalChunks?: number | null;
  completedChunks?: number | null;
  failedChunks?: number | null;
  maxScore?: number | null;
  avgMs?: number | null;
};

export type ModerationReviewTraceManualSummary = {
  hasManual?: boolean | null;
  lastAction?: string | null;
  lastActorName?: string | null;
  lastActorId?: number | null;
  lastAt?: string | null;
};

export type ModerationReviewTraceTaskItem = {
  queueId: number;
  contentType?: string | null;
  contentId?: number | null;
  queueStatus?: string | null;
  queueStage?: string | null;
  queueUpdatedAt?: string | null;

  latestRunId?: number | null;
  latestRunStatus?: string | null;
  latestFinalDecision?: string | null;
  latestTraceId?: string | null;
  latestStartedAt?: string | null;
  latestEndedAt?: string | null;
  latestTotalMs?: number | null;

  rule?: ModerationReviewTraceStageSummary | null;
  vec?: ModerationReviewTraceStageSummary | null;
  llm?: ModerationReviewTraceStageSummary | null;
  chunk?: ModerationReviewTraceChunkSummary | null;

  manual?: ModerationReviewTraceManualSummary | null;
};

export type ModerationReviewTraceTaskPage = {
  content: ModerationReviewTraceTaskItem[];
  totalPages: number;
  totalElements: number;
  page: number;
  pageSize: number;
};

export type ModerationReviewTraceChunkSet = {
  id?: number | null;
  queueId?: number | null;
  caseType?: string | null;
  contentType?: string | null;
  contentId?: number | null;
  status?: string | null;
  chunkThresholdChars?: number | null;
  chunkSizeChars?: number | null;
  overlapChars?: number | null;
  totalChunks?: number | null;
  completedChunks?: number | null;
  failedChunks?: number | null;
  configJson?: Record<string, unknown> | null;
  memoryJson?: Record<string, unknown> | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type ModerationReviewTraceTaskDetail = {
  queue?: Record<string, unknown> | null;
  latestRun?: Record<string, unknown> | null;
  runHistory?: Record<string, unknown> | null;
  chunkSet?: ModerationReviewTraceChunkSet | null;
  chunkProgress?: Record<string, unknown> | null;
  auditLogs?: Array<Record<string, unknown>> | null;
};

export type ModerationReviewTraceTaskQuery = {
  page?: number;
  pageSize?: number;
  queueId?: number;
  contentType?: string;
  contentId?: number;
  traceId?: string;
  status?: string;
  updatedFrom?: string;
  updatedTo?: string;
};

export async function adminListModerationReviewTraceTasks(query: ModerationReviewTraceTaskQuery = {}): Promise<ModerationReviewTraceTaskPage> {
  const qs = buildQuery({
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
    queueId: query.queueId,
    contentType: query.contentType,
    contentId: query.contentId,
    traceId: query.traceId,
    status: query.status,
    updatedFrom: query.updatedFrom,
    updatedTo: query.updatedTo,
  });

  const res = await fetch(apiUrl(`/api/admin/moderation/review-trace/tasks${qs}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取审核追溯列表失败');
  return data as ModerationReviewTraceTaskPage;
}

export async function adminGetModerationReviewTraceTaskDetail(queueId: number): Promise<ModerationReviewTraceTaskDetail> {
  const res = await fetch(apiUrl(`/api/admin/moderation/review-trace/tasks/${queueId}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取审核追溯详情失败');
  return data as ModerationReviewTraceTaskDetail;
}
