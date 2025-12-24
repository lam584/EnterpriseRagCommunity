import { getCsrfToken } from '../utils/csrfUtils';

export interface RolePermissionUpsertDTO {
  roleId: number;
  permissionId: number;
  allow: boolean;
}

export interface RolePermissionViewDTO {
  roleId: number;
  permissionId: number;
  allow: boolean;
}

// 后端：/api/admin/role-permissions
const API_BASE_URL = '/api/admin/role-permissions';

export async function listRolePermissionsByRole(roleId: number): Promise<RolePermissionViewDTO[]> {
  const res = await fetch(`${API_BASE_URL}/role/${roleId}`, {
    method: 'GET',
    credentials: 'include',
  });

  if (!res.ok) {
    const errorData = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const message = typeof errorData.message === 'string' ? errorData.message : undefined;
    throw new Error(message || 'Failed to list role permissions');
  }

  return res.json();
}

export async function upsertRolePermission(dto: RolePermissionUpsertDTO): Promise<RolePermissionViewDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(API_BASE_URL, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(dto),
  });

  if (!res.ok) {
    const errorData = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const message = typeof errorData.message === 'string' ? errorData.message : undefined;
    throw new Error(message || 'Failed to upsert role permission');
  }

  return res.json();
}

export async function deleteRolePermission(roleId: number, permissionId: number): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(
    `${API_BASE_URL}?roleId=${encodeURIComponent(String(roleId))}&permissionId=${encodeURIComponent(String(permissionId))}`,
    {
      method: 'DELETE',
      headers: {
        'X-XSRF-TOKEN': csrfToken,
      },
      credentials: 'include',
    },
  );

  if (!res.ok) {
    const errorData = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const message = typeof errorData.message === 'string' ? errorData.message : undefined;
    throw new Error(message || 'Failed to delete role permission');
  }
}
