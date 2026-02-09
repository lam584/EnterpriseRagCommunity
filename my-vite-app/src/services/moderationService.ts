import { getCsrfToken } from '../utils/csrfUtils';

export interface ModerationSamplesReindexResponse {
  success: boolean;
  message?: string;
  processedCount?: number;
}

export interface ModerationSamplesIndexStatusResponse {
  indexName: string;
  exists: boolean;
  embeddingDimsConfigured: number;
  embeddingDimsInMapping?: number;
  docCount?: number;
  lastIncrementalSyncAt?: string;
  available: boolean;
  availabilityMessage?: string;
}

export async function triggerReindexSamples(params: { onlyEnabled?: boolean; batchSize?: number; fromId?: number } = {}): Promise<ModerationSamplesReindexResponse> {
  const csrfToken = await getCsrfToken();
  const query = new URLSearchParams();
  if (params.onlyEnabled !== undefined) query.set('onlyEnabled', String(params.onlyEnabled));
  if (params.batchSize !== undefined) query.set('batchSize', String(params.batchSize));
  if (params.fromId !== undefined) query.set('fromId', String(params.fromId));

  const res = await fetch(`/api/admin/moderation/embed/reindex?${query.toString()}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken
    },
    credentials: 'include'
  });

  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || 'Reindex failed');
  }

  return res.json();
}

export async function getSamplesIndexStatus(): Promise<ModerationSamplesIndexStatusResponse> {
  const res = await fetch('/api/admin/moderation/embed/index-status', {
    credentials: 'include'
  });

  if (!res.ok) {
    throw new Error('Failed to load index status');
  }

  return res.json();
}
