// src/services/tagService.ts

export type TagType = 'TOPIC' | 'LANGUAGE' | 'RISK' | 'SYSTEM';

export interface TagCreateDTO {
  tenantId?: number; // nullable per DB
  type: TagType;
  name: string;
  slug: string; // unique with tenantId+type
  description?: string;
  system?: boolean; // default false
  active?: boolean; // default true
}

export interface TagDTO extends TagCreateDTO {
  id: number;
  usageCount?: number;
  createdAt: string;
}

export interface FieldError {
  fieldErrors: Record<string, string>;
}

const tags: TagDTO[] = [];
let tagSeq = 1;

function isValidSlug(s: string): boolean {
  return /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(s);
}

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
  // enforce unique (tenantId, type, slug)
  if (tags.some(t => (t.tenantId ?? null) === (payload.tenantId ?? null) && t.type === payload.type && t.slug === payload.slug)) {
    throw Object.assign(new Error('Tag already exists'), { fieldErrors: { slug: 'Duplicate slug under same tenant and type' } } as FieldError);
  }
  const now = new Date().toISOString();
  const tag: TagDTO = {
    id: tagSeq++,
    tenantId: payload.tenantId,
    type: payload.type,
    name: payload.name.trim(),
    slug: payload.slug.trim(),
    description: payload.description?.trim(),
    system: payload.system ?? false,
    active: payload.active ?? true,
    usageCount: 0,
    createdAt: now,
  };
  tags.push(tag);
  await new Promise(r => setTimeout(r, 200));
  return { ...tag };
}

export async function listTags(): Promise<TagDTO[]> {
  await new Promise(r => setTimeout(r, 100));
  return tags.map(t => ({ ...t }));
}

export async function incrementUsage(tagNames: string[]): Promise<void> {
  tagNames.forEach(name => {
    const t = tags.find(tt => tt.name.toLowerCase() === name.toLowerCase());
    if (t) t.usageCount = (t.usageCount ?? 0) + 1;
  });
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
  const existing = tags.find(t => t.id === id);
  if (!existing) throw new Error('Tag not found');
  const merged: TagCreateDTO = {
    tenantId: existing.tenantId,
    type: payload.type ?? existing.type,
    name: payload.name?.trim() ?? existing.name,
    slug: payload.slug?.trim() ?? existing.slug,
    description: payload.description?.trim() ?? existing.description,
    system: payload.system ?? existing.system ?? false,
    active: payload.active ?? existing.active ?? true,
  };
  const errors = validateTag(merged);
  if (Object.keys(errors).length > 0) {
    throw Object.assign(new Error('Validation failed'), { fieldErrors: errors } as FieldError);
  }
  // Unique check excluding self
  if (tags.some(t => t.id !== id && (t.tenantId ?? null) === (merged.tenantId ?? null) && t.type === merged.type && t.slug === merged.slug)) {
    throw Object.assign(new Error('Tag already exists'), { fieldErrors: { slug: 'Duplicate slug under same tenant and type' } } as FieldError);
  }
  existing.type = merged.type;
  existing.name = merged.name;
  existing.slug = merged.slug;
  existing.description = merged.description;
  existing.system = merged.system ?? false;
  existing.active = merged.active ?? true;
  await new Promise(r => setTimeout(r, 150));
  return { ...existing };
}

export async function deleteTag(id: number): Promise<void> {
  const idx = tags.findIndex(t => t.id === id);
  if (idx === -1) throw new Error('Tag not found');
  tags.splice(idx, 1);
  await new Promise(r => setTimeout(r, 120));
}

// helper: slugify (export for UI use)
export function slugify(input: string): string {
  return input
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
}
