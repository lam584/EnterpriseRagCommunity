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

function normTaskType(s: string | null | undefined): string {
  return String(s || '').trim().toUpperCase();
}

export type AdminLlmRoutingDecisionEventDTO = {
  tsMs: number;
  kind: string;
  taskType: string | null;
  attempt: number | null;
  taskId: string | null;
  providerId: string | null;
  modelName: string | null;
  ok: boolean | null;
  errorCode: string | null;
  errorMessage: string | null;
  latencyMs: number | null;
  apiSource?: string | null;
};

export type AdminLlmRoutingDecisionResponseDTO = {
  checkedAtMs: number;
  items: AdminLlmRoutingDecisionEventDTO[];
};

export async function adminGetLlmRoutingDecisions(params?: {
  taskType?: string | null | undefined;
  limit?: number | null | undefined;
}): Promise<AdminLlmRoutingDecisionResponseDTO> {
  const tt = params?.taskType ? normTaskType(params.taskType) : '';
  const lim = typeof params?.limit === 'number' && Number.isFinite(params.limit) ? Math.trunc(params.limit) : undefined;
  const qs = new URLSearchParams();
  if (tt) qs.set('taskType', tt);
  if (lim != null) qs.set('limit', String(Math.max(1, Math.min(10_000, lim))));

  const res = await fetch(apiUrl(`/api/admin/metrics/llm-routing/decisions${qs.toString() ? `?${qs}` : ''}`), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取路由事件失败');
  const dto = data as AdminLlmRoutingDecisionResponseDTO;
  return {
    checkedAtMs: typeof (dto as any)?.checkedAtMs === 'number' ? (dto as any).checkedAtMs : Date.now(),
    items: Array.isArray((dto as any)?.items) ? ((dto as any).items as AdminLlmRoutingDecisionEventDTO[]) : [],
  };
}

export function adminOpenLlmRoutingEventSource(params?: { taskType?: string | null | undefined }): EventSource {
  const tt = params?.taskType ? normTaskType(params.taskType) : '';
  const qs = new URLSearchParams();
  if (tt) qs.set('taskType', tt);
  const url = apiUrl(`/api/admin/metrics/llm-routing/stream${qs.toString() ? `?${qs}` : ''}`);
  const es: EventSource = (() => {
    try {
      return new (window as any).EventSource(url, { withCredentials: true });
    } catch {
      return new EventSource(url);
    }
  })();
  return es;
}

