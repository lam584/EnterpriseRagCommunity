import { getCsrfToken } from '../utils/csrfUtils';
import type { SpringPage } from '../types/page';
import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';

export type ContentType = 'POST' | 'COMMENT' | 'PROFILE';
export type ModerationCaseType = 'CONTENT' | 'REPORT';
export type QueueStatus = 'PENDING' | 'REVIEWING' | 'HUMAN' | 'APPROVED' | 'REJECTED';
export type QueueStage = 'RULE' | 'VEC' | 'LLM' | 'HUMAN';

export type ModerationQueueSummary = {
  title?: string | null;
  snippet?: string | null;
  authorId?: number | null;
  authorName?: string | null;
  postId?: number | null; // comment only
};

export type ModerationQueueItem = {
  id: number;
  caseType: ModerationCaseType;
  contentType: ContentType;
  contentId: number;
  status: QueueStatus;
  currentStage: QueueStage;
  priority: number;
  assignedToId?: number | null;
  createdAt: string;
  updatedAt: string;
  riskTags?: string[];
  riskTagItems?: Array<{ slug: string; name: string }>;
  chunkProgress?: {
    status?: string | null;
    totalChunks?: number | null;
    completedChunks?: number | null;
    failedChunks?: number | null;
    updatedAt?: string | null;
  } | null;
  summary?: ModerationQueueSummary | null;
};

export type ModerationQueueDetail = ModerationQueueItem & {
  post?: {
    id: number;
    boardId?: number | null;
    authorId?: number | null;
    title?: string | null;
    content?: string | null;
    attachments?: Array<{
      id: number;
      fileAssetId?: number | null;
      url: string;
      fileName?: string | null;
      mimeType?: string | null;
      sizeBytes?: number | null;
      width?: number | null;
      height?: number | null;
      createdAt?: string | null;
      extractStatus?: string | null;
      extractedTextChars?: number | null;
      extractedTextSnippet?: string | null;
      extractedMetadataJsonSnippet?: string | null;
      extractionErrorMessage?: string | null;
      extractionUpdatedAt?: string | null;
    }> | null;
    status?: string | null;
    createdAt?: string | null;
  } | null;
  comment?: {
    id: number;
    postId: number;
    parentId?: number | null;
    authorId?: number | null;
    content?: string | null;
    status?: string | null;
    createdAt?: string | null;
  } | null;
  profile?: {
    id: number;
    publicUsername?: string | null;
    publicAvatarUrl?: string | null;
    publicBio?: string | null;
    publicLocation?: string | null;
    publicWebsite?: string | null;
    pendingUsername?: string | null;
    pendingAvatarUrl?: string | null;
    pendingBio?: string | null;
    pendingLocation?: string | null;
    pendingWebsite?: string | null;
    pendingSubmittedAt?: string | null;
  } | null;
  reports?: Array<{
    id: number;
    reporterId?: number | null;
    reasonCode?: string | null;
    reasonText?: string | null;
    status?: string | null;
    createdAt?: string | null;
  }>;
};

export type ModerationChunkProgress = {
  queueId: number;
  status?: string | null;
  totalChunks?: number | null;
  completedChunks?: number | null;
  failedChunks?: number | null;
  runningChunks?: number | null;
  updatedAt?: string | null;
  chunks?: Array<{
    id: number;
    sourceType?: string | null;
    fileAssetId?: number | null;
    fileName?: string | null;
    chunkIndex?: number | null;
    startOffset?: number | null;
    endOffset?: number | null;
    status?: string | null;
    verdict?: string | null;
    confidence?: number | null;
    score?: number | null;
    attempts?: number | null;
    lastError?: string | null;
    decidedAt?: string | null;
    elapsedMs?: number | null;
  }>;
};

export type ModerationQueueBatchRequeueResponse = {
  total?: number | null;
  success?: number | null;
  failed?: number | null;
  successIds?: number[];
  failedItems?: Array<{ id?: number | null; error?: string | null }>;
};

export type ModerationQueueListQuery = {
  page?: number;
  pageSize?: number;
  orderBy?: string;
  sort?: 'asc' | 'desc';
  id?: number;
  boardId?: number;
  contentType?: ContentType;
  contentId?: number;
  status?: QueueStatus;
  assignedToId?: number;
};

export type ModerationQueueBackfillRequest = {
  contentTypes?: ContentType[];
  createdFrom?: string;
  createdTo?: string;
  limit?: number;
  dryRun?: boolean;
};

export type ModerationQueueBackfillResponse = {
  scannedPosts: number;
  scannedComments: number;
  alreadyQueued: number;
  enqueued: number;
  skipped: number;
};

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


export async function adminListModerationQueue(query: ModerationQueueListQuery = {}): Promise<SpringPage<ModerationQueueItem>> {
  const qs = buildQuery({
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
    orderBy: query.orderBy,
    sort: query.sort,
    id: query.id,
    boardId: query.boardId,
    contentType: query.contentType,
    contentId: query.contentId,
    status: query.status,
    assignedToId: query.assignedToId,
  });

  const res = await fetch(apiUrl(`/api/admin/moderation/queue${qs}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取审核队列失败');
  return data as SpringPage<ModerationQueueItem>;
}

export async function adminGetModerationQueueDetail(id: number): Promise<ModerationQueueDetail> {
  const res = await fetch(apiUrl(`api/admin/moderation/queue/${id}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取审核任务详情失败');
  return data as ModerationQueueDetail;
}

export async function adminGetModerationQueueChunkProgress(
  id: number,
  opts: { includeChunks?: boolean; limit?: number } = {},
): Promise<ModerationChunkProgress> {
  const qs = buildQuery({
    includeChunks: opts.includeChunks ? 1 : 0,
    limit: opts.limit ?? 80,
  });
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/chunk-progress${qs}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取分片进度失败');
  return data as ModerationChunkProgress;
}

export async function adminBatchRequeueModerationQueue(ids: number[], reason: string): Promise<ModerationQueueBatchRequeueResponse> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/batch/requeue`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ ids: ids ?? [], reason }),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '批量进入再次审核失败');
  return data as ModerationQueueBatchRequeueResponse;
}

export async function adminGetModerationQueueRiskTags(id: number): Promise<string[]> {
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/risk-tags`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取风险标签失败');
  return (data as string[]) ?? [];
}

export async function adminSetModerationQueueRiskTags(id: number, riskTags: string[]): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/risk-tags`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ riskTags: riskTags ?? [] }),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '设置风险标签失败');
  return data as ModerationQueueDetail;
}

export async function adminApproveModerationQueue(id: number, reason: string): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/approve`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ reason }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '审核通过失败');
  return data as ModerationQueueDetail;
}

export async function adminOverrideApproveModerationQueue(id: number, reason: string): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/override-approve`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ reason }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '覆核通过失败');
  return data as ModerationQueueDetail;
}

export async function adminRejectModerationQueue(id: number, reason: string): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/reject`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ reason }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '驳回失败');
  return data as ModerationQueueDetail;
}

export async function adminOverrideRejectModerationQueue(id: number, reason: string): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/override-reject`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ reason }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '覆核驳回失败');
  return data as ModerationQueueDetail;
}

export async function adminBanModerationQueueUser(id: number, reason: string): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/ban-user`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ reason }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '封禁用户失败');
  return data as ModerationQueueDetail;
}

export async function adminBackfillModerationQueue(
  body: ModerationQueueBackfillRequest = {}
): Promise<ModerationQueueBackfillResponse> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/moderation/queue/backfill'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(body ?? {}),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '补齐历史待审数据失败');
  return data as ModerationQueueBackfillResponse;
}

export async function adminClaimModerationQueue(id: number): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/claim`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({}),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '认领失败');
  return data as ModerationQueueDetail;
}

export async function adminReleaseModerationQueue(id: number): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/release`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({}),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '释放失败');
  return data as ModerationQueueDetail;
}

export async function adminRequeueModerationQueue(id: number, reason: string): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/requeue`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ reason }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '重新进入自动审核失败');
  return data as ModerationQueueDetail;
}

export async function adminToHumanModerationQueue(id: number, reason: string): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/to-human`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ reason }),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '进入人工审核失败');
  return data as ModerationQueueDetail;
}
