import { getCsrfToken } from '../utils/csrfUtils';
import type { SpringPage } from '../types/page';

export type ContentType = 'POST' | 'COMMENT';
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
  contentType: ContentType;
  contentId: number;
  status: QueueStatus;
  currentStage: QueueStage;
  priority: number;
  assignedToId?: number | null;
  createdAt: string;
  updatedAt: string;
  riskTags?: string[];
  summary?: ModerationQueueSummary | null;
};

export type ModerationQueueDetail = ModerationQueueItem & {
  post?: {
    id: number;
    boardId?: number | null;
    authorId?: number | null;
    title?: string | null;
    content?: string | null;
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
};

export type ModerationQueueListQuery = {
  page?: number;
  pageSize?: number;
  id?: number;
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

export async function adminListModerationQueue(query: ModerationQueueListQuery = {}): Promise<SpringPage<ModerationQueueItem>> {
  const qs = buildQuery({
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
    id: query.id,
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
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取审核任务详情失败');
  return data as ModerationQueueDetail;
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

export async function adminApproveModerationQueue(id: number, reason?: string): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/approve`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(reason ? { reason } : {}),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '审核通过失败');
  return data as ModerationQueueDetail;
}

export async function adminRejectModerationQueue(id: number, reason?: string): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/reject`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(reason ? { reason } : {}),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '驳回失败');
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

export async function adminRequeueModerationQueue(id: number, reason?: string): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/requeue`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(reason ? { reason } : {}),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '重新进入自动审核失败');
  return data as ModerationQueueDetail;
}

export async function adminToHumanModerationQueue(id: number, reason?: string): Promise<ModerationQueueDetail> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/moderation/queue/${id}/to-human`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(reason ? { reason } : {}),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '进入人工审核失败');
  return data as ModerationQueueDetail;
}
