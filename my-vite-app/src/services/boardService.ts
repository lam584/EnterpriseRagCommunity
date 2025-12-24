// src/services/boardService.ts
import { getCsrfToken } from '../utils/csrfUtils';

export interface BoardCreateDTO {
  tenantId?: number;
  parentId?: number | null;
  name: string;
  description?: string;
  visible?: boolean;
  sortOrder?: number;
}

export interface BoardUpdateDTO {
  id: number;
  tenantId?: number;
  parentId?: number | null;
  name?: string;
  description?: string;
  visible?: boolean;
  sortOrder?: number;
}

export interface BoardDTO {
  id: number;
  tenantId?: number;
  parentId?: number;
  name: string;
  description?: string;
  visible?: boolean;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface BoardQueryDTO {
  id?: number;
  tenantId?: number;
  parentId?: number;
  name?: string;
  nameLike?: string;
  description?: string;
  visible?: boolean;
  sortOrder?: number;
  sortOrderFrom?: number;
  sortOrderTo?: number;
  createdFrom?: string;
  createdTo?: string;
  updatedFrom?: string;
  updatedTo?: string;
  page?: number;
  pageSize?: number;
  sortBy?: string;
  sortOrderDirection?: string;
}

export interface FieldError {
  fieldErrors: Record<string, string>;
}

// Helper to build query string
function buildQueryString(query: BoardQueryDTO): string {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.append(key, String(value));
    }
  });
  return params.toString();
}

export async function createBoard(payload: BoardCreateDTO): Promise<BoardDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/boards', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({}));
    throw Object.assign(new Error(errorData.message || '创建失败'), { fieldErrors: errorData } as FieldError);
  }

  return res.json();
}

export async function listBoards(): Promise<BoardDTO[]> {
  // Default list, fetch all (or first page with large size)
  return searchBoards({ page: 1, pageSize: 1000 });
}

export async function updateBoard(payload: BoardUpdateDTO): Promise<BoardDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`/api/boards/${payload.id}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({}));
    throw Object.assign(new Error(errorData.message || '更新失败'), { fieldErrors: errorData } as FieldError);
  }

  return res.json();
}

export async function deleteBoard(id: number): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`/api/boards/${id}`, {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken
    },
    credentials: 'include'
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({}));
    throw new Error(errorData.message || '删除失败');
  }
}

export async function searchBoards(query: BoardQueryDTO): Promise<BoardDTO[]> {
  const queryString = buildQueryString(query);
  const res = await fetch(`/api/boards?${queryString}`, {
    method: 'GET',
    credentials: 'include'
  });

  if (!res.ok) {
    throw new Error('查询失败');
  }

  const data = await res.json();
  // Backend returns Page<BoardsDTO>, so we need data.content
  return data.content || [];
}
