// src/services/UserRoleService.ts
// import { getCsrfToken, clearCsrfToken } from '../utils/csrfUtils';
import { getCsrfToken } from '../utils/csrfUtils';
const API_BASE = '/api/reader-permissions';

export interface ReaderPermissionDTO {
  id: number;
  roles?: string;      // 改成 roles，与后端一致
  description?: string;
  maxBorrowCount?: number;
  maxBorrowDays?: number;
  createdAt?: string;
  updatedAt?: string;
  // 添加权限相关字段
  canLogin?: boolean;
  canReserve?: boolean;
  canViewAnnouncement?: boolean;
  canViewHelpArticles?: boolean;
  canResetOwnPassword?: boolean;
  canBorrowReturnBooks?: boolean;
  allowEditProfile?: boolean;
}

// 获取所有读者权限
export async function fetchReaderPermissions(): Promise<ReaderPermissionDTO[]> {
  const res = await fetch(API_BASE, { credentials: 'include' });
  if (!res.ok) throw new Error('获取读者权限列表失败');
  return res.json();
}

// 获取读者权限详情
export async function fetchReaderPermissionById(id: number): Promise<ReaderPermissionDTO> {
  const res = await fetch(`${API_BASE}/${id}`, { credentials: 'include' });
  if (!res.ok) throw new Error('获取读者权限详情失败');
  return res.json();
}

// 创建读者权限
export async function createReaderPermission(permission: Partial<ReaderPermissionDTO>): Promise<ReaderPermissionDTO> {
  try {
    // 获取 CSRF 令牌
    const csrfToken = await getCsrfToken();

    const res = await fetch(API_BASE, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': csrfToken
      },
      body: JSON.stringify(permission)
    });

    if (!res.ok) {
      const errorData = await res.json().catch(() => ({ message: '创建读者权限失败' }));
      throw new Error(errorData.message || '创建读者权限失败');
    }

    return res.json();
  } catch (error) {
    console.error('创建读者权限时发生错误:', error);
    throw error;
  }
}

// 更新读者权限
export async function updateReaderPermission(id: number, permission: ReaderPermissionDTO): Promise<ReaderPermissionDTO> {
  // 获取 CSRF 令牌
  const csrfToken = await getCsrfToken();

  const res = await fetch(`${API_BASE}/${id}`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken
    },
    body: JSON.stringify(permission)
  });
  if (!res.ok) throw new Error('更新读者权限失败');
  return res.json();
}

// 删除读者权限
export async function deleteReaderPermission(id: number): Promise<void> {
  // 获取 CSRF 令牌
  const csrfToken = await getCsrfToken();

  const res = await fetch(`${API_BASE}/${id}`, {
    method: 'DELETE',
    credentials: 'include',
    headers: {
      'X-XSRF-TOKEN': csrfToken
    }
  });
  if (!res.ok) throw new Error('删除读者权限失败');
}
