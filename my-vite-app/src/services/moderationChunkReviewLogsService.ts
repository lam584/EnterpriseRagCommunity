import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export type ModerationChunkLogItem = {
  id: number;
  chunkSetId: number;
  queueId: number;
  caseType?: string | null;
  contentType?: string | null;
  contentId?: number | null;

  sourceType?: string | null;
  sourceKey?: string | null;
  fileAssetId?: number | null;
  fileName?: string | null;
  chunkIndex?: number | null;
  startOffset?: number | null;
  endOffset?: number | null;

  status?: string | null;
  verdict?: string | null;
  confidence?: number | null;
  attempts?: number | null;
  lastError?: string | null;
  model?: string | null;
  tokensIn?: number | null;
  tokensOut?: number | null;
  budgetConvergenceLog?: Record<string, unknown> | null;

  decidedAt?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type ModerationChunkLogDetail = {
  chunk: {
    id: number;
    chunkSetId: number;
    queueId: number;
    caseType?: string | null;
    contentType?: string | null;
    contentId?: number | null;

    sourceType?: string | null;
    sourceKey?: string | null;
    fileAssetId?: number | null;
    fileName?: string | null;
    chunkIndex?: number | null;
    startOffset?: number | null;
    endOffset?: number | null;

    status?: string | null;
    attempts?: number | null;
    lastError?: string | null;
    model?: string | null;
    verdict?: string | null;
    confidence?: number | null;
    labels?: Record<string, unknown> | null;
    tokensIn?: number | null;
    tokensOut?: number | null;

    decidedAt?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
  };
  chunkSet: {
    id: number;
    queueId: number;
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
};

export type ModerationChunkContentPreview = {
  source?: {
    chunkId?: number | null;
    queueId?: number | null;
    contentType?: string | null;
    contentId?: number | null;
    sourceType?: string | null;
    fileAssetId?: number | null;
    startOffset?: number | null;
    endOffset?: number | null;
  } | null;
  text?: string | null;
  reason?: string | null;
  images?: Array<{
    index?: number | null;
    placeholder?: string | null;
    url?: string | null;
    mimeType?: string | null;
    fileName?: string | null;
    sizeBytes?: number | null;
    fileAssetId?: number | null;
    width?: number | null;
    height?: number | null;
  }> | null;
};

export type ModerationChunkLogQuery = {
  limit?: number;
  queueId?: number;
  status?: string;
  verdict?: string;
  sourceType?: string;
  fileAssetId?: number;
  keyword?: string;
};

export async function adminListModerationChunkLogs(query: ModerationChunkLogQuery): Promise<ModerationChunkLogItem[]> {
  const sp = new URLSearchParams();
  if (query.limit != null) sp.set('limit', String(query.limit));
  if (query.queueId != null) sp.set('queueId', String(query.queueId));
  if (query.status) sp.set('status', query.status);
  if (query.verdict) sp.set('verdict', query.verdict);
  if (query.sourceType) sp.set('sourceType', query.sourceType);
  if (query.fileAssetId != null) sp.set('fileAssetId', String(query.fileAssetId));
  if (query.keyword) sp.set('keyword', query.keyword);

  const url = apiUrl(`/api/admin/moderation/chunk-review/logs?${sp.toString()}`);
  const res = await fetch(url, { method: 'GET', credentials: 'include' });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '加载最近分片结果失败');
  return data as ModerationChunkLogItem[];
}

export async function adminGetModerationChunkLogDetail(id: number): Promise<ModerationChunkLogDetail> {
  const res = await fetch(apiUrl(`/api/admin/moderation/chunk-review/logs/${id}`), { method: 'GET', credentials: 'include' });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '加载分片详情失败');
  return data as ModerationChunkLogDetail;
}

export async function adminGetModerationChunkLogContent(id: number, signal?: AbortSignal): Promise<ModerationChunkContentPreview> {
  const res = await fetch(apiUrl(`/api/admin/moderation/chunk-review/logs/${id}/content`), { method: 'GET', credentials: 'include', signal });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '加载分片内容失败');
  return data as ModerationChunkContentPreview;
}
