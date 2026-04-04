// src/services/authService.ts
import { getCsrfToken, clearCsrfToken } from '../utils/csrfUtils';
import { getBackendMessage, readJsonRecord, readNumberField, readStringField } from './serviceErrorUtils';

function extractRegisterErrorMessage(data: Record<string, unknown>): string | undefined {
  return getBackendMessage(data)
    || readStringField(data, 'username')
    || readStringField(data, 'email')
    || readStringField(data, 'password');
}

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

export interface TotpMasterKeySetupResult {
  envVarName?: string;
  keyBase64?: string;
  attempted?: boolean;
  succeeded?: boolean;
  scope?: 'SYSTEM' | 'USER' | string;
  command?: string;
  fallbackCommand?: string;
  message?: string;
  error?: string;
  restartRequired?: boolean;
}

export interface InitialAdminRegisterResponse {
  user?: AdminDTO;
  totpMasterKeySetup?: TotpMasterKeySetupResult;
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
    const msg = errorData && typeof errorData === 'object' ? (errorData as any).message : undefined;
    const message = typeof msg === 'string' ? msg : '登录失败';
    const err = new Error(message) as Error & {
      code?: string;
      email?: string;
      methods?: string[];
      resendWaitSeconds?: number;
      codeTtlSeconds?: number;
      totpDigits?: number;
    };
    if (errorData && typeof errorData === 'object') {
      const code = (errorData as any).code;
      const respEmail = (errorData as any).email;
      const methods = (errorData as any).methods;
      const resendWaitSeconds = (errorData as any).resendWaitSeconds;
      const codeTtlSeconds = (errorData as any).codeTtlSeconds;
      const totpDigits = (errorData as any).totpDigits;
      if (typeof code === 'string') err.code = code;
      if (typeof respEmail === 'string') err.email = respEmail;
      if (Array.isArray(methods)) err.methods = methods.filter((x: unknown) => typeof x === 'string');
      if (typeof resendWaitSeconds === 'number') err.resendWaitSeconds = resendWaitSeconds;
      if (typeof codeTtlSeconds === 'number') err.codeTtlSeconds = codeTtlSeconds;
      if (typeof totpDigits === 'number') err.totpDigits = totpDigits;
    }
    throw err;
  }

  // 登录成功后，会话ID改变，CSRF令牌也会改变
  // 清除缓存的令牌，以便下次请求时获取新的令牌
  clearCsrfToken();

  return res.json();
}

export async function resendLogin2faEmail(): Promise<{
  message?: string;
  resendWaitSeconds?: number;
  codeTtlSeconds?: number;
}> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/auth/login/2fa/resend-email', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
  });

  const data = await readJsonRecord(res);
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '发送失败');
  }

  return {
    message: getBackendMessage(data),
    resendWaitSeconds: readNumberField(data, 'resendWaitSeconds'),
    codeTtlSeconds: readNumberField(data, 'codeTtlSeconds'),
  };
}

export async function verifyLogin2fa(method: 'email' | 'totp', code: string): Promise<AdminDTO> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/auth/login/2fa/verify', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ method, code }),
  });

  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    const msg = data && typeof data === 'object' && 'message' in data ? (data as any).message : undefined;
    throw new Error(typeof msg === 'string' ? msg : '验证失败');
  }

  clearCsrfToken();
  return data as AdminDTO;
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
export async function registerInitialAdmin(registerData: InitialAdminRegisterRequest): Promise<InitialAdminRegisterResponse> {
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
    return (data.data ?? {}) as InitialAdminRegisterResponse;
  }

  return data as InitialAdminRegisterResponse;
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

  const data = await readJsonRecord(res);

  if (!res.ok) {
    throw new Error(extractRegisterErrorMessage(data) || '注册失败');
  }

  // 若后端使用 ApiResponse 包装
  if ('success' in data) {
    if (!data.success) {
      throw new Error(getBackendMessage(data) || '注册失败');
    }
  }
}

export async function getRegistrationStatus(): Promise<{ registrationEnabled: boolean }> {
  const res = await fetch('/api/auth/registration-status', {
    method: 'GET',
    credentials: 'include',
  });
  const data = await readJsonRecord(res);
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '获取注册状态失败');
  }
  return { registrationEnabled: data?.registrationEnabled !== false };
}

export async function registerAndGetStatus(registerData: RegisterRequest): Promise<{ message?: string; status?: string }> {
  const csrfToken = await getCsrfToken();

  const res = await fetch('/api/auth/register', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify(registerData),
  });

  const data = await readJsonRecord(res);
  if (!res.ok) {
    throw new Error(extractRegisterErrorMessage(data) || '注册失败');
  }

  if ('success' in data) {
    if (!data.success) {
      throw new Error(getBackendMessage(data) || '注册失败');
    }
    const apiData = data.data;
    const status = apiData && typeof apiData === 'object' ? (apiData as Record<string, unknown>).status : undefined;
    const message = getBackendMessage(data);
    return { message: typeof message === 'string' ? message : undefined, status: typeof status === 'string' ? status : undefined };
  }

  return {};
}

export async function verifyRegister(email: string, code: string): Promise<void> {
  const res = await fetch('/api/auth/register/verify', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify({ email, code }),
  });

  const data = await readJsonRecord(res);
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '激活失败');
  }

  if ('success' in data) {
    if (!data.success) {
      throw new Error(getBackendMessage(data) || '激活失败');
    }
  }
}

export async function resendRegisterCode(email: string): Promise<{
  message?: string;
  resendWaitSeconds?: number;
  codeTtlSeconds?: number;
}> {
  const csrfToken = await getCsrfToken();
  const res = await fetch('/api/auth/register/resend-code', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrfToken,
    },
    credentials: 'include',
    body: JSON.stringify({ email }),
  });

  const data = await readJsonRecord(res);
  if (!res.ok) {
    throw new Error(getBackendMessage(data) || '发送失败');
  }

  return {
    message: getBackendMessage(data),
    resendWaitSeconds: readNumberField(data, 'resendWaitSeconds'),
    codeTtlSeconds: readNumberField(data, 'codeTtlSeconds'),
  };
}
