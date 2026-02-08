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

export type AiProviderDTO = {
  id?: string | null;
  name?: string | null;
  type?: string | null;
  baseUrl?: string | null;
  apiKey?: string | null;
  defaultChatModel?: string | null;
  defaultEmbeddingModel?: string | null;
  defaultRerankModel?: string | null;
  rerankEndpointPath?: string | null;
  supportsVision?: boolean | null;
  extraHeaders?: Record<string, string> | null;
  connectTimeoutMs?: number | null;
  readTimeoutMs?: number | null;
  maxConcurrent?: number | null;
  enabled?: boolean | null;
};

export type AiProvidersConfigDTO = {
  activeProviderId?: string | null;
  providers?: AiProviderDTO[] | null;
};

export async function adminGetAiProvidersConfig(): Promise<AiProvidersConfigDTO> {
  const res = await fetch(apiUrl('/api/admin/ai/providers/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取模型来源配置失败');
  return data as AiProvidersConfigDTO;
}

export async function adminUpdateAiProvidersConfig(payload: AiProvidersConfigDTO): Promise<AiProvidersConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/ai/providers/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存模型来源配置失败');
  return data as AiProvidersConfigDTO;
}

export type AiProviderModelDTO = {
  purpose?: string | null;
  modelName?: string | null;
  enabled?: boolean | null;
};

export type AiProviderModelsDTO = {
  providerId?: string | null;
  models?: AiProviderModelDTO[] | null;
};

export type AiUpstreamModelsDTO = {
  providerId?: string | null;
  models?: string[] | null;
};

export type AiUpstreamModelsPreviewRequestDTO = {
  providerId?: string | null;
  baseUrl?: string | null;
  apiKey?: string | null;
  extraHeaders?: Record<string, string> | null;
  connectTimeoutMs?: number | null;
  readTimeoutMs?: number | null;
};

export async function adminListProviderModels(providerId: string): Promise<AiProviderModelsDTO> {
  const res = await fetch(apiUrl(`/api/admin/ai/providers/${encodeURIComponent(providerId)}/models`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取模型列表失败');
  return data as AiProviderModelsDTO;
}

export async function adminAddProviderModel(providerId: string, purpose: string, modelName: string): Promise<AiProviderModelsDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/ai/providers/${encodeURIComponent(providerId)}/models`), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ purpose, modelName }),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '添加模型失败');
  return data as AiProviderModelsDTO;
}

export async function adminDeleteProviderModel(providerId: string, purpose: string, modelName: string): Promise<AiProviderModelsDTO> {
  const csrfToken = await getCsrfToken();
  const qs = new URLSearchParams({ purpose, modelName });
  const res = await fetch(apiUrl(`/api/admin/ai/providers/${encodeURIComponent(providerId)}/models?${qs.toString()}`), {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '删除模型失败');
  return data as AiProviderModelsDTO;
}

export async function adminFetchUpstreamModels(providerId: string): Promise<AiUpstreamModelsDTO> {
  const res = await fetch(apiUrl(`/api/admin/ai/providers/${encodeURIComponent(providerId)}/upstream-models`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取 /v1/models 失败');
  return data as AiUpstreamModelsDTO;
}

export async function adminPreviewUpstreamModels(payload: AiUpstreamModelsPreviewRequestDTO): Promise<AiUpstreamModelsDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/ai/providers/upstream-models/preview'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取 /v1/models 失败');
  return data as AiUpstreamModelsDTO;
}
