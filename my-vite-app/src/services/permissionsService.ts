import { getCsrfToken } from '../utils/csrfUtils';

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
    throw new Error('Failed to fetch permissions');
  }
  return res.json();
}

export async function getPermissionById(id: number): Promise<PermissionsUpdateDTO> {
  const res = await fetch(`${API_BASE_URL}/${id}`, {
    method: 'GET',
    credentials: 'include'
  });

  if (!res.ok) {
    throw new Error('Failed to fetch permission details');
  }
  return res.json();
}

export async function createPermission(data: PermissionsCreateDTO): Promise<PermissionsUpdateDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(API_BASE_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken
    },
    credentials: 'include',
    body: JSON.stringify(data)
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({}));
    throw new Error(errorData.message || 'Failed to create permission');
  }
  return res.json();
}

export async function updatePermission(data: PermissionsUpdateDTO): Promise<PermissionsUpdateDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(API_BASE_URL, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken
    },
    credentials: 'include',
    body: JSON.stringify(data)
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({}));
    throw new Error(errorData.message || 'Failed to update permission');
  }
  return res.json();
}

export async function deletePermission(id: number): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE_URL}/${id}`, {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken
    },
    credentials: 'include'
  });

  if (!res.ok) {
    throw new Error('Failed to delete permission');
  }
}
