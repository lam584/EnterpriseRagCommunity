// src/services/draftService.ts

import { getCsrfToken } from '../utils/csrfUtils';
import type { ContentFormat, PostCreateDTO } from './postService';
import type { UploadResult } from './uploadService';

export interface DraftAttachment extends UploadResult {
  fileAssetId?: number;
}

export interface PostDraftDTO {
  id: string; // backend uses number, front keeps string
  boardId: number;
  title: string;
  content: string;
  contentFormat: ContentFormat;
  tags?: string[];
  attachments?: DraftAttachment[];
  createdAt: string;
  updatedAt: string;
}

function mapFromApi(d: any): PostDraftDTO {
  const meta = (d && d.metadata && typeof d.metadata === 'object') ? d.metadata : {};
  return {
    id: String(d.id),
    boardId: Number(d.boardId),
    title: String(d.title ?? ''),
    content: String(d.content ?? ''),
    contentFormat: (d.contentFormat ?? 'MARKDOWN') as ContentFormat,
    tags: Array.isArray(meta.tags) ? meta.tags.map(String) : [],
    attachments: Array.isArray(meta.attachments) ? (meta.attachments as any[]).map((a) => ({
      id: Number(a.id),
      fileName: String(a.fileName),
      fileUrl: String(a.fileUrl),
      fileSize: Number(a.fileSize),
      mimeType: String(a.mimeType),
    })) : [],
    createdAt: d.createdAt ? String(d.createdAt) : new Date().toISOString(),
    updatedAt: d.updatedAt ? String(d.updatedAt) : new Date().toISOString(),
  };
}

function toApiBody(draft: PostDraftDTO) {
  return {
    boardId: draft.boardId,
    title: draft.title,
    content: draft.content,
    contentFormat: draft.contentFormat ?? 'MARKDOWN',
    metadata: {
      tags: draft.tags ?? [],
      attachments: draft.attachments ?? [],
    },
  };
}

export function createEmptyDraft(init?: Partial<Omit<PostDraftDTO, 'id' | 'createdAt' | 'updatedAt'>>): PostDraftDTO {
  const t = new Date().toISOString();
  return {
    id: '0',
    boardId: init?.boardId ?? 1,
    title: init?.title ?? '',
    content: init?.content ?? '',
    contentFormat: init?.contentFormat ?? 'MARKDOWN',
    tags: init?.tags ?? [],
    attachments: init?.attachments ?? [],
    createdAt: t,
    updatedAt: t,
  };
}

export async function listDrafts(): Promise<PostDraftDTO[]> {
  const res = await fetch('/api/post-drafts?page=0&size=100', {
    credentials: 'include',
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.message || '加载草稿失败');
  }
  const content = Array.isArray(data.content) ? data.content : (Array.isArray(data) ? data : []);
  return content.map(mapFromApi);
}

export async function getDraft(id: string): Promise<PostDraftDTO | null> {
  const res = await fetch(`/api/post-drafts/${encodeURIComponent(id)}`, {
    credentials: 'include',
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 404) return null;
    throw new Error(data.message || '加载草稿失败');
  }
  return mapFromApi(data);
}

export async function upsertDraft(draft: PostDraftDTO): Promise<PostDraftDTO> {
  const csrfToken = await getCsrfToken();
  const hasId = draft.id && draft.id !== '0' && !Number.isNaN(Number(draft.id));

  const res = await fetch(hasId ? `/api/post-drafts/${encodeURIComponent(draft.id)}` : '/api/post-drafts', {
    method: hasId ? 'PUT' : 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(toApiBody(draft)),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    // validation errors may be {field: msg}
    if (data && typeof data === 'object' && !Array.isArray(data)) {
      const keys = Object.keys(data);
      if (keys.length && keys.every(k => typeof (data as any)[k] === 'string')) {
        throw Object.assign(new Error('Validation failed'), { fieldErrors: data });
      }
    }
    throw new Error(data.message || '保存草稿失败');
  }

  return mapFromApi(data);
}

export async function deleteDraft(id: string): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`/api/post-drafts/${encodeURIComponent(id)}`, {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || '删除草稿失败');
  }
}

export async function clearDrafts(): Promise<void> {
  // 后端不提供批量清空；保留空实现
}

export function draftToPostCreateDTO(draft: PostDraftDTO): PostCreateDTO {
  return {
    boardId: draft.boardId,
    title: draft.title,
    content: draft.content,
    contentFormat: draft.contentFormat,
    tags: draft.tags,
    attachmentIds: draft.attachments?.map((a) => a.id),
  };
}
