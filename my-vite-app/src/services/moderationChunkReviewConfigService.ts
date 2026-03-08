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

export type ModerationChunkReviewConfig = {
  enabled: boolean;
  chunkThresholdChars: number;
  chunkSizeChars: number;
  overlapChars: number;
  maxChunksTotal: number;
  chunksPerRun: number;
  maxConcurrentWorkers: number;
  maxAttempts: number;

  chunkMode?: string;

  enableTempIndexHints: boolean;
  enableContextCompress: boolean;
  enableGlobalMemory: boolean;
  sendImagesOnlyWhenInEvidence: boolean;
  includeImagesBlockOnlyForEvidenceMatches: boolean;

  queueAutoRefreshEnabled: boolean;
  queuePollIntervalMs: number;
};

export async function getModerationChunkReviewConfig(): Promise<ModerationChunkReviewConfig> {
  const res = await fetch(apiUrl('/api/admin/moderation/chunk-review/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '加载分片审核配置失败');
  return data as ModerationChunkReviewConfig;
}

export async function updateModerationChunkReviewConfig(
  payload: Partial<ModerationChunkReviewConfig>,
): Promise<ModerationChunkReviewConfig> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/moderation/chunk-review/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存分片审核配置失败');
  return data as ModerationChunkReviewConfig;
}
