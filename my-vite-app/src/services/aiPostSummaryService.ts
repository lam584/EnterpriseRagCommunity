import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';
const apiUrl = serviceApiUrl;


export type PostSummaryPublicConfigDTO = {
  enabled: boolean;
};

export type PostAiSummaryDTO = {
  postId: number;
  enabled: boolean;
  status: string;
  summaryTitle?: string | null;
  summaryText?: string | null;
  model?: string | null;
  generatedAt?: string | null;
  latencyMs?: number | null;
  errorMessage?: string | null;
};

export async function getPostSummaryConfig(): Promise<PostSummaryPublicConfigDTO> {
  const res = await fetch(apiUrl('/api/ai/posts/summary/config'), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取摘要配置失败');
  return data as PostSummaryPublicConfigDTO;
}

export async function getPostAiSummary(postId: number): Promise<PostAiSummaryDTO> {
  const res = await fetch(apiUrl(`/api/ai/posts/${postId}/summary`), {
    method: 'GET',
    credentials: 'include',
  });

  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取AI摘要失败');
  return data as PostAiSummaryDTO;
}

