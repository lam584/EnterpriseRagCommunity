import { getCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage } from './serviceErrorUtils';
import { buildPageQuery } from './servicePagingUtils';
import { serviceApiUrl } from './serviceUrlUtils';
import type { SpringPage } from '../types/page';

const apiUrl = serviceApiUrl;

export type ChatContextGovernanceConfigDTO = {
  enabled?: boolean | null;

  maxPromptTokens?: number | null;
  reserveAnswerTokens?: number | null;
  maxPromptChars?: number | null;
  perMessageMaxTokens?: number | null;
  keepLastMessages?: number | null;

  allowDropRagContext?: boolean | null;

  compressionEnabled?: boolean | null;
  compressionTriggerTokens?: number | null;
  compressionKeepLastMessages?: number | null;
  compressionPerMessageSnippetChars?: number | null;
  compressionMaxChars?: number | null;

  maxFiles?: number | null;
  perFileMaxChars?: number | null;
  totalFilesMaxChars?: number | null;

  logEnabled?: boolean | null;
  logSampleRate?: number | null;
  logMaxDays?: number | null;
};

export type AdminChatContextEventLogDTO = {
  id: number;
  userId?: number | null;
  sessionId?: number | null;
  questionMessageId?: number | null;
  kind?: string | null;
  reason?: string | null;
  beforeTokens?: number | null;
  afterTokens?: number | null;
  beforeChars?: number | null;
  afterChars?: number | null;
  latencyMs?: number | null;
  createdAt?: string | null;
};

export type AdminChatContextEventDetailDTO = AdminChatContextEventLogDTO & {
  targetPromptTokens?: number | null;
  reserveAnswerTokens?: number | null;
  detailJson?: Record<string, unknown> | null;
};

export async function adminGetChatContextConfig(): Promise<ChatContextGovernanceConfigDTO> {
  const res = await fetch(apiUrl('api/admin/ai/chat-context/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取对话上下文治理配置失败');
  return data as ChatContextGovernanceConfigDTO;
}

export async function adminUpdateChatContextConfig(payload: ChatContextGovernanceConfigDTO): Promise<ChatContextGovernanceConfigDTO> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/ai/chat-context/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrf,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存对话上下文治理配置失败');
  return data as ChatContextGovernanceConfigDTO;
}

export async function adminListChatContextLogs(params?: {
  page?: number;
  size?: number;
  from?: string;
  to?: string;
}): Promise<SpringPage<AdminChatContextEventLogDTO>> {
  const res = await fetch(apiUrl(`/api/admin/ai/chat-context/logs?${buildPageQuery(params)}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取对话上下文治理日志失败');
  return data as SpringPage<AdminChatContextEventLogDTO>;
}

export async function adminGetChatContextLog(id: number): Promise<AdminChatContextEventDetailDTO> {
  const res = await fetch(apiUrl(`/api/admin/ai/chat-context/logs/${id}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取对话上下文治理日志详情失败');
  return data as AdminChatContextEventDetailDTO;
}
