import { getCsrfToken } from '../utils/csrfUtils';
import type { SpringPage } from '../types/page';

const API_BASE: string = ((import.meta as unknown as { env?: Record<string, unknown> })?.env?.VITE_API_BASE_URL as string) ?? '';

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

export type HybridRetrievalConfigDTO = {
  enabled?: boolean | null;

  bm25K?: number | null;
  bm25TitleBoost?: number | null;
  bm25ContentBoost?: number | null;

  vecK?: number | null;

  hybridK?: number | null;
  fusionMode?: 'RRF' | 'LINEAR' | string | null;
  bm25Weight?: number | null;
  vecWeight?: number | null;
  rrfK?: number | null;

  rerankEnabled?: boolean | null;
  rerankModel?: string | null;
  rerankTemperature?: number | null;
  rerankK?: number | null;

  maxDocs?: number | null;
  perDocMaxTokens?: number | null;
  maxInputTokens?: number | null;
};

export type HybridRetrievalTestRequest = {
  queryText: string;
  boardId?: number | null;
  debug?: boolean | null;
  useSavedConfig?: boolean | null;
  config?: HybridRetrievalConfigDTO | null;
};

export type HybridDocHit = {
  docId?: string | null;
  score?: number | null;
  postId?: number | null;
  chunkIndex?: number | null;
  boardId?: number | null;
  title?: string | null;
  contentText?: string | null;
  bm25Score?: number | null;
  vecScore?: number | null;
  fusedScore?: number | null;
  rerankRank?: number | null;
  rerankScore?: number | null;
};

export type HybridRetrievalTestResponse = {
  queryText?: string | null;
  boardId?: number | null;
  config?: HybridRetrievalConfigDTO | null;

  bm25LatencyMs?: number | null;
  vecLatencyMs?: number | null;
  fuseLatencyMs?: number | null;
  rerankLatencyMs?: number | null;

  bm25Error?: string | null;
  vecError?: string | null;
  rerankError?: string | null;

  bm25Hits?: HybridDocHit[] | null;
  vecHits?: HybridDocHit[] | null;
  fusedHits?: HybridDocHit[] | null;
  rerankHits?: HybridDocHit[] | null;
  finalHits?: HybridDocHit[] | null;

  debugInfo?: Record<string, unknown> | null;
};

export type RetrievalEventLogDTO = {
  id: number;
  userId?: number | null;
  queryText?: string | null;
  bm25K?: number | null;
  vecK?: number | null;
  hybridK?: number | null;
  rerankModel?: string | null;
  rerankK?: number | null;
  createdAt?: string | null;
};

export type RetrievalHitLogDTO = {
  id: number;
  eventId: number;
  rank?: number | null;
  hitType?: 'BM25' | 'VEC' | 'RERANK' | string | null;
  documentId?: number | null;
  chunkId?: number | null;
  score?: number | null;
};

export async function adminGetHybridRetrievalConfig(): Promise<HybridRetrievalConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/retrieval/hybrid/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取 Hybrid 检索配置失败');
  return data as HybridRetrievalConfigDTO;
}

export async function adminUpdateHybridRetrievalConfig(payload: HybridRetrievalConfigDTO): Promise<HybridRetrievalConfigDTO> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/retrieval/hybrid/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrf,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存 Hybrid 检索配置失败');
  return data as HybridRetrievalConfigDTO;
}

export async function adminTestHybridRetrieval(payload: HybridRetrievalTestRequest): Promise<HybridRetrievalTestResponse> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/retrieval/hybrid/test'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrf,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || 'Hybrid 检索测试失败');
  return data as HybridRetrievalTestResponse;
}

export async function adminListHybridRetrievalEvents(params?: {
  page?: number;
  size?: number;
  from?: string;
  to?: string;
}): Promise<SpringPage<RetrievalEventLogDTO>> {
  const sp = new URLSearchParams();
  sp.set('page', String(params?.page ?? 0));
  sp.set('size', String(params?.size ?? 20));
  if (params?.from) sp.set('from', params.from);
  if (params?.to) sp.set('to', params.to);

  const res = await fetch(apiUrl(`/api/admin/retrieval/hybrid/logs/events?${sp.toString()}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取检索日志失败');
  return data as SpringPage<RetrievalEventLogDTO>;
}

export async function adminListHybridRetrievalHits(eventId: number): Promise<RetrievalHitLogDTO[]> {
  const res = await fetch(apiUrl(`/api/admin/retrieval/hybrid/logs/events/${eventId}/hits`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取检索命中详情失败');
  return data as RetrievalHitLogDTO[];
}
