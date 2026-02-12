import { getCsrfToken } from '../utils/csrfUtils';
import { toApiError } from './apiError';

export interface PermissionsCreateDTO {
  resource: string;
  action: string;
  description?: string;
}

export interface PermissionsUpdateDTO {
  id: number;
  resource?: string;
  action?: string;
  description?: string;
}

export interface PermissionsQueryDTO {
  pageNum?: number;
  pageSize?: number;
  orderBy?: string;
  sort?: string;
  id?: number;
  resource?: string;
  action?: string;
  description?: string;
}

export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

const API_BASE_URL = '/api/admin/permissions';

export async function queryPermissions(query: PermissionsQueryDTO): Promise<Page<PermissionsUpdateDTO>> {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.append(key, String(value));
    }
  });

  const res = await fetch(`${API_BASE_URL}?${params.toString()}`, {
    method: 'GET',
    credentials: 'include'
  });

  if (!res.ok) {
    throw await toApiError(res, '获取权限列表失败');
  }
  return res.json();
}

export async function getPermissionById(id: number): Promise<PermissionsUpdateDTO> {
  const res = await fetch(`${API_BASE_URL}/${id}`, {
    method: 'GET',
    credentials: 'include'
  });

  if (!res.ok) {
    throw await toApiError(res, '获取权限详情失败');
  }
  return res.json();
}

export async function createPermission(
  data: PermissionsCreateDTO,
  opts?: { adminReason?: string },
): Promise<PermissionsUpdateDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(API_BASE_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      ...(opts?.adminReason ? { 'X-Admin-Reason': opts.adminReason } : {}),
    },
    credentials: 'include',
    body: JSON.stringify(data)
  });

  if (!res.ok) {
    throw await toApiError(res, '创建权限失败');
  }
  return res.json();
}

export async function updatePermission(
  data: PermissionsUpdateDTO,
  opts?: { adminReason?: string },
): Promise<PermissionsUpdateDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(API_BASE_URL, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      ...(opts?.adminReason ? { 'X-Admin-Reason': opts.adminReason } : {}),
    },
    credentials: 'include',
    body: JSON.stringify(data)
  });

  if (!res.ok) {
    throw await toApiError(res, '更新权限失败');
  }
  return res.json();
}

export async function deletePermission(id: number, opts?: { adminReason?: string }): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE_URL}/${id}`, {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
      ...(opts?.adminReason ? { 'X-Admin-Reason': opts.adminReason } : {}),
    },
    credentials: 'include'
  });

  if (!res.ok) {
    throw await toApiError(res, '删除权限失败');
  }
}
