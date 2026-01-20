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

export type LlmModerationDecision = 'APPROVE' | 'REJECT' | 'HUMAN';

export type LlmModerationConfig = {
  promptTemplate: string;
  model?: string | null;
  temperature?: number | null;
  maxTokens?: number | null;
  threshold?: number | null;
  autoRun?: boolean | null;
  // auto runner throttling
  maxConcurrent?: number | null;
  minDelayMs?: number | null;
  qps?: number | null;
};

export type LlmModerationConfigDTO = LlmModerationConfig & {
  id?: number;
  version?: number;
  updatedAt?: string;
  updatedBy?: string | null;
};

export type LlmModerationTestRequest = {
  queueId?: number;
  text?: string;
  configOverride?: Partial<LlmModerationConfig>;
};

export type LlmModerationTestResponse = {
  decision: LlmModerationDecision;
  score?: number | null;
  reasons?: string[] | null;
  riskTags?: string[] | null;
  rawModelOutput?: string | null;
  model?: string | null;
  latencyMs?: number | null;
  usage?: {
    promptTokens?: number | null;
    completionTokens?: number | null;
    totalTokens?: number | null;
  } | null;
};

export async function adminGetLlmModerationConfig(): Promise<LlmModerationConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/moderation/llm/config'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取 LLM 审核配置失败');
  return data as LlmModerationConfigDTO;
}

export async function adminUpsertLlmModerationConfig(payload: LlmModerationConfig): Promise<LlmModerationConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/moderation/llm/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存 LLM 审核配置失败');
  return data as LlmModerationConfigDTO;
}

export async function adminTestLlmModeration(payload: LlmModerationTestRequest): Promise<LlmModerationTestResponse> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/moderation/llm/test'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || 'LLM 试运行失败');
  return data as LlmModerationTestResponse;
}
