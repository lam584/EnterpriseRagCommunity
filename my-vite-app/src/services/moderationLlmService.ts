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

function createFetchSignal(args: { signal?: AbortSignal; timeoutMs?: number }): { signal?: AbortSignal; cleanup: () => void } {
  const hasOuter = Boolean(args.signal);
  const hasTimeout = typeof args.timeoutMs === 'number' && Number.isFinite(args.timeoutMs) && args.timeoutMs > 0;
  if (!hasOuter && !hasTimeout) return { signal: undefined, cleanup: () => {} };

  const controller = new AbortController();
  const onAbort = () => {
    try {
      controller.abort();
    } catch {}
  };

  let timeoutId: number | null = null;
  if (hasTimeout) {
    timeoutId = window.setTimeout(() => onAbort(), Math.max(1, Math.floor(args.timeoutMs as number)));
  }

  if (args.signal) {
    if (args.signal.aborted) onAbort();
    else args.signal.addEventListener('abort', onAbort, { once: true });
  }

  return {
    signal: controller.signal,
    cleanup: () => {
      if (timeoutId != null) window.clearTimeout(timeoutId);
      if (args.signal) args.signal.removeEventListener('abort', onAbort);
    },
  };
}

export type LlmModerationDecision = 'APPROVE' | 'REJECT' | 'HUMAN';

export type LlmModerationConfig = {
  multimodalPromptCode: string;
  judgePromptCode?: string | null;
  autoRun?: boolean | null;
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
  images?: Array<{ url: string; mimeType?: string }> | null;
  configOverride?: Partial<LlmModerationConfig>;
};

export type LlmModerationTestResponse = {
  decision: LlmModerationDecision;
  score?: number | null;
  reasons?: string[] | null;
  riskTags?: string[] | null;
  severity?: string | null;
  uncertainty?: number | null;
  evidence?: string[] | null;
  rawModelOutput?: string | null;
  model?: string | null;
  latencyMs?: number | null;
  promptMessages?: Array<{ role: string; content: string }> | null;
  images?: string[] | null;
  inputMode?: string | null;
  stages?: LlmModerationStages | null;
  usage?: {
    promptTokens?: number | null;
    completionTokens?: number | null;
    totalTokens?: number | null;
  } | null;
};

export type LlmModerationStage = {
  decision?: LlmModerationDecision | string | null;
  score?: number | null;
  reasons?: string[] | null;
  riskTags?: string[] | null;
  severity?: string | null;
  uncertainty?: number | null;
  evidence?: string[] | null;
  rawModelOutput?: string | null;
  model?: string | null;
  latencyMs?: number | null;
  inputMode?: string | null;
  description?: string | null;
} | null;

export type LlmModerationStages = {
  text?: LlmModerationStage;
  image?: LlmModerationStage;
  judge?: LlmModerationStage;
  upgrade?: LlmModerationStage;
};

export async function adminGetLlmModerationConfig(): Promise<LlmModerationConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/moderation/llm/config'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || 'Failed to load LLM moderation config');
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
  if (!res.ok) throw new Error(getBackendMessage(data) || 'Failed to save LLM moderation config');
  return data as LlmModerationConfigDTO;
}

export async function adminTestLlmModeration(
  payload: LlmModerationTestRequest,
  opts: { signal?: AbortSignal; timeoutMs?: number } = {}
): Promise<LlmModerationTestResponse> {
  const csrfToken = await getCsrfToken();
  const { signal, cleanup } = createFetchSignal({ signal: opts.signal, timeoutMs: opts.timeoutMs });
  try {
    const res = await fetch(apiUrl('/api/admin/moderation/llm/test'), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': csrfToken,
      },
      credentials: 'include',
      body: JSON.stringify(payload),
      signal,
    });

    const data: unknown = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(getBackendMessage(data) || 'Failed to run LLM moderation test');
    return data as LlmModerationTestResponse;
  } finally {
    cleanup();
  }
}
