import { getCsrfToken } from '../utils/csrfUtils';

export interface RolePermissionUpsertDTO {
  roleId: number;
  roleName?: string;
  permissionId: number;
  allow: boolean;
}

export interface RolePermissionViewDTO {
  roleId: number;
  roleName?: string;
  permissionId: number;
  allow: boolean;
}

export interface RoleSummaryDTO {
  roleId: number;
  roleName: string | null;
}

// 后端：/api/admin/role-permissions
const API_BASE_URL = '/api/admin/role-permissions';

export async function listRoleIds(): Promise<number[]> {
  const res = await fetch(`${API_BASE_URL}/roles`, {
    method: 'GET',
    credentials: 'include',
  });

  if (!res.ok) {
    const errorData = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const message = typeof errorData.message === 'string' ? errorData.message : undefined;
    throw new Error(message || 'Failed to list role ids');
  }

  return res.json();
}

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

/**
 * 创建新角色并写入权限矩阵（roleId 由后端自动生成）。
 * - list 传入 allow/deny 项；不传的权限默认为 UNSET。
 */
export async function createRoleWithMatrix(list: Omit<RolePermissionUpsertDTO, 'roleId'>[]): Promise<RolePermissionViewDTO[]> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE_URL}/role`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(list),
  });

  if (!res.ok) {
    const errorData = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const message = typeof errorData.message === 'string' ? errorData.message : undefined;
    throw new Error(message || 'Failed to create role');
  }

  return res.json();
}

/** 覆盖更新某 roleId 的权限矩阵（全量提交 allow/deny 配置；空数组表示清空）。 */
export async function replaceRolePermissions(roleId: number, list: RolePermissionUpsertDTO[]): Promise<RolePermissionViewDTO[]> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE_URL}/role/${encodeURIComponent(String(roleId))}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(list),
  });

  if (!res.ok) {
    const errorData = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const message = typeof errorData.message === 'string' ? errorData.message : undefined;
    throw new Error(message || 'Failed to replace role permissions');
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

export async function clearRolePermissions(roleId: number): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE_URL}/role/${encodeURIComponent(String(roleId))}`, {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });

  if (!res.ok) {
    const errorData = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const message = typeof errorData.message === 'string' ? errorData.message : undefined;
    throw new Error(message || 'Failed to clear role permissions');
  }
}

export async function listRoleSummaries(): Promise<RoleSummaryDTO[]> {
  const res = await fetch(`${API_BASE_URL}/role-summaries`, {
    method: 'GET',
    credentials: 'include',
  });

  if (!res.ok) {
    const errorData = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    const message = typeof errorData.message === 'string' ? errorData.message : undefined;
    throw new Error(message || 'Failed to list role summaries');
  }

  return res.json();
}
