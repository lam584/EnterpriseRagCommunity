import { getCsrfToken } from '../utils/csrfUtils';

export type TokenMetricsModelItemDTO = {
  model: string;
  providerId?: string | null;
  tokensIn: number;
  tokensOut: number;
  totalTokens: number;
  cost: string | number;
  priceMissing?: boolean | null;
};

export type TokenMetricsResponseDTO = {
  start: string;
  end: string;
  currency?: string | null;
  totalTokens: number;
  totalCost: string | number;
  items: TokenMetricsModelItemDTO[];
};

export type TokenTimelinePointDTO = {
  time: string;
  tokensIn: number;
  tokensOut: number;
  totalTokens: number;
};

export type TokenTimelineResponseDTO = {
  start: string;
  end: string;
  source: string;
  bucket: string;
  totalTokens: number;
  points: TokenTimelinePointDTO[];
};

export type AdminTokenSourceDTO = {
  taskType: string;
  label: string;
  category: string;
  sortIndex: number;
};

export type AdminLlmPriceConfigDTO = {
  id: number;
  name: string;
  currency: string;
  inputCostPer1k?: string | number | null;
  outputCostPer1k?: string | number | null;
  pricing?: AdminLlmPriceConfigPricingDTO | null;
  updatedAt?: string | null;
};

export type AdminLlmPriceConfigPricingTierDTO = {
  upToTokens: number;
  inputCostPerUnit?: string | number | null;
  outputCostPerUnit?: string | number | null;
};

export type AdminLlmPriceConfigPricingDTO = {
  strategy?: string | null;
  unit?: string | null;
  defaultInputCostPerUnit?: string | number | null;
  defaultOutputCostPerUnit?: string | number | null;
  nonThinkingInputCostPerUnit?: string | number | null;
  nonThinkingOutputCostPerUnit?: string | number | null;
  thinkingInputCostPerUnit?: string | number | null;
  thinkingOutputCostPerUnit?: string | number | null;
  tiers?: AdminLlmPriceConfigPricingTierDTO[] | null;
};

export type AdminLlmPriceConfigUpsertRequest = {
  name: string;
  currency?: string | null;
  inputCostPer1k?: string | number | null;
  outputCostPer1k?: string | number | null;
  pricing?: AdminLlmPriceConfigPricingDTO | null;
};

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  return API_BASE ? `${API_BASE}${path}` : path;
}

function buildQuery(params: Record<string, unknown>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue;
    sp.set(k, String(v));
  }
  const qs = sp.toString();
  return qs ? `?${qs}` : '';
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

export async function adminGetTokenMetrics(
  params: { start?: string; end?: string; source?: string; pricingMode?: string } = {},
): Promise<TokenMetricsResponseDTO> {
  const qs = buildQuery({ start: params.start, end: params.end, source: params.source, pricingMode: params.pricingMode });
  const res = await fetch(apiUrl(`/api/admin/metrics/token${qs}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取 Token 成本统计失败');
  return data as TokenMetricsResponseDTO;
}

export async function adminGetTokenTimeline(params: {
  start?: string;
  end?: string;
  source?: string;
  bucket?: 'AUTO' | 'HOUR' | 'DAY' | string;
} = {}): Promise<TokenTimelineResponseDTO> {
  const qs = buildQuery({ start: params.start, end: params.end, source: params.source, bucket: params.bucket });
  const res = await fetch(apiUrl(`/api/admin/metrics/token/timeline${qs}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取 Token 趋势失败');
  return data as TokenTimelineResponseDTO;
}

export async function adminListTokenSources(): Promise<AdminTokenSourceDTO[]> {
  const res = await fetch(apiUrl('/api/admin/metrics/token/sources'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取场景列表失败');
  return data as AdminTokenSourceDTO[];
}

export async function adminListLlmPriceConfigs(): Promise<AdminLlmPriceConfigDTO[]> {
  const res = await fetch(apiUrl('/api/admin/ai/prices'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取模型单价配置失败');
  return data as AdminLlmPriceConfigDTO[];
}

export async function adminUpsertLlmPriceConfig(payload: AdminLlmPriceConfigUpsertRequest): Promise<AdminLlmPriceConfigDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/ai/prices'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存模型单价配置失败');
  return data as AdminLlmPriceConfigDTO;
}
