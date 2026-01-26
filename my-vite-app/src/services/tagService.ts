// src/services/tagService.ts

import { getCsrfToken } from '../utils/csrfUtils';

export type TagType = 'TOPIC' | 'LANGUAGE' | 'RISK' | 'SYSTEM';

export interface TagCreateDTO {
  tenantId?: number; // nullable per DB
  type: TagType;
  name: string;
  slug: string; // unique with tenantId+type+slug
  description?: string;
  system?: boolean; // 前端字段：映射到后端 isSystem
  active?: boolean; // 前端字段：映射到后端 isActive
}

export interface TagDTO extends TagCreateDTO {
  usageCount: number;
  id: number;
  createdAt: string;
}

export interface FieldError {
  fieldErrors: Record<string, string>;
}

export interface RequestOptions {
  signal?: AbortSignal;
}

const API_BASE = '/api/tags';

export type TagListQuery = {
  page?: number;
  pageSize?: number;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
  tenantId?: number;
  type?: TagType;
  isSystem?: boolean;
  isActive?: boolean;
  keyword?: string;
};

export type SpringPage<T> = {
  content: T[];
  totalElements?: number;
  totalPages?: number;
  number?: number;
  size?: number;
  first?: boolean;
  last?: boolean;
};

type BackendTagsDTO = {
  id: number;
  tenantId?: number;
  type: TagType;
  name: string;
  slug: string;
  description?: string;
  isSystem: boolean;
  isActive: boolean;
  createdAt: string;
  usageCount?: number;
};

function mapToBackendPayload(dto: TagCreateDTO): Record<string, unknown> {
  return {
    tenantId: dto.tenantId ?? null,
    type: dto.type,
    name: dto.name,
    slug: dto.slug,
    description: dto.description ?? null,
    isSystem: dto.system ?? false,
    isActive: dto.active ?? true,
  };
}

function mapFromBackend(dto: BackendTagsDTO): TagDTO {
  return {
    usageCount: typeof dto.usageCount === 'number' ? dto.usageCount : 0,
    id: dto.id,
    tenantId: dto.tenantId ?? undefined,
    type: dto.type,
    name: dto.name,
    slug: dto.slug,
    description: dto.description ?? undefined,
    system: dto.isSystem,
    active: dto.isActive,
    createdAt: dto.createdAt,
  };
}

function getBackendMessage(payload: unknown): string | undefined {
  if (!payload || typeof payload !== 'object') return undefined;
  const m = (payload as { message?: unknown }).message;
  return typeof m === 'string' && m.trim() ? m : undefined;
}

function extractFieldErrors(payload: unknown): Record<string, string> | undefined {
  if (!payload || typeof payload !== 'object') return undefined;
  // GlobalExceptionHandler 对 MethodArgumentNotValidException 返回 Map<field, message>
  const asMap = payload as Record<string, unknown>;
  const allString = Object.values(asMap).every(v => typeof v === 'string');
  return allString ? (asMap as Record<string, string>) : undefined;
}

function isValidSlug(s: string): boolean {
  return /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(s);
}

// 前端本地校验（与 UI 约束一致，后端仍会做校验）
function validateTag(dto: TagCreateDTO): Record<string, string> {
  const errors: Record<string, string> = {};
  if (!dto.type || !['TOPIC', 'LANGUAGE', 'RISK', 'SYSTEM'].includes(dto.type)) {
    errors.type = 'Invalid tag type';
  }
  if (!dto.name || dto.name.trim() === '') {
    errors.name = 'Tag name is required';
  } else if (dto.name.length > 64) {
    errors.name = 'Tag name must not exceed 64 characters';
  }
  if (!dto.slug || dto.slug.trim() === '') {
    errors.slug = 'Slug is required';
  } else if (dto.slug.length > 96) {
    errors.slug = 'Slug must not exceed 96 characters';
  } else if (!isValidSlug(dto.slug)) {
    errors.slug = 'Slug must be kebab-case: lowercase letters, numbers and dashes';
  }
  if (dto.description && dto.description.length > 255) {
    errors.description = 'Description must not exceed 255 characters';
  }
  return errors;
}

export async function createTag(payload: TagCreateDTO): Promise<TagDTO> {
  const errors = validateTag(payload);
  if (Object.keys(errors).length > 0) {
    throw Object.assign(new Error('Validation failed'), { fieldErrors: errors } as FieldError);
  }

  const csrfToken = await getCsrfToken();
  const res = await fetch(API_BASE, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(mapToBackendPayload(payload)),
  });

  if (!res.ok) {
    const data = await res.json().catch(() => undefined);
    const fieldErrors = extractFieldErrors(data);
    if (fieldErrors) throw Object.assign(new Error('Validation failed'), { fieldErrors } as FieldError);
    throw new Error(getBackendMessage(data) ?? '创建失败');
  }

  const dto = (await res.json()) as BackendTagsDTO;
  return mapFromBackend(dto);
}

export async function listTagsPage(query: TagListQuery = {}, options: RequestOptions = {}): Promise<SpringPage<TagDTO>> {
  const url = new URL(API_BASE, window.location.origin);
  url.searchParams.set('page', String(query.page ?? 1));
  url.searchParams.set('pageSize', String(query.pageSize ?? 25));
  url.searchParams.set('sortBy', query.sortBy ?? 'createdAt');
  url.searchParams.set('sortOrder', query.sortOrder ?? 'desc');
  if (query.tenantId != null) url.searchParams.set('tenantId', String(query.tenantId));
  if (query.type) url.searchParams.set('type', query.type);
  if (query.isSystem != null) url.searchParams.set('isSystem', String(query.isSystem));
  if (query.isActive != null) url.searchParams.set('isActive', String(query.isActive));
  if (query.keyword && query.keyword.trim()) url.searchParams.set('keyword', query.keyword.trim());

  const res = await fetch(url.toString(), { credentials: 'include', signal: options.signal });
  if (!res.ok) {
    const data = await res.json().catch(() => ({ message: '加载失败' }));
    throw new Error(getBackendMessage(data) ?? '加载失败');
  }

  const page = (await res.json()) as SpringPage<BackendTagsDTO>;
  return {
    ...page,
    content: (page.content ?? []).map(mapFromBackend),
  };
}

export async function listTags(query: TagListQuery = {}, options: RequestOptions = {}): Promise<TagDTO[]> {
  const page = await listTagsPage(query, options);
  return page.content ?? [];
}

export async function incrementUsage(): Promise<void> {
  // 目前后端 TagsEntity 没有 usageCount 字段（SQL 也没有）。
  // 这里保持接口不崩，但不执行任何请求。
  // 如需支持使用量，请在 DB/Entity/DTO 层新增字段与接口（当前规则禁止修改底层）。
  return;
}

export interface TagUpdateDTO {
  type?: TagType;
  name?: string;
  slug?: string;
  description?: string;
  system?: boolean;
  active?: boolean;
}

export async function updateTag(id: number, payload: TagUpdateDTO): Promise<TagDTO> {
  // 兼容：沿用前端校验逻辑（仅对提供的字段做格式限制）
  const merged: TagCreateDTO = {
    tenantId: undefined,
    type: payload.type ?? 'TOPIC',
    name: payload.name ?? 'x',
    slug: payload.slug ?? 'x',
    description: payload.description,
    system: payload.system,
    active: payload.active,
  };
  const errs = validateTag(merged);
  // 去掉 name/slug/type 的强制必填错误（因为 update 是可选字段）
  delete errs.name;
  delete errs.slug;
  delete errs.type;
  if (payload.name && payload.name.length > 64) errs.name = 'Tag name must not exceed 64 characters';
  if (payload.slug && payload.slug.length > 96) errs.slug = 'Slug must not exceed 96 characters';
  if (payload.slug && !isValidSlug(payload.slug)) errs.slug = 'Slug must be kebab-case: lowercase letters, numbers and dashes';
  if (Object.keys(errs).length) {
    throw Object.assign(new Error('Validation failed'), { fieldErrors: errs } as FieldError);
  }

  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE}/${id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({
      // 后端 update DTO 使用 Optional 字段，这里只传存在的字段
      ...(payload.type != null ? { type: payload.type } : {}),
      ...(payload.name != null ? { name: payload.name } : {}),
      ...(payload.slug != null ? { slug: payload.slug } : {}),
      ...(payload.description != null ? { description: payload.description } : {}),
      ...(payload.system != null ? { isSystem: payload.system } : {}),
      ...(payload.active != null ? { isActive: payload.active } : {}),
    }),
  });

  if (!res.ok) {
    const data = await res.json().catch(() => undefined);
    const fieldErrors = extractFieldErrors(data);
    if (fieldErrors) throw Object.assign(new Error('Validation failed'), { fieldErrors } as FieldError);
    throw new Error(getBackendMessage(data) ?? '更新失败');
  }

  const dto = (await res.json()) as BackendTagsDTO;
  return mapFromBackend(dto);
}

export async function deleteTag(id: number): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE}/${id}`, {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({ message: '删除失败' }));
    throw new Error(getBackendMessage(data) ?? '删除失败');
  }
}

// helper: slugify (export for UI use)
export function slugify(input: string): string {
  const s = input
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
  if (s) return s;
  let h = 0;
  for (let i = 0; i < input.length; i++) {
    h = (h * 31 + input.charCodeAt(i)) | 0;
  }
  const u = (h >>> 0).toString(36);
  return `tag-${u}`;
}
