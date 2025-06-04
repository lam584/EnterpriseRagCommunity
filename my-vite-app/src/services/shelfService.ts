// src/services/shelfService.ts
import { getCsrfToken } from '../utils/csrfUtils';

const API_BASE = '/api/shelves';

export interface ShelfDTO {
  id?: number;
  shelfCode: string;
  locationDescription: string;
  capacity: number;
}

export async function fetchShelves(): Promise<ShelfDTO[]> {
  try {
    // 获取CSRF令牌
    const csrfToken = await getCsrfToken();

    // 检查用户是否登录
    const storedCookie = document.cookie.includes('JSESSIONID');
    if (!storedCookie) {
      console.warn('可能未登录，尝试继续请求...');
    }

    const res = await fetch(API_BASE, {
      credentials: 'include', // 确保发送会话cookie
      headers: {
        'X-CSRF-TOKEN': csrfToken
      }
    });

    if (!res.ok) {
      if (res.status === 401) {
        console.error('发生认证错误，将重定向到登录页');
        // 如果希望自动跳转到登录页，可以取消下面的注释
        // window.location.href = '/login';
        throw new Error('您的会话可能已过期，请重新登录');
      }
      throw new Error('获取书架列表失败');
    }
    return res.json();
  } catch (error) {
    console.error('获取书架列表时发生错误:', error);
    throw error;
  }
}

export async function createShelf(data: ShelfDTO): Promise<ShelfDTO> {
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
        console.error('发生认证错误，尝试重定向到登录页');
        // window.location.href = '/login';
        throw new Error('您的会话可能已过期，请重新登录');
      }
      throw new Error('创建书架失败');
    }
    return res.json();
  } catch (error) {
    console.error('创建书架时发生错误:', error);
    throw error;
  }
}

export async function updateShelf(id: number, data: ShelfDTO): Promise<ShelfDTO> {
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
        console.error('发生认证错误，尝试重定向到登录页');
        // window.location.href = '/login';
        throw new Error('您的会话可能已过期，请重新登录');
      }
      throw new Error('更新书架失败');
    }
    return res.json();
  } catch (error) {
    console.error('更新书架时发生错误:', error);
    throw error;
  }
}

export async function deleteShelf(id: number): Promise<void> {
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
        console.error('发生认证错误，尝试重定向到登录页');
        // window.location.href = '/login';
        throw new Error('您的会话可能已过期，请重新登录');
      }
      throw new Error('删除书架失败');
    }
  } catch (error) {
    console.error('删除书架时发生错误:', error);
    throw error;
  }
}
