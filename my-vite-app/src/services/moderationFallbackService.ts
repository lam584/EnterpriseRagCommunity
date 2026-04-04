import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { serviceApiUrl } from './serviceUrlUtils';

const apiUrl = serviceApiUrl;

export type FallbackAction = 'REJECT' | 'LLM' | 'HUMAN';

export interface ModerationConfidenceFallbackConfig {
  id?: number;
  version?: number;

  llmEnabled: boolean;
  llmRejectThreshold: number;
  llmHumanThreshold: number;

  chunkLlmRejectThreshold: number;
  chunkLlmHumanThreshold: number;

  llmTextRiskThreshold: number;
  llmImageRiskThreshold: number;
  llmStrongRejectThreshold: number;
  llmStrongPassThreshold: number;
  llmCrossModalThreshold: number;

  reportHumanThreshold: number;

  chunkThresholdChars: number;

  thresholds?: Record<string, unknown>;

  updatedAt?: string;
  updatedBy?: string | null;
}

export async function getFallbackConfig(): Promise<ModerationConfidenceFallbackConfig> {
  const res = await fetch(apiUrl('/api/admin/moderation/fallback/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '加载置信回退配置失败');
  return data as ModerationConfidenceFallbackConfig;
}

export async function updateFallbackConfig(payload: Partial<ModerationConfidenceFallbackConfig>): Promise<ModerationConfidenceFallbackConfig> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/moderation/fallback/config'), {
    method: 'PUT',
    headers: { 
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存置信回退配置失败');
  return data as ModerationConfidenceFallbackConfig;
}
