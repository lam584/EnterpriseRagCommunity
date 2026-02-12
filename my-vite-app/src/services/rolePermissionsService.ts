import { getCsrfToken } from '../utils/csrfUtils';
import { toApiError } from './apiError';

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
    throw await toApiError(res, '获取角色ID列表失败');
  }

  return res.json();
}

export async function listRolePermissionsByRole(roleId: number): Promise<RolePermissionViewDTO[]> {
  const res = await fetch(`${API_BASE_URL}/role/${roleId}`, {
    method: 'GET',
    credentials: 'include',
  });

  if (!res.ok) {
    throw await toApiError(res, '获取角色权限矩阵失败');
  }

  return res.json();
}

/**
 * 创建新角色并写入权限矩阵（roleId 由后端自动生成）。
 * - list 传入 allow/deny 项；不传的权限默认为 UNSET。
 */
export async function createRoleWithMatrix(
  list: Omit<RolePermissionUpsertDTO, 'roleId'>[],
  opts?: { adminReason?: string },
): Promise<RolePermissionViewDTO[]> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE_URL}/role`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      ...(opts?.adminReason ? { 'X-Admin-Reason': opts.adminReason } : {}),
    },
    credentials: 'include',
    body: JSON.stringify(list),
  });

  if (!res.ok) {
    throw await toApiError(res, '创建角色失败');
  }

  return res.json();
}

/** 覆盖更新某 roleId 的权限矩阵（全量提交 allow/deny 配置；空数组表示清空）。 */
export async function replaceRolePermissions(
  roleId: number,
  list: RolePermissionUpsertDTO[],
  opts?: { adminReason?: string },
): Promise<RolePermissionViewDTO[]> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE_URL}/role/${encodeURIComponent(String(roleId))}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      ...(opts?.adminReason ? { 'X-Admin-Reason': opts.adminReason } : {}),
    },
    credentials: 'include',
    body: JSON.stringify(list),
  });

  if (!res.ok) {
    throw await toApiError(res, '保存角色权限矩阵失败');
  }

  return res.json();
}

export async function upsertRolePermission(dto: RolePermissionUpsertDTO, opts?: { adminReason?: string }): Promise<RolePermissionViewDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(API_BASE_URL, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
      ...(opts?.adminReason ? { 'X-Admin-Reason': opts.adminReason } : {}),
    },
    credentials: 'include',
    body: JSON.stringify(dto),
  });

  if (!res.ok) {
    throw await toApiError(res, '更新角色权限失败');
  }

  return res.json();
}

export async function deleteRolePermission(roleId: number, permissionId: number, opts?: { adminReason?: string }): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(
    `${API_BASE_URL}?roleId=${encodeURIComponent(String(roleId))}&permissionId=${encodeURIComponent(String(permissionId))}`,
    {
      method: 'DELETE',
      headers: {
        'X-XSRF-TOKEN': csrfToken,
        ...(opts?.adminReason ? { 'X-Admin-Reason': opts.adminReason } : {}),
      },
      credentials: 'include',
    },
  );

  if (!res.ok) {
    throw await toApiError(res, '删除角色权限失败');
  }
}

export async function clearRolePermissions(roleId: number, opts?: { adminReason?: string }): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${API_BASE_URL}/role/${encodeURIComponent(String(roleId))}`, {
    method: 'DELETE',
    headers: {
      'X-XSRF-TOKEN': csrfToken,
      ...(opts?.adminReason ? { 'X-Admin-Reason': opts.adminReason } : {}),
    },
    credentials: 'include',
  });

  if (!res.ok) {
    throw await toApiError(res, '清空角色权限失败');
  }
}

export async function listRoleSummaries(): Promise<RoleSummaryDTO[]> {
  const res = await fetch(`${API_BASE_URL}/role-summaries`, {
    method: 'GET',
    credentials: 'include',
  });

  if (!res.ok) {
    throw await toApiError(res, '获取角色列表失败');
  }

  return res.json();
}
