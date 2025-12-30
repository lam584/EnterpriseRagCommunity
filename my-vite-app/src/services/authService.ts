// src/services/authService.ts
import { getCsrfToken, clearCsrfToken } from '../utils/csrfUtils';

export interface AdminDTO {
  id: number;
  email: string; 
  username: string; // 对齐：SQL users.username → DTO.username
  isDeleted: boolean; // 对齐：SQL users.is_deleted → DTO.isDeleted
  /**
   * 多租户：当前登录用户所属 tenantId（后端若未返回则为 undefined）。
   * 用于前端在创建角色/资源时自动补齐 tenantId。
   */
  tenantId?: number;
}

export interface InitialSetupStatusResponse {
  setupRequired: boolean;
}

export interface InitialAdminRegisterRequest {
  email: string;
  password: string;
  /**
   * 后端 RegisterRequest 必填字段为 username。
   * 前端页面仍可展示为“显示名称”，但提交时需要发 username。
   */
  username: string;
  code?: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  username: string;
}

export async function login(email: string, password: string, csrfToken: string): Promise<AdminDTO> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken // 添加 CSRF 令牌到请求头
    },
    credentials: 'include', // 确保包含凭证
    body: JSON.stringify({ email, password })
  });

  if (!res.ok) {
    const errorData = await res.json().catch(() => ({}));
    throw new Error((errorData && errorData.message) || '登录失败');
  }

  // 登录成功后，会话ID改变，CSRF令牌也会改变
  // 清除缓存的令牌，以便下次请求时获取新的令牌
  clearCsrfToken();

  return res.json();
}

export async function logout(): Promise<void> {
  // 获取 CSRF 令牌
  const csrfToken = await getCsrfToken();

  const res = await fetch('/api/auth/logout', {
    method: 'POST',
    headers: {
      'X-XSRF-TOKEN': csrfToken // 添加 CSRF 令牌到请求头
    },
    credentials: 'include' // 确保包含凭证
  });

  // 无论成功与否，都清除本地缓存的 CSRF 令牌
  clearCsrfToken();

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
      'X-XSRF-TOKEN': csrfToken
    },
    credentials: 'include',
    body: JSON.stringify(registerData)
  });

  const data = await res.json().catch(() => ({}));

  if (!res.ok) {
    // 后端校验失败时可能返回字段错误映射，如 { username: '...' }
    const fieldErrMsg = typeof data === 'object' && data !== null
      ? (data.message || data.username || data.email || data.password)
      : undefined;
    throw new Error(fieldErrMsg || '注册初始管理员失败');
  }

  // 后端成功时使用 ApiResponse 包裹 { success, message, data }
  if (data && typeof data === 'object' && 'success' in data) {
    if (!data.success) {
      throw new Error(data.message || '注册初始管理员失败');
    }
    return;
  }

  return;
}

/**
 * 普通用户注册（非“初始管理员”）。
 *
 * 后端约定：POST /api/auth/register
 */
export async function register(registerData: RegisterRequest): Promise<void> {
  const csrfToken = await getCsrfToken();

  const res = await fetch('/api/auth/register', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken
    },
    credentials: 'include',
    body: JSON.stringify(registerData)
  });

  const data = await res.json().catch(() => ({}));

  if (!res.ok) {
    const fieldErrMsg = typeof data === 'object' && data !== null
      ? (data.message || data.username || data.email || data.password)
      : undefined;
    throw new Error(fieldErrMsg || '注册失败');
  }

  // 若后端使用 ApiResponse 包装
  if (data && typeof data === 'object' && 'success' in data) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const api: any = data;
    if (!api.success) {
      throw new Error(api.message || '注册失败');
    }
  }
}
