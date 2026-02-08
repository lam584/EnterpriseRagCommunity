import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function getBackendMessage(data: unknown): string | null {
  if (!data || typeof data !== 'object') return null;
  const d = data as Record<string, unknown>;
  const msg = d.message ?? d.error;
  return msg == null ? null : String(msg);
}

export type PostComposeAiSnapshotTargetType = 'DRAFT' | 'POST';
export type PostComposeAiSnapshotStatus = 'PENDING' | 'APPLIED' | 'REVERTED' | 'EXPIRED';

export type PostComposeAiSnapshotDTO = {
  id: number;
  tenantId?: number | null;
  userId?: number | null;
  targetType: PostComposeAiSnapshotTargetType;
  draftId?: number | null;
  postId?: number | null;
  beforeTitle: string;
  beforeContent: string;
  beforeBoardId: number;
  beforeMetadata?: Record<string, unknown> | null;
  afterContent?: string | null;
  instruction?: string | null;
  providerId?: string | null;
  model?: string | null;
  temperature?: number | null;
  topP?: number | null;
  status: PostComposeAiSnapshotStatus;
  expiresAt?: string | null;
  resolvedAt?: string | null;
  createdAt?: string | null;
};

export type CreatePostComposeAiSnapshotRequest = {
  targetType: PostComposeAiSnapshotTargetType;
  draftId?: number | null;
  postId?: number | null;
  beforeTitle: string;
  beforeContent: string;
  beforeBoardId: number;
  beforeMetadata?: Record<string, unknown> | null;
  instruction?: string | null;
  providerId?: string | null;
  model?: string | null;
  temperature?: number | null;
  topP?: number | null;
};

export async function createPostComposeAiSnapshot(payload: CreatePostComposeAiSnapshotRequest): Promise<PostComposeAiSnapshotDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/post-compose/ai-snapshots'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '创建快照失败');
  }
  return data as PostComposeAiSnapshotDTO;
}

export async function getPendingPostComposeAiSnapshot(params: {
  targetType: PostComposeAiSnapshotTargetType;
  draftId?: number | null;
  postId?: number | null;
}): Promise<PostComposeAiSnapshotDTO | null> {
  const qs = new URLSearchParams();
  qs.set('targetType', params.targetType);
  if (params.draftId != null) qs.set('draftId', String(params.draftId));
  if (params.postId != null) qs.set('postId', String(params.postId));
  const res = await fetch(apiUrl(`/api/post-compose/ai-snapshots/pending?${qs.toString()}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 404) return null;
    throw new Error(getBackendMessage(data) || '加载待处理快照失败');
  }
  return data as PostComposeAiSnapshotDTO;
}

export async function applyPostComposeAiSnapshot(snapshotId: number, afterContent: string): Promise<PostComposeAiSnapshotDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/post-compose/ai-snapshots/${snapshotId}/apply`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ afterContent }),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '采纳快照失败');
  }
  return data as PostComposeAiSnapshotDTO;
}

export async function revertPostComposeAiSnapshot(snapshotId: number): Promise<PostComposeAiSnapshotDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/post-compose/ai-snapshots/${snapshotId}/revert`), {
    method: 'POST',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '回滚快照失败');
  }
  return data as PostComposeAiSnapshotDTO;
}

