// src/services/authService.ts
import { getCsrfToken } from '../utils/csrfUtils';

export interface AdminDTO {
  id: number;
  username: string;
  name: string;
  role: string;
}

export interface InitialSetupStatusResponse {
  setupRequired: boolean;
}

export interface InitialAdminRegisterRequest {
  account: string;
  password: string;
  email: string;
  phone?: string;
  sex?: string;
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

/**
 * 检查系统是否需要初始设置管理员
 * @returns 包含setupRequired字段的响应
 */
export async function checkInitialSetupStatus(): Promise<InitialSetupStatusResponse> {
  const res = await fetch('/api/auth/initial-setup-status', {
    method: 'GET',
    credentials: 'include'
  });

  if (!res.ok) {
    throw new Error('检查系统初始设置状态失败');
  }

  return res.json();
}

/**
 * 注册初始管理员账户
 * @param registerData 注册信息
 * @returns 注册成功的管理员信息
 */
export async function registerInitialAdmin(registerData: InitialAdminRegisterRequest): Promise<AdminDTO> {
  // 获取 CSRF 令牌
  const csrfToken = await getCsrfToken();

  const res = await fetch('/api/auth/register-initial-admin', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': csrfToken
    },
    credentials: 'include',
    body: JSON.stringify(registerData)
  });

  if (!res.ok) {
    const errorData = await res.json();
    throw new Error(errorData.message || '注册初始管理员失败');
  }

  return res.json();
}
