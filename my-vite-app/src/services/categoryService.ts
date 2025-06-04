// src/services/categoryService.ts
import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = '/api/categories';

export interface CategoryDTO {
  id?: number;
  name: string;
  description: string;
}

export async function fetchCategories(): Promise<CategoryDTO[]> {
  try {
    console.log('开始获取分类数据...');

    // 获取CSRF令牌
    console.log('正在获取CSRF令牌...');
    const csrfToken = await getCsrfToken();
    console.log('CSRF令牌获取结果:', csrfToken ? '成功' : '失败');

    // 检查用户是否登录
    const storedCookie = document.cookie;
    console.log('当前Cookie:', storedCookie);
    const isLoggedIn = document.cookie.includes('JSESSIONID');
    console.log('Cookie中是否包含JSESSIONID:', isLoggedIn);

    if (!isLoggedIn) {
      console.warn('可能未登录，尝试继续请求...');
    }

    console.log('请求URL:', API_BASE);
    console.log('请求头:', {
      'X-CSRF-TOKEN': csrfToken,
      'credentials': 'include'
    });

    const res = await fetch(API_BASE, {
      credentials: 'include', // 确保发送会话cookie
      headers: {
        'X-CSRF-TOKEN': csrfToken
      }
    });

    // 详细记录响应情况
    console.log('响应状态码:', res.status);
    console.log('响应状态文本:', res.statusText);
    console.log('响应头:', [...res.headers.entries()].reduce((obj, [key, val]) => {
      obj[key] = val;
      return obj;
    }, {} as Record<string, string>));

    // 克隆响应以便可以多次读取body
    const resClone = res.clone();

    try {
      // 尝试以文本形式读取响应以便调试
      const textResponse = await resClone.text();
      console.log('响应原始文本:', textResponse);
    } catch (textError) {
      console.error('读取响应文本时出错:', textError);
    }

    if (!res.ok) {
      if (res.status === 401) {
        console.error('发生认证错误，将重定向到登录页');
        console.error('响应详情:', {
          status: res.status,
          statusText: res.statusText,
          type: res.type,
          url: res.url
        });
        // 如果希望自动跳转到登录页，可以取消下面的注释
        // window.location.href = '/login';
        throw new Error('您的会话可能已过期，请重新登录');
      }
      throw new Error(`获取分类列表失败: ${res.status} ${res.statusText}`);
    }

    const data = await res.json();
    console.log('成功获取分类数据:', data);
    return data;
  } catch (error) {
    console.error('获取分类列表时发生错误:', error);
    console.error('错误详情:', {
      message: error instanceof Error ? error.message : String(error),
      stack: error instanceof Error ? error.stack : undefined
    });
    throw error;
  }
}

export async function createCategory(data: CategoryDTO): Promise<CategoryDTO> {
  try {
    // 获取CSRF令牌
    const csrfToken = await getCsrfToken();

    const res = await fetch(API_BASE, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfToken
      },
      credentials: 'include',
      body: JSON.stringify(data),
    });

    if (!res.ok) {
      if (res.status === 401) {
        console.error('发生认证错误，将重定向到登录页');
        // window.location.href = '/login';
        throw new Error('您的会话可能已过期，请重新登录');
      }
      throw new Error('创建分类失败');
    }
    return res.json();
  } catch (error) {
    console.error('创建分类时发生错误:', error);
    throw error;
  }
}

export async function updateCategory(id: number, data: CategoryDTO): Promise<CategoryDTO> {
  try {
    // 获取CSRF令牌
    const csrfToken = await getCsrfToken();

    const res = await fetch(`${API_BASE}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': csrfToken
      },
      credentials: 'include',
      body: JSON.stringify(data),
    });

    if (!res.ok) {
      if (res.status === 401) {
        console.error('发生认证错误，将重定向到登录页');
        // window.location.href = '/login';
        throw new Error('您的会话可能已过期，请重新登录');
      }
      throw new Error('更新分类失败');
    }
    return res.json();
  } catch (error) {
    console.error('更新分类时发生错误:', error);
    throw error;
  }
}

export async function deleteCategory(id: number): Promise<void> {
  try {
    // 获取CSRF令牌
    const csrfToken = await getCsrfToken();

    const res = await fetch(`${API_BASE}/${id}`, {
      method: 'DELETE',
      credentials: 'include',
      headers: {
        'X-CSRF-TOKEN': csrfToken
      }
    });

    if (!res.ok) {
      if (res.status === 401) {
        console.error('发生认证错误，将重定向到登录页');
        // window.location.href = '/login';
        throw new Error('您的会话可能已过期，请重新登录');
      }
      throw new Error('删除分类失败');
    }
  } catch (error) {
    console.error('删除分类时发生错误:', error);
    throw error;
  }
}
