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

export type FallbackAction = 'REJECT' | 'LLM' | 'HUMAN';

export interface ModerationConfidenceFallbackConfig {
  id?: number;
  version?: number;

  ruleEnabled: boolean;
  ruleHighAction: FallbackAction;
  ruleMediumAction: FallbackAction;
  ruleLowAction: FallbackAction;

  vecEnabled: boolean;
  vecThreshold: number;
  vecHitAction: FallbackAction;
  vecMissAction: FallbackAction;

  llmEnabled: boolean;
  llmRejectThreshold: number;
  llmHumanThreshold: number;

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
  const res = await fetch(apiUrl('/api/admin/moderation/fallback/config'), {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存置信回退配置失败');
  return data as ModerationConfidenceFallbackConfig;
}
