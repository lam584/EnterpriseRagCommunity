// src/services/postService.ts

import type { UploadResult } from './uploadService';

export type PostStatus = 'DRAFT' | 'PENDING' | 'PUBLISHED' | 'REJECTED' | 'ARCHIVED';
export type ContentFormat = 'PLAIN' | 'MARKDOWN' | 'HTML';

export interface PostCreateDTO {
  boardId: number;
  title: string;
  content: string;
  contentFormat?: ContentFormat; // default MARKDOWN
  tags?: string[];
  attachmentIds?: number[];
}

export interface PostDTO extends PostCreateDTO {
  id: number;
  tenantId?: number;
  authorId?: number;
  status?: PostStatus;
  authorName?: string;
  boardName?: string;
  attachments?: Array<UploadResult & { fileAssetId?: number } >;
  commentCount?: number;
  reactionCount?: number;
  createdAt: string;
  updatedAt: string;
  publishedAt?: string;
}

export interface FieldError { fieldErrors: Record<string, string> }

const posts: PostDTO[] = [];
let postSeq = 1;

function validate(dto: PostCreateDTO): Record<string, string> {
  const errors: Record<string, string> = {};
  if (dto.boardId === undefined || dto.boardId === null || Number.isNaN(dto.boardId)) {
    errors.boardId = 'Board ID is required';
  }
  if (!dto.title || dto.title.trim() === '') {
    errors.title = 'Title is required';
  } else if (dto.title.length > 191) {
    errors.title = 'Title must not exceed 191 characters';
  }
  if (!dto.content || dto.content.trim() === '') {
    errors.content = 'Content is required';
  }
  if (dto.contentFormat && !['PLAIN','MARKDOWN','HTML'].includes(dto.contentFormat)) {
    errors.contentFormat = 'Invalid content format';
  }
  return errors;
}

export async function createPost(payload: PostCreateDTO): Promise<PostDTO> {
  const errors = validate(payload);
  if (Object.keys(errors).length) {
    throw Object.assign(new Error('Validation failed'), { fieldErrors: errors } as FieldError);
  }
  const now = new Date().toISOString();
  const post: PostDTO = {
    id: postSeq++,
    boardId: payload.boardId,
    title: payload.title.trim(),
    content: payload.content.trim(),
    contentFormat: payload.contentFormat ?? 'MARKDOWN',
    tags: payload.tags?.map(t => t.trim()),
    attachmentIds: payload.attachmentIds,
    status: 'DRAFT',
    createdAt: now,
    updatedAt: now,
  };
  posts.push(post);
  await new Promise(r => setTimeout(r, 300));
  return { ...post };
}

export async function listPosts(): Promise<PostDTO[]> {
  await new Promise(r => setTimeout(r, 150));
  return posts.slice().reverse().map(p => ({ ...p }));
}

export interface PostSearchCriteria {
  keyword?: string;
  boardId?: number;
  authorId?: number;
  status?: PostStatus | 'ALL';
  createdFrom?: string;
  createdTo?: string;
}

function parseDateOnly(s?: string): number | undefined {
  if (!s) return undefined;
  const d = new Date(s);
  const t = d.getTime();
  return Number.isNaN(t) ? undefined : t;
}

export async function searchPosts(criteria: PostSearchCriteria = {}): Promise<PostDTO[]> {
  await new Promise(r => setTimeout(r, 120));
  const { keyword, boardId, authorId, status, createdFrom, createdTo } = criteria;
  const fromMs = parseDateOnly(createdFrom);
  const toMs = parseDateOnly(createdTo);
  const kw = keyword?.trim().toLowerCase();

  let result = posts.slice();
  if (kw && kw.length > 0) {
    result = result.filter(p =>
      p.title.toLowerCase().includes(kw) || p.content.toLowerCase().includes(kw)
    );
  }
  if (boardId != null) {
    result = result.filter(p => p.boardId === boardId);
  }
  if (authorId != null) {
    result = result.filter(p => p.authorId === authorId);
  }
  if (status && status !== 'ALL') {
    result = result.filter(p => (p.status ?? 'DRAFT') === status);
  }
  if (fromMs != null) {
    result = result.filter(p => new Date(p.createdAt).getTime() >= fromMs);
  }
  if (toMs != null) {
    result = result.filter(p => new Date(p.createdAt).getTime() <= toMs);
  }

  return result.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
               .map(p => ({ ...p }));
}

export async function updatePostStatus(postId: number, next: PostStatus): Promise<PostDTO> {
  const p = posts.find(pp => pp.id === postId);
  await new Promise(r => setTimeout(r, 150));
  if (!p) {
    throw new Error('Post not found');
  }
  p.status = next;
  if (next === 'PUBLISHED' && !p.publishedAt) {
    p.publishedAt = new Date().toISOString();
  }
  p.updatedAt = new Date().toISOString();
  return { ...p };
}
