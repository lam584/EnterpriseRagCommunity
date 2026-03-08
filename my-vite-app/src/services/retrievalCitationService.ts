import { getCsrfToken } from '../utils/csrfUtils';

function getApiBase(): string {
  const globalBase = (globalThis as unknown as { __VITE_API_BASE_URL__?: string }).__VITE_API_BASE_URL__;
  if (typeof globalBase === 'string') return globalBase;
  return (((import.meta as unknown as { env?: Record<string, unknown> })?.env?.VITE_API_BASE_URL as string) ?? '') || '';
}

function apiUrl(path: string): string {
  if (!path.startsWith('/')) path = `/${path}`;
  const base = getApiBase();
  return base ? `${base}${path}` : path;
}

function getBackendMessage(data: unknown): string | undefined {
  if (data && typeof data === 'object' && 'message' in data && typeof (data as { message?: unknown }).message === 'string') {
    return (data as { message: string }).message;
  }
  return undefined;
}

export type CitationConfigDTO = {
  enabled?: boolean | null;
  citationMode?: 'MODEL_INLINE' | 'SOURCES_SECTION' | 'BOTH' | string | null;
  instructionTemplate?: string | null;

  sourcesTitle?: string | null;
  maxSources?: number | null;

  includeUrl?: boolean | null;
  includeScore?: boolean | null;
  includeTitle?: boolean | null;
  includePostId?: boolean | null;
  includeChunkIndex?: boolean | null;

  postUrlTemplate?: string | null;
};

export type CitationTestItem = {
  postId?: number | null;
  chunkIndex?: number | null;
  score?: number | null;
  title?: string | null;
};

export type CitationTestRequest = {
  useSavedConfig?: boolean | null;
  config?: CitationConfigDTO | null;
  items?: CitationTestItem[] | null;
};

export type CitationSource = {
  index: number;
  postId?: number | null;
  chunkIndex?: number | null;
  score?: number | null;
  title?: string | null;
  url?: string | null;
};

export type CitationTestResponse = {
  config?: CitationConfigDTO | null;
  instructionPreview?: string | null;
  sourcesPreview?: string | null;
  sources?: CitationSource[] | null;
};

export async function adminGetCitationConfig(): Promise<CitationConfigDTO> {
  const res = await fetch(apiUrl('api/admin/retrieval/citation/config'), {
    method: 'GET',
    credentials: 'include',
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '获取引用配置失败');
  return data as CitationConfigDTO;
}

export async function adminUpdateCitationConfig(payload: CitationConfigDTO): Promise<CitationConfigDTO> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/retrieval/citation/config'), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrf,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '保存引用配置失败');
  return data as CitationConfigDTO;
}

export async function adminTestCitation(payload: CitationTestRequest): Promise<CitationTestResponse> {
  const csrf = await getCsrfToken();
  const res = await fetch(apiUrl('/api/admin/retrieval/citation/test'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrf,
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '引用配置测试失败');
  return data as CitationTestResponse;
}
