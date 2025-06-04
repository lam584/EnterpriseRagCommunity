// src/services/authService.ts
import { getCsrfToken } from '../utils/csrfUtils';

export interface AdminDTO {
  id: number;
  username: string;
  name: string;
  role: string;
}

export async function login(username: string, password: string, csrfToken: string): Promise<AdminDTO> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': csrfToken // 添加 CSRF 令牌到请求头
    },
    credentials: 'include', // 确保包含凭证
    body: JSON.stringify({ username, password })
  });

  if (!res.ok) {
    const errorData = await res.json();
    throw new Error(errorData.message || '登录失败');
  }

  return res.json();
}

export async function logout(): Promise<void> {
  // 获取 CSRF 令牌
  const csrfToken = await getCsrfToken();

  const res = await fetch('/api/auth/logout', {
    method: 'POST',
    headers: {
      'X-CSRF-TOKEN': csrfToken // 添加 CSRF 令牌到请求头
    },
    credentials: 'include' // 确保包含凭证
  });

  if (!res.ok) {
    const errorData = await res.json();
    throw new Error(errorData.message || '退出登录失败');
  }
}

export async function getCurrentAdmin(): Promise<AdminDTO> {
  const res = await fetch('/api/auth/current-admin', {
    credentials: 'include' // 确保包含凭证
  });

  if (!res.ok) throw new Error('获取当前登录管理员信息失败');
  return res.json();
}
