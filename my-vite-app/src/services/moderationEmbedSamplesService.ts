import { getCsrfToken } from '../utils/csrfUtils';

export type ModerationSample = {
  id: number;
  category: 'AD_SAMPLE' | 'HISTORY_VIOLATION';
  refContentType?: 'POST' | 'COMMENT' | null;
  refContentId?: number | null;
  rawText?: string | null;
  normalizedText?: string | null;
  textHash?: string | null;
  riskLevel?: number | null;
  labels?: string | null;
  source?: 'HUMAN' | 'RULE' | 'LLM' | 'IMPORT' | null;
  enabled?: boolean | null;
  createdAt?: string | null;
  updatedAt?: string | null;
  esSynced?: boolean | null;
  esSyncMessage?: string | null;
};

export type SpringPage<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
};

export type ModerationSampleCreateRequest = {
  category: 'AD_SAMPLE' | 'HISTORY_VIOLATION';
  refContentType?: 'POST' | 'COMMENT' | null;
  refContentId?: number | null;
  rawText: string;
  riskLevel?: number | null;
  labels?: string | null;
  source?: 'HUMAN' | 'RULE' | 'LLM' | 'IMPORT' | null;
  enabled?: boolean | null;
};

export type ModerationSampleUpdateRequest = Partial<ModerationSampleCreateRequest>;

export type ModerationSamplesSyncResult = {
  id: number;
  action: 'upsert' | 'delete' | string;
  success: boolean;
  message?: string | null;
};

export type ModerationSamplesReindexResponse = {
  total?: number;
  success?: number;
  failed?: number;
  failedIds?: number[];
  fromId?: number | null;
  batchSize?: number | null;
  onlyEnabled?: boolean | null;
  cleared?: boolean | null;
  clearError?: string | null;
  orphanDeleted?: number | null;
  orphanFailed?: number | null;
  orphanFailedIds?: number[];
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

async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const isWrite = !!init?.method && init.method !== 'GET';
  const csrfToken = isWrite ? await getCsrfToken() : undefined;

  const res = await fetch(apiUrl(path), {
    credentials: 'include',
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}),
      ...(init?.headers || {}),
    },
    ...init,
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || `请求失败: ${res.status}`);
  }

  // 兼容 204
  if (res.status === 204) return undefined as T;
  return data as T;
}

export async function listSamples(params: {
  page: number;
  pageSize: number;
  category?: 'AD_SAMPLE' | 'HISTORY_VIOLATION' | '';
  enabled?: 'true' | 'false' | '';
}): Promise<SpringPage<ModerationSample>> {
  const qs = buildQuery({
    page: params.page,
    pageSize: params.pageSize,
    category: params.category || undefined,
    enabled: params.enabled || undefined,
  });
  return apiFetch<SpringPage<ModerationSample>>(`/api/admin/moderation/embed/samples${qs}`, { method: 'GET' });
}

export async function createSample(payload: ModerationSampleCreateRequest): Promise<ModerationSample> {
  return apiFetch<ModerationSample>(`/api/admin/moderation/embed/samples`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function updateSample(id: number, payload: ModerationSampleUpdateRequest): Promise<ModerationSample> {
  return apiFetch<ModerationSample>(`/api/admin/moderation/embed/samples/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export async function deleteSample(id: number): Promise<void> {
  return apiFetch<void>(`/api/admin/moderation/embed/samples/${id}`, {
    method: 'DELETE',
  });
}

export async function syncSample(id: number): Promise<ModerationSamplesSyncResult> {
  return apiFetch<ModerationSamplesSyncResult>(`/api/admin/moderation/embed/samples/${id}/sync`, {
    method: 'POST',
  });
}

export async function syncSamplesIncremental(params: {
  onlyEnabled?: boolean;
  batchSize?: number;
  fromId?: number;
}): Promise<ModerationSamplesReindexResponse> {
  const qs = buildQuery({
    onlyEnabled: params.onlyEnabled,
    batchSize: params.batchSize,
    fromId: params.fromId,
  });
  return apiFetch<ModerationSamplesReindexResponse>(`/api/admin/moderation/embed/samples/sync${qs}`, {
    method: 'POST',
  });
}
