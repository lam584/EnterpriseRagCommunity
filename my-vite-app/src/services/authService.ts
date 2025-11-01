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
  email: string;
  password: string;
  displayName: string;
  code?: string;
}

export async function login(username: string, password: string, csrfToken: string): Promise<AdminDTO> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-TOKEN': csrfToken // 添加 CSRF 令牌到请求头
    },
    credentials: 'include', // 确保包含凭证
    body: JSON.stringify({ email: username, password })
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({}));
    throw new Error((errorData && errorData.message) || '登录失败');
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
 * @param registerData 注册信息（仅 email、password、displayName）
 */
export async function registerInitialAdmin(registerData: InitialAdminRegisterRequest): Promise<void> {
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

  const data = await res.json().catch(() => ({}));

  if (!res.ok) {
    // 后端校验失败时可能返回字段错误映射，如 { displayName: '...' }
    const fieldErrMsg = typeof data === 'object' && data !== null
      ? (data.message || data.displayName || data.email || data.password)
      : undefined;
    throw new Error(fieldErrMsg || '注册初始管理员失败');
  }

  // 后端成功时使用 ApiResponse 包裹 { success, message, data }
  if (data && typeof data === 'object' && 'success' in data) {
    if (!data.success) {
      throw new Error(data.message || '注册初始管理员失败');
    }
    // 成功则无需返回值
    return;
  }

  // 如果不是标准包裹，直接返回
  return;
}
