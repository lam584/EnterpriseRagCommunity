/**
 * CSRF 工具函数
 * 用于获取和管理 CSRF 令牌
 */

let csrfToken: string | null = null;

/**
 * 获取 CSRF 令牌
 * 如果本地没有缓存或强制刷新，会从服务器获取一个新的令牌
 * @param forceRefresh 是否强制从服务器刷新令牌
 */
export async function getCsrfToken(forceRefresh: boolean = false): Promise<string> {
  // 如果已经有令牌且不需要强制刷新，直接返回
  if (csrfToken && !forceRefresh) {
    return csrfToken;
  }

  try {
    // 从服务器获取新令牌
    console.log('从服务器获取新的 CSRF 令牌...');
    const response = await fetch('/api/auth/csrf-token', {
      credentials: 'include' // 确保包含 cookies
    });

    if (!response.ok) {
      throw new Error(`获取 CSRF 令牌失败: ${response.status}`);
    }

    const data = await response.json();
    if (!data.token) {
      throw new Error('服务器响应中没有 CSRF 令牌');
    }

    // 缓存令牌
    csrfToken = data.token;
    console.log('成功获取 CSRF 令牌:', csrfToken);
    // 由于 csrfToken 在此时一定不为 null，这里使用非空断言运算符
    return csrfToken as string;
  } catch (error) {
    console.error('获取 CSRF 令牌时出错:', error);
    throw new Error('无法获取安全令牌，请刷新页面重试');
  }
}

/**
 * 清除缓存的 CSRF 令牌
 * 通常在登出或令牌过期时调用
 */
export function clearCsrfToken(): void {
  csrfToken = null;
}
