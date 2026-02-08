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

export type VectorIndexProvider = 'FAISS' | 'MILVUS' | 'OTHER';
export type VectorIndexStatus = 'READY' | 'BUILDING' | 'ERROR';

export type VectorIndexDTO = {
  id: number;
  provider: VectorIndexProvider;
  collectionName: string;
  metric: string;
  dim: number;
  status: VectorIndexStatus;
  metadata?: Record<string, unknown> | null;
};

export type CreateVectorIndexRequest = {
  provider: VectorIndexProvider;
  collectionName: string;
  metric: string;
  dim: number;
  status: VectorIndexStatus;
  metadata?: Record<string, unknown> | null;
};

export type UpdateVectorIndexRequest = {
  id: number;
  provider?: VectorIndexProvider;
  collectionName?: string;
  metric?: string;
  dim?: number;
  status?: VectorIndexStatus;
  metadata?: Record<string, unknown> | null;
};

export type RagPostsBuildResponse = {
  totalPosts?: number;
  totalChunks?: number;
  successChunks?: number;
  failedChunks?: number;
  failedDocIds?: string[] | null;
  failedDocs?: Array<{ docId?: string | null; error?: string | null }> | null;
  fromPostId?: number | null;
  lastPostId?: number | null;
  boardId?: number | null;
  postBatchSize?: number | null;
  chunkMaxChars?: number | null;
  chunkOverlapChars?: number | null;
  embeddingDims?: number | null;
  embeddingModel?: string | null;
  embeddingProviderId?: string | null;
  cleared?: boolean | null;
  clearError?: string | null;
  tookMs?: number | null;
};

export type RagAutoSyncConfigDTO = {
  enabled?: boolean | null;
  intervalSeconds?: number | null;
};

export type RagPostsTestQueryRequest = {
  queryText: string;
  topK?: number;
  boardId?: number;
  numCandidates?: number;
  embeddingModel?: string;
  embeddingProviderId?: string;
};

export type RagPostsTestQueryResponse = {
  indexName?: string | null;
  topK?: number | null;
  boardId?: number | null;
  embeddingDims?: number | null;
  embeddingModel?: string | null;
  embeddingProviderId?: string | null;
  numCandidates?: number | null;
  tookMs?: number | null;
  hits?: Array<{
    docId?: string | null;
    score?: number | null;
    postId?: number | null;
    chunkIndex?: number | null;
    authorId?: number | null;
    boardId?: number | null;
    title?: string | null;
    contentTextPreview?: string | null;
  }> | null;
};

export async function adminListVectorIndices(params?: { page?: number; size?: number }): Promise<SpringPage<VectorIndexDTO>> {
  const sp = new URLSearchParams();
  sp.set('page', String(params?.page ?? 0));
  sp.set('size', String(params?.size ?? 50));

  const res = await fetch(apiUrl(`/api/admin/retrieval/vector-indices?${sp.toString()}`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取向量索引列表失败');
  return data as SpringPage<VectorIndexDTO>;
}

export async function adminCreateVectorIndex(payload: CreateVectorIndexRequest): Promise<VectorIndexDTO> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/retrieval/vector-indices'), {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
    },
    body: JSON.stringify(payload),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '创建向量索引失败');
  return data as VectorIndexDTO;
}

export async function adminUpdateVectorIndex(payload: UpdateVectorIndexRequest): Promise<VectorIndexDTO> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/retrieval/vector-indices/${payload.id}`), {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
    },
    body: JSON.stringify(payload),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '更新向量索引失败');
  return data as VectorIndexDTO;
}

export async function adminDeleteVectorIndex(id: number): Promise<void> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/retrieval/vector-indices/${id}`), {
    method: 'DELETE',
    credentials: 'include',
    headers: {
      ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
    },
  });

  if (res.status === 404) return;
  if (!res.ok) {
    const data: unknown = await res.json().catch(() => ({}));
    throw new Error(getBackendMessage(data) || '删除向量索引失败');
  }
}

export async function adminBuildPostRagIndex(params: {
  id: number;
  boardId?: number;
  fromPostId?: number;
  postBatchSize?: number;
  chunkMaxChars?: number;
  chunkOverlapChars?: number;
  clear?: boolean;
  embeddingModel?: string;
  embeddingProviderId?: string;
  embeddingDims?: number;
}): Promise<RagPostsBuildResponse> {
  const sp = new URLSearchParams();
  if (params.boardId) sp.set('boardId', String(params.boardId));
  if (params.fromPostId) sp.set('fromPostId', String(params.fromPostId));
  if (params.postBatchSize) sp.set('postBatchSize', String(params.postBatchSize));
  if (params.chunkMaxChars) sp.set('chunkMaxChars', String(params.chunkMaxChars));
  if (params.chunkOverlapChars !== undefined && params.chunkOverlapChars !== null) sp.set('chunkOverlapChars', String(params.chunkOverlapChars));
  if (params.clear) sp.set('clear', 'true');
  if (params.embeddingModel) sp.set('embeddingModel', params.embeddingModel);
  if (params.embeddingProviderId) sp.set('embeddingProviderId', params.embeddingProviderId);
  if (params.embeddingDims) sp.set('embeddingDims', String(params.embeddingDims));

  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/retrieval/vector-indices/${params.id}/build/posts?${sp.toString()}`), {
    method: 'POST',
    credentials: 'include',
    headers: {
      ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
    },
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '构建向量索引失败');
  return data as RagPostsBuildResponse;
}

export async function adminRebuildPostRagIndex(params: {
  id: number;
  boardId?: number;
  postBatchSize?: number;
  chunkMaxChars?: number;
  chunkOverlapChars?: number;
  embeddingModel?: string;
  embeddingProviderId?: string;
  embeddingDims?: number;
}): Promise<RagPostsBuildResponse> {
  const sp = new URLSearchParams();
  if (params.boardId) sp.set('boardId', String(params.boardId));
  if (params.postBatchSize) sp.set('postBatchSize', String(params.postBatchSize));
  if (params.chunkMaxChars) sp.set('chunkMaxChars', String(params.chunkMaxChars));
  if (params.chunkOverlapChars !== undefined && params.chunkOverlapChars !== null) sp.set('chunkOverlapChars', String(params.chunkOverlapChars));
  if (params.embeddingModel) sp.set('embeddingModel', params.embeddingModel);
  if (params.embeddingProviderId) sp.set('embeddingProviderId', params.embeddingProviderId);
  if (params.embeddingDims) sp.set('embeddingDims', String(params.embeddingDims));

  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/retrieval/vector-indices/${params.id}/rebuild/posts?${sp.toString()}`), {
    method: 'POST',
    credentials: 'include',
    headers: {
      ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
    },
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '全量重建失败');
  return data as RagPostsBuildResponse;
}

export async function adminSyncPostRagIndex(params: {
  id: number;
  boardId?: number;
  postBatchSize?: number;
  chunkMaxChars?: number;
  chunkOverlapChars?: number;
  embeddingModel?: string;
  embeddingProviderId?: string;
  embeddingDims?: number;
}): Promise<RagPostsBuildResponse> {
  const sp = new URLSearchParams();
  if (params.boardId) sp.set('boardId', String(params.boardId));
  if (params.postBatchSize) sp.set('postBatchSize', String(params.postBatchSize));
  if (params.chunkMaxChars) sp.set('chunkMaxChars', String(params.chunkMaxChars));
  if (params.chunkOverlapChars !== undefined && params.chunkOverlapChars !== null) sp.set('chunkOverlapChars', String(params.chunkOverlapChars));
  if (params.embeddingModel) sp.set('embeddingModel', params.embeddingModel);
  if (params.embeddingProviderId) sp.set('embeddingProviderId', params.embeddingProviderId);
  if (params.embeddingDims) sp.set('embeddingDims', String(params.embeddingDims));

  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/retrieval/vector-indices/${params.id}/sync/posts?${sp.toString()}`), {
    method: 'POST',
    credentials: 'include',
    headers: {
      ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
    },
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '增量同步失败');
  return data as RagPostsBuildResponse;
}

export async function adminGetRagAutoSyncConfig(): Promise<RagAutoSyncConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/retrieval/rag-sync/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取自动同步配置失败');
  return data as RagAutoSyncConfigDTO;
}

export async function adminUpdateRagAutoSyncConfig(payload: RagAutoSyncConfigDTO): Promise<RagAutoSyncConfigDTO> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/retrieval/rag-sync/config'), {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
    },
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '更新自动同步配置失败');
  return data as RagAutoSyncConfigDTO;
}

export async function adminTestQueryPostRagIndex(params: { id: number; payload: RagPostsTestQueryRequest }): Promise<RagPostsTestQueryResponse> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/retrieval/vector-indices/${params.id}/test-query`), {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
    },
    body: JSON.stringify(params.payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '测试查询失败');
  return data as RagPostsTestQueryResponse;
}
