import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';
const apiUrl = serviceApiUrl;


export type AdminAiModelProbeKind = 'CHAT' | 'EMBEDDING' | 'RERANK';

export type AdminAiModelProbeResultDTO = {
  providerId?: string | null;
  modelName?: string | null;
  kind?: string | null;
  ok?: boolean | null;
  latencyMs?: number | null;
  errorMessage?: string | null;
  usedProviderId?: string | null;
  usedModel?: string | null;
};

export async function adminProbeModel(
  kind: AdminAiModelProbeKind,
  providerId: string,
  modelName: string,
  opts?: { timeoutMs?: number; signal?: AbortSignal },
): Promise<AdminAiModelProbeResultDTO> {
  const qs = new URLSearchParams();
  qs.set('kind', kind);
  qs.set('providerId', providerId);
  qs.set('modelName', modelName);
  if (opts?.timeoutMs != null) qs.set('timeoutMs', String(opts.timeoutMs));
  const res = await fetch(apiUrl(`/api/admin/ai/models/probe?${qs.toString()}`), {
    method: 'GET',
    credentials: 'include',
    signal: opts?.signal,
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '模型探活失败');
  return data as AdminAiModelProbeResultDTO;
}

