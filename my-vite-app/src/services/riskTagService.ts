import { getCsrfToken } from '../utils/csrfUtils';
import type { SpringPage } from '../types/page';
import { slugify } from './tagService';
import { serviceApiUrl } from './serviceUrlUtils';
import { getBackendMessage } from './serviceErrorUtils';

export type RiskTagDTO = {
  id: number;
  tenantId?: number;
  name: string;
  slug: string;
  description?: string;
  system: boolean;
  active: boolean;
  threshold?: number;
  createdAt: string;
  usageCount: number;
};

export type RiskTagListQuery = {
  page?: number;
  pageSize?: number;
  keyword?: string;
};

const apiUrl = serviceApiUrl;


type BackendTagsDTO = {
  id: number;
  tenantId?: number | null;
  type: 'RISK';
  name: string;
  slug: string;
  description?: string | null;
  isSystem: boolean;
  isActive: boolean;
  threshold?: number | null;
  createdAt: string;
  usageCount?: number | null;
};

function mapFromBackend(dto: BackendTagsDTO): RiskTagDTO {
  return {
    id: dto.id,
    tenantId: dto.tenantId ?? undefined,
    name: dto.name,
    slug: dto.slug,
    description: dto.description ?? undefined,
    system: dto.isSystem,
    active: dto.isActive,
    threshold: typeof dto.threshold === 'number' ? dto.threshold : undefined,
    createdAt: dto.createdAt,
    usageCount: typeof dto.usageCount === 'number' ? dto.usageCount : 0,
  };
}

export async function listRiskTagsPage(query: RiskTagListQuery = {}): Promise<SpringPage<RiskTagDTO>> {
  const url = new URL(apiUrl('/api/admin/risk-tags'), window.location.origin);
  url.searchParams.set('page', String(query.page ?? 1));
  url.searchParams.set('pageSize', String(query.pageSize ?? 25));
  url.searchParams.set('sortBy', 'createdAt');
  url.searchParams.set('sortOrder', 'desc');
  if (query.keyword && query.keyword.trim()) url.searchParams.set('keyword', query.keyword.trim());

  const res = await fetch(url.toString(), { credentials: 'include' });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '加载风险标签失败');
  const page = data as SpringPage<BackendTagsDTO>;
  return { ...page, content: (page.content ?? []).map(mapFromBackend) };
}

export type RiskTagCreatePayload = {
  tenantId?: number;
  name: string;
  slug?: string;
  description?: string;
  active?: boolean;
  threshold?: number;
};

export async function createRiskTag(payload: RiskTagCreatePayload): Promise<RiskTagDTO> {
  const csrfToken = await getCsrfToken();
  const slug = payload.slug?.trim() ? payload.slug.trim() : slugify(payload.name);
  const res = await fetch(apiUrl('/api/admin/risk-tags'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({
      tenantId: payload.tenantId ?? 1,
      type: 'RISK',
      name: payload.name,
      slug,
      description: payload.description ?? null,
      isSystem: false,
      isActive: payload.active ?? true,
      threshold: payload.threshold,
    }),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '创建风险标签失败');
  return mapFromBackend(data as BackendTagsDTO);
}

export type RiskTagUpdatePayload = {
  name?: string;
  slug?: string;
  description?: string | null;
  active?: boolean;
  threshold?: number;
};

export async function updateRiskTag(id: number, payload: RiskTagUpdatePayload): Promise<RiskTagDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/risk-tags/${id}`), {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({
      ...(payload.name != null ? { name: payload.name } : {}),
      ...(payload.slug != null ? { slug: payload.slug } : {}),
      ...(payload.description !== undefined ? { description: payload.description } : {}),
      ...(payload.active != null ? { isActive: payload.active } : {}),
      ...(payload.threshold !== undefined ? { threshold: payload.threshold } : {}),
    }),
  });
  const data: unknown = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(getBackendMessage(data) || '更新风险标签失败');
  return mapFromBackend(data as BackendTagsDTO);
}

export async function deleteRiskTag(id: number): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(apiUrl(`/api/admin/risk-tags/${id}`), {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  if (!res.ok) {
    const data: unknown = await res.json().catch(() => ({}));
    throw new Error(getBackendMessage(data) || '删除风险标签失败');
  }
}
