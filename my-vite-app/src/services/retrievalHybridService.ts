import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { buildPageQuery } from './servicePagingUtils';
import { serviceApiUrl } from './serviceUrlUtils';
import type { SpringPage } from '../types/page';

const apiUrl = serviceApiUrl;

export type HybridRetrievalConfigDTO = {
  enabled?: boolean | null;

  bm25K?: number | null;
  bm25TitleBoost?: number | null;
  bm25ContentBoost?: number | null;

  vecK?: number | null;
  fileVecEnabled?: boolean | null;
  fileVecK?: number | null;

  hybridK?: number | null;
  fusionMode?: 'RRF' | 'LINEAR' | string | null;
  bm25Weight?: number | null;
  vecWeight?: number | null;
  fileVecWeight?: number | null;
  rrfK?: number | null;

  rerankEnabled?: boolean | null;
  rerankModel?: string | null;
  rerankTemperature?: number | null;
  rerankTopP?: number | null;
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
  postIds?: number[] | null;
  fileAssetId?: number | null;
  chunkIndex?: number | null;
  boardId?: number | null;
  sourceType?: string | null;
  title?: string | null;
  contentText?: string | null;
  bm25Score?: number | null;
  vecScore?: number | null;
  fileVecScore?: number | null;
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
  fileVecLatencyMs?: number | null;
  fuseLatencyMs?: number | null;
  rerankLatencyMs?: number | null;

  bm25Error?: string | null;
  vecError?: string | null;
  fileVecError?: string | null;
  rerankError?: string | null;

  bm25Hits?: HybridDocHit[] | null;
  vecHits?: HybridDocHit[] | null;
  fileVecHits?: HybridDocHit[] | null;
  fusedHits?: HybridDocHit[] | null;
  rerankHits?: HybridDocHit[] | null;
  finalHits?: HybridDocHit[] | null;

  debugInfo?: Record<string, unknown> | null;
};

export type HybridRerankTestDocumentDTO = {
  docId?: string | null;
  title?: string | null;
  text?: string | null;
};

export type HybridRerankTestRequest = {
  queryText: string;
  topN?: number | null;
  debug?: boolean | null;
  useSavedConfig?: boolean | null;
  config?: HybridRetrievalConfigDTO | null;
  documents: HybridRerankTestDocumentDTO[];
};

export type HybridRerankTestHitDTO = {
  index?: number | null;
  relevanceScore?: number | null;
  docId?: string | null;
  title?: string | null;
  text?: string | null;
};

export type HybridRerankTestResponse = {
  queryText?: string | null;
  topN?: number | null;
  ok?: boolean | null;
  latencyMs?: number | null;
  errorMessage?: string | null;
  usedProviderId?: string | null;
  usedModel?: string | null;
  totalTokens?: number | null;
  results?: HybridRerankTestHitDTO[] | null;
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
  postId?: number | null;
  chunkId?: number | null;
  score?: number | null;
};

export async function adminGetHybridRetrievalConfig(): Promise<HybridRetrievalConfigDTO> {
  const res = await fetch(apiUrl('api/admin/retrieval/hybrid/config'), {
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

export async function adminTestHybridRerank(payload: HybridRerankTestRequest): Promise<HybridRerankTestResponse> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/retrieval/hybrid/test-rerank'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrf,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '重排模型测试失败');
  return data as HybridRerankTestResponse;
}

export async function adminListHybridRetrievalEvents(params?: {
  page?: number;
  size?: number;
  from?: string;
  to?: string;
}): Promise<SpringPage<RetrievalEventLogDTO>> {
  const res = await fetch(apiUrl(`/api/admin/retrieval/hybrid/logs/events?${buildPageQuery(params)}`), {
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
