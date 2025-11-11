// src/services/accountService.ts
import { getCsrfToken } from '../utils/csrfUtils';

export interface AdminAccountInfo {
  id: number;
  account: string;
  email: string;
  phone: string;
  sex: string;
}

export interface UpdateAccountRequest {
  phone?: string;
  email?: string;
  sex?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

const BASE_URL = '/api/account';

export async function getAccountInfo(): Promise<AdminAccountInfo> {
  const res = await fetch(`${BASE_URL}/me`, {
    method: 'GET',
    credentials: 'include',
  });
  if (!res.ok) {
    throw new Error('获取账户信息失败');
  }
  return res.json();
}

export async function updateAccountInfo(body: UpdateAccountRequest): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${BASE_URL}/me`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || '更新账户信息失败');
  }
}

export async function changePassword(body: ChangePasswordRequest): Promise<void> {
  const csrfToken = await getCsrfToken();
  const res = await fetch(`${BASE_URL}/password`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || '修改密码失败');
  }
}

